package at.techbee.gplaypromocodeprovider.admin.app.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.admin.app.UploadFormState
import at.techbee.gplaypromocodeprovider.admin.components.ErrorText
import at.techbee.gplaypromocodeprovider.admin.text
import at.techbee.gplaypromocodeprovider.api.AdminBatch
import at.techbee.gplaypromocodeprovider.api.BatchUploadResponse
import at.techbee.gplaypromocodeprovider.api.CodeStats
import at.techbee.gplaypromocodeprovider.platform.endOfLocalDay
import at.techbee.gplaypromocodeprovider.platform.formatDateTime
import kotlin.time.Clock
import kotlin.time.Instant

/** Duplicates listed in the upload result; more are only counted. */
private const val MAX_LISTED_DUPLICATES = 100

/** The form for uploading a batch of codes, and the result of the last upload. */
@Composable
internal fun BatchUploadCard(
    form: UploadFormState,
    onDescriptionChange: (String) -> Unit,
    onExpiryDateChange: (Long?) -> Unit,
    onCodesChange: (String) -> Unit,
    onUpload: () -> Unit,
    onDismissResult: () -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Upload codes", style = MaterialTheme.typography.titleMedium)
            form.result?.let { UploadResult(it, onDismissResult) }
            OutlinedTextField(
                value = form.description,
                onValueChange = onDescriptionChange,
                label = { Text("Description (optional)") },
                placeholder = { Text("e.g. Beta wave 2") },
                singleLine = true,
                enabled = !form.uploading,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = { pickingDate = true }, enabled = !form.uploading) {
                    Text(if (form.expiryDateMillis == null) "Pick expiry date" else "Change expiry date")
                }
                Text(
                    form.expiryDateMillis?.let { "Valid until ${formatDateTime(endOfLocalDay(it))}" }
                        ?: "Use the expiry date shown in Google Play Console.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            OutlinedTextField(
                value = form.codes,
                onValueChange = onCodesChange,
                label = { Text("Codes, one per line") },
                minLines = 6,
                maxLines = 15,
                enabled = !form.uploading,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                supportingText = {
                    Text(if (form.codeCount == 1) "1 code" else "${form.codeCount} codes")
                },
                modifier = Modifier.fillMaxWidth(),
            )
            form.error?.let { ErrorText(it.text) }
            Button(onClick = onUpload, enabled = form.canUpload, modifier = Modifier.align(Alignment.End)) {
                Text(if (form.uploading) "Uploading…" else "Upload")
            }
        }
    }

    if (pickingDate) {
        ExpiryDatePickerDialog(
            initial = form.expiryDateMillis,
            onPick = {
                onExpiryDateChange(it)
                pickingDate = false
            },
            onDismiss = { pickingDate = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpiryDatePickerDialog(initial: Long?, onPick: (Long?) -> Unit, onDismiss: () -> Unit) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        // Days whose end has passed can't be picked; the server would reject them.
        selectableDates = remember {
            val now = Clock.System.now()
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = endOfLocalDay(utcTimeMillis) > now
            }
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { onPick(pickerState.selectedDateMillis) }, enabled = pickerState.selectedDateMillis != null) {
                Text("OK")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(pickerState, title = { Text("Codes valid until", Modifier.padding(start = 24.dp, top = 16.dp)) })
    }
}

@Composable
private fun UploadResult(result: BatchUploadResponse, onDismiss: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                if (result.addedCount == 1) "Added 1 code." else "Added ${result.addedCount} codes.",
                style = MaterialTheme.typography.titleSmall,
            )
            val duplicates = result.duplicates
            if (duplicates.isEmpty()) {
                Text("No duplicates.", style = MaterialTheme.typography.bodyMedium)
            } else {
                Text(
                    "Skipped ${duplicates.size} duplicate${if (duplicates.size == 1) "" else "s"} " +
                        "(already uploaded for this app, or listed twice):",
                    style = MaterialTheme.typography.bodyMedium,
                )
                SelectionContainer {
                    Text(
                        buildString {
                            append(duplicates.take(MAX_LISTED_DUPLICATES).joinToString("\n"))
                            if (duplicates.size > MAX_LISTED_DUPLICATES) {
                                append("\n… and ${duplicates.size - MAX_LISTED_DUPLICATES} more")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState()),
                    )
                }
            }
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) { Text("OK") }
        }
    }
}

// Previews. The expiry day is a fixed sample; "Valid until" shows it in the JVM's time zone.

private val previewDay = Instant.parse("2027-01-31T00:00:00Z").toEpochMilliseconds()

@Composable
private fun UploadPreview(form: UploadFormState) {
    MaterialTheme {
        Surface {
            BatchUploadCard(form, onDescriptionChange = {}, onExpiryDateChange = {}, onCodesChange = {}, onUpload = {}, onDismissResult = {})
        }
    }
}

@Preview(widthDp = 600, heightDp = 500)
@Composable
private fun UploadEmptyPreview() = UploadPreview(UploadFormState())

@Preview(widthDp = 600, heightDp = 500)
@Composable
private fun UploadFilledPreview() = UploadPreview(
    UploadFormState(description = "Beta wave 2", expiryDateMillis = previewDay, codes = "AAAA1111\nBBBB2222\n\nCCCC3333\n"),
)

@Preview(widthDp = 600, heightDp = 550)
@Composable
private fun UploadErrorPreview() = UploadPreview(
    UploadFormState(
        expiryDateMillis = previewDay,
        codes = "AAAA1111",
        error = ActionError.Rejected("All 1 codes already exist for this app"),
    ),
)

@Preview(widthDp = 600, heightDp = 750)
@Composable
private fun UploadResultPreview() = UploadPreview(
    UploadFormState(
        result = BatchUploadResponse(
            batch = AdminBatch(3, 1, "Beta wave 2", Instant.parse("2027-01-31T22:59:59Z"), Instant.parse("2026-11-03T10:00:00Z"), CodeStats(12, 0, 0)),
            addedCount = 12,
            duplicates = listOf("AAAA1111", "BBBB2222"),
        ),
    ),
)
