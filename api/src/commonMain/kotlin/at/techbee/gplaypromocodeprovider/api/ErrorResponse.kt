package at.techbee.gplaypromocodeprovider.api

import kotlinx.serialization.Serializable

/** Body of every error response. [error] is a stable machine-readable code, [message] is for humans. */
@Serializable
data class ErrorResponse(
    val error: String,
    val message: String,
)

/** Values of [ErrorResponse.error]. */
object ApiErrors {
    const val NOT_FOUND = "not_found"
    const val NO_CODE_AVAILABLE = "no_code_available"
    const val INVALID_REQUEST = "invalid_request"
    const val UNAUTHORIZED = "unauthorized"
    const val INTERNAL = "internal_error"
}
