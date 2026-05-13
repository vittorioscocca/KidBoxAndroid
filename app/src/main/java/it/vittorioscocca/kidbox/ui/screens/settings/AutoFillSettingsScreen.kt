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
                title = { Text("AutoFill") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
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
                append(if (autofillKidBoxPreferred == true) "KidBox è il servizio preferito" else "KidBox non è preferito")
                append(" · ")
                append(if (autofillAnyEnabled) "autofill attivo" else "autofill spento")
            }
            Text(serviceLine, style = MaterialTheme.typography.bodyLarge)
            Text(
                when {
                    currentDiag == null -> "Verifica…"
                    !currentDiag.loggedIn -> "Accedi a KidBox"
                    currentDiag.familyId.isNullOrBlank() -> "Apri Home e scegli una famiglia"
                    !currentDiag.snapshotHasItems -> "Apri Password nell’app per aggiornare i suggerimenti"
                    else -> "Suggerimenti pronti"
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
            ) { Text("Imposta servizio autofill") }
            if (Build.VERSION.SDK_INT >= 34) {
                Button(
                    onClick = {
                        val act = context as? Activity ?: return@Button
                        act.startSettingsIntentSafe(
                            Intent(Settings.ACTION_CREDENTIAL_PROVIDER),
                            "Questo dispositivo non apre la schermata dedicata. Cerca \"Password\" o \"Credenziali\" nelle impostazioni di sistema.",
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Password di sistema") }
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
                ) { Text("Credential Manager") }
            }
            Spacer(Modifier.height(8.dp))
            Text("Biometria per autofill", style = MaterialTheme.typography.titleMedium)
            Text(
                "Se disattivata: meno passaggi, meno sicuro.",
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
