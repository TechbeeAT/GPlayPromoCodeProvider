package at.techbee.gplaypromocodeprovider.invite.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import at.techbee.gplaypromocodeprovider.PreviewTheme
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.invite.InviteNotice

@Composable
internal fun NoticeCard(notice: InviteNotice, onDismiss: () -> Unit) {
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
@Preview(widthDp = 400, heightDp = 600)
private fun NoticeCard_Preview_BATCH_RAN_OUT() {
    PreviewTheme {
        NoticeCard(InviteNotice.BATCH_RAN_OUT) {}
    }
}

@Composable
@Preview(widthDp = 400, heightDp = 600)
private fun NoticeCard_Preview_SERVER_ERROR() {
    PreviewTheme {
        NoticeCard(InviteNotice.SERVER_ERROR) {}
    }
}

@Composable
@Preview(widthDp = 400, heightDp = 600)
private fun NoticeCard_Preview_NETWORK_ERROR() {
    PreviewTheme {
        NoticeCard(InviteNotice.NETWORK_ERROR) {}
    }
}