package at.techbee.gplaypromocodeprovider.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

class DateFormatTest {

    // Holds in every time zone: the picked day ends at 23:59 local time, and is shown in local time.
    @Test
    fun pickedDayEndsAtItsLastMinuteInLocalTime() {
        val day = Instant.parse("2027-01-31T00:00:00Z").toEpochMilliseconds()

        assertEquals("31 Jan 2027, 23:59", formatDateTime(endOfLocalDay(day)))
    }

    @Test
    fun endOfDayAcceptsAnyTimeOfThePickedUtcDay() {
        val midnight = Instant.parse("2027-06-15T00:00:00Z").toEpochMilliseconds()
        val evening = Instant.parse("2027-06-15T21:30:00Z").toEpochMilliseconds()

        assertEquals(endOfLocalDay(midnight), endOfLocalDay(evening))
    }
}
