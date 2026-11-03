package at.techbee.gplaypromocodeprovider

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable

/**
 * Wraps a `@Preview` in the same theme and background as the app (see [App]), so previews look like
 * the real page. Use it for every preview.
 */
@Composable
internal fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(content = content)
    }
}
