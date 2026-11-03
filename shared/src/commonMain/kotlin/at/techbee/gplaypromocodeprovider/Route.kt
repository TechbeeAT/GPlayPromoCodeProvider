package at.techbee.gplaypromocodeprovider

import io.ktor.http.decodeURLPart

/**
 * The screen to show, derived from the browser's path, so a deep link works the same as a page reload.
 * The admin pages navigate between each other in the app (see [Admin.path]); everything else is a
 * full page load.
 */
sealed interface Route {

    /** `/` and every path the app doesn't know. */
    data object Home : Route

    /** `/invite/<token>`, the link a user gets from the admin. */
    data class Invite(val token: String) : Route

    /** The admin area. Its pages need a login; without one they show the login form. */
    sealed interface Admin : Route {
        /** The path of this page, for navigating to it. */
        val path: String
    }

    /** `/admin` (also `/admin/apps`): the list of apps. */
    data object AdminApps : Admin {
        override val path = "/admin"
    }

    /** `/admin/apps/<id>`: one app with its invite link, batches and the upload form. */
    data class AdminApp(val appId: Long) : Admin {
        override val path get() = "/admin/apps/$appId"
    }

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
            if (segments[0] == "admin") {
                when (segments.size) {
                    1 -> return AdminApps
                    2 -> if (segments[1] == "apps") return AdminApps
                    3 -> if (segments[1] == "apps") {
                        segments[2].toLongOrNull()?.takeIf { it > 0 }?.let { return AdminApp(it) }
                    }
                }
            }
            return Home
        }
    }
}
