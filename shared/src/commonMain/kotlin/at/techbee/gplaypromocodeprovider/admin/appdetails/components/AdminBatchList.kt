package at.techbee.gplaypromocodeprovider.admin.appdetails.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.admin.components.StatsRow
import at.techbee.gplaypromocodeprovider.api.AdminBatch
import at.techbee.gplaypromocodeprovider.api.CodeStats
import at.techbee.gplaypromocodeprovider.platform.formatDateTime
import kotlin.time.Instant

/** The app's batches with their statistics, newest first. */
@Composable
internal fun AdminBatchList(batches: List<AdminBatch>) {
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
                    StatsRow(batch.stats)
                }
            }
        }
    }
}

@Preview(widthDp = 600, heightDp = 400)
@Composable
private fun AdminBatchListPreview() {
    PreviewTheme {
        AdminBatchList(
            listOf(
                AdminBatch(2, 1, "Beta wave 2", Instant.parse("2027-01-31T22:59:59Z"), Instant.parse("2026-11-03T10:00:00Z"), CodeStats(40, 10, 0)),
                AdminBatch(1, 1, null, Instant.parse("2026-10-31T22:59:59Z"), Instant.parse("2026-09-01T08:30:00Z"), CodeStats(0, 17, 3)),
            ),
        )
    }
}

@Preview(widthDp = 600, heightDp = 150)
@Composable
private fun AdminBatchListEmptyPreview() {
    PreviewTheme { AdminBatchList(emptyList()) }
}
