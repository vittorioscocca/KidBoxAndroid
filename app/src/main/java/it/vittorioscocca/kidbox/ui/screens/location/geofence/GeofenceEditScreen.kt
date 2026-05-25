package it.vittorioscocca.kidbox.ui.screens.location.geofence

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.Circle as MapCircle
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
private val presetEmojis = listOf("🏫", "🏠", "🏥", "🏋️", "⛪", "🛒", "🏖️", "🌳")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GeofenceEditScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: GeofenceEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showMonitorPicker by remember { mutableStateOf(false) }
    var showNotifyPicker by remember { mutableStateOf(false) }
    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(state.latitude, state.longitude, state.radius) {
        cameraPositionState.animate(
            CameraUpdateFactory.newLatLngZoom(LatLng(state.latitude, state.longitude), radiusToZoom(state.radius)),
        )
    }
    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }
    LaunchedEffect(state.saveSucceeded) {
        if (state.saveSucceeded) onSaved()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.geofenceId == null) "Nuova zona" else "Modifica zona") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                actions = {
                    if (state.isOwner) {
                        IconButton(
                            onClick = { viewModel.save() },
                            enabled = !state.isSaving,
                        ) {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.padding(8.dp), strokeWidth = 2.dp)
                            } else {
                                Icon(Icons.Default.Check, contentDescription = "Salva")
                            }
                        }
                    }
                },
            )
        },
        containerColor = MaterialTheme.kidBoxColors.background,
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (!state.isOwner) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Solo il proprietario può modificare le zone")
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::updateName,
                label = { Text("Nome zona") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Text("Emoji", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presetEmojis.forEach { emoji ->
                    Text(
                        text = emoji,
                        fontSize = 28.sp,
                        modifier = Modifier
                            .clickable { viewModel.updateEmoji(emoji) }
                            .background(
                                if (state.selectedEmoji == emoji) MaterialTheme.colorScheme.primaryContainer
                                else MaterialTheme.colorScheme.surface,
                                RoundedCornerShape(8.dp),
                            )
                            .padding(8.dp),
                    )
                }
            }
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::updateSearchQuery,
                label = { Text("Cerca indirizzo") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            state.addressSuggestions.take(5).forEach { s ->
                Text(
                    text = s.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { viewModel.selectSuggestion(s) }
                        .padding(vertical = 6.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp),
                shape = RoundedCornerShape(12.dp),
            ) {
                val pin = LatLng(state.latitude, state.longitude)
                val accent = MaterialTheme.colorScheme.primary
                GoogleMap(
                    modifier = Modifier.fillMaxSize(),
                    cameraPositionState = cameraPositionState,
                    onMapClick = { latLng ->
                        viewModel.updateMapPosition(latLng.latitude, latLng.longitude)
                    },
                ) {
                    Marker(
                        state = MarkerState(pin),
                        title = state.name.ifBlank { "Zona" },
                    )
                    MapCircle(
                        center = pin,
                        radius = state.radius.toDouble(),
                        strokeColor = accent,
                        strokeWidth = 3f,
                        fillColor = accent.copy(alpha = 0.18f),
                    )
                }
            }
            Text("Raggio: ${state.radius.toInt()} m", fontWeight = FontWeight.SemiBold)
            Slider(
                value = state.radius,
                onValueChange = viewModel::updateRadius,
                valueRange = 50f..2000f,
                steps = 38,
            )
            SettingsRow(
                title = "Applica la zona a",
                subtitle = memberSummary(
                    useAll = state.monitorAllMembers,
                    selectedIds = state.monitoredUserIds,
                    members = state.members,
                    fallbackEmpty = "Seleziona membri…",
                ),
                onClick = { showMonitorPicker = true },
            )
            SettingsRow(
                title = "Avvisa",
                subtitle = memberSummary(
                    useAll = state.notifyAllMembers,
                    selectedIds = state.notifyUserIds,
                    members = state.members,
                    fallbackEmpty = "Seleziona destinatari…",
                ),
                onClick = { showNotifyPicker = true },
            )
            Text("Quando avvisare", fontWeight = FontWeight.SemiBold)
            ToggleRow("All'arrivo", state.notifyOnArrive, viewModel::updateNotifyOnArrive)
            ToggleRow("Alla partenza", state.notifyOnLeave, viewModel::updateNotifyOnLeave)
        }
    }

    if (showMonitorPicker) {
        GeofenceMemberPickerSheet(
            title = "Chi monitorare",
            footer = "Sul telefono di ogni persona selezionata, con posizione condivisa attiva, KidBox rileva entrata e uscita da questa zona.",
            useAll = state.monitorAllMembers,
            onUseAllChange = viewModel::updateMonitorAll,
            selectedIds = state.monitoredUserIds,
            onToggle = viewModel::toggleMonitoredUser,
            members = state.members,
            onDismiss = { showMonitorPicker = false },
        )
    }
    if (showNotifyPicker) {
        GeofenceMemberPickerSheet(
            title = "Chi avvisare",
            footer = "Riceveranno una notifica quando qualcuno entra o esce. Chi genera l'evento non riceve la propria notifica.",
            useAll = state.notifyAllMembers,
            onUseAllChange = viewModel::updateNotifyAll,
            selectedIds = state.notifyUserIds,
            onToggle = viewModel::toggleNotifyUser,
            members = state.members,
            onDismiss = { showNotifyPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GeofenceMemberPickerSheet(
    title: String,
    footer: String,
    useAll: Boolean,
    onUseAllChange: (Boolean) -> Unit,
    selectedIds: Set<String>,
    onToggle: (String) -> Unit,
    members: List<KBFamilyMemberEntity>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Spacer(Modifier.height(12.dp))

            // ── Sezione "Tutti i membri" ────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (!useAll) onUseAllChange(true)
                    }
                    .padding(vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Tutti i membri")
                Icon(
                    imageVector = if (useAll) Icons.Default.Check else Icons.Outlined.Circle,
                    contentDescription = null,
                    tint = if (useAll) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                )
            }
            Text(
                text = footer,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(8.dp))

            // ── Sezione "Membri" (selezione singola) ────────────────────
            Text(
                "Membri",
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
            if (members.isEmpty()) {
                Text(
                    "Nessun membro disponibile",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 12.dp),
                )
            } else {
                members.forEach { member ->
                    val label = member.displayName?.takeIf { it.isNotBlank() }
                        ?: member.email
                        ?: member.userId
                    val selected = !useAll && selectedIds.contains(member.userId)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onToggle(member.userId) }
                            .padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, color = MaterialTheme.colorScheme.onSurface)
                        Icon(
                            imageVector = if (selected) Icons.Default.Check else Icons.Outlined.Circle,
                            contentDescription = null,
                            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
            if (useAll) {
                Text(
                    "Tocca un membro per scegliere solo alcune persone.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                Text("Fine")
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

private fun memberSummary(
    useAll: Boolean,
    selectedIds: Set<String>,
    members: List<KBFamilyMemberEntity>,
    fallbackEmpty: String,
): String {
    if (useAll) return "Tutti i membri"
    val names = members
        .filter { selectedIds.contains(it.userId) }
        .mapNotNull { m ->
            m.displayName?.trim()?.takeIf { it.isNotEmpty() }
                ?: m.email?.trim()?.takeIf { it.isNotEmpty() }
        }
    return if (names.isEmpty()) fallbackEmpty else names.joinToString(", ")
}

@Composable
private fun SettingsRow(title: String, subtitle: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
        }
    }
}

@Composable
private fun ToggleRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun radiusToZoom(radius: Float): Float {
    return when {
        radius < 150f -> 16f
        radius < 400f -> 15f
        radius < 800f -> 14f
        else -> 13f
    }
}
