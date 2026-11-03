package at.techbee.gplaypromocodeprovider.admin

import androidx.lifecycle.ViewModel
import at.techbee.gplaypromocodeprovider.api.ApiError
import at.techbee.gplaypromocodeprovider.client.ApiResult

/** Why an admin page couldn't load or an admin action failed. */
sealed interface ActionError {

    /** No answer from the server. */
    data object Network : ActionError

    /** The server answered with an unexpected error. */
    data object Server : ActionError

    /** The server rejected the input. [reason] comes from the server and is written for humans. */
    data class Rejected(val reason: String) : ActionError

    /** The admin session ended (expired, or the server restarted). The login form is shown meanwhile. */
    data object SessionExpired : ActionError
}

/** The text shown to the admin for this error. */
val ActionError.text: String
    get() = when (this) {
        ActionError.Network -> "The server can't be reached. Check your internet connection and try again."
        ActionError.Server -> "Something went wrong on the server. Please try again in a moment."
        is ActionError.Rejected -> reason
        ActionError.SessionExpired -> "Your session has ended. Please log in again."
    }

/**
 * Base of the admin page view models.
 *
 * @param onSessionExpired called when the server answers `UNAUTHORIZED`; the admin area then shows the
 *   login form. The page's view model is kept meanwhile (a pasted upload isn't lost), and the page
 *   calls [resumeAfterLogin] when it is shown again.
 */
abstract class AdminViewModel(private val onSessionExpired: () -> Unit) : ViewModel() {

    private var sessionExpired = false

    /**
     * Called by the page each time it is shown. After a login in between, [onLoggedInAgain] redoes
     * what failed because the session had ended.
     */
    fun resumeAfterLogin() {
        if (!sessionExpired) return
        sessionExpired = false
        onLoggedInAgain()
    }

    /** Reload what failed with [ActionError.SessionExpired] and clear those errors, so the admin can retry. */
    protected abstract fun onLoggedInAgain()

    /** The [ActionError] for a result that isn't a success. Reports an ended session. */
    protected fun errorOf(result: ApiResult<*>): ActionError = when (result) {
        is ApiResult.Success -> error("Not an error: $result")
        is ApiResult.NetworkError -> ActionError.Network
        is ApiResult.Failure -> when (result.error) {
            ApiError.UNAUTHORIZED -> {
                sessionExpired = true
                onSessionExpired()
                ActionError.SessionExpired
            }
            ApiError.INVALID_REQUEST -> ActionError.Rejected(result.message)
            else -> ActionError.Server
        }
    }
}
