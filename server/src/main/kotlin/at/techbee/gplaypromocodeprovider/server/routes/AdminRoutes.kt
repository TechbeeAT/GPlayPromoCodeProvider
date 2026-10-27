package at.techbee.gplaypromocodeprovider.server.routes

import at.techbee.gplaypromocodeprovider.api.AdminApp
import at.techbee.gplaypromocodeprovider.api.AdminBatch
import at.techbee.gplaypromocodeprovider.api.AdminSessionResponse
import at.techbee.gplaypromocodeprovider.api.ApiErrors
import at.techbee.gplaypromocodeprovider.api.AppRequest
import at.techbee.gplaypromocodeprovider.api.BatchUploadRequest
import at.techbee.gplaypromocodeprovider.api.BatchUploadResponse
import at.techbee.gplaypromocodeprovider.api.CodeStats
import at.techbee.gplaypromocodeprovider.api.ErrorResponse
import at.techbee.gplaypromocodeprovider.api.LoginRequest
import at.techbee.gplaypromocodeprovider.server.ServerConfig
import at.techbee.gplaypromocodeprovider.server.domain.AdminCredentials
import at.techbee.gplaypromocodeprovider.server.domain.App
import at.techbee.gplaypromocodeprovider.server.domain.AppInput
import at.techbee.gplaypromocodeprovider.server.domain.AppRepository
import at.techbee.gplaypromocodeprovider.server.domain.BatchRepository
import at.techbee.gplaypromocodeprovider.server.domain.BatchWithStats
import at.techbee.gplaypromocodeprovider.server.domain.Stats
import at.techbee.gplaypromocodeprovider.server.domain.UploadResult
import io.ktor.http.HttpStatusCode
import io.ktor.server.auth.authenticate
import io.ktor.server.auth.principal
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import io.ktor.server.sessions.clear
import io.ktor.server.sessions.sessions
import io.ktor.server.sessions.set
import kotlin.time.Clock

fun Route.adminRoutes(config: ServerConfig, apps: AppRepository, batches: BatchRepository, clock: Clock) {
    val credentials = AdminCredentials(config.adminUsername, config.adminPassword)

    route("/admin") {
        post("/login") {
            val request = call.receive<LoginRequest>()
            if (!credentials.matches(request.username, request.password)) {
                return@post call.respond(
                    HttpStatusCode.Unauthorized,
                    ErrorResponse(ApiErrors.UNAUTHORIZED, "Wrong username or password"),
                )
            }
            // A fresh session id on every login (no session fixation).
            call.sessions.clear<AdminSession>()
            call.sessions.set(AdminSession(config.adminUsername, clock.now()))
            call.respond(HttpStatusCode.NoContent)
        }

        post("/logout") {
            call.sessions.clear<AdminSession>()
            call.respond(HttpStatusCode.NoContent)
        }

        authenticate(ADMIN_AUTH) {
            get("/session") {
                call.respond(AdminSessionResponse(call.principal<AdminSession>()!!.username))
            }

            route("/apps") {
                get {
                    val statsByApp = batches.listWithStats().groupBy { it.batch.appId }
                        .mapValues { (_, list) -> list.fold(Stats.ZERO) { sum, b -> sum + b.stats } }
                    call.respond(apps.list().map { it.toAdminApp(statsByApp[it.id] ?: Stats.ZERO) })
                }

                post {
                    val request = call.receive<AppRequest>()
                    val app = apps.create(AppInput.of(request.name, request.packageName, request.description))
                    call.respond(HttpStatusCode.Created, app.toAdminApp(Stats.ZERO))
                }

                route("/{appId}") {
                    get {
                        val app = call.parameters["appId"]?.toLongOrNull()?.let(apps::find)
                            ?: return@get call.respondNotFound()
                        call.respond(app.toAdminApp(batches.statsOf(app.id)))
                    }

                    put {
                        val appId = call.parameters["appId"]?.toLongOrNull() ?: return@put call.respondNotFound()
                        val request = call.receive<AppRequest>()
                        val app = apps.update(appId, AppInput.of(request.name, request.packageName, request.description))
                            ?: return@put call.respondNotFound()
                        call.respond(app.toAdminApp(batches.statsOf(app.id)))
                    }

                    post("/invite-token") {
                        val app = call.parameters["appId"]?.toLongOrNull()?.let(apps::regenerateInviteToken)
                            ?: return@post call.respondNotFound()
                        call.respond(app.toAdminApp(batches.statsOf(app.id)))
                    }

                    get("/batches") {
                        val app = call.parameters["appId"]?.toLongOrNull()?.let(apps::find)
                            ?: return@get call.respondNotFound()
                        call.respond(batches.listWithStats(app.id).map { it.toAdminBatch() })
                    }

                    post("/batches") {
                        val appId = call.parameters["appId"]?.toLongOrNull() ?: return@post call.respondNotFound()
                        val request = call.receive<BatchUploadRequest>()
                        when (val result = batches.upload(appId, request.description, request.expiresAt, request.codes)) {
                            is UploadResult.Created -> call.respond(
                                HttpStatusCode.Created,
                                BatchUploadResponse(
                                    batch = BatchWithStats(result.batch, Stats(result.addedCount, 0, 0)).toAdminBatch(),
                                    addedCount = result.addedCount,
                                    duplicates = result.duplicates,
                                ),
                            )
                            UploadResult.AppNotFound -> call.respondNotFound()
                        }
                    }
                }
            }
        }
    }
}

private fun BatchRepository.statsOf(appId: Long) = listWithStats(appId).fold(Stats.ZERO) { sum, b -> sum + b.stats }

private fun Stats.toCodeStats() = CodeStats(available, consumed, expired)

private fun App.toAdminApp(stats: Stats) = AdminApp(id, name, packageName, description, inviteToken, stats.toCodeStats())

private fun BatchWithStats.toAdminBatch() =
    AdminBatch(batch.id, batch.appId, batch.description, batch.expiresAt, batch.createdAt, stats.toCodeStats())
