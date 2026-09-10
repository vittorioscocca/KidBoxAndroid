package it.vittorioscocca.kidbox.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.CrashAnalyzer

@Composable
fun CrashReportConsentDialog(
    visible: Boolean,
    issueCount: Int,
    onDismiss: () -> Unit = { CrashAnalyzer.dismissConsentPrompt() },
) {
    val context = LocalContext.current
    if (!visible || issueCount <= 0) return

    // Il plurale lo decide la lingua, non un `if`: in inglese e spagnolo le
    // regole non coincidono con quelle italiane.
    val label = pluralStringResource(R.plurals.crash_consent_issues, issueCount, issueCount)

    AlertDialog(
        onDismissRequest = {
            CrashAnalyzer.onConsentDecline(context)
            onDismiss()
        },
        title = { Text(stringResource(R.string.crash_consent_title)) },
        text = {
            Text(stringResource(R.string.crash_consent_body, label))
        },
        confirmButton = {
            TextButton(
                onClick = {
                    CrashAnalyzer.onConsentSend(context)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.crash_consent_send))
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    CrashAnalyzer.onConsentDecline(context)
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.crash_consent_decline))
            }
        },
    )
}
