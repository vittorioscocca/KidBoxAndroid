package it.vittorioscocca.kidbox.ui.permissions

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * Controlli runtime: richiedere un permesso solo se non è già concesso.
 */
object RuntimePermissions {

    fun isGranted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasLocationAccess(context: Context): Boolean =
        isGranted(context, Manifest.permission.ACCESS_FINE_LOCATION) ||
            isGranted(context, Manifest.permission.ACCESS_COARSE_LOCATION)

    fun hasBackgroundLocationAccess(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return hasLocationAccess(context)
        return isGranted(context, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    fun needsNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    fun hasNotificationPermission(context: Context): Boolean =
        !needsNotificationPermission() ||
            isGranted(context, Manifest.permission.POST_NOTIFICATIONS)

    /**
     * Sveglia a tutto schermo per i promemoria urgenti.
     *
     * Da Android 14 `USE_FULL_SCREEN_INTENT` è pre-concesso all'installazione
     * solo alle app dichiarate come sveglia o telefono su Play Console — per
     * KidBox è dichiarata «Sveglia», ma la pre-concessione va approvata e può
     * comunque essere revocata dall'utente. Senza, la sveglia non copre lo
     * schermo bloccato: diventa una finestra mobile visibile 60 secondi.
     *
     * Google chiede esplicitamente di **verificarne la presenza** invece di
     * darla per scontata: è quello che fa questa funzione.
     */
    fun canUseFullScreenIntent(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        return runCatching { manager.canUseFullScreenIntent() }.getOrDefault(true)
    }

    /**
     * La schermata di sistema dove si concede la sveglia a tutto schermo. È
     * per app, non globale: `package:` è obbligatorio.
     */
    fun fullScreenIntentSettings(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return null
        return Intent(
            Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
            Uri.fromParts("package", context.packageName, null),
        )
    }
}

/**
 * Chiede [Manifest.permission.CAMERA] solo se mancante, poi esegue [onLaunchCamera].
 */
@Composable
fun rememberCameraPermissionRequester(
    onDenied: (() -> Unit)? = null,
    onLaunchCamera: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onLaunchCamera() else onDenied?.invoke()
    }
    return {
        if (RuntimePermissions.isGranted(context, Manifest.permission.CAMERA)) {
            onLaunchCamera()
        } else {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }
}

/**
 * Chiede notifiche (API 33+) solo se mancanti, poi [onGranted].
 */
@Composable
fun rememberNotificationPermissionRequester(
    onDenied: (() -> Unit)? = null,
    onGranted: () -> Unit,
): () -> Unit {
    val context = LocalContext.current
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onGranted() else onDenied?.invoke()
    }
    return {
        if (RuntimePermissions.hasNotificationPermission(context)) {
            onGranted()
        } else {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}
