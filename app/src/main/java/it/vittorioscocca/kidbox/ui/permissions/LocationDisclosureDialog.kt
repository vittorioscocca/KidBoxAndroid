package it.vittorioscocca.kidbox.ui.permissions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import androidx.compose.runtime.Composable

/**
 * Prominent disclosure per l'accesso alla posizione (Google Play User Data policy).
 *
 * Deve precedere **immediatamente** la richiesta del permesso di sistema: il popup di
 * Android va lanciato solo da [onAccept]. Mostrare il permesso senza questa schermata è
 * ciò che ha fatto rifiutare la pubblicazione a luglio 2026.
 *
 * [purpose] deve descrivere l'uso reale della posizione in quella specifica schermata:
 * la policy richiede che l'utente sappia a cosa serve *lì*, non una frase generica.
 */
@Composable
fun LocationDisclosureDialog(
    visible: Boolean,
    purpose: String,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    if (!visible) return

    AlertDialog(
        onDismissRequest = onDecline,
        title = { Text(stringResource(R.string.permissions_location_dialog_title)) },
        text = {
            Text(
                purpose + stringResource(R.string.permissions_location_dialog_body_suffix),
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.permissions_location_dialog_accept))
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text(stringResource(R.string.permissions_location_dialog_decline))
            }
        },
    )
}
