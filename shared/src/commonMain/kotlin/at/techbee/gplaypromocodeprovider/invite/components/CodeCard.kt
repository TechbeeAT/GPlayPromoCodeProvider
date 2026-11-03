package at.techbee.gplaypromocodeprovider.invite.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.invite.ReceivedCode
import at.techbee.gplaypromocodeprovider.invite.formatDateTime
import at.techbee.gplaypromocodeprovider.invite.plainTextClipEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.invite.PreviewData
import kotlin.time.Duration.Companion.milliseconds


@Composable
internal fun CodeCard(code: ReceivedCode, prominent: Boolean = true) {
    val clipboard = LocalClipboard.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    var copyResult by remember(code.code) { mutableStateOf<Boolean?>(null) }
    val expiry = remember(code.expiresAt) { formatDateTime(code.expiresAt) }
    LaunchedEffect(copyResult) {
        if (copyResult == true) {
            delay(2_000.milliseconds)
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
                listOfNotNull(code.batchDescription, "valid until $expiry")
                    .joinToString(" · ")
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.bodySmall,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { uriHandler.openUri(code.redeemUrl) }) { Text("Redeem on Google Play") }
                OutlinedButton(onClick = {
                    scope.launch {
                        copyResult = try {
                            clipboard.setClipEntry(plainTextClipEntry(code.code))
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

@Composable
@Preview(widthDp = 400, heightDp = 600)
private fun CodeCard_prominent_Preview() {
    PreviewTheme {
        CodeCard(code = PreviewData.codes.first(), prominent = true)
    }
}

@Composable
@Preview(widthDp = 400, heightDp = 600)
private fun CodeCard_earlier_Preview() {
    PreviewTheme {
        CodeCard(code = PreviewData.codes.first(), prominent = false)
    }
}
