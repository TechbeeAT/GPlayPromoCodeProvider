package at.techbee.gplaypromocodeprovider.server.routes

import io.ktor.http.CacheControl
import io.ktor.server.http.content.staticFiles
import io.ktor.server.routing.Route
import java.io.File

/**
 * Serves the compiled web app (`index.html`, scripts, Wasm files) from [dir].
 *
 * Every path without a file (e.g. `/invite/<token>`, `/admin/apps/1`) gets `index.html`, so deep
 * links and page reloads work: the web app picks its screen from the path. Unknown `/api` paths don't
 * get here, they have their own 404 (see `Application.module`).
 *
 * The file names of a build don't change between releases (`webApp.js`, ...), so the browser must
 * not use a cached copy without asking: `Cache-Control: no-cache` makes it revalidate each time, and
 * the `ConditionalHeaders` plugin answers `304 Not Modified` when the file is unchanged.
 */
fun Route.webAppRoutes(dir: File) {
    require(dir.resolve("index.html").isFile) { "WEB_DIR ${dir.absolutePath} contains no index.html" }
    staticFiles("/", dir) {
        default("index.html")
        cacheControl { listOf(CacheControl.NoCache(null)) }
    }
}
