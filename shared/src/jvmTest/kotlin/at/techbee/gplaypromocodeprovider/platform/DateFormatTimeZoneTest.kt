package at.techbee.gplaypromocodeprovider.platform

import java.util.TimeZone
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

/**
 * The common date tests run in the test runner's time zone, UTC in CI and cloud sessions, where
 * "midnight UTC" and "local day" can't disagree. These run the same checks east and west of UTC.
 */
class DateFormatTimeZoneTest {

    private val original = TimeZone.getDefault()
    private val pickedDay = Instant.parse("2027-01-31T00:00:00Z").toEpochMilliseconds()

    @AfterTest
    fun restore() = TimeZone.setDefault(original)

    private fun inZone(id: String, block: () -> Unit) {
        TimeZone.setDefault(TimeZone.getTimeZone(id))
        block()
    }

    @Test
    fun westOfUtcThePickedDayIsStillThatDay() = inZone("America/New_York") {
        // The bug the date picker's own headline had: midnight UTC is the evening before here.
        assertEquals("30 Jan 2027", formatDate(Instant.fromEpochMilliseconds(pickedDay)))

        assertEquals("31 Jan 2027", formatDate(endOfLocalDay(pickedDay)))
        assertEquals("31 Jan 2027, 23:59", formatDateTime(endOfLocalDay(pickedDay)))
        assertEquals(Instant.parse("2027-02-01T04:59:59Z"), endOfLocalDay(pickedDay))
    }

    @Test
    fun eastOfUtcThePickedDayIsStillThatDay() = inZone("Pacific/Auckland") {
        assertEquals("31 Jan 2027", formatDate(endOfLocalDay(pickedDay)))
        assertEquals("31 Jan 2027, 23:59", formatDateTime(endOfLocalDay(pickedDay)))
        assertEquals(Instant.parse("2027-01-31T10:59:59Z"), endOfLocalDay(pickedDay))
    }

    @Test
    fun dayWithADaylightSavingSwitchEndsAtLocal2359() = inZone("Europe/Vienna") {
        // 28 Mar 2027: clocks go from 02:00 to 03:00; the day ends at 23:59:59 CEST (UTC+2).
        val day = Instant.parse("2027-03-28T00:00:00Z").toEpochMilliseconds()

        assertEquals(Instant.parse("2027-03-28T21:59:59Z"), endOfLocalDay(day))
    }
}
