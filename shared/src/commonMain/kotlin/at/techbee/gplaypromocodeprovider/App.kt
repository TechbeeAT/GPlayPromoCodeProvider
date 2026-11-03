package at.techbee.gplaypromocodeprovider

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.techbee.gplaypromocodeprovider.admin.AdminArea
import at.techbee.gplaypromocodeprovider.client.ApiClient
import at.techbee.gplaypromocodeprovider.invite.InviteScreen
import at.techbee.gplaypromocodeprovider.invite.InviteViewModel
import at.techbee.gplaypromocodeprovider.platform.observeBrowserPath
import at.techbee.gplaypromocodeprovider.platform.pushBrowserPath

/**
 * The app shell. Picks the screen from the browser's URL path. The admin pages navigate between
 * each other without a page load ([pushBrowserPath]); the browser's back and forward buttons work.
 *
 * @param path the page's URL path when it was loaded, e.g. `/invite/abc`
 * @param origin the origin the page was loaded from; the API is expected there
 */
@Composable
fun App(path: String, origin: String) {
    var currentPath by remember { mutableStateOf(path) }
    DisposableEffect(Unit) {
        val stop = observeBrowserPath { currentPath = it }
        onDispose { stop() }
    }
    val route = remember(currentPath) { Route.parse(currentPath) }
    val navigate = { to: Route.Admin ->
        pushBrowserPath(to.path)
        currentPath = to.path
    }
    val api = remember(origin) { ApiClient(origin) }
    DisposableEffect(api) { onDispose { api.close() } }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (route) {
                is Route.Invite -> InviteScreen(viewModel { InviteViewModel(route.token, api) })
                is Route.Admin -> AdminArea(route, api, origin, navigate)
                Route.Home -> HomeScreen()
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("GPlay Promo Code Provider", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Text(
            "Open the invite link you received to get a promo code.",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun HomeScreenPreview() {
    MaterialTheme {
        Surface { HomeScreen() }
    }
}
