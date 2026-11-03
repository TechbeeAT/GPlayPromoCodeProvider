package at.techbee.gplaypromocodeprovider.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The browser tests run in America/New_York (`TZ` in shared/build.gradle.kts), so the browser versions
 * of the date functions are checked west of UTC, where midnight UTC is still the evening before.
 */
class BrowserTimeZoneTest {

    private val pickedDay = Instant.parse("2027-01-31T00:00:00Z").toEpochMilliseconds()

    @Test
    fun browserRunsWestOfUtc() {
        // Guards the setup: if TZ is no longer applied, the tests below would pass for the wrong reason.
        assertEquals("30 Jan 2027", formatDate(Instant.fromEpochMilliseconds(pickedDay)))
    }

    @Test
    fun pickedDayIsStillThatDay() {
        assertEquals("31 Jan 2027", formatDate(endOfLocalDay(pickedDay)))
        assertEquals("31 Jan 2027, 23:59", formatDateTime(endOfLocalDay(pickedDay)))
        assertEquals(Instant.parse("2027-02-01T04:59:59Z"), endOfLocalDay(pickedDay))
    }
}
