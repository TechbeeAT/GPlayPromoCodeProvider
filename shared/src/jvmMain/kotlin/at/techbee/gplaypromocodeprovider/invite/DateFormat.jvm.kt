package at.techbee.gplaypromocodeprovider.invite

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Instant
import kotlin.time.toJavaInstant

// Mirrors the browser version (English, day before month, 24-hour clock), so previews look like the app.
private val formatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.UK)

actual fun formatDateTime(instant: Instant): String =
    formatter.format(instant.toJavaInstant().atZone(ZoneId.systemDefault()))
