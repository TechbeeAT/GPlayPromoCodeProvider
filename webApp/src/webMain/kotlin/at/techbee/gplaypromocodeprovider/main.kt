package at.techbee.gplaypromocodeprovider

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import at.techbee.gplaypromocodeprovider.client.ApiClient
import kotlinx.browser.window

@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    // One client for the whole page, on the origin the page came from. It lives as long as the page,
    // so it is never closed.
    val api = ApiClient(window.location.origin)
    ComposeViewport {
        App(path = window.location.pathname, origin = window.location.origin, api = api)
    }
}
