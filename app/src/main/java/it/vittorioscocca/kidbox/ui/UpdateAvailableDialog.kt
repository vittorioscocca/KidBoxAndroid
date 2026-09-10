package it.vittorioscocca.kidbox.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@Composable
fun UpdateAvailableDialog(
    visible: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.update_available_title)) },
        text = {
            Text(stringResource(R.string.update_available_body))
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text(stringResource(R.string.update_available_update))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.update_available_later))
            }
        },
    )
}
