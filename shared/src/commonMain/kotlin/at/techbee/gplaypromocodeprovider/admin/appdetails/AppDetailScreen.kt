package at.techbee.gplaypromocodeprovider.admin.appdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.admin.appdetails.components.AdminBatchList
import at.techbee.gplaypromocodeprovider.admin.appdetails.components.AdminBatchUploadCard
import at.techbee.gplaypromocodeprovider.admin.appdetails.components.InvalidateBatchDialog
import at.techbee.gplaypromocodeprovider.admin.appdetails.components.InviteLinkCard
import at.techbee.gplaypromocodeprovider.admin.appdetails.components.RegenerateLinkDialog
import at.techbee.gplaypromocodeprovider.admin.appdetails.components.inviteLink
import at.techbee.gplaypromocodeprovider.admin.components.AppDialogState
import at.techbee.gplaypromocodeprovider.admin.components.AppFormDialog
import at.techbee.gplaypromocodeprovider.admin.components.LoadFailedMessage
import at.techbee.gplaypromocodeprovider.admin.components.LoadingBox
import at.techbee.gplaypromocodeprovider.admin.components.StatsRow
import at.techbee.gplaypromocodeprovider.api.AdminApp
import at.techbee.gplaypromocodeprovider.api.AdminBatch
import at.techbee.gplaypromocodeprovider.api.AppRequest
import at.techbee.gplaypromocodeprovider.api.CodeStats
import kotlin.time.Instant

/**
 * @param origin the web app's origin, for building the invite link
 * @param onBack goes to the list of apps
 */
@Composable
fun AppDetailScreen(viewModel: AppDetailViewModel, origin: String, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.resumeAfterLogin() }
    AppDetailPage(
        state = state,
        origin = origin,
        actions = AppDetailActions(
            onBack = onBack,
            onRetry = viewModel::retry,
            onEdit = viewModel::openEditDialog,
            onSave = viewModel::saveApp,
            onDismissEdit = viewModel::closeEditDialog,
            onRegenerate = viewModel::askToRegenerateInviteLink,
            onConfirmRegenerate = viewModel::regenerateInviteLink,
            onDismissRegenerate = viewModel::cancelRegenerateInviteLink,
            onInvalidateBatch = viewModel::askToInvalidateBatch,
            onConfirmInvalidate = viewModel::invalidateBatch,
            onDismissInvalidate = viewModel::cancelInvalidateBatch,
            onDescriptionChange = viewModel::setDescription,
            onExpiryDateChange = viewModel::setExpiryDate,
            isSelectableExpiryDay = viewModel::isSelectableExpiryDay,
            onCodesChange = viewModel::setCodes,
            onUpload = viewModel::upload,
            onDismissUploadResult = viewModel::dismissUploadResult,
        ),
    )
}

/** The page's callbacks, bundled so the page and its previews stay readable. */
private class AppDetailActions(
    val onBack: () -> Unit = {},
    val onRetry: () -> Unit = {},
    val onEdit: () -> Unit = {},
    val onSave: (AppRequest) -> Unit = {},
    val onDismissEdit: () -> Unit = {},
    val onRegenerate: () -> Unit = {},
    val onConfirmRegenerate: () -> Unit = {},
    val onDismissRegenerate: () -> Unit = {},
    val onInvalidateBatch: (AdminBatch) -> Unit = {},
    val onConfirmInvalidate: () -> Unit = {},
    val onDismissInvalidate: () -> Unit = {},
    val onDescriptionChange: (String) -> Unit = {},
    val onExpiryDateChange: (Long?) -> Unit = {},
    val isSelectableExpiryDay: (Long) -> Boolean = { true },
    val onCodesChange: (String) -> Unit = {},
    val onUpload: () -> Unit = {},
    val onDismissUploadResult: () -> Unit = {},
)

