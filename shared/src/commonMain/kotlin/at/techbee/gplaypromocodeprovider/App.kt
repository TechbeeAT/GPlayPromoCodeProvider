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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import at.techbee.gplaypromocodeprovider.client.ApiClient
import at.techbee.gplaypromocodeprovider.invite.InviteScreen
import at.techbee.gplaypromocodeprovider.invite.InviteViewModel

/**
 * The app shell. Picks the screen from the browser's URL path. The admin screens (step #6) will be
 * added here.
 *
 * @param path the page's URL path, e.g. `/invite/abc`
 * @param origin the origin the page was loaded from; the API is expected there
 */
@Composable
fun App(path: String, origin: String) {
    val route = remember(path) { Route.parse(path) }
    val api = remember(origin) { ApiClient(origin) }
    DisposableEffect(api) { onDispose { api.close() } }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            when (route) {
                is Route.Invite -> InviteScreen(viewModel { InviteViewModel(route.token, api) })
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
