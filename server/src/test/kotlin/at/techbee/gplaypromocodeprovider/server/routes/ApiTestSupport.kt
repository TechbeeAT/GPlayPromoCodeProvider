package at.techbee.gplaypromocodeprovider.server.routes

import at.techbee.gplaypromocodeprovider.api.AdminApp
import at.techbee.gplaypromocodeprovider.api.AppRequest
import at.techbee.gplaypromocodeprovider.api.BatchUploadRequest
import at.techbee.gplaypromocodeprovider.api.BatchUploadResponse
import at.techbee.gplaypromocodeprovider.api.LoginRequest
import at.techbee.gplaypromocodeprovider.server.ServerConfig
import at.techbee.gplaypromocodeprovider.server.TestClock
import at.techbee.gplaypromocodeprovider.server.createTestDatabase
import at.techbee.gplaypromocodeprovider.server.module
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days

const val ADMIN_USER = "admin"
const val ADMIN_PASSWORD = "correct horse battery staple"

class ApiTestScope(val builder: ApplicationTestBuilder, val clock: TestClock) {
    /** A client that keeps cookies, like a browser. */
    val client: HttpClient = builder.createClient {
        install(ContentNegotiation) { json() }
        install(HttpCookies)
    }

    suspend fun login(client: HttpClient = this.client) {
        val response = client.post("/api/admin/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(ADMIN_USER, ADMIN_PASSWORD))
        }
        assertEquals(HttpStatusCode.NoContent, response.status)
    }

    suspend fun createApp(name: String = "Test app"): AdminApp {
        val response = client.post("/api/admin/apps") {
            contentType(ContentType.Application.Json)
            setBody(AppRequest(name))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }

    suspend fun uploadBatch(appId: Long, codes: String, description: String? = null, expiresIn: Duration = 30.days): BatchUploadResponse {
        val response = client.post("/api/admin/apps/$appId/batches") {
            contentType(ContentType.Application.Json)
            setBody(BatchUploadRequest(description, clock.now() + expiresIn, codes))
        }
        assertEquals(HttpStatusCode.Created, response.status)
        return response.body()
    }
}

/** Runs [block] against the full application with a fresh database. */
fun apiTest(block: suspend ApiTestScope.() -> Unit) = testApplication {
    val clock = TestClock()
    val config = ServerConfig(
        port = 0,
        databasePath = "",
        adminUsername = ADMIN_USER,
        adminPassword = ADMIN_PASSWORD,
        cookieSecure = false,
    )
    val db = createTestDatabase()
    application { module(config, db, clock) }
    ApiTestScope(this, clock).block()
}
