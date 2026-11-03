package at.techbee.gplaypromocodeprovider.invite

import at.techbee.gplaypromocodeprovider.api.AvailableBatch
import io.ktor.http.encodeURLParameter
import kotlin.time.Instant

/** Everything the invite page shows. */
data class InviteUiState(
    val content: InviteContent = InviteContent.Loading,
    /** The codes received on this page, newest first. Kept when the batch list changes or fails to load. */
    val receivedCodes: List<ReceivedCode> = emptyList(),
    /** The batch a code is being requested for. While set, no further request can be started. */
    val requestingBatchId: Long? = null,
    /** The batch list is being reloaded in the background, e.g. after a batch ran out. */
    val refreshing: Boolean = false,
    /** A message about the last code request; `null` when there is nothing to say. */
    val notice: InviteNotice? = null,
)

sealed interface InviteContent {

    data object Loading : InviteContent

    /**
     * The server doesn't know the token. Deliberately the same for a mistyped, a revoked and a never
     * existing link, so the page reveals nothing about which apps exist.
     */
    data object InvalidLink : InviteContent

    /** The batch list couldn't be loaded; the user can retry. */
    data class LoadFailed(val reason: FailureReason) : InviteContent

    /** [batches] is empty when the app has no codes available right now. */
    data class Loaded(val appName: String, val batches: List<AvailableBatch>) : InviteContent
}

enum class FailureReason {
    /** No answer: offline, server down, ... */
    NETWORK,

    /** The server answered with an unexpected error. */
    SERVER,
}

/** Outcome of a code request that didn't produce a code. */
enum class InviteNotice {
    /** The batch ran out (or expired) while the page was open. It has been removed from the list. */
    BATCH_RAN_OUT,

    /** The batch no longer exists (e.g. the admin removed it). It has been removed from the list. */
    BATCH_UNAVAILABLE,
    NETWORK_ERROR,
    SERVER_ERROR,
}

/** A code handed out to this user, with the batch it came from. */
data class ReceivedCode(
    val code: String,
    val batchDescription: String?,
    val expiresAt: Instant,
) {
    val redeemUrl: String get() = redeemUrl(code)
}

/** Google Play's page for redeeming [code]. */
fun redeemUrl(code: String): String = "https://play.google.com/redeem?code=${code.encodeURLParameter()}"
