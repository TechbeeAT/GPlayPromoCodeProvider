package at.techbee.gplaypromocodeprovider.client

import at.techbee.gplaypromocodeprovider.api.AdminBatch
import at.techbee.gplaypromocodeprovider.api.ApiError
import at.techbee.gplaypromocodeprovider.api.AppRequest
import at.techbee.gplaypromocodeprovider.api.AvailableBatch
import at.techbee.gplaypromocodeprovider.api.BatchUploadRequest
import at.techbee.gplaypromocodeprovider.api.BatchUploadResponse
import at.techbee.gplaypromocodeprovider.api.CodeStats
import at.techbee.gplaypromocodeprovider.api.InviteResponse
import at.techbee.gplaypromocodeprovider.api.PromoCodeResponse
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class ApiClientTest {

    private val requests = mutableListOf<HttpRequestData>()

    private fun client(
        baseUrl: String = "https://promo.example.com",
        handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData,
    ) = ApiClient(
        baseUrl,
        MockEngine { request ->
            requests += request
            handler(request)
        },
    )

    private fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

    private val request get() = requests.single()

    private val HttpRequestData.bodyText get() = (body as TextContent).text

    @Test
    fun getInviteCallsTheInviteEndpointAndParsesTheResponse() = runTest {
        val api = client {
            json("""{"appName":"My App","batches":[{"id":7,"description":null,"expiresAt":"2027-01-31T23:00:00Z"}]}""")
        }

        val result = api.getInvite("tok-123")

        assertEquals(HttpMethod.Get, request.method)
        assertEquals("https://promo.example.com/api/invite/tok-123", request.url.toString())
        assertEquals(
            ApiResult.Success(InviteResponse("My App", listOf(AvailableBatch(7, null, Instant.parse("2027-01-31T23:00:00Z"))))),
            result,
        )
    }

    @Test
    fun pathSegmentsAreEncodedSoATokenCannotChangeThePath() = runTest {
        val api = client(baseUrl = "https://promo.example.com/") { json("""{"appName":"A","batches":[]}""") }

        api.getInvite("../admin/apps?x=1")

        assertEquals("/api/invite/..%2Fadmin%2Fapps%3Fx=1", request.url.encodedPath)
    }

    @Test
    fun requestCodePostsToTheBatchAndReturnsTheCode() = runTest {
        val api = client { json("""{"code":"ABC-123"}""") }

        val result = api.requestCode("tok", 42)

        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/invite/tok/batches/42/code", request.url.encodedPath)
        assertEquals(ApiResult.Success(PromoCodeResponse("ABC-123")), result)
    }

    @Test
    fun noCodeLeftIsAFailureWithItsErrorCode() = runTest {
        val api = client {
            json("""{"error":"no_code_available","message":"No code available in this batch"}""", HttpStatusCode.Conflict)
        }

        val result = api.requestCode("tok", 42)

        assertEquals(ApiResult.Failure(409, ApiError.NO_CODE_AVAILABLE, "No code available in this batch"), result)
    }

    @Test
    fun unknownInviteLinkIsNotFound() = runTest {
        val api = client { json("""{"error":"not_found","message":"Not found"}""", HttpStatusCode.NotFound) }

        val result = api.getInvite("unknown")

        assertEquals(ApiResult.Failure(404, ApiError.NOT_FOUND, "Not found"), result)
    }

    @Test
    fun missingSessionIsUnauthorized() = runTest {
        val api = client { json("""{"error":"unauthorized","message":"Login required"}""", HttpStatusCode.Unauthorized) }

        val result = api.listApps()

        assertEquals("/api/admin/apps", request.url.encodedPath)
        assertEquals(ApiResult.Failure(401, ApiError.UNAUTHORIZED, "Login required"), result)
    }

    @Test
    fun errorCodeAddedLaterBecomesUnknown() = runTest {
        val api = client { json("""{"error":"rate_limited","message":"Slow down"}""", HttpStatusCode.TooManyRequests) }

        val result = api.getInvite("tok")

        assertEquals(ApiResult.Failure(429, ApiError.UNKNOWN, "Slow down"), result)
    }

    @Test
    fun errorWithoutErrorResponseBodyIsDerivedFromTheStatus() = runTest {
        val api = client {
            respond("<html>Internal error</html>", HttpStatusCode.InternalServerError, headersOf(HttpHeaders.ContentType, "text/html"))
        }

        val result = api.getInvite("tok")

        assertEquals(ApiResult.Failure(500, ApiError.INTERNAL, "HTTP 500"), result)
    }

    @Test
    fun gatewayErrorOfAProxyMeansTheServerCantBeReached() = runTest {
        for (status in listOf(HttpStatusCode.BadGateway, HttpStatusCode.ServiceUnavailable, HttpStatusCode.GatewayTimeout)) {
            val api = client { respond("<html>Bad Gateway</html>", status, headersOf(HttpHeaders.ContentType, "text/html")) }

            val result = api.getInvite("tok")

            assertIs<ApiResult.NetworkError>(result)
            assertEquals(status.value, assertIs<ServerUnreachableException>(result.cause).status)
        }
    }

    @Test
    fun gatewayStatusWithAnErrorResponseFromTheServerStaysAFailure() = runTest {
        val api = client { json("""{"error":"internal_error","message":"Maintenance"}""", HttpStatusCode.ServiceUnavailable) }

        val result = api.getInvite("tok")

        assertEquals(ApiResult.Failure(503, ApiError.INTERNAL, "Maintenance"), result)
    }

    @Test
    fun successWithAnUnexpectedBodyIsAFailure() = runTest {
        val api = client {
            respond("<html>index</html>", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "text/html"))
        }

        val result = api.getInvite("tok")

        assertEquals(ApiResult.Failure(200, ApiError.UNKNOWN, "Unexpected response from the server"), result)
    }

    @Test
    fun unreachableServerIsANetworkError() = runTest {
        val api = client { throw IllegalStateException("Failed to fetch") }

        val result = api.getInvite("tok")

        assertIs<ApiResult.NetworkError>(result)
        // Type and message, not identity: on the JVM, coroutines may hand over a copy of the exception
        // (stack trace recovery).
        assertIs<IllegalStateException>(result.cause)
        assertEquals("Failed to fetch", result.cause.message)
    }

    @Test
    fun loginSendsTheCredentialsAsJson() = runTest {
        val api = client { respond("", HttpStatusCode.NoContent) }

        val result = api.login("admin", "secret")

        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/admin/login", request.url.encodedPath)
        assertEquals(ContentType.Application.Json, request.body.contentType?.withoutParameters())
        assertEquals("""{"username":"admin","password":"secret"}""", request.bodyText)
        assertEquals(ApiResult.Success(Unit), result)
    }

    @Test
    fun wrongCredentialsAreUnauthorized() = runTest {
        val api = client {
            json("""{"error":"unauthorized","message":"Wrong username or password"}""", HttpStatusCode.Unauthorized)
        }

        val result = api.login("admin", "wrong")

        assertEquals(ApiResult.Failure(401, ApiError.UNAUTHORIZED, "Wrong username or password"), result)
    }

    @Test
    fun uploadBatchSendsTheRequestAndIgnoresUnknownResponseFields() = runTest {
        val expiresAt = Instant.parse("2027-03-01T00:00:00Z")
        val createdAt = Instant.parse("2026-11-02T10:00:00Z")
        val api = client {
            json(
                """{"batch":{"id":3,"appId":1,"description":"Beta","expiresAt":"$expiresAt","createdAt":"$createdAt",
                   "stats":{"available":2,"consumed":0,"expired":0},"addedLater":true},
                   "addedCount":2,"duplicates":["DUP"]}""",
                HttpStatusCode.Created,
            )
        }

        val result = api.uploadBatch(1, BatchUploadRequest("Beta", expiresAt, "A\nB\nDUP"))

        assertEquals(HttpMethod.Post, request.method)
        assertEquals("/api/admin/apps/1/batches", request.url.encodedPath)
        val sent = Json.parseToJsonElement(request.bodyText).jsonObject
        assertEquals("2027-03-01T00:00:00Z", sent.getValue("expiresAt").jsonPrimitive.content)
        assertEquals("A\nB\nDUP", sent.getValue("codes").jsonPrimitive.content)
        assertEquals(
            ApiResult.Success(
                BatchUploadResponse(AdminBatch(3, 1, "Beta", expiresAt, createdAt, CodeStats(2, 0, 0)), 2, listOf("DUP")),
            ),
            result,
        )
    }

    @Test
    fun invalidateBatchParsesTheInvalidatedBatch() = runTest {
        val api = client {
            json(
                """{"id":3,"appId":1,"description":null,"expiresAt":"2027-03-01T00:00:00Z","createdAt":"2026-11-02T10:00:00Z",
                   "stats":{"available":0,"consumed":1,"expired":0,"invalidated":2},"invalidatedAt":"2026-11-03T09:00:00Z"}""",
            )
        }

        val batch = assertIs<ApiResult.Success<AdminBatch>>(api.invalidateBatch(1, 3)).value

        assertEquals(CodeStats(available = 0, consumed = 1, expired = 0, invalidated = 2), batch.stats)
        assertEquals(Instant.parse("2026-11-03T09:00:00Z"), batch.invalidatedAt)
    }

    @Test
    fun adminEndpointsUseTheirPaths() = runTest {
        val api = client { respond("", HttpStatusCode.InternalServerError) }

        api.logout()
        api.getSession()
        api.getApp(5)
        api.updateApp(5, AppRequest("Name"))
        api.regenerateInviteToken(5)
        api.listBatches(5)
        api.invalidateBatch(5, 7)

        assertEquals(
            listOf(
                "POST /api/admin/logout",
                "GET /api/admin/session",
                "GET /api/admin/apps/5",
                "PUT /api/admin/apps/5",
                "POST /api/admin/apps/5/invite-token",
                "GET /api/admin/apps/5/batches",
                "POST /api/admin/apps/5/batches/7/invalidate",
            ),
            requests.map { "${it.method.value} ${it.url.encodedPath}" },
        )
    }
}
