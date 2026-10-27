package at.techbee.gplaypromocodeprovider.api

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/** Body of `POST /api/admin/login`. */
@Serializable
data class LoginRequest(
    val username: String,
    val password: String,
)

/** Response of `GET /api/admin/session` when the admin is logged in. */
@Serializable
data class AdminSessionResponse(
    val username: String,
)

/** Body of `POST /api/admin/apps` and `PUT /api/admin/apps/{appId}`. */
@Serializable
data class AppRequest(
    val name: String,
    val packageName: String? = null,
    val description: String? = null,
)

/** An app as seen by the admin, including its invite token and code statistics. */
@Serializable
data class AdminApp(
    val id: Long,
    val name: String,
    val packageName: String?,
    val description: String?,
    val inviteToken: String,
    val stats: CodeStats,
)

/** Body of `POST /api/admin/apps/{appId}/batches`. */
@Serializable
data class BatchUploadRequest(
    val description: String? = null,
    val expiresAt: Instant,
    /** The codes as pasted by the admin: one per line, surrounding whitespace and blank lines are ignored. */
    val codes: String,
)

/** Response of a batch upload. */
@Serializable
data class BatchUploadResponse(
    val batch: AdminBatch,
    /** Number of codes stored in the new batch. */
    val addedCount: Int,
    /** Codes that were skipped because they already exist for this app or appear twice in the upload. */
    val duplicates: List<String>,
)

/** A batch as seen by the admin, including code statistics. */
@Serializable
data class AdminBatch(
    val id: Long,
    val appId: Long,
    val description: String?,
    val expiresAt: Instant,
    val createdAt: Instant,
    val stats: CodeStats,
)

/**
 * Code counts. A code is either consumed (handed out), expired (not handed out and its batch
 * is past the expiration date) or available.
 */
@Serializable
data class CodeStats(
    val available: Int,
    val consumed: Int,
    val expired: Int,
)
