package at.techbee.gplaypromocodeprovider.platform

import kotlinx.browser.window
import org.w3c.dom.events.Event

// `pushState` takes a JS value as its state object; there is none, hence the interop opt-in.
@OptIn(ExperimentalWasmJsInterop::class)
actual fun pushBrowserPath(path: String) {
    window.history.pushState(null, "", path)
}

actual fun observeBrowserPath(onPathChange: (String) -> Unit): () -> Unit {
    val listener: (Event) -> Unit = { onPathChange(window.location.pathname) }
    window.addEventListener("popstate", listener)
    return { window.removeEventListener("popstate", listener) }
}
