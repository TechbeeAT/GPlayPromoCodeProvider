package at.techbee.gplaypromocodeprovider.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import at.techbee.gplaypromocodeprovider.platform.plainTextClipEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/** Copies a text to the clipboard and remembers how that went, for the button's label and an error hint. */
@Stable
internal class CopyToClipboard(
    private val text: String,
    private val clipboard: Clipboard,
    private val scope: CoroutineScope,
) {
    /** `true` right after copying (for a moment), `false` if the browser refused, otherwise `null`. */
    var result by mutableStateOf<Boolean?>(null)
        private set

    fun copy() {
        scope.launch {
            result = try {
                clipboard.setClipEntry(plainTextClipEntry(text))
                true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                false // E.g. the browser denied clipboard access.
            }
        }
    }

    internal fun clearSuccess() {
        if (result == true) result = null
    }
}

/** A [CopyToClipboard] for [text]. A success is shown for two seconds, a failure until the next try. */
@Composable
internal fun rememberCopyToClipboard(text: String): CopyToClipboard {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val copy = remember(text, clipboard, scope) { CopyToClipboard(text, clipboard, scope) }
    LaunchedEffect(copy, copy.result) {
        if (copy.result == true) {
            delay(2.seconds)
            copy.clearSuccess()
        }
    }
    return copy
}
