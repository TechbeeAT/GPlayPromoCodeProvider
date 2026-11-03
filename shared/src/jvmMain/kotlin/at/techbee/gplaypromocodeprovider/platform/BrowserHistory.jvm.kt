package at.techbee.gplaypromocodeprovider.platform

// No browser on the JVM (previews only): there is no history to change or observe.

actual fun pushBrowserPath(path: String) = Unit

actual fun observeBrowserPath(onPathChange: (String) -> Unit): () -> Unit = {}
