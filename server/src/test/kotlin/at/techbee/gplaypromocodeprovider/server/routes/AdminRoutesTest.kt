package at.techbee.gplaypromocodeprovider.server.routes

import at.techbee.gplaypromocodeprovider.api.AdminApp
import at.techbee.gplaypromocodeprovider.api.AdminBatch
import at.techbee.gplaypromocodeprovider.api.AdminSessionResponse
import at.techbee.gplaypromocodeprovider.api.ApiErrors
import at.techbee.gplaypromocodeprovider.api.AppRequest
import at.techbee.gplaypromocodeprovider.api.BatchUploadRequest
import at.techbee.gplaypromocodeprovider.api.CodeStats
import at.techbee.gplaypromocodeprovider.api.ErrorResponse
import at.techbee.gplaypromocodeprovider.api.LoginRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.http.setCookie
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

class AdminRoutesTest {

    private suspend fun ApiTestScope.adminEndpointsRespond(): List<HttpResponse> = listOf(
        client.get("/api/admin/session"),
        client.get("/api/admin/apps"),
        client.post("/api/admin/apps") { contentType(ContentType.Application.Json); setBody(AppRequest("X")) },
        client.get("/api/admin/apps/1"),
        client.put("/api/admin/apps/1") { contentType(ContentType.Application.Json); setBody(AppRequest("X")) },
        client.post("/api/admin/apps/1/invite-token"),
        client.get("/api/admin/apps/1/batches"),
        client.post("/api/admin/apps/1/batches") {
            contentType(ContentType.Application.Json)
            setBody(BatchUploadRequest(null, clock.now() + 1.days, "A"))
        },
    )

    @Test
    fun adminEndpointsRejectMissingSession() = apiTest {
        for (response in adminEndpointsRespond()) {
            assertEquals(HttpStatusCode.Unauthorized, response.status, response.call.request.url.toString())
        }
    }

    @Test
    fun wrongCredentialsAreRejected() = apiTest {
        for (credentials in listOf(LoginRequest(ADMIN_USER, "wrong"), LoginRequest("other", ADMIN_PASSWORD), LoginRequest("", ""))) {
            val response = client.post("/api/admin/login") {
                contentType(ContentType.Application.Json)
                setBody(credentials)
            }
            assertEquals(HttpStatusCode.Unauthorized, response.status)
            assertEquals(ApiErrors.UNAUTHORIZED, response.body<ErrorResponse>().error)
        }
        assertTrue(adminEndpointsRespond().all { it.status == HttpStatusCode.Unauthorized })
    }

    @Test
    fun loginSetsAHardenedSessionCookie() = apiTest {
        val response = client.post("/api/admin/login") {
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(ADMIN_USER, ADMIN_PASSWORD))
        }

