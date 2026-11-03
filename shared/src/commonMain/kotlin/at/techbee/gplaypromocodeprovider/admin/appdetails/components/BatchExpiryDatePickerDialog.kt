package at.techbee.gplaypromocodeprovider.admin.appdetails.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import at.techbee.gplaypromocodeprovider.platform.endOfLocalDay
import at.techbee.gplaypromocodeprovider.platform.formatDate
import kotlin.time.Instant


@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchExpiryDatePickerDialog(
    initial: Long?,
    isSelectable: (Long) -> Boolean,
    onPick: (Long?) -> Unit,
    onDismiss: () -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initial,
        // Days whose end has passed can't be picked; the server would reject them.
        selectableDates = remember(isSelectable) {
            object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = isSelectable(utcTimeMillis)
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
        DatePicker(
            pickerState,
            title = { Text("Codes valid until", Modifier.padding(start = 24.dp, top = 16.dp)) },
            // Material's own headline formats the picked day (midnight UTC) in the browser's time zone, which
            // shows the day before west of UTC. The end of the picked local day is that day everywhere.
            headline = {
                Text(
                    pickerState.selectedDateMillis?.let { formatDate(endOfLocalDay(it)) } ?: "No date picked",
                    Modifier.padding(start = 24.dp, end = 12.dp, bottom = 12.dp),
                )
            },
        )
    }
}


@Preview(widthDp = 200, heightDp = 200)
@Composable
private fun BatchExpiryDatePickerDialog_Preview() {
    Surface {
        BatchExpiryDatePickerDialog(
            initial = Instant.parse("2027-01-31T00:00:00Z").toEpochMilliseconds(),
            isSelectable = { true },
            onPick = {},
            onDismiss = {}
        )
    }
}