@Composable
private fun AppDetailPage(state: AppDetailUiState, origin: String, actions: AppDetailActions) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TextButton(onClick = actions.onBack) { Text("← All apps") }
        // Reloading in the background, e.g. the statistics after an upload; the old values stay visible.
        if (state.refreshing && state.content is AppDetailContent.Loaded) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
        }
        when (val content = state.content) {
            AppDetailContent.Loading -> LoadingBox()
            AppDetailContent.NotFound -> Text("This app doesn't exist.", style = MaterialTheme.typography.headlineSmall)
            is AppDetailContent.LoadFailed -> LoadFailedMessage("Couldn't load the app", content.error, actions.onRetry)
            is AppDetailContent.Loaded -> {
                AppHeader(content.app, actions.onEdit)
                InviteLinkCard(inviteLink(origin, content.app.inviteToken), actions.onRegenerate)
                AdminBatchUploadCard(
                    form = state.upload,
                    onDescriptionChange = actions.onDescriptionChange,
                    onExpiryDateChange = actions.onExpiryDateChange,
                    isSelectableExpiryDay = actions.isSelectableExpiryDay,
                    onCodesChange = actions.onCodesChange,
                    onUpload = actions.onUpload,
                    onDismissResult = actions.onDismissUploadResult,
                )
                AdminBatchList(content.batches, actions.onInvalidateBatch)

                state.editDialog?.let {
                    val app = content.app
                    AppFormDialog(
                        title = "Edit app",
                        initial = AppRequest(app.name, app.packageName, app.description),
                        state = it,
                        onSave = actions.onSave,
                        onDismiss = actions.onDismissEdit,
                    )
                }
                state.regenerateDialog?.let {
                    RegenerateLinkDialog(it, onConfirm = actions.onConfirmRegenerate, onDismiss = actions.onDismissRegenerate)
                }
                state.invalidateDialog?.let {
                    InvalidateBatchDialog(it, onConfirm = actions.onConfirmInvalidate, onDismiss = actions.onDismissInvalidate)
                }
            }
        }
    }
}

@Composable
private fun AppHeader(app: AdminApp, onEdit: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(app.name, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            OutlinedButton(onClick = onEdit) { Text("Edit") }
        }
        app.packageName?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        app.description?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        StatsRow(app.stats)
    }
}

// Previews: one per state of the page. Sample data only.

private val previewApp = AdminApp(
    id = 1,
    name = "Jtx Board",
    packageName = "at.techbee.jtx",
    description = "Journals, notes and tasks",
    inviteToken = "q8Vh1bT2kLx9_Za-3mN7pR4sW6yE0cJ5uI8oA2dF1gH",
    stats = CodeStats(40, 27, 3),
)

private val previewBatches = listOf(
    AdminBatch(2, 1, "Beta wave 2", Instant.parse("2027-01-31T22:59:59Z"), Instant.parse("2026-11-03T10:00:00Z"), CodeStats(40, 10, 0)),
    AdminBatch(1, 1, null, Instant.parse("2026-10-31T22:59:59Z"), Instant.parse("2026-09-01T08:30:00Z"), CodeStats(0, 17, 3)),
)

@Composable
private fun AppDetailPreview(state: AppDetailUiState) {
    PreviewTheme {
        Column(Modifier.padding(16.dp)) { AppDetailPage(state, "https://promo.example.com", AppDetailActions()) }
    }
}

@Preview(widthDp = 600, heightDp = 300)
@Composable
private fun AppDetailLoadingPreview() = AppDetailPreview(AppDetailUiState())

@Preview(widthDp = 600, heightDp = 200)
@Composable
private fun AppDetailNotFoundPreview() = AppDetailPreview(AppDetailUiState(content = AppDetailContent.NotFound))

@Preview(widthDp = 600, heightDp = 400)
@Composable
private fun AppDetailLoadFailedPreview() =
    AppDetailPreview(AppDetailUiState(content = AppDetailContent.LoadFailed(ActionError.Server)))

@Preview(widthDp = 600, heightDp = 1300)
@Composable
private fun AppDetailLoadedPreview() =
    AppDetailPreview(AppDetailUiState(content = AppDetailContent.Loaded(previewApp, previewBatches)))

@Preview(widthDp = 600, heightDp = 800)
@Composable
private fun AppDetailEditPreview() = AppDetailPreview(
    AppDetailUiState(content = AppDetailContent.Loaded(previewApp, previewBatches), editDialog = AppDialogState()),
)

@Preview(widthDp = 600, heightDp = 800)
@Composable
private fun AppDetailRegeneratePreview() = AppDetailPreview(
    AppDetailUiState(content = AppDetailContent.Loaded(previewApp, previewBatches), regenerateDialog = RegenerateDialogState()),
)

@Preview(widthDp = 600, heightDp = 800)
@Composable
private fun AppDetailInvalidatePreview() = AppDetailPreview(
    AppDetailUiState(
        content = AppDetailContent.Loaded(previewApp, previewBatches),
        invalidateDialog = InvalidateDialogState(previewBatches.first()),
    ),
)

@Preview(widthDp = 600, heightDp = 400)
@Composable
private fun AppDetailRefreshingPreview() = AppDetailPreview(
    AppDetailUiState(content = AppDetailContent.Loaded(previewApp, previewBatches), refreshing = true),
)
