package at.techbee.gplaypromocodeprovider.invite

import kotlin.time.Instant

actual fun formatDateTime(instant: Instant): String = formatEpochMillis(instant.toEpochMilliseconds().toDouble())

// English (UI language), with day before month and a 24-hour clock, so it reads the same everywhere.
@OptIn(ExperimentalWasmJsInterop::class)
private fun formatEpochMillis(epochMillis: Double): String =
    js("new Date(epochMillis).toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' })")
