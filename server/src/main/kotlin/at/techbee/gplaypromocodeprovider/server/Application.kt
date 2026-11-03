package at.techbee.gplaypromocodeprovider.server

import at.techbee.gplaypromocodeprovider.api.ApiError
import at.techbee.gplaypromocodeprovider.api.ErrorResponse
import at.techbee.gplaypromocodeprovider.server.domain.AppRepository
import at.techbee.gplaypromocodeprovider.server.domain.BatchRepository
import at.techbee.gplaypromocodeprovider.server.domain.CodeAllocator
import at.techbee.gplaypromocodeprovider.server.domain.ValidationException
import at.techbee.gplaypromocodeprovider.server.routes.adminRoutes
import at.techbee.gplaypromocodeprovider.server.routes.installAdminAuth
import at.techbee.gplaypromocodeprovider.server.routes.respondNotFound
import at.techbee.gplaypromocodeprovider.server.routes.userRoutes
import at.techbee.gplaypromocodeprovider.server.routes.webAppRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.condition
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.conditionalheaders.ConditionalHeaders
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.forwardedheaders.XForwardedHeaders
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.path
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
import java.io.File
import java.sql.SQLException
import kotlin.time.Clock

private val log = LoggerFactory.getLogger("at.techbee.gplaypromocodeprovider.server")

/**
 * The Ktor application. Deliberately no CallLogging: request paths contain invite tokens,
 * which must not be logged (CLAUDE.md, domain rule 6).
 */
fun Application.module(config: ServerConfig, db: Database, clock: Clock = Clock.System) {
    val apps = AppRepository(db, clock)
    val batches = BatchRepository(db, clock)
    val allocator = CodeAllocator(db, clock)

    // Behind Fly.io's proxy, TLS ends at the proxy and the server sees plain HTTP. The proxy's
    // X-Forwarded-Proto header tells Ktor that the browser used HTTPS; without it, Ktor refuses to
    // send the Secure session cookie. A client talking to the server directly could fake the header,
    // but it only affects what Ktor thinks the scheme and client address are, and nothing relies on
    // the client address.
    install(XForwardedHeaders)
    // The web app's files are large (several MB of Wasm) and compress well. API responses are not
    // compressed: they contain secrets (codes, invite tokens), and compressing secrets next to
    // attacker-influenced data can leak them (BREACH). They are small anyway.
    install(Compression) {
        gzip()
        condition { !request.path().startsWith("/api") }
    }
    // 304 Not Modified for unchanged web app files (Last-Modified / If-Modified-Since).
    install(ConditionalHeaders)
    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ApiError.INVALID_REQUEST, cause.message.orEmpty()))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ApiError.INVALID_REQUEST, "Malformed request"))
        }
        exception<Throwable> { call, cause ->
            if (cause is SQLException) {
                // No message: it can contain the SQL statement including promo codes.
                log.error("Unhandled {} (SQL state {}, error code {})", cause::class.qualifiedName, cause.sqlState, cause.errorCode)
            } else {
                log.error("Unhandled exception", cause)
            }
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(ApiError.INTERNAL, "Internal server error"))
        }
    }
    installAdminAuth(config, clock)

    routing {
        route("/api") {
            // Used by Fly.io's health check: the server is up and answers requests.
            get("/health") { call.respondText("ok") }
            userRoutes(apps, batches, allocator)
            adminRoutes(config, apps, batches, clock)
            // Unknown API paths get the API's 404, not the web app's index.html.
            route("{...}") { handle { call.respondNotFound() } }
        }
        config.webDir?.let { webAppRoutes(File(it)) }
    }
}
