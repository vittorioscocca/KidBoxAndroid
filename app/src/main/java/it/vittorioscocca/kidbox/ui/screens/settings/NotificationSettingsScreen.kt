package it.vittorioscocca.kidbox.ui.screens.settings

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.core.app.NotificationManagerCompat
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.notification.PushNotificationManager.PreferenceKeys
import it.vittorioscocca.kidbox.notifications.KidBoxFirebaseMessagingService
import it.vittorioscocca.kidbox.ui.permissions.RuntimePermissions
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import androidx.compose.foundation.layout.width
import androidx.compose.ui.unit.sp

@Composable
fun NotificationSettingsScreen(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var pendingEnableKey by remember { mutableStateOf<String?>(null) }
    var isNotificationChannelSilent by remember { mutableStateOf(false) }
    // Permesso notifiche di sistema negato (Android 13+) o notifiche disattivate
    // dalle impostazioni di sistema. In quel caso non arriva niente, comunque
    // siano le preferenze qui sotto: toggle spenti e disabilitati, banner col
    // vero motivo. `areNotificationsEnabled` copre anche <33, dove non c'è
    // permesso runtime ma l'utente può comunque averle disattivate a mano.
    var systemDenied by remember {
        mutableStateOf(!NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                systemDenied = !NotificationManagerCompat.from(context).areNotificationsEnabled()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val key = pendingEnableKey
        if (key == null) return@rememberLauncherForActivityResult
        if (granted) {
            viewModel.setPreference(key = key, enabled = true, registerToken = true)
        } else {
            viewModel.setPreference(key = key, enabled = false, registerToken = false)
        }
        pendingEnableKey = null
    }

    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
    }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            val channel = manager?.getNotificationChannel(KidBoxFirebaseMessagingService.CHANNEL_ID_FAMILY_UPDATES)
            isNotificationChannelSilent = channel != null && channel.importance < NotificationManager.IMPORTANCE_DEFAULT
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 14.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        KBBackButton(onClick = onBack)
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_notifications_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
        )
        Spacer(Modifier.height(16.dp))
        if (systemDenied) {
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = kb.card),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // Impostazioni notifiche dell'app: da qui l'utente
                            // riattiva il permesso di sistema.
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = stringResource(R.string.settings_notif_blocked),
                        style = MaterialTheme.typography.titleMedium,
                        color = kb.title,
                    )
                    Text(
                        text = stringResource(R.string.settings_notif_blocked_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = kb.subtitle,
                    )
                    Text(
                        text = stringResource(R.string.settings_notif_open),
                        color = Color(0xFFFF6B00),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = kb.card),
        ) {
            if (isNotificationChannelSilent) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val intent = Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).apply {
                                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                    putExtra(Settings.EXTRA_CHANNEL_ID, KidBoxFirebaseMessagingService.CHANNEL_ID_FAMILY_UPDATES)
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_notif_silent_channel),
                            style = MaterialTheme.typography.titleMedium,
                            color = kb.title,
                        )
                        Text(
                            text = stringResource(R.string.settings_notif_silent_channel_sub),
                            style = MaterialTheme.typography.bodySmall,
                            color = kb.subtitle,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_notif_open),
                        color = Color(0xFFFF6B00),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            NotificationToggleRow(
                title = stringResource(R.string.settings_notif_chat),
                subtitle = stringResource(R.string.settings_notif_chat_sub),
                checked = state.notifyOnNewMessages && !systemDenied,
                enabled = !state.isLoading && pendingEnableKey == null && !systemDenied,
                onCheckedChange = { enabled ->
                    updatePreferenceWithPermission(
                        key = PreferenceKeys.NOTIFY_ON_NEW_MESSAGES,
                        enabled = enabled,
                        context = context,
                        setPendingKey = { pendingEnableKey = it },
                        requestPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onSet = { key, value, register ->
                            viewModel.setPreference(key = key, enabled = value, registerToken = register)
                        },
                    )
                },
            )
            NotificationToggleRow(
                title = stringResource(R.string.settings_notif_location),
                subtitle = stringResource(R.string.settings_notif_location_sub),
                checked = state.notifyOnLocationSharing && !systemDenied,
                enabled = !state.isLoading && pendingEnableKey == null && !systemDenied,
                onCheckedChange = { enabled ->
                    updatePreferenceWithPermission(
                        key = PreferenceKeys.NOTIFY_ON_LOCATION_SHARING,
                        enabled = enabled,
                        context = context,
                        setPendingKey = { pendingEnableKey = it },
                        requestPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onSet = { key, value, register ->
                            viewModel.setPreference(key = key, enabled = value, registerToken = register)
                        },
                    )
                },
            )
            NotificationToggleRow(
                title = stringResource(R.string.settings_notif_todo),
                subtitle = stringResource(R.string.settings_notif_todo_sub),
                checked = state.notifyOnTodoAssigned && !systemDenied,
                enabled = !state.isLoading && pendingEnableKey == null && !systemDenied,
                onCheckedChange = { enabled ->
                    updatePreferenceWithPermission(
                        key = PreferenceKeys.NOTIFY_ON_TODO_ASSIGNED,
                        enabled = enabled,
                        context = context,
                        setPendingKey = { pendingEnableKey = it },
                        requestPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onSet = { key, value, register ->
                            viewModel.setPreference(key = key, enabled = value, registerToken = register)
                        },
                    )
                },
            )
            NotificationToggleRow(
                title = stringResource(R.string.settings_notif_shopping),
                subtitle = stringResource(R.string.settings_notif_shopping_sub),
                checked = state.notifyOnNewGroceryItem && !systemDenied,
                enabled = !state.isLoading && pendingEnableKey == null && !systemDenied,
                onCheckedChange = { enabled ->
                    updatePreferenceWithPermission(
                        key = PreferenceKeys.NOTIFY_ON_NEW_GROCERY_ITEM,
                        enabled = enabled,
                        context = context,
                        setPendingKey = { pendingEnableKey = it },
                        requestPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onSet = { key, value, register ->
                            viewModel.setPreference(key = key, enabled = value, registerToken = register)
                        },
                    )
                },
            )
            NotificationToggleRow(
                title = stringResource(R.string.settings_notif_notes),
                subtitle = stringResource(R.string.settings_notif_notes_sub),
                checked = state.notifyOnNewNote && !systemDenied,
                enabled = !state.isLoading && pendingEnableKey == null && !systemDenied,
                onCheckedChange = { enabled ->
                    updatePreferenceWithPermission(
                        key = PreferenceKeys.NOTIFY_ON_NEW_NOTE,
                        enabled = enabled,
                        context = context,
                        setPendingKey = { pendingEnableKey = it },
                        requestPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onSet = { key, value, register ->
                            viewModel.setPreference(key = key, enabled = value, registerToken = register)
                        },
                    )
                },
            )
            NotificationToggleRow(
                title = stringResource(R.string.settings_notif_calendar),
                subtitle = stringResource(R.string.settings_notif_calendar_sub),
                checked = state.notifyOnNewCalendarEvent && !systemDenied,
                enabled = !state.isLoading && pendingEnableKey == null && !systemDenied,
                onCheckedChange = { enabled ->
                    updatePreferenceWithPermission(
                        key = PreferenceKeys.NOTIFY_ON_NEW_CALENDAR_EVENT,
                        enabled = enabled,
                        context = context,
                        setPendingKey = { pendingEnableKey = it },
                        requestPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onSet = { key, value, register ->
                            viewModel.setPreference(key = key, enabled = value, registerToken = register)
                        },
                    )
                },
            )
            NotificationToggleRow(
                title = stringResource(R.string.settings_notif_expenses),
                subtitle = stringResource(R.string.settings_notif_expenses_sub),
                checked = state.notifyOnNewExpense && !systemDenied,
                enabled = !state.isLoading && pendingEnableKey == null && !systemDenied,
                onCheckedChange = { enabled ->
                    updatePreferenceWithPermission(
                        key = PreferenceKeys.NOTIFY_ON_NEW_EXPENSE,
                        enabled = enabled,
                        context = context,
                        setPendingKey = { pendingEnableKey = it },
                        requestPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onSet = { key, value, register ->
                            viewModel.setPreference(key = key, enabled = value, registerToken = register)
                        },
                    )
                },
            )
            NotificationToggleRow(
                title = stringResource(R.string.settings_notif_tips),
                subtitle = stringResource(R.string.settings_notif_tips_sub),
                checked = state.nudgesEnabled && !systemDenied,
                // Non passa da `updatePreferenceWithPermission`: spegnere non
                // richiede alcun permesso, e riaccendere non deve chiederlo a
                // freddo — il motore verifica da sé se può notificare.
                enabled = !state.isLoading && !systemDenied,
                onCheckedChange = { viewModel.setNudgesEnabled(it) },
            )
            NotificationToggleRow(
                title = stringResource(R.string.settings_notif_wallet),
                subtitle = stringResource(R.string.settings_notif_wallet_sub),
                checked = state.notifyOnWallet && !systemDenied,
                enabled = !state.isLoading && pendingEnableKey == null && !systemDenied,
                onCheckedChange = { enabled ->
                    updatePreferenceWithPermission(
                        key = PreferenceKeys.NOTIFY_ON_WALLET,
                        enabled = enabled,
                        context = context,
                        setPendingKey = { pendingEnableKey = it },
                        requestPermission = {
                            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        },
                        onSet = { key, value, register ->
                            viewModel.setPreference(key = key, enabled = value, registerToken = register)
                        },
                    )
                },
            )
        }
        Spacer(Modifier.height(12.dp))
        SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun NotificationToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = Color(0xFFFF6B00),
            )
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = kb.title,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = kb.subtitle,
                )
            }
        }
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
        )
    }
}

private fun updatePreferenceWithPermission(
    key: String,
    enabled: Boolean,
    context: android.content.Context,
    setPendingKey: (String?) -> Unit,
    requestPermission: () -> Unit,
    onSet: (key: String, value: Boolean, registerToken: Boolean) -> Unit,
) {
    if (!enabled) {
        onSet(key, false, false)
        return
    }
    if (RuntimePermissions.hasNotificationPermission(context)) {
        onSet(key, true, true)
    } else {
        setPendingKey(key)
        requestPermission()
    }
}
