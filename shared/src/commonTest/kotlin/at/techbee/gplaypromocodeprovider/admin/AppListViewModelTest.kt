package at.techbee.gplaypromocodeprovider.admin

import at.techbee.gplaypromocodeprovider.admin.apps.AppListContent
import at.techbee.gplaypromocodeprovider.admin.apps.AppListUiState
import at.techbee.gplaypromocodeprovider.admin.apps.AppListViewModel
import at.techbee.gplaypromocodeprovider.admin.components.AppDialogState
import at.techbee.gplaypromocodeprovider.api.AppRequest
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
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

@OptIn(ExperimentalCoroutinesApi::class)
class AppListViewModelTest {

    private var sessionExpiredCalls = 0

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(server: MockServer) = AppListViewModel(server.api, onSessionExpired = { sessionExpiredCalls++ })

    private suspend fun AppListViewModel.await(predicate: (AppListUiState) -> Boolean) = state.first(predicate)

    private suspend fun AppListViewModel.awaitApps() =
        (await { it.content is AppListContent.Loaded }.content as AppListContent.Loaded).apps

    @Test
    fun loadsTheApps() = runTest {
        val server = MockServer { json("[${appJson(1, "Alpha")},${appJson(2, "Beta")}]") }

        assertEquals(listOf("Alpha", "Beta"), viewModel(server).awaitApps().map { it.name })
        assertEquals("/api/admin/apps", server.requests.single().path)
    }

    @Test
    fun loadErrorCanBeRetried() = runTest {
        var fail = true
        val vm = viewModel(MockServer { if (fail) error(HttpStatusCode.InternalServerError, "internal_error") else json("[]") })

        vm.await { it.content == AppListContent.LoadFailed(ActionError.Server) }
        fail = false
        vm.retry()

        assertEquals(emptyList(), vm.awaitApps())
    }

    @Test
    fun createdAppIsAddedSortedByName() = runTest {
        val server = MockServer {
            if (it.method == HttpMethod.Post) json(appJson(3, "Beta"), HttpStatusCode.Created) else json("[${appJson(1, "Alpha")},${appJson(2, "Gamma")}]")
        }
        val vm = viewModel(server)
        vm.awaitApps()

        vm.openCreateDialog()
        vm.createApp(AppRequest("Beta", "com.example.beta", null))
        val state = vm.await { it.createDialog == null }

        assertEquals(listOf("Alpha", "Beta", "Gamma"), (state.content as AppListContent.Loaded).apps.map { it.name })
        assertEquals(
            """{"name":"Beta","packageName":"com.example.beta"}""",
            server.requests.single { it.method == HttpMethod.Post }.bodyText,
        )
    }

    @Test
    fun rejectedAppShowsTheServersReasonInTheDialog() = runTest {
        val vm = viewModel(
            MockServer {
                if (it.method == HttpMethod.Post) error(HttpStatusCode.BadRequest, "invalid_request", "Name must not be empty") else json("[]")
            },
        )
        vm.awaitApps()

        vm.openCreateDialog()
        vm.createApp(AppRequest(""))

        vm.await { it.createDialog == AppDialogState(error = ActionError.Rejected("Name must not be empty")) }
        vm.closeCreateDialog()
        assertNull(vm.state.value.createDialog)
    }

    @Test
    fun endedSessionIsReportedAndTheListReloadsAfterLogin() = runTest {
        var loggedIn = false
        val vm = viewModel(MockServer { if (loggedIn) json("[${appJson()}]") else error(HttpStatusCode.Unauthorized, "unauthorized") })

        vm.await { it.content == AppListContent.LoadFailed(ActionError.SessionExpired) }
        assertEquals(1, sessionExpiredCalls)

        loggedIn = true
        vm.resumeAfterLogin()

        assertEquals(1, vm.awaitApps().size)
    }

    @Test
    fun resumeWithoutEndedSessionDoesNothing() = runTest {
        val server = MockServer { json("[]") }
        val vm = viewModel(server)
        vm.awaitApps()

        vm.resumeAfterLogin()
        vm.resumeAfterLogin()

        assertEquals(1, server.requests.size)
    }
}
