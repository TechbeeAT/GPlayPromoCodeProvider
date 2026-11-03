package at.techbee.gplaypromocodeprovider.admin

import at.techbee.gplaypromocodeprovider.admin.session.AdminSessionState
import at.techbee.gplaypromocodeprovider.admin.session.AdminSessionViewModel
import at.techbee.gplaypromocodeprovider.admin.session.LoginError
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
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AdminSessionViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(StandardTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun AdminSessionViewModel.await(predicate: (AdminSessionState) -> Boolean) = state.first(predicate)

    @Test
    fun existingSessionIsLoggedIn() = runTest {
        val server = MockServer { json("""{"username":"admin"}""") }
        val vm = AdminSessionViewModel(server.api)

        assertEquals(AdminSessionState.LoggedIn("admin"), vm.await { it is AdminSessionState.LoggedIn })
        assertEquals("/api/admin/session", server.requests.single().path)
    }

    @Test
    fun noSessionShowsTheLoginForm() = runTest {
        val vm = AdminSessionViewModel(MockServer { error(HttpStatusCode.Unauthorized, "unauthorized") }.api)

        assertEquals(AdminSessionState.LoggedOut(), vm.await { it is AdminSessionState.LoggedOut })
    }

    @Test
    fun failedSessionCheckCanBeRetried() = runTest {
        var fail = true
        val vm = AdminSessionViewModel(
            MockServer { if (fail) throw RuntimeException("offline") else error(HttpStatusCode.Unauthorized, "unauthorized") }.api,
        )

        vm.await { it == AdminSessionState.CheckFailed(ActionError.Network) }
        fail = false
        vm.retry()

        vm.await { it is AdminSessionState.LoggedOut }
    }

    @Test
    fun loginWithTheRightCredentials() = runTest {
        val server = MockServer {
            if (it.path == "/api/admin/login") noContent() else error(HttpStatusCode.Unauthorized, "unauthorized")
        }
        val vm = AdminSessionViewModel(server.api)
        vm.await { it is AdminSessionState.LoggedOut }

        vm.login("admin", "secret")

        assertEquals(AdminSessionState.LoggedIn("admin"), vm.await { it is AdminSessionState.LoggedIn })
        val login = server.requests.single { it.path == "/api/admin/login" }
        assertEquals("""{"username":"admin","password":"secret"}""", login.bodyText)
    }

    @Test
    fun wrongCredentialsAreReported() = runTest {
        val vm = AdminSessionViewModel(MockServer { error(HttpStatusCode.Unauthorized, "unauthorized") }.api)
        vm.await { it is AdminSessionState.LoggedOut }

        vm.login("admin", "wrong")

        vm.await { it == AdminSessionState.LoggedOut(error = LoginError.WRONG_CREDENTIALS) }
    }

    @Test
    fun networkErrorDuringLoginIsReported() = runTest {
        val vm = AdminSessionViewModel(
            MockServer { if (it.path == "/api/admin/login") throw RuntimeException("offline") else error(HttpStatusCode.Unauthorized, "unauthorized") }.api,
        )
        vm.await { it is AdminSessionState.LoggedOut }

        vm.login("admin", "secret")

        vm.await { it == AdminSessionState.LoggedOut(error = LoginError.NETWORK) }
    }

    @Test
    fun emptyFieldsDontSendALogin() = runTest {
        val server = MockServer { error(HttpStatusCode.Unauthorized, "unauthorized") }
        val vm = AdminSessionViewModel(server.api)
        vm.await { it is AdminSessionState.LoggedOut }

        vm.login(" ", "secret")
        vm.login("admin", "")

        assertEquals(1, server.requests.size) // Only the session check.
    }

    @Test
    fun logoutShowsTheLoginForm() = runTest {
        val server = MockServer { if (it.path == "/api/admin/logout") noContent() else json("""{"username":"admin"}""") }
        val vm = AdminSessionViewModel(server.api)
        vm.await { it is AdminSessionState.LoggedIn }

        vm.logout()

        assertEquals(AdminSessionState.LoggedOut(), vm.await { it is AdminSessionState.LoggedOut })
        assertEquals(1, server.count(HttpMethod.Post, "/api/admin/logout"))
    }

    @Test
    fun failedLogoutKeepsTheAdminLoggedIn() = runTest {
        val vm = AdminSessionViewModel(
            MockServer { if (it.path == "/api/admin/logout") throw RuntimeException("offline") else json("""{"username":"admin"}""") }.api,
        )
        vm.await { it is AdminSessionState.LoggedIn }

        vm.logout()
        vm.await { it == AdminSessionState.LoggedIn("admin", logoutError = ActionError.Network) }
        vm.dismissLogoutError()

        assertEquals(AdminSessionState.LoggedIn("admin"), vm.state.value)
    }

    @Test
    fun endedSessionShowsTheLoginFormWithAHint() = runTest {
        val vm = AdminSessionViewModel(MockServer { json("""{"username":"admin"}""") }.api)
        vm.await { it is AdminSessionState.LoggedIn }

        vm.sessionExpired()

        val state = vm.state.value
        assertTrue(state is AdminSessionState.LoggedOut && state.sessionExpired)
    }
}
