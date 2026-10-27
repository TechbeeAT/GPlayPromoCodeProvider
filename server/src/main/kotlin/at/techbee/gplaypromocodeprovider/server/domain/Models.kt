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
)

data class Stats(val available: Int, val consumed: Int, val expired: Int) {
    operator fun plus(other: Stats) =
        Stats(available + other.available, consumed + other.consumed, expired + other.expired)

    companion object {
        val ZERO = Stats(0, 0, 0)
    }
}

data class BatchWithStats(val batch: Batch, val stats: Stats)

/** A batch is expired from its expiration instant on; its remaining codes are never handed out. */
fun Batch.isExpired(now: Instant) = expiresAt <= now
