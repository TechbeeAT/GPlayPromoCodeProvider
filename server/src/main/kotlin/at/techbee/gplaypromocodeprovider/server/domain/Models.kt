package at.techbee.gplaypromocodeprovider.server.domain

import kotlin.time.Instant

data class App(
    val id: Long,
    val name: String,
    val packageName: String?,
    val description: String?,
    val inviteToken: String,
)

data class Batch(
    val id: Long,
    val appId: Long,
    val description: String?,
    val expiresAt: Instant,
    val createdAt: Instant,
    /** When the admin invalidated the batch; `null` while it is valid. */
    val invalidatedAt: Instant? = null,
)

/**
 * Code counts of a batch or app. A code that wasn't handed out counts as [invalidated] if its batch
 * was invalidated, otherwise as [expired] once the batch is expired, otherwise as [available].
 */
data class Stats(val available: Int, val consumed: Int, val expired: Int, val invalidated: Int = 0) {
    operator fun plus(other: Stats) = Stats(
        available + other.available,
        consumed + other.consumed,
        expired + other.expired,
        invalidated + other.invalidated,
    )

    companion object {
        val ZERO = Stats(0, 0, 0, 0)
    }
}

data class BatchWithStats(val batch: Batch, val stats: Stats)

/** A batch is expired from its expiration instant on; its remaining codes are never handed out. */
fun Batch.isExpired(now: Instant) = expiresAt <= now

/** An invalidated batch never hands out its remaining codes again. */
val Batch.isInvalidated get() = invalidatedAt != null
