package at.techbee.gplaypromocodeprovider.api

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Body of every error response. [error] is for the client's logic, [message] is for humans. */
@Serializable
data class ErrorResponse(
    /** Defaults to [ApiError.UNKNOWN] so a client with `coerceInputValues` survives codes it doesn't know yet. */
    val error: ApiError = ApiError.UNKNOWN,
    val message: String,
)

/** Machine-readable error codes. The [SerialName]s are the values on the wire; don't change them. */
@Serializable
enum class ApiError {
    @SerialName("not_found")
    NOT_FOUND,

    @SerialName("no_code_available")
    NO_CODE_AVAILABLE,

    @SerialName("invalid_request")
    INVALID_REQUEST,

    @SerialName("unauthorized")
    UNAUTHORIZED,

    @SerialName("internal_error")
    INTERNAL,

    /** Never sent by the server. A client's fallback for a code added after it was built. */
    @SerialName("unknown")
    UNKNOWN,
}
