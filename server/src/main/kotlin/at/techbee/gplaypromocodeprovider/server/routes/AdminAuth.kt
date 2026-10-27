package at.techbee.gplaypromocodeprovider.server.routes

import at.techbee.gplaypromocodeprovider.api.ApiErrors
import at.techbee.gplaypromocodeprovider.api.ErrorResponse
import at.techbee.gplaypromocodeprovider.server.ServerConfig
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.auth.Authentication
import io.ktor.server.auth.session
import io.ktor.server.response.respond
import io.ktor.server.sessions.SessionStorageMemory
import io.ktor.server.sessions.Sessions
import io.ktor.server.sessions.cookie
import kotlinx.serialization.Serializable
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

const val ADMIN_AUTH = "admin"
const val ADMIN_SESSION_COOKIE = "admin_session"
val ADMIN_SESSION_DURATION = 12.hours

/** The logged-in admin. Stored on the server; the cookie only carries a random session id. */
@Serializable
data class AdminSession(val username: String, val loggedInAt: Instant)

/**
 * Admin authentication: `POST /api/admin/login` creates a session, admin endpoints require it.
 *
 * Sessions are kept in memory, so a server restart logs the admin out. That is fine for a
 * single admin and a single server instance.
 */
fun Application.installAdminAuth(config: ServerConfig, clock: Clock) {
    install(Sessions) {
        cookie<AdminSession>(ADMIN_SESSION_COOKIE, SessionStorageMemory()) {
            cookie.path = "/api/admin"
            cookie.httpOnly = true
            cookie.secure = config.cookieSecure
            cookie.maxAgeInSeconds = ADMIN_SESSION_DURATION.inWholeSeconds
            cookie.extensions["SameSite"] = "Strict"
        }
    }
    install(Authentication) {
        session<AdminSession>(ADMIN_AUTH) {
            validate { session ->
                session.takeIf { clock.now() - it.loggedInAt < ADMIN_SESSION_DURATION }
            }
            challenge {
                call.respond(HttpStatusCode.Unauthorized, ErrorResponse(ApiErrors.UNAUTHORIZED, "Login required"))
            }
        }
    }
}
