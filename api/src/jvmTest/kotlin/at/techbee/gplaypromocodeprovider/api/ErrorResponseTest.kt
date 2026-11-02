package at.techbee.gplaypromocodeprovider.api

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ErrorResponseTest {

    @Test
    fun errorCodesKeepTheirWireValues() {
        val wireValues = ApiError.entries.associateWith { Json.encodeToString(it) }

        assertEquals(
            mapOf(
                ApiError.NOT_FOUND to "\"not_found\"",
                ApiError.NO_CODE_AVAILABLE to "\"no_code_available\"",
                ApiError.INVALID_REQUEST to "\"invalid_request\"",
                ApiError.UNAUTHORIZED to "\"unauthorized\"",
                ApiError.INTERNAL to "\"internal_error\"",
                ApiError.UNKNOWN to "\"unknown\"",
            ),
            wireValues,
        )
    }

    @Test
    fun unknownCodeFallsBackToUnknownWithCoerceInputValues() {
        val json = Json { coerceInputValues = true }

        val response = json.decodeFromString<ErrorResponse>("""{"error":"added_later","message":"Hi"}""")

        assertEquals(ErrorResponse(ApiError.UNKNOWN, "Hi"), response)
    }
}
