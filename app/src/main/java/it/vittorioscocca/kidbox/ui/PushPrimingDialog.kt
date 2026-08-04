package it.vittorioscocca.kidbox.ui

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.notification.PushNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@EntryPoint
@InstallIn(SingletonComponent::class)
private interface PushPrimingEntryPoint {
    fun pushNotificationManager(): PushNotificationManager
}

/**
 * Priming del permesso notifiche, mostrato una sola volta al primo accesso
 * (utente autenticato). Gemello di `PushPrimingView` su iOS.
 *
 * Perché una schermata nostra prima di quella di sistema: da Android 13 il
 * permesso `POST_NOTIFICATIONS` si chiede a runtime, e se l'utente nega due
 * volte il sistema non ripropone più il dialog. Spiegare prima *perché*
 * servono, e chiedere il permesso solo su "Attiva", non brucia quell'occasione
 * su un no impulsivo.
 *
 * Sotto Android 13 il permesso runtime non esiste: le notifiche sono già
 * concesse all'installazione. Lì il priming si limita a registrare il token e a
 * segnare la decisione, senza mostrare alcun dialog di sistema.
 */
@Composable
fun PushPrimingGate() {
    val context = androidx.compose.ui.platform.LocalContext.current
    // Un solo utente autenticato: senza login non c'è dove salvare il token.
    if (FirebaseAuth.getInstance().currentUser == null) return

    val pushNotificationManager = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            PushPrimingEntryPoint::class.java,
        ).pushNotificationManager()
    }

    var decided by remember { mutableStateOf(hasDecided(context)) }

    // Se il permesso è già concesso (o siamo sotto il regime runtime, dove non
    // esiste), non c'è niente da chiedere: registra il token e chiudi in
    // silenzio. In un LaunchedEffect, non nel corpo del composable: scrivere le
    // prefs e lanciare una coroutine durante la composizione le ripeterebbe a
    // ogni ricomposizione.
    val alreadyEnabled = remember { NotificationManagerCompat.from(context).areNotificationsEnabled() }
    val autoRegister = !decided && alreadyEnabled &&
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
    LaunchedEffect(autoRegister) {
        if (autoRegister) {
            markDecided(context)
            registerToken(pushNotificationManager)
            decided = true
        }
    }

    if (decided || autoRegister) return

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        markDecided(context)
        // Il token si registra comunque: `getUserTokensIfEnabled` lato server
        // filtra per preferenza, e senza token nessuna notifica futura potrebbe
        // arrivare se l'utente riattiva il permesso dalle impostazioni.
        if (granted) registerToken(pushNotificationManager)
        decided = true
    }

    AlertDialog(
        onDismissRequest = { /* decisione esplicita richiesta: nessun dismiss */ },
        title = { Text(stringResource(R.string.push_priming_title)) },
        text = {
            Text(
                stringResource(R.string.push_priming_body),
                style = MaterialTheme.typography.bodyMedium,
            )
        },
        confirmButton = {
            TextButton(onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    // Nessun permesso runtime da chiedere: registra e chiudi.
                    markDecided(context)
                    registerToken(pushNotificationManager)
                    decided = true
                }
            }) { Text(stringResource(R.string.push_priming_enable)) }
        },
        dismissButton = {
            TextButton(onClick = {
                markDecided(context)
                decided = true
            }) { Text(stringResource(R.string.push_priming_later)) }
        },
    )
}

private const val PREFS = "kidbox_prefs"
private const val KEY_DECIDED = "kb_did_prime_push"

private fun hasDecided(context: Context): Boolean =
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_DECIDED, false)

private fun markDecided(context: Context) {
    context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putBoolean(KEY_DECIDED, true) }
}

private fun registerToken(pushNotificationManager: PushNotificationManager) {
    CoroutineScope(Dispatchers.IO).launch {
        runCatching { pushNotificationManager.registerCurrentFcmToken() }
    }
}

