package at.techbee.gplaypromocodeprovider.server.routes

import at.techbee.gplaypromocodeprovider.api.ApiError
import at.techbee.gplaypromocodeprovider.api.ErrorResponse
import at.techbee.gplaypromocodeprovider.api.LoginRequest
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WebAppRoutesTest {

    private val webDir: File = Files.createTempDirectory("gplay-web").toFile().apply {
        deleteOnExit()
        resolve("index.html").writeText("<html>index</html>")
        resolve("webApp.js").writeText("console.log('app')")
        resolve("app.wasm").writeBytes(ByteArray(4096))
    }

    private fun webTest(block: suspend ApiTestScope.() -> Unit) =
        apiTest(configure = { it.copy(webDir = webDir.path) }, block = block)

    @Test
    fun servesIndexHtmlAtTheRoot() = webTest {
        val response = client.get("/")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("<html>index</html>", response.bodyAsText())
        assertEquals(ContentType.Text.Html, response.contentType()?.withoutParameters())
    }

    @Test
    fun servesIndexHtmlForAppPaths() = webTest {
        for (path in listOf("/invite/some-token", "/admin", "/admin/apps/1")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.OK, response.status, path)
            assertEquals("<html>index</html>", response.bodyAsText(), path)
            assertEquals("no-cache", response.headers[HttpHeaders.CacheControl], path)
        }
    }

    @Test
    fun missingFilesGetNotFoundInsteadOfIndexHtml() = webTest {
        for (path in listOf("/webApp.js.map", "/missing.wasm", "/invite/x/old-chunk.js")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.NotFound, response.status, path)
            assertTrue("index" !in response.bodyAsText(), path)
        }
    }

    @Test
    fun invitePathsAreNeverTakenForFiles() {
        // Invite tokens are URL-safe Base64 (letters, digits, '-' and '_'): no dot, so never a "file".
        assertFalse(looksLikeFile("/invite/Ab-3_xYz09"))
        assertFalse(looksLikeFile("/admin/apps/12"))
        assertFalse(looksLikeFile("/"))
        assertTrue(looksLikeFile("/webApp.js.map"))
        assertTrue(looksLikeFile("/composeResources/font.ttf"))
    }

    @Test
    fun servesFilesWithTheirContentType() = webTest {
        val script = client.get("/webApp.js")
        assertEquals("console.log('app')", script.bodyAsText())
        assertEquals(ContentType.Text.JavaScript, script.contentType()?.withoutParameters())

        // Browsers only compile Wasm while downloading it (instantiateStreaming) with this type.
        val wasm = client.get("/app.wasm")
        assertEquals(ContentType.parse("application/wasm"), wasm.contentType()?.withoutParameters())
    }

    @Test
    fun letsTheBrowserRevalidateFiles() = webTest {
        val response = client.get("/webApp.js")
        assertEquals("no-cache", response.headers[HttpHeaders.CacheControl])
        val lastModified = assertNotNull(response.headers[HttpHeaders.LastModified])

        val again = client.get("/webApp.js") { header(HttpHeaders.IfModifiedSince, lastModified) }
        assertEquals(HttpStatusCode.NotModified, again.status)
    }

    @Test
    fun compressesWebAppFilesButNotApiResponses() = webTest {
        val wasm = client.get("/app.wasm") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertEquals("gzip", wasm.headers[HttpHeaders.ContentEncoding])

        login()
        val apps = client.get("/api/admin/apps") { header(HttpHeaders.AcceptEncoding, "gzip") }
        assertEquals(HttpStatusCode.OK, apps.status)
        assertNull(apps.headers[HttpHeaders.ContentEncoding])
    }

    @Test
    fun unknownApiPathsGetTheApiNotFound() = webTest {
        for (path in listOf("/api", "/api/unknown", "/api/invite", "/api/admin/apps/1/unknown")) {
            val response = client.get(path)
            assertEquals(HttpStatusCode.NotFound, response.status, path)
            assertEquals(ApiError.NOT_FOUND, response.body<ErrorResponse>().error, path)
        }
    }

    @Test
    fun doesNotServeFilesOutsideTheWebDirectory() = webTest {
        webDir.parentFile.resolve("gplay-secret.txt").apply { writeText("secret"); deleteOnExit() }

        val response = client.get("/..%2Fgplay-secret.txt")

        assertTrue("secret" !in response.bodyAsText())
    }

    @Test
    fun servesNoWebAppWithoutWebDir() = apiTest {
        assertEquals(HttpStatusCode.NotFound, client.get("/").status)
        assertEquals(HttpStatusCode.NotFound, client.get("/invite/some-token").status)
    }

    @Test
    fun healthCheckAnswers() = apiTest {
        val response = client.get("/api/health")

        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun sendsTheSecureCookieBehindAnHttpsProxy() = apiTest(configure = { it.copy(cookieSecure = true) }) {
        val response = client.post("/api/admin/login") {
            header("X-Forwarded-Proto", "https")
            contentType(ContentType.Application.Json)
            setBody(LoginRequest(ADMIN_USER, ADMIN_PASSWORD))
        }

        assertEquals(HttpStatusCode.NoContent, response.status)
        val cookie = assertNotNull(response.headers[HttpHeaders.SetCookie])
        assertTrue(cookie.startsWith("$ADMIN_SESSION_COOKIE="))
        assertTrue("Secure" in cookie)
    }
}
