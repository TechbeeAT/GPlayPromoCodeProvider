package at.techbee.gplaypromocodeprovider

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport {
        App(path = window.location.pathname, origin = window.location.origin)
    }
}
