package at.techbee.gplaypromocodeprovider.admin.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.api.ApiError
import at.techbee.gplaypromocodeprovider.client.ApiClient
import at.techbee.gplaypromocodeprovider.client.ApiResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface AdminSessionState {

    /** Asking the server whether the browser already has a session (on page load). */
    data object Checking : AdminSessionState

    /** The session check failed; the admin can retry. */
    data class CheckFailed(val error: ActionError) : AdminSessionState

    /**
     * Not logged in: the login form.
     *
     * @param sessionExpired the admin was logged in, but the session ended (shown as a hint on the form)
     */
    data class LoggedOut(
        val loggingIn: Boolean = false,
        val error: LoginError? = null,
        val sessionExpired: Boolean = false,
    ) : AdminSessionState

    data class LoggedIn(
        val username: String,
        val loggingOut: Boolean = false,
        val logoutError: ActionError? = null,
    ) : AdminSessionState
}

enum class LoginError { WRONG_CREDENTIALS, NETWORK, SERVER }

/**
 * The admin's login state, shared by all admin pages. Checks for an existing session on creation.
 * The password is passed through to the server and never kept.
 */
class AdminSessionViewModel(private val api: ApiClient) : ViewModel() {

    private val _state = MutableStateFlow<AdminSessionState>(AdminSessionState.Checking)
    val state: StateFlow<AdminSessionState> = _state.asStateFlow()

    init {
        checkSession()
    }

    fun retry() = checkSession()

    /** Ignored while a login is running or when a field is empty. */
    fun login(username: String, password: String) {
        val current = _state.value as? AdminSessionState.LoggedOut ?: return
        if (current.loggingIn || username.isBlank() || password.isEmpty()) return
        _state.value = current.copy(loggingIn = true, error = null)
        viewModelScope.launch {
            _state.value = when (val result = api.login(username, password)) {
                is ApiResult.Success -> AdminSessionState.LoggedIn(username)
                is ApiResult.Failure -> current.copy(
                    loggingIn = false,
                    error = if (result.error == ApiError.UNAUTHORIZED) LoginError.WRONG_CREDENTIALS else LoginError.SERVER,
                )
                is ApiResult.NetworkError -> current.copy(loggingIn = false, error = LoginError.NETWORK)
            }
        }
    }

    fun logout() {
        val current = _state.value as? AdminSessionState.LoggedIn ?: return
        if (current.loggingOut) return
        _state.value = current.copy(loggingOut = true, logoutError = null)
        viewModelScope.launch {
            val result = api.logout()
            _state.update {
                // Another call may have noticed meanwhile that the session ended.
                if (it !is AdminSessionState.LoggedIn) return@update it
                when (result) {
                    is ApiResult.Success -> AdminSessionState.LoggedOut()
                    is ApiResult.Failure -> it.copy(loggingOut = false, logoutError = ActionError.Server)
                    is ApiResult.NetworkError -> it.copy(loggingOut = false, logoutError = ActionError.Network)
                }
            }
        }
    }

    fun dismissLogoutError() = _state.update {
        if (it is AdminSessionState.LoggedIn) it.copy(logoutError = null) else it
    }

    /** A page got `UNAUTHORIZED`: show the login form. */
    fun sessionExpired() = _state.update {
        if (it is AdminSessionState.LoggedIn) AdminSessionState.LoggedOut(sessionExpired = true) else it
    }

    private fun checkSession() {
        _state.value = AdminSessionState.Checking
        viewModelScope.launch {
            _state.value = when (val result = api.getSession()) {
                is ApiResult.Success -> AdminSessionState.LoggedIn(result.value.username)
                is ApiResult.Failure ->
                    if (result.error == ApiError.UNAUTHORIZED) {
                        AdminSessionState.LoggedOut()
                    } else {
                        AdminSessionState.CheckFailed(ActionError.Server)
                    }
                is ApiResult.NetworkError -> AdminSessionState.CheckFailed(ActionError.Network)
            }
        }
    }
}
