package at.techbee.gplaypromocodeprovider.platform

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

// Mirrors the browser version (English, day before month, 24-hour clock), so previews look like the app.
private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)

actual fun formatDateTime(instant: Instant): String =
    formatter.format(instant.toJavaInstant().atZone(ZoneId.systemDefault()))

actual fun endOfLocalDay(utcDateMillis: Long): Instant {
    val day = LocalDate.ofEpochDay(Math.floorDiv(utcDateMillis, 86_400_000L))
    val end = day.atTime(LocalTime.of(23, 59, 59)).atZone(ZoneId.systemDefault())
    return Instant.fromEpochMilliseconds(end.toInstant().toEpochMilli())
}
