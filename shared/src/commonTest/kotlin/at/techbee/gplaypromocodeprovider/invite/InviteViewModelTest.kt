package at.techbee.gplaypromocodeprovider.invite

import at.techbee.gplaypromocodeprovider.api.AvailableBatch
import at.techbee.gplaypromocodeprovider.client.ApiClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockEngineConfig
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
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
import kotlin.test.assertNull
import kotlin.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class InviteViewModelTest {

    private val expiry = Instant.parse("2027-01-31T23:00:00Z")
    private val batch7 = AvailableBatch(7, "Beta wave 2", expiry)
    private val batch8 = AvailableBatch(8, null, expiry)
    private val inviteJson =
        """{"appName":"My App","batches":[{"id":7,"description":"Beta wave 2","expiresAt":"2027-01-31T23:00:00Z"},""" +
            """{"id":8,"description":null,"expiresAt":"2027-01-31T23:00:00Z"}]}"""

    private val requests = mutableListOf<HttpRequestData>()

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = InviteViewModel(
        token = "tok",
        api = ApiClient(
            "https://promo.example.com",
            MockEngine(
                MockEngineConfig().apply {
                    // Run the handlers on the test dispatcher (set as Main), not on Ktor's IO threads:
                    // single-threaded on every platform, so the tests' plain variables need no locking.
                    dispatcher = Dispatchers.Main
                    addHandler { request ->
                        requests += request
                        handler(request)
                    }
                },
            ),
        ),
    )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private fun MockRequestHandleScope.error(status: HttpStatusCode, code: String) =
        json("""{"error":"$code","message":"x"}""", status)

    private val HttpRequestData.isInvite get() = method == HttpMethod.Get
    private val HttpRequestData.isCodeRequest get() = method == HttpMethod.Post

    private suspend fun InviteViewModel.awaitState(predicate: (InviteUiState) -> Boolean) = state.first(predicate)

    private suspend fun InviteViewModel.awaitLoaded() =
        awaitState { it.content is InviteContent.Loaded }.content as InviteContent.Loaded

    @Test
    fun loadsAppNameAndBatches() = runTest {
        val vm = viewModel { json(inviteJson) }

        assertEquals(InviteContent.Loaded("My App", listOf(batch7, batch8)), vm.awaitLoaded())
        assertEquals("/api/invite/tok", requests.single().url.encodedPath)
    }

    @Test
    fun appWithoutBatchesLoadsAnEmptyList() = runTest {
        val vm = viewModel { json("""{"appName":"My App","batches":[]}""") }

        assertEquals(emptyList(), vm.awaitLoaded().batches)
    }

    @Test
    fun unknownTokenShowsTheInvalidLinkState() = runTest {
        val vm = viewModel { error(HttpStatusCode.NotFound, "not_found") }

        vm.awaitState { it.content == InviteContent.InvalidLink }
    }

    @Test
    fun networkErrorWhileLoadingCanBeRetried() = runTest {
        var fail = true
        val vm = viewModel { if (fail) throw RuntimeException("offline") else json(inviteJson) }

        vm.awaitState { it.content == InviteContent.LoadFailed(FailureReason.NETWORK) }
        fail = false
        vm.retry()

        vm.awaitLoaded()
    }

    @Test
    fun serverErrorWhileLoadingIsReported() = runTest {
        val vm = viewModel { error(HttpStatusCode.InternalServerError, "internal_error") }

        vm.awaitState { it.content == InviteContent.LoadFailed(FailureReason.SERVER) }
    }

    @Test
    fun requestedCodesAreCollectedNewestFirst() = runTest {
        var n = 0
        val vm = viewModel { if (it.isInvite) json(inviteJson) else json("""{"code":"CODE-${++n}"}""") }
        vm.awaitLoaded()

        vm.requestCode(7)
        vm.awaitState { it.receivedCodes.size == 1 && !it.refreshing }
        vm.requestCode(8)
        val state = vm.awaitState { it.receivedCodes.size == 2 && !it.refreshing }

        assertEquals(
            listOf(ReceivedCode("CODE-2", null, expiry), ReceivedCode("CODE-1", "Beta wave 2", expiry)),
            state.receivedCodes,
        )
        assertNull(state.requestingBatchId)
        assertEquals("/api/invite/tok/batches/7/code", requests.filter { it.isCodeRequest }[0].url.encodedPath)
    }

    @Test
    fun secondRequestIsIgnoredWhileOneIsRunning() = runTest {
        val release = CompletableDeferred<Unit>()
        val vm = viewModel {
            if (it.isInvite) {
                json(inviteJson)
            } else {
                release.await()
                json("""{"code":"CODE-1"}""")
            }
        }
        vm.awaitLoaded()

        vm.requestCode(7)
        vm.awaitState { it.requestingBatchId == 7L }
        vm.requestCode(7)
        vm.requestCode(8)
        release.complete(Unit)
        vm.awaitState { it.receivedCodes.isNotEmpty() && it.requestingBatchId == null }

        assertEquals(1, requests.count { it.isCodeRequest })
        assertEquals(1, vm.state.value.receivedCodes.size)
    }

    @Test
    fun batchThatRanOutShowsANoticeAndRefreshesTheList() = runTest {
        var invites = 0
        val vm = viewModel {
            if (it.isInvite) {
                if (++invites == 1) json(inviteJson) else json("""{"appName":"My App","batches":[]}""")
            } else {
                error(HttpStatusCode.Conflict, "no_code_available")
            }
        }
        vm.awaitLoaded()

        vm.requestCode(7)
        val state = vm.awaitState { (it.content as? InviteContent.Loaded)?.batches?.isEmpty() == true }

        assertEquals(InviteNotice.BATCH_RAN_OUT, state.notice)
        assertNull(state.requestingBatchId)
        assertEquals(false, state.refreshing)
    }

    @Test
    fun batchThatRanOutIsRemovedBeforeTheRefreshAnswers() = runTest {
        val refreshAnswered = CompletableDeferred<Unit>()
        var invites = 0
        val vm = viewModel {
            if (it.isInvite) {
                if (++invites == 2) refreshAnswered.await()
                json(inviteJson)
            } else {
                error(HttpStatusCode.Conflict, "no_code_available")
            }
        }
        vm.awaitLoaded()

        vm.requestCode(7)
        val state = vm.awaitState { it.notice == InviteNotice.BATCH_RAN_OUT }

        assertEquals(listOf(batch8), (state.content as InviteContent.Loaded).batches)
        assertNull(state.requestingBatchId)
        refreshAnswered.complete(Unit)
    }

    @Test
    fun missingBatchShowsANoticeAndIsRemoved() = runTest {
        var invites = 0
        val vm = viewModel {
            if (it.isInvite) {
                if (++invites == 1) json(inviteJson) else throw RuntimeException("offline")
            } else {
                error(HttpStatusCode.NotFound, "not_found")
            }
        }
        vm.awaitLoaded()

        vm.requestCode(7)
        val state = vm.awaitState { invites == 2 && !it.refreshing }

        assertEquals(InviteNotice.BATCH_UNAVAILABLE, state.notice)
        assertEquals(InviteContent.Loaded("My App", listOf(batch8)), state.content)
        assertNull(state.requestingBatchId)
    }

    @Test
    fun revokedLinkDuringRequestShowsInvalidLinkButKeepsReceivedCodes() = runTest {
        var revoked = false
        val vm = viewModel {
            when {
                revoked -> error(HttpStatusCode.NotFound, "not_found")
                it.isInvite -> json(inviteJson)
                else -> json("""{"code":"CODE-1"}""")
            }
        }
        vm.awaitLoaded()
        vm.requestCode(7)
        vm.awaitState { it.receivedCodes.isNotEmpty() && !it.refreshing }

        revoked = true
        vm.requestCode(7)
        val state = vm.awaitState { it.content == InviteContent.InvalidLink }

        assertEquals(listOf("CODE-1"), state.receivedCodes.map { it.code })
    }

    @Test
    fun proxyAnsweringForTheServerDuringRequestSaysACodeMayBeLost() = runTest {
        val vm = viewModel {
            if (it.isInvite) json(inviteJson) else respond("Gateway Timeout", HttpStatusCode.GatewayTimeout)
        }
        vm.awaitLoaded()

        vm.requestCode(7)

        vm.awaitState { it.notice == InviteNotice.NO_ANSWER && it.requestingBatchId == null }
    }

    @Test
    fun proxyAnsweringForTheServerWhileLoadingIsANetworkError() = runTest {
        val vm = viewModel { respond("Bad Gateway", HttpStatusCode.BadGateway) }

        vm.awaitState { it.content == InviteContent.LoadFailed(FailureReason.NETWORK) }
    }

    @Test
    fun networkErrorDuringRequestShowsANoticeThatCanBeDismissed() = runTest {
        val vm = viewModel { if (it.isInvite) json(inviteJson) else throw RuntimeException("offline") }
        vm.awaitLoaded()

        vm.requestCode(7)
        vm.awaitState { it.notice == InviteNotice.NETWORK_ERROR && it.requestingBatchId == null }
        vm.dismissNotice()

        assertNull(vm.state.value.notice)
    }

    @Test
    fun serverErrorDuringRequestShowsANotice() = runTest {
        val vm = viewModel {
            if (it.isInvite) json(inviteJson) else error(HttpStatusCode.InternalServerError, "internal_error")
        }
        vm.awaitLoaded()

        vm.requestCode(7)

        vm.awaitState { it.notice == InviteNotice.SERVER_ERROR }
    }

    @Test
    fun failedRefreshKeepsTheListWithoutTheBatchThatRanOut() = runTest {
        var invites = 0
        val vm = viewModel {
            if (it.isInvite) {
                if (++invites == 1) json(inviteJson) else throw RuntimeException("offline")
            } else {
                error(HttpStatusCode.Conflict, "no_code_available")
            }
        }
        vm.awaitLoaded()

        vm.requestCode(7)
        val state = vm.awaitState { invites == 2 && !it.refreshing && it.notice == InviteNotice.BATCH_RAN_OUT }

        // The batch that ran out isn't offered again, although the refresh failed.
        assertEquals(InviteContent.Loaded("My App", listOf(batch8)), state.content)
    }

    @Test
    fun listIsRefreshedAfterACodeWasHandedOut() = runTest {
        var invites = 0
        val vm = viewModel {
            if (it.isInvite) {
                if (++invites == 1) {
                    json(inviteJson)
                } else {
                    json("""{"appName":"My App","batches":[{"id":8,"description":null,"expiresAt":"2027-01-31T23:00:00Z"}]}""")
                }
            } else {
                json("""{"code":"LAST-ONE"}""")
            }
        }
        vm.awaitLoaded()

        vm.requestCode(7)
        val state = vm.awaitState { invites == 2 && !it.refreshing }

        assertEquals(listOf(batch8), (state.content as InviteContent.Loaded).batches)
        assertEquals(listOf("LAST-ONE"), state.receivedCodes.map { it.code })
    }

    @Test
    fun requestIsIgnoredWhileTheListIsRefreshing() = runTest {
        val refreshAnswered = CompletableDeferred<Unit>()
        var invites = 0
        val vm = viewModel {
            if (it.isInvite) {
                if (++invites == 2) refreshAnswered.await()
                json(inviteJson)
            } else {
                json("""{"code":"CODE-1"}""")
            }
        }
        vm.awaitLoaded()
        vm.requestCode(7)
        vm.awaitState { it.receivedCodes.isNotEmpty() && it.refreshing }

        vm.requestCode(8)
        refreshAnswered.complete(Unit)
        vm.awaitState { !it.refreshing }

        assertEquals(1, requests.count { it.isCodeRequest })
    }
}
