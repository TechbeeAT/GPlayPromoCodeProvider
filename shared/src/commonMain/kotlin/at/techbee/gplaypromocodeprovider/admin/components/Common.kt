package at.techbee.gplaypromocodeprovider.admin.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.admin.text
import at.techbee.gplaypromocodeprovider.api.CodeStats
import at.techbee.gplaypromocodeprovider.ui.rememberCopyToClipboard

@Composable
internal fun ErrorText(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error, modifier = modifier)
}

@Composable
internal fun LoadingBox() {
    Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() }
}

/** A page that couldn't load, with a retry button. */
@Composable
internal fun LoadFailedMessage(title: String, error: ActionError, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(error.text, style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = onRetry) { Text("Try again") }
    }
}

/** Available, consumed and expired counts side by side. */
@Composable
internal fun StatsRow(stats: CodeStats, modifier: Modifier = Modifier) {
    FlowRow(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        Stat("Available", stats.available, MaterialTheme.colorScheme.primary)
        Stat("Consumed", stats.consumed, MaterialTheme.colorScheme.onSurface)
        Stat("Expired", stats.expired, MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Stat(label: String, value: Int, color: androidx.compose.ui.graphics.Color) {
    Column {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Copies [text] to the clipboard. Says "Copied" for a moment, or that copying failed. */
@Composable
internal fun CopyButton(text: String, label: String) {
    val copy = rememberCopyToClipboard(text)
    Column {
        OutlinedButton(onClick = copy::copy) { Text(if (copy.result == true) "Copied" else label) }
        if (copy.result == false) ErrorText("Couldn't copy. Select the text and copy it by hand.")
    }
}

@Preview(widthDp = 400, heightDp = 100)
@Composable
private fun StatsRowPreview() {
    PreviewTheme { StatsRow(CodeStats(available = 42, consumed = 7, expired = 3), Modifier.padding(16.dp)) }
}

@Preview(widthDp = 400, heightDp = 300)
@Composable
private fun LoadFailedMessagePreview() {
    PreviewTheme { LoadFailedMessage("Couldn't load the apps", ActionError.Network, onRetry = {}) }
}
