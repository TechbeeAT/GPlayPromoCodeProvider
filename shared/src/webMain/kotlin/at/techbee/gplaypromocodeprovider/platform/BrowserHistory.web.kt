package at.techbee.gplaypromocodeprovider.platform

import kotlinx.browser.window
import org.w3c.dom.events.Event

actual fun pushBrowserPath(path: String) = pushState(window.location.pathname, path)

actual fun previousBrowserPath(): String? = historyStateString()

actual fun goBackInBrowser() = window.history.back()

actual fun observeBrowserPath(onPathChange: (String) -> Unit): () -> Unit {
    val listener: (Event) -> Unit = { onPathChange(window.location.pathname) }
    window.addEventListener("popstate", listener)
    return { window.removeEventListener("popstate", listener) }
}

// The history entry's state object is the path it was opened from. Written in JS, so it is a plain
// string on both targets (JS and Wasm).
@OptIn(ExperimentalWasmJsInterop::class)
private fun pushState(from: String, path: String): Unit = js("window.history.pushState(from, '', path)")

@OptIn(ExperimentalWasmJsInterop::class)
private fun historyStateString(): String? =
    js("(typeof window.history.state === 'string') ? window.history.state : null")
