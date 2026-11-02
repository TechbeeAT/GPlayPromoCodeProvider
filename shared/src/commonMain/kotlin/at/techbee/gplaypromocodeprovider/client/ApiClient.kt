package at.techbee.gplaypromocodeprovider.client

import at.techbee.gplaypromocodeprovider.api.AdminApp
import at.techbee.gplaypromocodeprovider.api.AdminBatch
import at.techbee.gplaypromocodeprovider.api.AdminSessionResponse
import at.techbee.gplaypromocodeprovider.api.ApiError
import at.techbee.gplaypromocodeprovider.api.AppRequest
import at.techbee.gplaypromocodeprovider.api.BatchUploadRequest
import at.techbee.gplaypromocodeprovider.api.BatchUploadResponse
import at.techbee.gplaypromocodeprovider.api.ErrorResponse
import at.techbee.gplaypromocodeprovider.api.InviteResponse
import at.techbee.gplaypromocodeprovider.api.LoginRequest
import at.techbee.gplaypromocodeprovider.api.PromoCodeResponse
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.NoTransformationFoundException
import io.ktor.client.call.body
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.appendPathSegments
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.http.takeFrom
import io.ktor.serialization.ContentConvertException
import io.ktor.serialization.kotlinx.json.json
import io.ktor.util.reflect.TypeInfo
import io.ktor.util.reflect.typeInfo
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlin.coroutines.cancellation.CancellationException

/**
 * Typed client for the server's REST API (see the README for the endpoints).
 *
 * Every call returns an [ApiResult] instead of throwing, so the UI has to handle "no code left",
 * "unknown invite link", "not logged in" and network errors explicitly.
 *
 * The admin session is a cookie set by the server. The browser stores and sends it by itself,
 * as long as the web app is served from the same origin as the API.
 *
 * @param baseUrl origin of the server, e.g. `https://promo.example.com`. In the browser, use the
 *   origin the web app was loaded from.
 * @param engine HTTP engine. `null` uses the platform default (the browser's `fetch`); tests pass a mock.
 */
class ApiClient(baseUrl: String, engine: HttpClientEngine? = null) : AutoCloseable {

    private val baseUrl = baseUrl.trimEnd('/')

    private val http: HttpClient = if (engine == null) HttpClient { configure() } else HttpClient(engine) { configure() }

    private fun HttpClientConfig<*>.configure() {
        expectSuccess = false
        install(ContentNegotiation) { json(apiJson) }
    }

    // User endpoints, scoped by the invite token.

    /** The app behind an invite link and its batches with codes available. Unknown token: `NOT_FOUND`. */
    suspend fun getInvite(token: String): ApiResult<InviteResponse> =
        call(HttpMethod.Get, "invite", token)

    /**
     * Hands out one code of a batch. `NO_CODE_AVAILABLE` if the batch has run out (or expired) meanwhile,
     * `NOT_FOUND` for an unknown token or batch.
     */
    suspend fun requestCode(token: String, batchId: Long): ApiResult<PromoCodeResponse> =
        call(HttpMethod.Post, "invite", token, "batches", batchId.toString(), "code")

    // Admin endpoints. All except login and logout answer `UNAUTHORIZED` without a valid session.

    /** Logs the admin in; the server sets the session cookie. Wrong credentials: `UNAUTHORIZED`. */
    suspend fun login(username: String, password: String): ApiResult<Unit> =
        call(HttpMethod.Post, "admin", "login", body = LoginRequest(username, password))

    suspend fun logout(): ApiResult<Unit> =
        call(HttpMethod.Post, "admin", "logout")

    /** The logged-in admin, or `UNAUTHORIZED`. Use it to check for a session when the app starts. */
    suspend fun getSession(): ApiResult<AdminSessionResponse> =
        call(HttpMethod.Get, "admin", "session")

    suspend fun listApps(): ApiResult<List<AdminApp>> =
        call(HttpMethod.Get, "admin", "apps")

