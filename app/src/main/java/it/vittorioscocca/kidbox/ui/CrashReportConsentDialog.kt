package it.vittorioscocca.kidbox.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import it.vittorioscocca.kidbox.util.CrashAnalyzer

@Composable
fun CrashReportConsentDialog(
    visible: Boolean,
    issueCount: Int,
    onDismiss: () -> Unit = { CrashAnalyzer.dismissConsentPrompt() },
) {
    val context = LocalContext.current
    if (!visible || issueCount <= 0) return

    val label = if (issueCount == 1) "1 anomalia tecnica" else "$issueCount anomalie tecniche"

    AlertDialog(
        onDismissRequest = {
            CrashAnalyzer.onConsentDecline(context)
            onDismiss()
        },
        title = { Text("Aiutaci a migliorare KidBox") },
        text = {
            Text(
                "Abbiamo rilevato $label. " +
                    "Puoi inviarci un report anonimo? " +
                    "Non verranno inviati dati personali o familiari.",
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    CrashAnalyzer.onConsentSend(context)
                    onDismiss()
                },
            ) {
                Text("Invia report")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    CrashAnalyzer.onConsentDecline(context)
                    onDismiss()
                },
            ) {
                Text("No grazie")
            }
        },
    )
}
