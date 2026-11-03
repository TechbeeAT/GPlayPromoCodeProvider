package at.techbee.gplaypromocodeprovider.admin

import at.techbee.gplaypromocodeprovider.admin.app.AppDetailContent
import at.techbee.gplaypromocodeprovider.admin.app.AppDetailUiState
import at.techbee.gplaypromocodeprovider.admin.app.AppDetailViewModel
import at.techbee.gplaypromocodeprovider.admin.app.RegenerateDialogState
import at.techbee.gplaypromocodeprovider.admin.app.UploadFormState
import at.techbee.gplaypromocodeprovider.admin.app.countCodes
import at.techbee.gplaypromocodeprovider.api.AppRequest
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class AppDetailViewModelTest {

    private val pickedDay = Instant.parse("2027-01-31T00:00:00Z").toEpochMilliseconds()
    private val expiry = Instant.parse("2027-01-31T22:59:59Z")
    private var sessionExpiredCalls = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    /** A server for app 1 that answers the two GETs, and every other request through [other]. */
    private fun server(
        app: () -> String = { appJson() },
        batches: () -> String = { "[${batchJson()}]" },
        other: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData = { error(HttpStatusCode.NotFound, "not_found") },
    ) = MockServer {
        when {
            it.method == HttpMethod.Get && it.path == "/api/admin/apps/1" -> json(app())
            it.method == HttpMethod.Get && it.path == "/api/admin/apps/1/batches" -> json(batches())
            else -> other(it)
        }
    }

    private fun viewModel(server: MockServer) =
        AppDetailViewModel(1, server.api, onSessionExpired = { sessionExpiredCalls++ }, endOfDay = { expiry })

    private suspend fun AppDetailViewModel.await(predicate: (AppDetailUiState) -> Boolean) = state.first(predicate)

    private suspend fun AppDetailViewModel.awaitLoaded() =
        await { it.content is AppDetailContent.Loaded && !it.refreshing }.content as AppDetailContent.Loaded

    private fun AppDetailViewModel.fillForm(codes: String = "AAA\nBBB") {
        setDescription("  Beta wave 2 ")
        setExpiryDate(pickedDay)
        setCodes(codes)
    }

    @Test
    fun loadsTheAppAndItsBatches() = runTest {
        val loaded = viewModel(server()).awaitLoaded()

        assertEquals("tok", loaded.app.inviteToken)
        assertEquals(listOf(5L), loaded.batches.map { it.id })
    }

    @Test
    fun unknownAppIsNotFound() = runTest {
        val vm = viewModel(MockServer { error(HttpStatusCode.NotFound, "not_found") })

        vm.await { it.content == AppDetailContent.NotFound }
    }

    @Test
    fun failedBatchListIsALoadError() = runTest {
        val vm = viewModel(
            MockServer { if (it.path.endsWith("/batches")) throw RuntimeException("offline") else json(appJson()) },
        )

        vm.await { it.content == AppDetailContent.LoadFailed(ActionError.Network) }
    }

    @Test
    fun editedAppReplacesTheShownApp() = runTest {
        val server = server(other = { json(appJson(name = "Renamed")) })
        val vm = viewModel(server)
        vm.awaitLoaded()

        vm.openEditDialog()
        vm.saveApp(AppRequest("Renamed"))
        val state = vm.await { it.editDialog == null }

        assertEquals("Renamed", (state.content as AppDetailContent.Loaded).app.name)
        assertEquals(1, server.count(HttpMethod.Put, "/api/admin/apps/1"))
    }

    @Test
    fun regeneratingTheInviteLinkShowsTheNewToken() = runTest {
        val server = server(other = { json(appJson(token = "new-token")) })
        val vm = viewModel(server)
        vm.awaitLoaded()

        vm.askToRegenerateInviteLink()
        assertEquals(RegenerateDialogState(), vm.state.value.regenerateDialog)
        vm.regenerateInviteLink()
        val state = vm.await { it.regenerateDialog == null }

        assertEquals("new-token", (state.content as AppDetailContent.Loaded).app.inviteToken)
        assertEquals(1, server.count(HttpMethod.Post, "/api/admin/apps/1/invite-token"))
    }

    @Test
    fun inviteLinkIsOnlyRegeneratedAfterConfirmation() = runTest {
        val server = server()
        val vm = viewModel(server)
        vm.awaitLoaded()

        vm.regenerateInviteLink() // Not asked yet: ignored.
        vm.askToRegenerateInviteLink()
        vm.cancelRegenerateInviteLink()
        vm.regenerateInviteLink() // Cancelled: ignored.

        assertEquals(0, server.count(HttpMethod.Post, "/api/admin/apps/1/invite-token"))
        assertNull(vm.state.value.regenerateDialog)
    }

    @Test
    fun failedRegenerationStaysInTheDialog() = runTest {
        val vm = viewModel(server(other = { throw RuntimeException("offline") }))
        vm.awaitLoaded()

        vm.askToRegenerateInviteLink()
        vm.regenerateInviteLink()

        vm.await { it.regenerateDialog == RegenerateDialogState(error = ActionError.Network) }
        assertEquals("tok", (vm.state.value.content as AppDetailContent.Loaded).app.inviteToken)
    }

    @Test
    fun uploadNeedsAnExpiryDateAndCodes() = runTest {
        val server = server()
        val vm = viewModel(server)
        vm.awaitLoaded()

        vm.setCodes("AAA")
        assertFalse(vm.state.value.upload.canUpload)
        vm.upload()
        vm.setCodes(" \n ")
        vm.setExpiryDate(pickedDay)
        assertFalse(vm.state.value.upload.canUpload)
        vm.upload()

        assertEquals(0, server.requests.count { it.method == HttpMethod.Post })
    }

    @Test
    fun uploadSendsTheFormAndReportsAddedAndSkippedCodes() = runTest {
        var uploaded = false
        val server = server(
            app = { appJson(available = if (uploaded) 4 else 2) },
            batches = { if (uploaded) "[${batchJson(6, 2)},${batchJson()}]" else "[${batchJson()}]" },
            other = {
                uploaded = true
                json(
                    """{"batch":${batchJson(6, 2)},"addedCount":2,"duplicates":["OLD","BBB"]}""",
                    HttpStatusCode.Created,
                )
            },
        )
        val vm = viewModel(server)
        vm.awaitLoaded()

        vm.fillForm(codes = "AAA\nOLD\n\nBBB\n  CCC  \nBBB")
        assertEquals(5, vm.state.value.upload.codeCount)
        vm.upload()
        val state = vm.await { it.upload.result != null && !it.refreshing && it.content is AppDetailContent.Loaded }

        val request = server.requests.single { it.method == HttpMethod.Post }
        assertEquals("/api/admin/apps/1/batches", request.path)
        assertEquals(
            """{"description":"Beta wave 2","expiresAt":"2027-01-31T22:59:59Z","codes":"AAA\nOLD\n\nBBB\n  CCC  \nBBB"}""",
            request.bodyText,
        )
        assertEquals(2, state.upload.result!!.addedCount)
        assertEquals(listOf("OLD", "BBB"), state.upload.result.duplicates)
        // The form is cleared, the statistics and the batch list are reloaded.
        assertEquals(UploadFormState(result = state.upload.result), state.upload)
        val loaded = state.content as AppDetailContent.Loaded
        assertEquals(4, loaded.app.stats.available)
        assertEquals(listOf(6L, 5L), loaded.batches.map { it.id })

        vm.dismissUploadResult()
        assertNull(vm.state.value.upload.result)
    }

    @Test
    fun blankDescriptionIsLeftOut() = runTest {
        val server = server(other = { json("""{"batch":${batchJson(6)},"addedCount":1,"duplicates":[]}""", HttpStatusCode.Created) })
        val vm = viewModel(server)
        vm.awaitLoaded()

        vm.fillForm(codes = "AAA")
        vm.setDescription("   ")
        vm.upload()
        vm.await { it.upload.result != null }

        assertEquals(
            """{"expiresAt":"2027-01-31T22:59:59Z","codes":"AAA"}""",
            server.requests.single { it.method == HttpMethod.Post }.bodyText,
        )
    }

    @Test
    fun rejectedUploadKeepsTheFormAndShowsTheReason() = runTest {
        val vm = viewModel(
            server(other = { error(HttpStatusCode.BadRequest, "invalid_request", "All 2 codes already exist for this app") }),
        )
        vm.awaitLoaded()

        vm.fillForm()
        vm.upload()
        val upload = vm.await { it.upload.error != null }.upload

        assertEquals(ActionError.Rejected("All 2 codes already exist for this app"), upload.error)
        assertEquals("AAA\nBBB", upload.codes)
        assertFalse(upload.uploading)
    }

    @Test
    fun secondUploadIsIgnoredWhileOneIsRunning() = runTest {
        val release = CompletableDeferred<Unit>()
        val server = server(other = {
            release.await()
            json("""{"batch":${batchJson(6)},"addedCount":2,"duplicates":[]}""", HttpStatusCode.Created)
        })
        val vm = viewModel(server)
        vm.awaitLoaded()

        vm.fillForm()
        vm.upload()
        vm.await { it.upload.uploading }
        vm.setCodes("CHANGED") // The form is locked while uploading.
        vm.upload()
        release.complete(Unit)
        vm.await { it.upload.result != null }

        assertEquals(1, server.requests.count { it.method == HttpMethod.Post })
    }

    @Test
    fun uploadAfterTheSessionEndedKeepsTheFormForAfterLogin() = runTest {
        var loggedIn = true
        val server = server(other = {
            if (loggedIn) {
                json("""{"batch":${batchJson(6)},"addedCount":2,"duplicates":[]}""", HttpStatusCode.Created)
            } else {
                error(HttpStatusCode.Unauthorized, "unauthorized")
            }
        })
        val vm = viewModel(server)
        vm.awaitLoaded()
        loggedIn = false

        vm.fillForm()
        vm.upload()
        vm.await { it.upload.error == ActionError.SessionExpired }
        assertEquals(1, sessionExpiredCalls)

        loggedIn = true
        vm.resumeAfterLogin()
        assertNull(vm.state.value.upload.error)
        assertEquals("AAA\nBBB", vm.state.value.upload.codes)
        vm.upload()

        assertEquals(2, vm.await { it.upload.result != null }.upload.result!!.addedCount)
    }

    @Test
    fun codesAreCountedLikeTheServerParsesThem() {
        assertEquals(0, countCodes(""))
        assertEquals(0, countCodes(" \n\t\n"))
        assertEquals(3, countCodes("A\r\n\r\n  B  \nC\n"))
    }
}
