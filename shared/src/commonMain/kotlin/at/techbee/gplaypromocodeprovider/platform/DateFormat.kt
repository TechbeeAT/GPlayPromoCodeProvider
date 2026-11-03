package at.techbee.gplaypromocodeprovider.platform

import kotlin.time.Instant

/**
 * [instant] as a date and time in the browser's time zone, e.g. "31 Jan 2027, 23:59".
 * Uses the browser's `Intl` support, which knows the user's time zone including daylight saving time.
 */
expect fun formatDateTime(instant: Instant): String

/** The day of [instant] in the browser's time zone, e.g. "31 Jan 2027". */
expect fun formatDate(instant: Instant): String

/**
 * The last minute of a calendar day in the browser's time zone (23:59:59 local time), for turning a
 * date picked by the admin into a batch's exact expiration instant.
 *
 * @param utcDateMillis the picked day as Material's date picker returns it: midnight UTC of that day
 */
expect fun endOfLocalDay(utcDateMillis: Long): Instant
