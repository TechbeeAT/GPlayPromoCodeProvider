package at.techbee.gplaypromocodeprovider.invite

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import at.techbee.gplaypromocodeprovider.api.AvailableBatch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
                InviteContent.InvalidLink -> Message(
                    title = "This link doesn't work",
                    text = "The invite link may be mistyped or no longer active. " +
                        "Please ask the person who sent it to you for a new one.",
                )
                is InviteContent.LoadFailed -> Message(
                    title = "Couldn't load the codes",
                    text = when (content.reason) {
                        FailureReason.NETWORK -> "The server can't be reached. Check your internet connection and try again."
                        FailureReason.SERVER -> "The server isn't responding properly right now. Please try again in a moment."
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

@Composable
private fun Message(title: String, text: String, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

@Composable
private fun NoticeCard(notice: InviteNotice, onDismiss: () -> Unit) {
    val text = when (notice) {
        InviteNotice.BATCH_RAN_OUT -> "Sorry, that batch has just run out. The list below is up to date."
        InviteNotice.NETWORK_ERROR -> "The server can't be reached. Check your internet connection and try again."
        InviteNotice.SERVER_ERROR -> "The server isn't responding properly right now. Please try again in a moment."
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 8.dp, bottom = 4.dp)) {
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("OK") }
        }
    }
}

@Composable
private fun BatchList(
    batches: List<AvailableBatch>,
    requestingBatchId: Long?,
    enabled: Boolean,
    hasCode: Boolean,
    onRequestCode: (Long) -> Unit,
) {
    if (batches.isEmpty()) {
        Message(
            title = "No codes available",
            text = "There are no promo codes left right now. Please check back later.",
        )
        return
    }
    Text(
        if (hasCode) "Need another code? Pick a batch:" else "Pick a batch to get a promo code:",
        style = MaterialTheme.typography.titleMedium,
    )
    batches.forEach { batch ->
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(batch.description ?: "Promo codes", style = MaterialTheme.typography.titleMedium)
                Text("Valid until ${formatDateTime(batch.expiresAt)}", style = MaterialTheme.typography.bodyMedium)
                Button(
                    onClick = { onRequestCode(batch.id) },
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (requestingBatchId == batch.id) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Text("Get code")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
private fun CodeCard(code: ReceivedCode, prominent: Boolean = true) {
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var copyResult by remember(code.code) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(copyResult) {
        if (copyResult == true) {
            delay(2_000)
            copyResult = null
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = if (prominent) {
            CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (prominent) Text("Your promo code", style = MaterialTheme.typography.titleMedium)
            SelectionContainer {
                Text(
                    code.code,
                    style = if (prominent) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                listOfNotNull(code.batchDescription, "valid until ${formatDateTime(code.expiresAt)}")
                    .joinToString(" · ")
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { uriHandler.openUri(code.redeemUrl) }) { Text("Redeem on Google Play") }
                OutlinedButton(onClick = {
                    scope.launch {
                        copyResult = try {
                            clipboard.setClipEntry(ClipEntry.withPlainText(code.code))
                            true
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Throwable) {
                            false // E.g. the browser denied clipboard access.
                        }
                    }
                }) {
                    Text(if (copyResult == true) "Copied" else "Copy code")
                }
            }
            if (copyResult == false) {
                Text(
                    "Couldn't copy the code. Select it and copy it by hand.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            if (prominent) {
                Text(
                    "This code is yours now and won't be given to anyone else. " +
                        "It is only shown on this page, so copy or redeem it before you close it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
