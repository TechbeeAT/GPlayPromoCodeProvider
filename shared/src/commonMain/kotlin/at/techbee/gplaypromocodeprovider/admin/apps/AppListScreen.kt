package at.techbee.gplaypromocodeprovider.admin.apps

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.admin.components.AppDialogState
import at.techbee.gplaypromocodeprovider.admin.components.AppFormDialog
import at.techbee.gplaypromocodeprovider.admin.components.LoadFailedMessage
import at.techbee.gplaypromocodeprovider.admin.components.LoadingBox
import at.techbee.gplaypromocodeprovider.admin.components.StatsRow
import at.techbee.gplaypromocodeprovider.api.AdminApp
import at.techbee.gplaypromocodeprovider.api.AppRequest
import at.techbee.gplaypromocodeprovider.api.CodeStats

@Composable
fun AppListScreen(viewModel: AppListViewModel, onOpenApp: (Long) -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.resumeAfterLogin() }
    AppListPage(
        state = state,
        onOpenApp = onOpenApp,
        onRetry = viewModel::retry,
        onNewApp = viewModel::openCreateDialog,
        onCreate = viewModel::createApp,
        onDismissCreate = viewModel::closeCreateDialog,
    )
}

@Composable
private fun AppListPage(
    state: AppListUiState,
    onOpenApp: (Long) -> Unit,
    onRetry: () -> Unit,
    onNewApp: () -> Unit,
    onCreate: (AppRequest) -> Unit,
    onDismissCreate: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("Apps", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            if (state.content is AppListContent.Loaded) Button(onClick = onNewApp) { Text("New app") }
        }
        when (val content = state.content) {
            AppListContent.Loading -> LoadingBox()
            is AppListContent.LoadFailed -> LoadFailedMessage("Couldn't load the apps", content.error, onRetry)
            is AppListContent.Loaded ->
                if (content.apps.isEmpty()) {
                    Text(
                        "No apps yet. Create one, then upload promo codes for it and share its invite link.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    content.apps.forEach { AppCard(it, onClick = { onOpenApp(it.id) }) }
                }
        }
    }
    state.createDialog?.let {
        AppFormDialog(title = "New app", initial = null, state = it, onSave = onCreate, onDismiss = onDismissCreate)
    }
}

@Composable
private fun AppCard(app: AdminApp, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(app.name, style = MaterialTheme.typography.titleLarge)
            app.packageName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            StatsRow(app.stats)
        }
    }
}

// Previews: one per state of the page. Sample data only.

private val previewApps = listOf(
    AdminApp(1, "Jtx Board", "at.techbee.jtx", "Journals, notes and tasks", "token1", CodeStats(42, 7, 3)),
    AdminApp(2, "Kanban", null, null, "token2", CodeStats(0, 0, 0)),
)

@Composable
private fun AppListPreview(state: AppListUiState) {
    MaterialTheme {
        Surface {
            Column(Modifier.padding(16.dp)) {
                AppListPage(state, onOpenApp = {}, onRetry = {}, onNewApp = {}, onCreate = {}, onDismissCreate = {})
            }
        }
    }
}

@Preview(widthDp = 600, heightDp = 300)
@Composable
private fun AppListLoadingPreview() = AppListPreview(AppListUiState())

@Preview(widthDp = 600, heightDp = 400)
@Composable
private fun AppListLoadFailedPreview() =
    AppListPreview(AppListUiState(content = AppListContent.LoadFailed(ActionError.Network)))

@Preview(widthDp = 600, heightDp = 300)
@Composable
private fun AppListEmptyPreview() = AppListPreview(AppListUiState(content = AppListContent.Loaded(emptyList())))

@Preview(widthDp = 600, heightDp = 500)
@Composable
private fun AppListLoadedPreview() = AppListPreview(AppListUiState(content = AppListContent.Loaded(previewApps)))

@Preview(widthDp = 600, heightDp = 600)
@Composable
private fun AppListCreatingPreview() = AppListPreview(
    AppListUiState(content = AppListContent.Loaded(previewApps), createDialog = AppDialogState(saving = true)),
)
