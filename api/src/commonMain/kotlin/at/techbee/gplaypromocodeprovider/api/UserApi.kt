package at.techbee.gplaypromocodeprovider.api

import kotlinx.serialization.Serializable
import kotlin.time.Instant

/**
 * Response of `GET /api/invite/{token}`: the app behind an invite link and its batches
 * that still have codes available.
 */
@Serializable
data class InviteResponse(
    val appName: String,
    val batches: List<AvailableBatch>,
)

/** A batch as shown to users. Deliberately contains no codes and no counts. */
@Serializable
data class AvailableBatch(
    val id: Long,
    val description: String?,
    val expiresAt: Instant,
)

/** Response of `POST /api/invite/{token}/batches/{batchId}/code`: the single handed-out code. */
@Serializable
data class PromoCodeResponse(
    val code: String,
)
