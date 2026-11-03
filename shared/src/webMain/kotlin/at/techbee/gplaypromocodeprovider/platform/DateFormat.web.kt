package at.techbee.gplaypromocodeprovider.platform

import kotlin.time.Instant

actual fun formatDateTime(instant: Instant): String = formatEpochMillis(instant.toEpochMilliseconds().toDouble())

actual fun formatDate(instant: Instant): String = formatEpochMillisAsDate(instant.toEpochMilliseconds().toDouble())

@OptIn(ExperimentalWasmJsInterop::class)
private fun formatEpochMillisAsDate(epochMillis: Double): String =
    js("new Date(epochMillis).toLocaleDateString('en-GB', { dateStyle: 'medium' })")

// English (UI language), with day before month and a 24-hour clock, so it reads the same everywhere.
@OptIn(ExperimentalWasmJsInterop::class)
private fun formatEpochMillis(epochMillis: Double): String =
    js("new Date(epochMillis).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' })")

actual fun endOfLocalDay(utcDateMillis: Long): Instant =
    Instant.fromEpochMilliseconds(endOfLocalDayMillis(utcDateMillis.toDouble()).toLong())

// Takes year, month and day in UTC (that's how the picker encodes the day), then builds a local time.
// The Date constructor applies the browser's time zone including daylight saving time.
@OptIn(ExperimentalWasmJsInterop::class)
private fun endOfLocalDayMillis(utcDateMillis: Double): Double =
    js("(function(d) { return new Date(d.getUTCFullYear(), d.getUTCMonth(), d.getUTCDate(), 23, 59, 59).getTime(); })(new Date(utcDateMillis))")
