package it.vittorioscocca.kidbox.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun UpdateAvailableDialog(
    visible: Boolean,
    onUpdate: () -> Unit,
    onDismiss: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("È disponibile un aggiornamento") },
        text = {
            Text(
                "Per usare KidBox al meglio ti consigliamo di aggiornare all'ultima " +
                    "versione: contiene miglioramenti e correzioni.",
            )
        },
        confirmButton = {
            TextButton(onClick = onUpdate) {
                Text("Aggiorna")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Non ora")
            }
        },
    )
}
