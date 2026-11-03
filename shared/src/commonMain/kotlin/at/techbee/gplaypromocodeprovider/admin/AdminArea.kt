package at.techbee.gplaypromocodeprovider.admin

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import at.techbee.gplaypromocodeprovider.Route
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.admin.appdetails.AppDetailScreen
import at.techbee.gplaypromocodeprovider.admin.appdetails.AppDetailViewModel
import at.techbee.gplaypromocodeprovider.admin.applist.AppListScreen
import at.techbee.gplaypromocodeprovider.admin.applist.AppListViewModel
import at.techbee.gplaypromocodeprovider.admin.components.ErrorText
import at.techbee.gplaypromocodeprovider.admin.components.LoadFailedMessage
import at.techbee.gplaypromocodeprovider.admin.components.LoadingBox
import at.techbee.gplaypromocodeprovider.admin.login.AdminSessionState
import at.techbee.gplaypromocodeprovider.admin.login.AdminSessionViewModel
import at.techbee.gplaypromocodeprovider.admin.login.LoginScreen
import at.techbee.gplaypromocodeprovider.client.ApiClient

/**
 * The admin area: the login form, or the admin page of [route] with a bar for logging out.
 *
 * Each page gets its own view models, created when the page is opened and cleared when another page
 * is opened (like a page load). They are kept while the login form is shown after the session ended,
 * so e.g. pasted codes aren't lost, and cleared on logout.
 */
@Composable
fun AdminArea(route: Route.Admin, api: ApiClient, origin: String, navigate: (Route.Admin) -> Unit) {
    val session = viewModel { AdminSessionViewModel(api) }
    val state by session.state.collectAsStateWithLifecycle()

    val pageViewModels = remember(route) { PageViewModels() }
    DisposableEffect(pageViewModels) { onDispose { pageViewModels.viewModelStore.clear() } }
    LaunchedEffect(state) {
        val s = state
        if (s is AdminSessionState.LoggedOut && !s.sessionExpired) pageViewModels.viewModelStore.clear()
    }

    when (val s = state) {
        AdminSessionState.Checking -> LoadingBox()
        is AdminSessionState.CheckFailed -> SessionCheckFailed(s.error, session::retry)
        is AdminSessionState.LoggedOut -> LoginScreen(s, session::login)
        is AdminSessionState.LoggedIn -> Column(Modifier.fillMaxSize()) {
            AdminBar(s, onLogout = session::logout, onDismissLogoutError = session::dismissLogoutError)
            AdminColumn {
                CompositionLocalProvider(LocalViewModelStoreOwner provides pageViewModels) {
                    when (route) {
                        Route.AdminApps -> AppListScreen(
                            viewModel { AppListViewModel(api, session::sessionExpired) },
                            onOpenApp = { navigate(Route.AdminApp(it)) },
                        )
                        is Route.AdminApp -> AppDetailScreen(
                            viewModel { AppDetailViewModel(route.appId, api, session::sessionExpired) },
                            origin = origin,
                            onBack = { navigate(Route.AdminApps) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCheckFailed(error: ActionError, onRetry: () -> Unit) {
    AdminColumn { LoadFailedMessage("Couldn't open the admin area", error, onRetry) }
}

/** Holds the view models of one admin page. */
private class PageViewModels : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

/** One scrollable, centered column, at most 800 dp wide. */
@Composable
private fun AdminColumn(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(Modifier.widthIn(max = 800.dp).fillMaxWidth().padding(16.dp)) { content() }
    }
}

@Composable
private fun AdminBar(state: AdminSessionState.LoggedIn, onLogout: () -> Unit, onDismissLogoutError: () -> Unit) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Promo codes · Admin", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(state.username, style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onLogout, enabled = !state.loggingOut) { Text(if (state.loggingOut) "Logging out…" else "Log out") }
        }
        state.logoutError?.let {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                ErrorText("Couldn't log out. ${it.text}", Modifier.weight(1f))
                TextButton(onClick = onDismissLogoutError) { Text("OK") }
            }
        }
        HorizontalDivider()
    }
}

@Preview(widthDp = 600, heightDp = 100)
@Composable
private fun AdminBarPreview() {
    PreviewTheme { AdminBar(AdminSessionState.LoggedIn("admin"), onLogout = {}, onDismissLogoutError = {}) }
}

@Preview(widthDp = 600, heightDp = 150)
@Composable
private fun AdminBarLogoutFailedPreview() {
    PreviewTheme {
        AdminBar(AdminSessionState.LoggedIn("admin", logoutError = ActionError.Network), onLogout = {}, onDismissLogoutError = {})
    }
}

// The session check's states (the login form has its own previews in login/LoginScreen.kt).

@Preview(widthDp = 600, heightDp = 300)
@Composable
private fun SessionCheckingPreview() {
    PreviewTheme { LoadingBox() }
}

@Preview(widthDp = 600, heightDp = 300)
@Composable
private fun SessionCheckFailedPreview() {
    PreviewTheme { SessionCheckFailed(ActionError.Network, onRetry = {}) }
}
