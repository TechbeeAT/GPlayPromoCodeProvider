package at.techbee.gplaypromocodeprovider.admin.appdetails.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.admin.appdetails.RegenerateDialogState
import at.techbee.gplaypromocodeprovider.admin.components.CopyButton
import at.techbee.gplaypromocodeprovider.admin.components.ErrorText
import at.techbee.gplaypromocodeprovider.admin.text
import io.ktor.http.encodeURLPathPart

/** The link users open to get codes of the app with [inviteToken]. */
fun inviteLink(origin: String, inviteToken: String): String =
    "${origin.trimEnd('/')}/invite/${inviteToken.encodeURLPathPart()}"

@Composable
internal fun InviteLinkCard(link: String, onRegenerate: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Invite link", style = MaterialTheme.typography.titleMedium)
            Text(
                "Share this link with your testers. Anyone who has it can get codes of this app.",
                style = MaterialTheme.typography.bodyMedium,
            )
            SelectionContainer { Text(link, style = MaterialTheme.typography.bodyLarge) }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CopyButton(link, "Copy link")
                OutlinedButton(onClick = onRegenerate) { Text("New link…") }
            }
        }
    }
}

/** Asks before the invite link is replaced, since the old one stops working. */
@Composable
internal fun RegenerateLinkDialog(state: RegenerateDialogState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        title = { Text("Create a new invite link?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The current link stops working right away: people who have it can no longer get codes. " +
                        "You'll have to share the new link with everyone who should still get codes.",
                )
                state.error?.let { ErrorText(it.text) }
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm, enabled = !state.running) {
                Text(if (state.running) "Creating…" else "Create new link")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.running) { Text("Cancel") } },
    )
}

@Preview(widthDp = 500, heightDp = 250)
@Composable
private fun InviteLinkCardPreview() {
    PreviewTheme {
        InviteLinkCard(inviteLink("https://promo.example.com", "q8Vh1bT2kLx9_Za-3mN7pR4sW6yE0cJ5uI8oA2dF1gH"), onRegenerate = {})
    }
}

@Preview(widthDp = 500, heightDp = 400)
@Composable
private fun RegenerateLinkDialogPreview() {
    PreviewTheme { RegenerateLinkDialog(RegenerateDialogState(error = ActionError.Network), onConfirm = {}, onDismiss = {}) }
}