    suspend fun createApp(request: AppRequest): ApiResult<AdminApp> =
        call(HttpMethod.Post, "admin", "apps", body = request)

    suspend fun getApp(appId: Long): ApiResult<AdminApp> =
        call(HttpMethod.Get, "admin", "apps", appId.toString())

    suspend fun updateApp(appId: Long, request: AppRequest): ApiResult<AdminApp> =
        call(HttpMethod.Put, "admin", "apps", appId.toString(), body = request)

    /** Gives the app a new invite token. The old invite link stops working. */
    suspend fun regenerateInviteToken(appId: Long): ApiResult<AdminApp> =
        call(HttpMethod.Post, "admin", "apps", appId.toString(), "invite-token")

    /** The app's batches with statistics, newest first. */
    suspend fun listBatches(appId: Long): ApiResult<List<AdminBatch>> =
        call(HttpMethod.Get, "admin", "apps", appId.toString(), "batches")

    /** Uploads a batch. Invalid input (no new code, expiry in the past, ...): `INVALID_REQUEST`. */
    suspend fun uploadBatch(appId: Long, request: BatchUploadRequest): ApiResult<BatchUploadResponse> =
        call(HttpMethod.Post, "admin", "apps", appId.toString(), "batches", body = request)

    override fun close() = http.close()

    private suspend inline fun <reified T> call(method: HttpMethod, vararg path: String): ApiResult<T> =
        execute(method, path, typeInfo<T>()) {}

    private suspend inline fun <reified B : Any, reified T> call(
        method: HttpMethod,
        vararg path: String,
        body: B,
    ): ApiResult<T> = execute(method, path, typeInfo<T>()) {
        contentType(ContentType.Application.Json)
        setBody(body, typeInfo<B>())
    }

    private suspend fun <T> execute(
        method: HttpMethod,
        path: Array<out String>,
        responseType: TypeInfo,
        configure: HttpRequestBuilder.() -> Unit,
    ): ApiResult<T> {
        val response = try {
            http.request {
                this.method = method
                // Each segment is encoded, including "/", so a token can't change the path.
                url { takeFrom(baseUrl).appendPathSegments(listOf("api", *path), encodeSlash = true) }
                configure()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return ApiResult.NetworkError(e)
        }
        return response.toResult(responseType)
    }

    private suspend fun <T> HttpResponse.toResult(type: TypeInfo): ApiResult<T> {
        if (status.isSuccess()) {
            return try {
                @Suppress("UNCHECKED_CAST")
                val value = if (type.type == Unit::class) Unit as T else body<T>(type)
                ApiResult.Success(value)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                if (e.isUnreadableBody()) {
                    ApiResult.Failure(status.value, ApiError.UNKNOWN, "Unexpected response from the server")
                } else {
                    ApiResult.NetworkError(e)
                }
            }
        }
        val errorBody = try {
            body<ErrorResponse>()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            null // Not an ErrorResponse, e.g. an error page of a proxy.
        }
        return ApiResult.Failure(
            status = status.value,
            error = errorBody?.error ?: fallbackError(status),
            message = errorBody?.message ?: "HTTP ${status.value}",
        )
    }
}

/**
 * JSON settings of the client. Lenient towards a newer server: unknown fields are ignored, and an
 * unknown enum value (such as an [ApiError] added later) becomes the field's default.
 */
internal val apiJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
}

/** The body is there but isn't what was expected (not JSON, or JSON of another shape). */
private fun Throwable.isUnreadableBody() =
    this is ContentConvertException || this is SerializationException || this is NoTransformationFoundException

/** Error code for a response whose body is not an [ErrorResponse]. */
private fun fallbackError(status: HttpStatusCode): ApiError = when {
    status == HttpStatusCode.BadRequest -> ApiError.INVALID_REQUEST
    status == HttpStatusCode.Unauthorized -> ApiError.UNAUTHORIZED
    status == HttpStatusCode.NotFound -> ApiError.NOT_FOUND
    status.value in 500..599 -> ApiError.INTERNAL
    else -> ApiError.UNKNOWN
}
