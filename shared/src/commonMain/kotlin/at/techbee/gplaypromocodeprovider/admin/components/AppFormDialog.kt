package at.techbee.gplaypromocodeprovider.admin.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.PreviewTheme
import at.techbee.gplaypromocodeprovider.admin.ActionError
import at.techbee.gplaypromocodeprovider.admin.text
import at.techbee.gplaypromocodeprovider.api.AppRequest

/** State of the dialog for creating or editing an app. Its fields live in the dialog itself. */
data class AppDialogState(val saving: Boolean = false, val error: ActionError? = null)

/**
 * Dialog with the app's fields. Blank optional fields are sent as `null`.
 *
 * @param initial the current values when editing, `null` when creating an app
 */
@Composable
internal fun AppFormDialog(
    title: String,
    initial: AppRequest?,
    state: AppDialogState,
    onSave: (AppRequest) -> Unit,
    onDismiss: () -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var packageName by remember { mutableStateOf(initial?.packageName.orEmpty()) }
    var description by remember { mutableStateOf(initial?.description.orEmpty()) }

    AlertDialog(
        onDismissRequest = { if (!state.saving) onDismiss() },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = packageName,
                    onValueChange = { packageName = it },
                    label = { Text("Package name (optional)") },
                    placeholder = { Text("com.example.app") },
                    singleLine = true,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description (optional)") },
                    minLines = 2,
                    enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth(),
                )
                state.error?.let { ErrorText(it.text) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(AppRequest(name.trim(), packageName.trim().ifEmpty { null }, description.trim().ifEmpty { null }))
                },
                enabled = !state.saving && name.isNotBlank(),
            ) { Text(if (state.saving) "Saving…" else "Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !state.saving) { Text("Cancel") }
        },
    )
}

@Preview(widthDp = 500, heightDp = 500)
@Composable
private fun AppFormDialogPreview() {
    PreviewTheme {
        AppFormDialog(
            title = "Edit app",
            initial = AppRequest("Jtx Board", "at.techbee.jtx", null),
            state = AppDialogState(error = ActionError.Rejected("Name must be at most 100 characters")),
            onSave = {},
            onDismiss = {},
        )
    }
}
