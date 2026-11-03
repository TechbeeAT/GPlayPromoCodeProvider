package at.techbee.gplaypromocodeprovider.invite

import at.techbee.gplaypromocodeprovider.api.AvailableBatch
import kotlin.time.Instant

/** Sample data for the `@Preview`s of the invite page and its components. Fixed dates, so previews don't change. */
internal object PreviewData {
    const val APP_NAME = "jtx Board"

    val expiry: Instant = Instant.parse("2027-01-31T22:59:00Z")
    private val earlierExpiry: Instant = Instant.parse("2026-12-31T22:59:00Z")

    val batches = listOf(
        AvailableBatch(1, "Beta wave 2", expiry),
        AvailableBatch(2, null, earlierExpiry),
    )

    val codes = listOf(
        ReceivedCode("WAVE2BBBBBBBBBBBBBBB", "Beta wave 2", expiry),
        ReceivedCode("WAVE2AAAAAAAAAAAAAAA", "Beta wave 2", expiry),
        ReceivedCode("SINGLEXXXXXXXXXXXXXX", null, earlierExpiry),
    )
}
