package at.techbee.gplaypromocodeprovider.server.routes

import io.ktor.http.CacheControl
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.http.content.staticFiles
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondFile
import io.ktor.server.routing.Route
import java.io.File

/**
 * Serves the compiled web app (`index.html`, scripts, Wasm files) from [dir].
 *
 * A page path without a file (e.g. `/invite/<token>`, `/admin/apps/1`) gets `index.html`, so deep
 * links and page reloads work: the web app picks its screen from the path. A missing *file* (a path
 * whose last segment has an extension, like `/webApp.js.map`) gets a plain `404` instead: answering it
 * with `index.html` makes the browser try to parse HTML as a script, source map or Wasm file, which
 * hides the real problem. Unknown `/api` paths don't get here, they have their own 404 (see
 * `Application.module`).
 *
 * The file names of a build don't change between releases (`webApp.js`, ...), so the browser must
 * not use a cached copy without asking: `Cache-Control: no-cache` makes it revalidate each time, and
 * the `ConditionalHeaders` plugin answers `304 Not Modified` when the file is unchanged.
 */
fun Route.webAppRoutes(dir: File) {
    val index = dir.resolve("index.html")
    require(index.isFile) { "WEB_DIR ${dir.absolutePath} contains no index.html" }
    staticFiles("/", dir) {
        cacheControl { listOf(CacheControl.NoCache(null)) }
        fallback { requestedPath, call ->
            if (looksLikeFile(requestedPath)) {
                call.respond(HttpStatusCode.NotFound)
            } else {
                call.response.header(HttpHeaders.CacheControl, CacheControl.NoCache(null).toString())
                call.respondFile(index)
            }
        }
    }
}

/** Whether the last path segment has a file extension (`app.wasm`), unlike page paths (`/invite/<token>`). */
internal fun looksLikeFile(path: String): Boolean = '.' in path.substringAfterLast('/')
