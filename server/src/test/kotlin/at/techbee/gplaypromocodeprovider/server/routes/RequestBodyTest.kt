package at.techbee.gplaypromocodeprovider.server.routes

import at.techbee.gplaypromocodeprovider.api.ApiError
import at.techbee.gplaypromocodeprovider.api.BatchUploadRequest
import at.techbee.gplaypromocodeprovider.api.BatchUploadResponse
import at.techbee.gplaypromocodeprovider.api.ErrorResponse
import at.techbee.gplaypromocodeprovider.server.MAX_REQUEST_BODY_BYTES
import at.techbee.gplaypromocodeprovider.server.domain.CodeParser
import at.techbee.gplaypromocodeprovider.server.domain.MAX_CODES_PER_UPLOAD
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.http.contentType
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days

/** Requests whose body can't be read get a 4xx with an [ErrorResponse], never a 500. */
class RequestBodyTest {

    private suspend fun HttpResponse.assertInvalidRequest(status: HttpStatusCode) {
        assertEquals(status, this.status)
        assertEquals(ApiError.INVALID_REQUEST, body<ErrorResponse>().error)
    }

    /** A JSON login body of about [size] bytes. */
    private fun loginJson(size: Int) = """{"username":"admin","password":"${"x".repeat(size)}"}"""

    @Test
    fun bodyOfAnotherContentTypeIsUnsupportedMediaType() = apiTest {
        client.post("/api/admin/login") {
            contentType(ContentType.Application.Xml)
            setBody(loginJson(10))
        }.assertInvalidRequest(HttpStatusCode.UnsupportedMediaType)
    }

    @Test
    fun bodyWithoutContentTypeIsUnsupportedMediaType() = apiTest {
        client.post("/api/admin/login") { setBody(loginJson(10).encodeToByteArray()) }
            .assertInvalidRequest(HttpStatusCode.UnsupportedMediaType)
    }

    @Test
    fun missingBodyIsUnsupportedMediaType() = apiTest {
        client.post("/api/admin/login").assertInvalidRequest(HttpStatusCode.UnsupportedMediaType)
    }

    @Test
    fun malformedJsonIsBadRequest() = apiTest {
        client.post("/api/admin/login") {
            contentType(ContentType.Application.Json)
            setBody("{\"username\":")
        }.assertInvalidRequest(HttpStatusCode.BadRequest)
    }

    @Test
    fun tooLargeBodyIsRejectedBeforeLogin() = apiTest {
        client.post("/api/admin/login") {
            contentType(ContentType.Application.Json)
            setBody(loginJson(MAX_REQUEST_BODY_BYTES.toInt()))
        }.assertInvalidRequest(HttpStatusCode.PayloadTooLarge)
    }

    /** Without a Content-Length the limit applies while reading. */
    @Test
    fun tooLargeStreamedBodyIsRejected() = apiTest {
        val bytes = loginJson(MAX_REQUEST_BODY_BYTES.toInt()).encodeToByteArray()
        client.post("/api/admin/login") {
            setBody(object : OutgoingContent.WriteChannelContent() {
                override val contentType = ContentType.Application.Json
                override suspend fun writeTo(channel: ByteWriteChannel) = channel.writeFully(bytes)
            })
        }.assertInvalidRequest(HttpStatusCode.PayloadTooLarge)
    }

    /** The upload has a higher limit, enough for the largest upload the server accepts. */
    @Test
    fun largestValidUploadFitsItsLimit() = apiTest {
        login()
        val app = createApp()
        val codes = List(MAX_CODES_PER_UPLOAD) { it.toString().padStart(CodeParser.MAX_CODE_LENGTH, 'C') }

        val response = client.post("/api/admin/apps/${app.id}/batches") {
            contentType(ContentType.Application.Json)
            setBody(BatchUploadRequest("x".repeat(1000), clock.now() + 30.days, codes.joinToString("\n")))
        }

        assertEquals(HttpStatusCode.Created, response.status)
        assertEquals(MAX_CODES_PER_UPLOAD, response.body<BatchUploadResponse>().addedCount)
    }

    @Test
    fun uploadAboveItsLimitIsRejected() = apiTest {
        login()
        val app = createApp()
        val codes = "C".repeat(MAX_UPLOAD_BODY_BYTES.toInt())
        assertTrue(codes.length > MAX_REQUEST_BODY_BYTES)

        client.post("/api/admin/apps/${app.id}/batches") {
            contentType(ContentType.Application.Json)
            setBody(BatchUploadRequest(null, clock.now() + 30.days, codes))
        }.assertInvalidRequest(HttpStatusCode.PayloadTooLarge)
    }
}
