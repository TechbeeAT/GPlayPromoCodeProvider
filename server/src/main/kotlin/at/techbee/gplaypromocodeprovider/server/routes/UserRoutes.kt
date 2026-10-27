package at.techbee.gplaypromocodeprovider.server.routes

import at.techbee.gplaypromocodeprovider.api.ApiErrors
import at.techbee.gplaypromocodeprovider.api.AvailableBatch
import at.techbee.gplaypromocodeprovider.api.ErrorResponse
import at.techbee.gplaypromocodeprovider.api.InviteResponse
import at.techbee.gplaypromocodeprovider.api.PromoCodeResponse
import at.techbee.gplaypromocodeprovider.server.domain.AppRepository
import at.techbee.gplaypromocodeprovider.server.domain.BatchRepository
import at.techbee.gplaypromocodeprovider.server.domain.CodeAllocator
import at.techbee.gplaypromocodeprovider.server.domain.HandOutResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route

/**
 * User endpoints, scoped by the app's invite token. An unknown token, an unknown batch and a
 * batch of another app all get the same 404, so nothing is revealed (CLAUDE.md, domain rule 5).
 */
fun Route.userRoutes(apps: AppRepository, batches: BatchRepository, allocator: CodeAllocator) {
    route("/invite/{token}") {
        get {
            val app = apps.findByInviteToken(call.parameters["token"].orEmpty()) ?: return@get call.respondNotFound()
            val available = batches.listAvailable(app.id).map { AvailableBatch(it.id, it.description, it.expiresAt) }
            call.respond(InviteResponse(app.name, available))
        }

        post("/batches/{batchId}/code") {
            val app = apps.findByInviteToken(call.parameters["token"].orEmpty()) ?: return@post call.respondNotFound()
            val batchId = call.parameters["batchId"]?.toLongOrNull() ?: return@post call.respondNotFound()
            when (val result = allocator.handOut(app.id, batchId)) {
                is HandOutResult.Success -> call.respond(PromoCodeResponse(result.code))
                HandOutResult.NoCodeAvailable -> call.respond(
                    HttpStatusCode.Conflict,
                    ErrorResponse(ApiErrors.NO_CODE_AVAILABLE, "No code available in this batch"),
                )
                HandOutResult.BatchNotFound -> call.respondNotFound()
            }
        }
    }
}

internal suspend fun RoutingCall.respondNotFound() =
    respond(HttpStatusCode.NotFound, ErrorResponse(ApiErrors.NOT_FOUND, "Not found"))
