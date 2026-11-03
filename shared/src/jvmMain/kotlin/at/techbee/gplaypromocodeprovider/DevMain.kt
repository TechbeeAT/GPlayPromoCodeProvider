package at.techbee.gplaypromocodeprovider

import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState

/**
 * Development-only desktop window for Compose Hot Reload (`./gradlew :shared:hotRunJvm`). The real app
 * runs in the browser (`webApp`); this window shows the same [App] on the JVM, so UI changes appear
 * within a second or two after saving, without a page reload and without losing state.
 *
 * Environment variables:
 * - `APP_PATH`: the URL path to open, e.g. `/invite/<token>` (default `/`)
 * - `API_ORIGIN`: where the Ktor server runs (default `http://localhost:8080`)
 *
 * Not shipped: the Docker image only contains the server and the compiled web app.
 */
fun main() {
    val path = System.getenv("APP_PATH")?.takeIf { it.isNotBlank() } ?: "/"
    val origin = System.getenv("API_ORIGIN")?.takeIf { it.isNotBlank() } ?: "http://localhost:8080"
    application {
        Window(
            onCloseRequest = ::exitApplication,
            title = "GPlay Promo Code Provider (dev)",
            state = rememberWindowState(width = 480.dp, height = 800.dp),
        ) {
            App(path = path, origin = origin)
        }
    }
}
