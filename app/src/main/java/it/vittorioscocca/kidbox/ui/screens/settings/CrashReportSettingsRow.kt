package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.util.CrashAnalyzer
import it.vittorioscocca.kidbox.util.CrashReportPreferences
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

/**
 * Switch «Invia report errori automatici» (schermata Impostazioni → Privacy).
 */
@Composable
fun CrashReportSettingsRow(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var enabled by remember {
        mutableStateOf(CrashReportPreferences.isReportingEnabled(context))
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.settings_crash_send_title),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
                )
                Text(
                    text = stringResource(R.string.settings_crash_send_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { checked ->
                    enabled = checked
                    CrashReportPreferences.setReportingEnabled(context, checked)
                    if (checked) {
                        CrashReportPreferences.setHasBeenAsked(context, true)
                        CrashReportPreferences.clearAnalysisThrottle(context)
                        scope.launch {
                            CrashAnalyzer.analyzeIfNeeded(
                                context.applicationContext,
                                force = true,
                            )
                        }
                    }
                },
            )
        }
    }
}
