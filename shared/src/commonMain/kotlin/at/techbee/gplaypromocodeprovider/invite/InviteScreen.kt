package at.techbee.gplaypromocodeprovider.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.invite.components.BatchList
import at.techbee.gplaypromocodeprovider.invite.components.CodeCard
import at.techbee.gplaypromocodeprovider.invite.components.InviteMessage
import at.techbee.gplaypromocodeprovider.invite.components.NETWORK_ERROR_TEXT
import at.techbee.gplaypromocodeprovider.invite.components.NoticeCard
import at.techbee.gplaypromocodeprovider.invite.components.SERVER_ERROR_TEXT

/** The page behind an invite link: the app's batches, a "Get code" button each, and the received codes. */
@Composable
fun InviteScreen(viewModel: InviteViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    InvitePage(
        state = state,
        onRequestCode = viewModel::requestCode,
        onRetry = viewModel::retry,
        onDismissNotice = viewModel::dismissNotice,
    )
}

@Composable
private fun InvitePage(
    state: InviteUiState,
    onRequestCode: (Long) -> Unit,
    onRetry: () -> Unit,
    onDismissNotice: () -> Unit,
) {
    // One centered column, at most 600 dp wide: full width on phones, readable on desktops.
    Box(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            when (val content = state.content) {
                InviteContent.Loading -> Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) {
                    CircularProgressIndicator()
                }
                InviteContent.InvalidLink -> InviteMessage(
                    title = "This link doesn't work",
                    text = "The invite link may be mistyped or no longer active. " +
                        "Please ask the person who sent it to you for a new one.",
                )
                is InviteContent.LoadFailed -> InviteMessage(
                    title = "Couldn't load the codes",
                    text = when (content.reason) {
                        FailureReason.NETWORK -> NETWORK_ERROR_TEXT
                        FailureReason.SERVER -> SERVER_ERROR_TEXT
                    },
                    action = { Button(onClick = onRetry) { Text("Try again") } },
                )
                is InviteContent.Loaded -> {
                    Text(content.appName, style = MaterialTheme.typography.headlineMedium)
                    state.notice?.let { NoticeCard(it, onDismissNotice) }
                    state.receivedCodes.firstOrNull()?.let { CodeCard(it) }
                    BatchList(
                        batches = content.batches,
                        requestingBatchId = state.requestingBatchId,
                        enabled = state.requestingBatchId == null && !state.refreshing,
                        hasCode = state.receivedCodes.isNotEmpty(),
                        onRequestCode = onRequestCode,
                    )
                }
            }

            // Shown in every state: a code that was handed out is consumed and must not get lost,
            // even if the link stops working or the list fails to refresh.
            val earlierCodes = if (state.content is InviteContent.Loaded) state.receivedCodes.drop(1) else state.receivedCodes
            if (earlierCodes.isNotEmpty()) {
                Text(
                    if (state.content is InviteContent.Loaded) "Your earlier codes" else "Your codes",
                    style = MaterialTheme.typography.titleMedium,
                )
                earlierCodes.forEach { CodeCard(it, prominent = false) }
            }
        }
    }
}


// Previews: one per state of the page, at phone width. Sample data only; nothing calls the server.

@Composable
private fun InvitePagePreview(state: InviteUiState) {
    PreviewTheme {
        InvitePage(state = state, onRequestCode = {}, onRetry = {}, onDismissNotice = {})
    }
}

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun LoadingPreview() = InvitePagePreview(InviteUiState(content = InviteContent.Loading))

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun InvalidLinkPreview() = InvitePagePreview(InviteUiState(content = InviteContent.InvalidLink))

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun LoadFailedNetworkPreview() =
    InvitePagePreview(InviteUiState(content = InviteContent.LoadFailed(FailureReason.NETWORK)))

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun LoadFailedServerPreview() =
    InvitePagePreview(InviteUiState(content = InviteContent.LoadFailed(FailureReason.SERVER)))

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun NoBatchesPreview() =
    InvitePagePreview(InviteUiState(content = InviteContent.Loaded(PreviewData.APP_NAME, emptyList())))

@Preview(widthDp = 400, heightDp = 500)
@Composable
private fun BatchesPreview() =
    InvitePagePreview(InviteUiState(content = InviteContent.Loaded(PreviewData.APP_NAME, PreviewData.batches)))

@Preview(widthDp = 400, heightDp = 500)
@Composable
private fun RequestingPreview() = InvitePagePreview(
    InviteUiState(content = InviteContent.Loaded(PreviewData.APP_NAME, PreviewData.batches), requestingBatchId = 1),
)

@Preview(widthDp = 400, heightDp = 1100)
@Composable
private fun CodesReceivedPreview() = InvitePagePreview(
    InviteUiState(content = InviteContent.Loaded(PreviewData.APP_NAME, PreviewData.batches), receivedCodes = PreviewData.codes),
)

@Preview(widthDp = 400, heightDp = 800)
@Composable
private fun BatchRanOutPreview() = InvitePagePreview(
    InviteUiState(
        content = InviteContent.Loaded(PreviewData.APP_NAME, PreviewData.batches.take(1)),
        receivedCodes = PreviewData.codes.take(1),
        notice = InviteNotice.BATCH_RAN_OUT,
    ),
)

@Preview(widthDp = 400, heightDp = 600)
@Composable
private fun InvalidLinkWithCodesPreview() = InvitePagePreview(
    InviteUiState(content = InviteContent.InvalidLink, receivedCodes = PreviewData.codes.take(2)),
)
