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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

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
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    tint = kb.title,
                )
            }
            Text(
                text = "Notifiche",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = kb.title,
            )
        }
        Spacer(Modifier.height(12.dp))
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
                            text = "Canale notifiche silenzioso",
                            style = MaterialTheme.typography.titleMedium,
                            color = kb.title,
                        )
                        Text(
                            text = "Tocca qui per attivare alert visibili (popup/suono).",
                            style = MaterialTheme.typography.bodySmall,
                            color = kb.subtitle,
                        )
                    }
                    Text(
                        text = "Apri",
                        color = Color(0xFFFF6B00),
                        style = MaterialTheme.typography.titleSmall,
                    )
                }
            }
            NotificationToggleRow(
                title = "Notifica nuovi documenti",
                subtitle = "Quando viene caricato un documento condiviso",
                checked = state.notifyOnNewDocs,
                enabled = !state.isLoading && pendingEnableKey == null,
                onCheckedChange = { enabled ->
                    updatePreferenceWithPermission(
                        key = PreferenceKeys.NOTIFY_ON_NEW_DOCS,
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
                title = "Notifica nuovi messaggi in chat",
                subtitle = "Quando un membro invia un messaggio",
                checked = state.notifyOnNewMessages,
                enabled = !state.isLoading && pendingEnableKey == null,
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
                title = "Notifiche posizione",
                subtitle = "Inizio/fine condivisione posizione",
                checked = state.notifyOnLocationSharing,
                enabled = !state.isLoading && pendingEnableKey == null,
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
                title = "Notifiche Todo",
                subtitle = "Assegnazioni e scadenze",
                checked = state.notifyOnTodoAssigned,
                enabled = !state.isLoading && pendingEnableKey == null,
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
                title = "Notifiche lista della spesa",
                subtitle = "Quando un membro aggiunge un prodotto",
                checked = state.notifyOnNewGroceryItem,
                enabled = !state.isLoading && pendingEnableKey == null,
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
                title = "Notifiche nuove note",
                subtitle = "Quando viene creata una nuova nota",
                checked = state.notifyOnNewNote,
                enabled = !state.isLoading && pendingEnableKey == null,
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
                title = "Notifiche calendario",
                subtitle = "Quando viene aggiunto un evento",
                checked = state.notifyOnNewCalendarEvent,
                enabled = !state.isLoading && pendingEnableKey == null,
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
                title = "Notifiche nuove spese",
                subtitle = "Quando viene registrata una spesa famiglia",
                checked = state.notifyOnNewExpense,
                enabled = !state.isLoading && pendingEnableKey == null,
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
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val granted = context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onSet(key, true, true)
        } else {
            setPendingKey(key)
            requestPermission()
        }
    } else {
        onSet(key, true, true)
    }
}
