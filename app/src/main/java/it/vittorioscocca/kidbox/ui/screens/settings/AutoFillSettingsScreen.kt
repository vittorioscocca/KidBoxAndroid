package it.vittorioscocca.kidbox.ui.screens.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.autofill.AutofillManager
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import androidx.credentials.CredentialManager
import it.vittorioscocca.kidbox.data.passwords.AutoFillSnapshotLoader
import it.vittorioscocca.kidbox.feature.passwords.autofill.autofillEntryPoint
import it.vittorioscocca.kidbox.feature.passwords.autofill.resolveAutofillFamilyId
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private data class AutofillDiag(
    val loggedIn: Boolean,
    val familyId: String?,
    val snapshotHasItems: Boolean,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutoFillSettingsScreen(
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val appCtx = context.applicationContext
    val prefs = remember(appCtx) { appCtx.autofillEntryPoint().autoFillUserPreferences() }
    var requireBio by remember { mutableStateOf(prefs.requireBiometricAlways) }

    val autofillManager = remember(appCtx) {
        appCtx.getSystemService(AutofillManager::class.java)
    }
    val autofillKidBoxPreferred = remember(autofillManager, appCtx) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            autofillManager?.autofillServiceComponentName?.packageName == appCtx.packageName
        } else {
            false
        }
    }
    val autofillAnyEnabled = autofillManager?.hasEnabledAutofillServices() == true

    var diag by remember { mutableStateOf<AutofillDiag?>(null) }
    LaunchedEffect(appCtx) {
        delay(400)
        val d = appCtx.autofillEntryPoint()
        val u = d.firebaseAuth().currentUser?.uid.orEmpty()
        val fid = resolveAutofillFamilyId(appCtx, d.familySessionPreferences(), u)
        val snapOk = if (u.isNotBlank() && !fid.isNullOrBlank()) {
            val s = AutoFillSnapshotLoader.load(appCtx, fid, u, d.autoFillSnapshotEncryptedStore())
            s != null && s.items.isNotEmpty()
        } else {
            false
        }
        diag = AutofillDiag(u.isNotBlank(), fid, snapOk)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_autofill_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_common_back))
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val currentDiag = diag
            val serviceLine = buildString {
                append(if (autofillKidBoxPreferred == true) stringResource(R.string.settings_autofill_preferred) else stringResource(R.string.settings_autofill_not_preferred))
                append(" · ")
                append(if (autofillAnyEnabled) stringResource(R.string.settings_autofill_on) else stringResource(R.string.settings_autofill_off))
            }
            Text(serviceLine, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    currentDiag == null -> stringResource(R.string.settings_autofill_checking)
                    !currentDiag.loggedIn -> stringResource(R.string.settings_autofill_sign_in)
                    currentDiag.familyId.isNullOrBlank() -> stringResource(R.string.settings_autofill_open_home)
                    !currentDiag.snapshotHasItems -> stringResource(R.string.settings_autofill_open_passwords)
                    else -> stringResource(R.string.settings_autofill_ready)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(
                onClick = {
                    val act = context as? Activity ?: return@Button
                    val intent = Intent(Settings.ACTION_REQUEST_SET_AUTOFILL_SERVICE).apply {
                        data = android.net.Uri.parse("package:${appCtx.packageName}")
                    }
                    act.startActivity(intent)
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.settings_autofill_set_service)) }
            if (Build.VERSION.SDK_INT >= 34) {
                Button(
                    onClick = {
                        val act = context as? Activity ?: return@Button
                        act.startSettingsIntentSafe(
                            Intent(Settings.ACTION_CREDENTIAL_PROVIDER),
                            context.getString(R.string.settings_autofill_manual_hint),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_autofill_system_passwords)) }
                Button(
                    onClick = {
                        val act = context as? Activity ?: return@Button
                        runCatching {
                            val cm = CredentialManager.create(appCtx)
                            val pi = cm.createSettingsPendingIntent()
                            act.startIntentSender(pi.intentSender, null, 0, 0, 0)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(stringResource(R.string.settings_autofill_credential_manager)) }
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.settings_autofill_biometrics), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(R.string.settings_autofill_biometrics_sub),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = requireBio,
                onCheckedChange = {
                    requireBio = it
                    prefs.requireBiometricAlways = it
                },
            )
        }
    }
}

/**
 * [Settings.ACTION_CREDENTIAL_PROVIDER] e simili non sono gestiti su tutti i dispositivi (es. alcune build MIUI).
 */
private fun Activity.startSettingsIntentSafe(intent: Intent, toastOnFallback: String) {
    try {
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        } else {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            Toast.makeText(this, toastOnFallback, Toast.LENGTH_LONG).show()
        }
    } catch (_: ActivityNotFoundException) {
        try {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            // ultimo tentativo: schermata app nelle impostazioni
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = android.net.Uri.parse("package:$packageName")
                },
            )
        }
        Toast.makeText(this, toastOnFallback, Toast.LENGTH_LONG).show()
    }
}
