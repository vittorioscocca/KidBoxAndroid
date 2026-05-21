package it.vittorioscocca.kidbox.ui.permissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
