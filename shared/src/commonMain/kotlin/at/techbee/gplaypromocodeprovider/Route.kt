package at.techbee.gplaypromocodeprovider

import io.ktor.http.decodeURLPart

/**
 * The screen to show, derived from the browser's path. The web app has no in-app navigation: every
 * route is a full page load, so a deep link works the same as a page reload.
 */
sealed interface Route {

    /** `/` and every path the app doesn't know. */
    data object Home : Route

    /** `/invite/<token>`, the link a user gets from the admin. */
    data class Invite(val token: String) : Route

    companion object {

        /** The route for a URL path such as `/invite/abc`. A trailing slash is ignored. */
        fun parse(path: String): Route {
            val segments = path.trim('/').split('/')
            if (segments.size == 2 && segments[0] == "invite") {
                val token = try {
                    segments[1].decodeURLPart()
                } catch (_: Exception) {
                    // Malformed percent encoding. Still an invite link, just an invalid one; the
                    // server answers 404 and the user sees the generic message.
                    segments[1]
                }
                if (token.isNotBlank()) return Invite(token)
            }
            return Home
        }
    }
}