        val cookie = assertNotNull(response.setCookie().singleOrNull { it.name == ADMIN_SESSION_COOKIE })
        assertTrue(cookie.httpOnly)
        assertEquals("/api/admin", cookie.path)
        assertEquals("Strict", cookie.extensions["SameSite"])
        assertEquals(AdminSessionResponse(ADMIN_USER), client.get("/api/admin/session").body<AdminSessionResponse>())
    }

    @Test
    fun forgedSessionCookieIsRejected() = apiTest {
        val response = client.get("/api/admin/apps") {
            headers.append("Cookie", "$ADMIN_SESSION_COOKIE=0123456789abcdef0123456789abcdef")
        }
        assertEquals(HttpStatusCode.Unauthorized, response.status)
    }

    @Test
    fun logoutEndsTheSession() = apiTest {
        login()
        assertEquals(HttpStatusCode.OK, client.get("/api/admin/session").status)

        assertEquals(HttpStatusCode.NoContent, client.post("/api/admin/logout").status)

        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/session").status)
    }

    @Test
    fun sessionExpires() = apiTest {
        login()
        clock.advance(ADMIN_SESSION_DURATION - 1.hours)
        assertEquals(HttpStatusCode.OK, client.get("/api/admin/session").status)
        clock.advance(1.hours)
        assertEquals(HttpStatusCode.Unauthorized, client.get("/api/admin/session").status)
    }

    @Test
    fun createEditAndListApps() = apiTest {
        login()
        val created = client.post("/api/admin/apps") {
            contentType(ContentType.Application.Json)
            setBody(AppRequest("  My App ", " com.example.app ", " "))
        }.body<AdminApp>()
        assertEquals("My App", created.name)
        assertEquals("com.example.app", created.packageName)
        assertEquals(null, created.description)
        assertEquals(43, created.inviteToken.length)

        val updated = client.put("/api/admin/apps/${created.id}") {
            contentType(ContentType.Application.Json)
            setBody(AppRequest("Renamed", null, "A description"))
        }.body<AdminApp>()
        assertEquals(created.copy(name = "Renamed", packageName = null, description = "A description"), updated)

        assertEquals(listOf(updated), client.get("/api/admin/apps").body<List<AdminApp>>())
        assertEquals(updated, client.get("/api/admin/apps/${created.id}").body<AdminApp>())
    }

    @Test
    fun invalidAppIsRejected() = apiTest {
        login()
        for (request in listOf(AppRequest(" "), AppRequest("x".repeat(101)))) {
            val response = client.post("/api/admin/apps") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(ApiErrors.INVALID_REQUEST, response.body<ErrorResponse>().error)
        }
        val malformed = client.post("/api/admin/apps") {
            contentType(ContentType.Application.Json)
            setBody("""{"nope": 1}""")
        }
        assertEquals(HttpStatusCode.BadRequest, malformed.status)
    }

    @Test
    fun unknownAppIsNotFound() = apiTest {
        login()
        assertEquals(HttpStatusCode.NotFound, client.get("/api/admin/apps/4711").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/admin/apps/abc").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/api/admin/apps/4711/batches").status)
        assertEquals(HttpStatusCode.NotFound, client.post("/api/admin/apps/4711/invite-token").status)
        val upload = client.post("/api/admin/apps/4711/batches") {
            contentType(ContentType.Application.Json)
            setBody(BatchUploadRequest(null, clock.now() + 1.days, "A"))
        }
        assertEquals(HttpStatusCode.NotFound, upload.status)
    }

    @Test
    fun uploadTrimsIgnoresBlankLinesAndReportsDuplicates() = apiTest {
        login()
        val app = createApp()
        uploadBatch(app.id, "A\nB")

        val result = uploadBatch(app.id, "  B  \n\n C \r\n\t\nD\nC\n", description = " Wave 2 ")

        assertEquals(2, result.addedCount)
        assertEquals(listOf("B", "C"), result.duplicates)
        assertEquals("Wave 2", result.batch.description)
        assertEquals(CodeStats(available = 2, consumed = 0, expired = 0), result.batch.stats)
    }

    @Test
    fun invalidUploadIsRejected() = apiTest {
        login()
        val app = createApp()
        for (request in listOf(
            BatchUploadRequest(null, clock.now() + 1.days, "\n  \n"),
            BatchUploadRequest(null, clock.now() - 1.days, "A"),
        )) {
            val response = client.post("/api/admin/apps/${app.id}/batches") {
                contentType(ContentType.Application.Json)
                setBody(request)
            }
            assertEquals(HttpStatusCode.BadRequest, response.status)
            assertEquals(ApiErrors.INVALID_REQUEST, response.body<ErrorResponse>().error)
        }
    }

    @Test
    fun statisticsPerAppAndBatch() = apiTest {
        login()
        val app = createApp("App")
        val other = createApp("Other")
        val expiring = uploadBatch(app.id, "A\nB\nC", expiresIn = 1.days).batch
        val running = uploadBatch(app.id, "D\nE").batch
        uploadBatch(other.id, "F")
        client.post("/api/invite/${app.inviteToken}/batches/${expiring.id}/code")
        client.post("/api/invite/${app.inviteToken}/batches/${running.id}/code")
        clock.advance(1.days)
        login() // the session has expired in the meantime

        val batches = client.get("/api/admin/apps/${app.id}/batches").body<List<AdminBatch>>().associateBy { it.id }
        assertEquals(setOf(expiring.id, running.id), batches.keys)
        assertEquals(CodeStats(available = 0, consumed = 1, expired = 2), batches.getValue(expiring.id).stats)
        assertEquals(CodeStats(available = 1, consumed = 1, expired = 0), batches.getValue(running.id).stats)

        val apps = client.get("/api/admin/apps").body<List<AdminApp>>().associateBy { it.id }
        assertEquals(CodeStats(available = 1, consumed = 2, expired = 2), apps.getValue(app.id).stats)
        assertEquals(CodeStats(available = 1, consumed = 0, expired = 0), apps.getValue(other.id).stats)
        assertEquals(apps.getValue(app.id).stats, client.get("/api/admin/apps/${app.id}").body<AdminApp>().stats)
    }
}
