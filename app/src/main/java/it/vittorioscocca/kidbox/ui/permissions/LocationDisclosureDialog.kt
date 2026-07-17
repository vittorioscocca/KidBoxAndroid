package it.vittorioscocca.kidbox.ui.permissions

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
        title = { Text("KidBox usa la tua posizione") },
        text = {
            Text(
                purpose +
                    "\n\nLa posizione viene raccolta solo quando lo richiedi tu e viene " +
                    "usata esclusivamente per questa funzione: non la vendiamo e non la " +
                    "usiamo per pubblicità." +
                    "\n\nNella schermata successiva Android ti chiederà di consentire " +
                    "l'accesso alla posizione.",
            )
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text("Continua")
            }
        },
        dismissButton = {
            TextButton(onClick = onDecline) {
                Text("No, grazie")
            }
        },
    )
}
