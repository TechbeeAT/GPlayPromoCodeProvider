package at.techbee.gplaypromocodeprovider.server

import at.techbee.gplaypromocodeprovider.api.ApiErrors
import at.techbee.gplaypromocodeprovider.api.ErrorResponse
import at.techbee.gplaypromocodeprovider.server.domain.AppRepository
import at.techbee.gplaypromocodeprovider.server.domain.BatchRepository
import at.techbee.gplaypromocodeprovider.server.domain.CodeAllocator
import at.techbee.gplaypromocodeprovider.server.domain.ValidationException
import at.techbee.gplaypromocodeprovider.server.routes.adminRoutes
import at.techbee.gplaypromocodeprovider.server.routes.installAdminAuth
import at.techbee.gplaypromocodeprovider.server.routes.userRoutes
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.BadRequestException
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.response.respond
import io.ktor.server.routing.route
import io.ktor.server.routing.routing
import org.jetbrains.exposed.v1.jdbc.Database
import org.slf4j.LoggerFactory
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

    install(ContentNegotiation) { json() }
    install(StatusPages) {
        exception<ValidationException> { call, cause ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ApiErrors.INVALID_REQUEST, cause.message.orEmpty()))
        }
        exception<BadRequestException> { call, _ ->
            call.respond(HttpStatusCode.BadRequest, ErrorResponse(ApiErrors.INVALID_REQUEST, "Malformed request"))
        }
        exception<Throwable> { call, cause ->
            if (cause is SQLException) {
                // No message: it can contain the SQL statement including promo codes.
                log.error("Unhandled {} (SQL state {}, error code {})", cause::class.qualifiedName, cause.sqlState, cause.errorCode)
            } else {
                log.error("Unhandled exception", cause)
            }
            call.respond(HttpStatusCode.InternalServerError, ErrorResponse(ApiErrors.INTERNAL, "Internal server error"))
        }
    }
    installAdminAuth(config, clock)

    routing {
        route("/api") {
            userRoutes(apps, batches, allocator)
            adminRoutes(config, apps, batches, clock)
        }
    }
}
