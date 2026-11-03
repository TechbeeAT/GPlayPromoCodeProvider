package at.techbee.gplaypromocodeprovider.invite

import kotlin.time.Instant

/**
 * [instant] as a date and time in the browser's time zone, e.g. "31 Jan 2027, 23:59".
 * Uses the browser's `Intl` support, which knows the user's time zone including daylight saving time.
 */
expect fun formatDateTime(instant: Instant): String
