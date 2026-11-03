package at.techbee.gplaypromocodeprovider.platform

/**
 * Shows [path] in the address bar and adds it to the browser history, without loading the page again.
 * Used for navigating between the admin pages; the back button then goes to the previous page. The new
 * history entry remembers the path it was opened from ([previousBrowserPath]).
 */
expect fun pushBrowserPath(path: String)

/**
 * The path the current history entry was opened from by [pushBrowserPath], i.e. the page one step back,
 * or `null` if the entry wasn't opened that way (e.g. a link or a reload).
 */
expect fun previousBrowserPath(): String?

/** Goes one step back in the browser history, like the back button. [observeBrowserPath] reports the new path. */
expect fun goBackInBrowser()

/**
 * Calls [onPathChange] with the new path when the user goes back or forward in the browser history.
 * Returns a function that stops listening.
 */
expect fun observeBrowserPath(onPathChange: (String) -> Unit): () -> Unit
