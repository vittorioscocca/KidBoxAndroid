package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhoneIphone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.components.KBBackButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.DateFormat

@Composable
fun DevicesScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
    viewModel: DevicesViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val snackbarHostState = remember { SnackbarHostState() }
    var pending by remember { mutableStateOf<DeviceSessionUi?>(null) }
    var confirmAll by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.signedOut) { if (state.signedOut) onSignedOut() }
    LaunchedEffect(state.message) {
        val msg = state.message ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(msg)
        viewModel.clearMessage()
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
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_devices_title),
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
        )
        Spacer(Modifier.height(16.dp))

        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = kb.card),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (state.isLoading && state.sessions.isEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp))
                }
            }
            state.sessions.forEachIndexed { index, session ->
                if (index > 0) HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
                DeviceRow(session, enabled = !state.isWorking) { pending = session }
            }
        }
        Text(
            text = stringResource(R.string.settings_devices_footer),
            style = MaterialTheme.typography.bodySmall,
            color = kb.subtitle,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
        )

        Spacer(Modifier.height(8.dp))
        Card(
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = kb.card),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = stringResource(R.string.settings_devices_signout_all),
                style = MaterialTheme.typography.titleMedium,
                color = Color(0xFFD32F2F),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = !state.isWorking) { confirmAll = true }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
            )
        }
        Text(
            text = stringResource(R.string.settings_devices_signout_all_sub),
            style = MaterialTheme.typography.bodySmall,
            color = kb.subtitle,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
        )

        SnackbarHost(hostState = snackbarHostState)
    }

    pending?.let { session ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text(stringResource(R.string.settings_devices_confirm_title)) },
            text = {
                Text(
                    if (session.isCurrent) {
                        stringResource(R.string.settings_devices_confirm_current)
                    } else {
                        stringResource(R.string.settings_devices_confirm_other)
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pending = null
                    viewModel.signOutDevice(session)
                }) { Text(stringResource(R.string.settings_devices_signout)) }
            },
            dismissButton = {
                TextButton(onClick = { pending = null }) {
                    Text(stringResource(R.string.settings_common_cancel))
                }
            },
        )
    }

    if (confirmAll) {
        AlertDialog(
            onDismissRequest = { confirmAll = false },
            title = { Text(stringResource(R.string.settings_devices_confirm_all_title)) },
            text = { Text(stringResource(R.string.settings_devices_confirm_all_body)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmAll = false
                    viewModel.signOutAllDevices()
                }) { Text(stringResource(R.string.settings_devices_signout_all_short)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmAll = false }) {
                    Text(stringResource(R.string.settings_common_cancel))
                }
            },
        )
    }
}

@Composable
private fun DeviceRow(
    session: DeviceSessionUi,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val icon = when (session.platform) {
        "ios" -> Icons.Default.PhoneIphone
        "web" -> Icons.Default.Language
        else -> Icons.Default.PhoneAndroid
    }
    // «Ultima apertura» e non «ultima attività»: il campo si aggiorna alla
    // registrazione della sessione, cioè all'avvio dell'app, non a ogni gesto.
    val when_ = session.lastSeenAt?.let {
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(it)
    }
    val subtitle = listOfNotNull(
        session.osVersion.takeIf { it.isNotBlank() },
        when_?.let { stringResource(R.string.settings_devices_last_open, it) },
    ).joinToString(" · ")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = Color(0xFFFF6B00))
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Text(
                text = session.deviceName.ifBlank { stringResource(R.string.settings_devices_fallback_name) },
                style = MaterialTheme.typography.titleMedium,
                color = kb.title,
            )
            if (session.isCurrent) {
                Text(
                    text = stringResource(R.string.settings_devices_this_one),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFFF6B00),
                )
            }
            Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
        }
    }
}
