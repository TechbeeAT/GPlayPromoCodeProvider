package at.techbee.gplaypromocodeprovider.client

import at.techbee.gplaypromocodeprovider.api.ApiError

/** Outcome of an [ApiClient] call. Calls never throw, except to propagate coroutine cancellation. */
sealed interface ApiResult<out T> {

    /** The server answered with a success status and the expected body. */
    data class Success<out T>(val value: T) : ApiResult<T>

    /**
     * The server answered, but not with success: an error status, or a body the client couldn't read.
     *
     * [error] is the code from the server's `ErrorResponse`. If the body isn't one (e.g. an error page
     * of a proxy), it is derived from [status]. [message] is meant for humans, not for logic.
     */
    data class Failure(val status: Int, val error: ApiError, val message: String) : ApiResult<Nothing>

    /**
     * No answer from the server: offline, server down, request blocked by the browser, ... Also when a
     * proxy in front of the server answers 502, 503 or 504 without an `ErrorResponse` ([ServerUnreachableException]).
     */
    data class NetworkError(val cause: Throwable) : ApiResult<Nothing>
}

/** The value of a [ApiResult.Success], otherwise `null`. */
fun <T> ApiResult<T>.getOrNull(): T? = (this as? ApiResult.Success)?.value
