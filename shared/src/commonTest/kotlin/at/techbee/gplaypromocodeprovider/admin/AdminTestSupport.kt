package at.techbee.gplaypromocodeprovider.admin

import at.techbee.gplaypromocodeprovider.client.ApiClient
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

/** A mock server for the admin view model tests: records the requests and answers through [handler]. */
class MockServer(private val handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) {
    val requests = mutableListOf<HttpRequestData>()

    val api = ApiClient(
        "https://promo.example.com",
        MockEngine { request ->
            requests += request
            handler(request)
        },
    )

    fun count(method: HttpMethod, path: String) = requests.count { it.method == method && it.url.encodedPath == path }
}

fun MockRequestHandleScope.json(body: String, status: HttpStatusCode = HttpStatusCode.OK) =
    respond(body, status, headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()))

fun MockRequestHandleScope.error(status: HttpStatusCode, code: String, message: String = "x") =
    json("""{"error":"$code","message":"$message"}""", status)

fun MockRequestHandleScope.noContent() = respond("", HttpStatusCode.NoContent)

val HttpRequestData.path: String get() = url.encodedPath

val HttpRequestData.bodyText: String get() = (body as TextContent).text

fun appJson(id: Long = 1, name: String = "My App", token: String = "tok", available: Int = 2) =
    """{"id":$id,"name":"$name","packageName":null,"description":null,"inviteToken":"$token",""" +
        """"stats":{"available":$available,"consumed":0,"expired":0}}"""

fun batchJson(id: Long = 5, available: Int = 2) =
    """{"id":$id,"appId":1,"description":"Beta","expiresAt":"2027-01-31T22:59:59Z",""" +
        """"createdAt":"2026-11-01T10:00:00Z","stats":{"available":$available,"consumed":0,"expired":0}}"""
