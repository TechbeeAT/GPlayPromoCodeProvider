package at.techbee.gplaypromocodeprovider.invite.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.api.AvailableBatch
import at.techbee.gplaypromocodeprovider.invite.formatDateTime
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.invite.PreviewData

@Composable
internal fun BatchList(
    batches: List<AvailableBatch>,
    requestingBatchId: Long?,
    enabled: Boolean,
    hasCode: Boolean,
    onRequestCode: (Long) -> Unit,
) {
    if (batches.isEmpty()) {
        InviteMessage(
            title = "No codes available",
            text = "There are no promo codes left right now. Please check back later.",
        )
        return
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {

        Text(
            if (hasCode) "Need another code? Pick a batch:" else "Pick a batch to get a promo code:",
            style = MaterialTheme.typography.titleMedium,
        )
        batches.forEach { batch ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(batch.description ?: "Promo codes", style = MaterialTheme.typography.titleMedium)
                    val expiry = remember(batch.expiresAt) { formatDateTime(batch.expiresAt) }
                    Text("Valid until $expiry", style = MaterialTheme.typography.bodyMedium)
                    Button(
                        onClick = { onRequestCode(batch.id) },
                        enabled = enabled,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Get code")
                            AnimatedVisibility(requestingBatchId == batch.id) {
                                // Content color: the default (primary) would be invisible on the filled button.
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = LocalContentColor.current,
                                    strokeWidth = 2.dp,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
@Preview(widthDp = 400, heightDp = 600)
private fun BatchList_Preview() {
    PreviewTheme {
        BatchList(
            batches = listOf(AvailableBatch(1, "First Batch", PreviewData.expiry)),
            requestingBatchId = 1,
            enabled = true,
            hasCode = false,
            onRequestCode = {}
        )
    }
}

@Composable
@Preview(widthDp = 400, heightDp = 600)
private fun BatchList_Has_Code_Preview() {
    PreviewTheme {
        BatchList(
            batches = listOf(AvailableBatch(1, "Second Batch", PreviewData.expiry)),
            requestingBatchId = 1,
            enabled = true,
            hasCode = true,
            onRequestCode = {}
        )
    }
}

@Composable
@Preview(widthDp = 400, heightDp = 600)
private fun BatchList_empty_Preview() {
    PreviewTheme {
        BatchList(
            batches = emptyList(),
            requestingBatchId = null,
            enabled = true,
            hasCode = true,
            onRequestCode = {}
        )
    }
}