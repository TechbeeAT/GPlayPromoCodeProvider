package at.techbee.gplaypromocodeprovider.admin.appdetails.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.admin.appdetails.InvalidateDialogState
import at.techbee.gplaypromocodeprovider.admin.components.ErrorText
import at.techbee.gplaypromocodeprovider.admin.components.StatsRow
import at.techbee.gplaypromocodeprovider.admin.text
import at.techbee.gplaypromocodeprovider.api.AdminBatch
import at.techbee.gplaypromocodeprovider.api.CodeStats
import at.techbee.gplaypromocodeprovider.platform.formatDateTime
import kotlin.time.Instant

/**
 * The app's batches with their statistics, newest first.
 *
 * @param onInvalidate asks to invalidate a batch; offered for batches that still have available codes
 */
@Composable
internal fun AdminBatchList(batches: List<AdminBatch>, onInvalidate: (AdminBatch) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Batches", style = MaterialTheme.typography.titleMedium)
        if (batches.isEmpty()) {
            Text("No codes uploaded yet.", style = MaterialTheme.typography.bodyMedium)
        }
        batches.forEach { batch ->
            OutlinedCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(batch.description ?: "No description", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Valid until ${formatDateTime(batch.expiresAt)} · uploaded ${formatDateTime(batch.createdAt)}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    batch.invalidatedAt?.let {
                        Text(
                            "Invalidated ${formatDateTime(it)}: no more codes are handed out",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatsRow(batch.stats, Modifier.weight(1f))
                        if (batch.invalidatedAt == null && batch.stats.available > 0) {
                            OutlinedButton(
                                onClick = { onInvalidate(batch) },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            ) { Text("Invalidate…") }
                        }
                    }
                }
            }
        }
    }
}

/** Asks before a batch is invalidated, since that can't be undone. */
@Composable
internal fun InvalidateBatchDialog(state: InvalidateDialogState, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val batch = state.batch
    val name = batch.description?.let { "“$it”" } ?: "this batch"
    AlertDialog(
        onDismissRequest = { if (!state.running) onDismiss() },
        title = { Text("Invalidate the batch?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "The ${batch.stats.available} codes still available in $name will never be handed out, " +
                        "and users no longer see the batch. Codes already handed out are not affected. " +
                        "This can't be undone.",
                )
                state.error?.let { ErrorText(it.text) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                enabled = !state.running,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) { Text(if (state.running) "Invalidating…" else "Invalidate codes") }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !state.running) { Text("Cancel") } },
    )
}

private val previewBatches = listOf(
    AdminBatch(3, 1, "Beta wave 3", Instant.parse("2027-01-31T22:59:59Z"), Instant.parse("2026-11-03T10:00:00Z"), CodeStats(40, 10, 0)),
    AdminBatch(
        2, 1, "Beta wave 2", Instant.parse("2027-01-31T22:59:59Z"), Instant.parse("2026-10-01T10:00:00Z"),
        CodeStats(0, 12, 0, invalidated = 8), invalidatedAt = Instant.parse("2026-10-20T14:15:00Z"),
    ),
    AdminBatch(1, 1, null, Instant.parse("2026-10-31T22:59:59Z"), Instant.parse("2026-09-01T08:30:00Z"), CodeStats(0, 17, 3)),
)

@Preview(widthDp = 600, heightDp = 550)
@Composable
private fun AdminBatchListPreview() {
    PreviewTheme { AdminBatchList(previewBatches, onInvalidate = {}) }
}

@Preview(widthDp = 600, heightDp = 150)
@Composable
private fun AdminBatchListEmptyPreview() {
    PreviewTheme { AdminBatchList(emptyList(), onInvalidate = {}) }
}

@Preview(widthDp = 500, heightDp = 400)
@Composable
private fun InvalidateBatchDialogPreview() {
    PreviewTheme {
        InvalidateBatchDialog(InvalidateDialogState(previewBatches.first(), error = ActionError.Network), onConfirm = {}, onDismiss = {})
    }
}
