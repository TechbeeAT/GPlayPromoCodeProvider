package at.techbee.gplaypromocodeprovider.platform

/**
 * Shows [path] in the address bar and adds it to the browser history, without loading the page again.
 * Used for navigating between the admin pages; the back button then goes to the previous page.
 */
expect fun pushBrowserPath(path: String)

/**
 * Calls [onPathChange] with the new path when the user goes back or forward in the browser history.
 * Returns a function that stops listening.
 */
expect fun observeBrowserPath(onPathChange: (String) -> Unit): () -> Unit
