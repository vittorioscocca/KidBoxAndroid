@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health.treatments

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PURPLE_DETAIL = Color(0xFF9573D9)
private val ORANGE_DETAIL = Color(0xFFFF6B00)
private val GREEN_DETAIL = Color(0xFF4CAF50)
private val DATE_FMT_DAY = SimpleDateFormat("EEE d MMM", Locale.ITALIAN)

@Composable
fun MedicalTreatmentDetailScreen(
    familyId: String,
    childId: String,
    treatmentId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: MedicalTreatmentDetailViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, treatmentId) { viewModel.bind(familyId, childId, treatmentId) }
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onBack() }
    LaunchedEffect(state.uploadError) {
        state.uploadError?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.consumeUploadError()
        }
    }
    LaunchedEffect(state.openFileEvent) {
        state.openFileEvent?.let { (mime, file) ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
            viewModel.consumeOpenFileEvent()
        }
    }

    val cameraFile = remember { File(File(context.cacheDir, "health-camera").apply { mkdirs() }, "treatment_camera_tmp.jpg") }
    val cameraUri = remember(cameraFile) { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile) }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (!granted) Toast.makeText(context, "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) viewModel.uploadAttachment(cameraUri)
    }
    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadAttachment(it) }
    }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let { viewModel.uploadAttachment(it) }
    }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(kb.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val treatment = state.treatment
    if (treatment == null) {
        Box(modifier = Modifier.fillMaxSize().background(kb.background), contentAlignment = Alignment.Center) {
            Text("Cura non trovata", color = kb.title)
        }
        return
    }

    val dosageStr = if (treatment.dosageValue % 1.0 == 0.0) "%.0f".format(treatment.dosageValue)
    else "%.1f".format(treatment.dosageValue)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top bar ──────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = kb.title)
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = { viewModel.showExtendSheet() }) {
                    Icon(Icons.Default.DateRange, contentDescription = "Estendi", tint = kb.title)
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Modifica", tint = kb.title)
                }
                IconButton(onClick = { viewModel.showDeleteDialog() }) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = Color.Red.copy(alpha = 0.8f))
                }
            }
        }

        // ── Header card ──────────────────────────────────────────────────────
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = kb.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(PURPLE_DETAIL.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.Medication, contentDescription = null, tint = PURPLE_DETAIL)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(treatment.drugName, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = kb.title)
                            Text("$dosageStr ${treatment.dosageUnit} · ${treatment.dailyFrequency}x/die", fontSize = 13.sp, color = kb.subtitle)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val isActiveNow = treatment.isActive && (treatment.isLongTerm || treatment.endDateEpochMillis == null || treatment.endDateEpochMillis >= System.currentTimeMillis())
                        StatusChip(
                            text = if (isActiveNow) "Attiva" else "Conclusa",
                            bg = if (isActiveNow) GREEN_DETAIL.copy(alpha = 0.15f) else kb.subtitle.copy(alpha = 0.15f),
                            fg = if (isActiveNow) GREEN_DETAIL else kb.subtitle,
                        )
                        if (treatment.isLongTerm) {
                            StatusChip("Lungo termine", PURPLE_DETAIL.copy(alpha = 0.15f), PURPLE_DETAIL)
                        }
                        if (treatment.reminderEnabled) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = PURPLE_DETAIL, modifier = Modifier.size(20.dp).align(Alignment.CenterVertically))
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Cura in corso", fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 14.sp)
                        Switch(
                            checked = state.isActive,
                            onCheckedChange = { viewModel.setActive(it) },
                            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PURPLE_DETAIL),
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Today's doses ────────────────────────────────────────────────────
        if (state.todaySlots.isNotEmpty()) {
            item {
                Text(
                    "OGGI",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = kb.subtitle,
                    letterSpacing = 0.8.sp,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
                Spacer(Modifier.height(8.dp))
            }
            items(state.todaySlots) { slot ->
                DoseSlotRow(
                    slot = slot,
                    onMarkTaken = { viewModel.markTaken(slot.dayNumber, slot.slotIndex, slot.scheduledTime) },
                    onMarkSkipped = { viewModel.markSkipped(slot.dayNumber, slot.slotIndex, slot.scheduledTime) },
                    onUndo = { slot.logId?.let { viewModel.clearLog(it) } },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }

        // ── Calendar toggle ──────────────────────────────────────────────────
        item {
            TextButton(
                onClick = { viewModel.toggleCalendar() },
                modifier = Modifier.padding(horizontal = 18.dp),
            ) {
                Text(
                    if (state.showCalendar) "Nascondi calendario" else "Vedi calendario completo",
                    color = PURPLE_DETAIL,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (state.showCalendar) {
            items(state.calendarDays) { day ->
                CalendarDayCard(day = day, kb = kb)
            }
            item { Spacer(Modifier.height(8.dp)) }
        }

        // ── Notes ────────────────────────────────────────────────────────────
        if (!treatment.notes.isNullOrBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = kb.card),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("NOTE", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = kb.subtitle, letterSpacing = 0.8.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(treatment.notes, fontSize = 14.sp, color = kb.title)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // ── Allegati card ────────────────────────────────────────────────────
        item {
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                HealthAttachmentsCard(
                    attachments = state.attachments,
                    tintColor = PURPLE_DETAIL,
                    isUploading = state.isUploading,
                    onPickFile = { pickFileLauncher.launch("*/*") },
                    onPickPhoto = { pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                    onTakePhoto = {
                        cameraPermLauncher.launch(android.Manifest.permission.CAMERA)
                        takePictureLauncher.launch(cameraUri)
                    },
                    onOpenAttachment = { viewModel.openAttachment(it) },
                    onDeleteAttachment = { viewModel.deleteAttachment(it) },
                )
            }
            Spacer(Modifier.height(16.dp))
        }

        // ── Bottom actions ───────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = onEdit,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Modifica") }
                Button(
                    onClick = { viewModel.showDeleteDialog() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Elimina", color = Color.White) }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    // ── Extend sheet ─────────────────────────────────────────────────────────
    if (state.showExtendSheet) {
        ExtendSheet(
            onDismiss = { viewModel.dismissExtendSheet() },
            onExtend = { days -> viewModel.extend(days) },
        )
    }

    // ── Delete confirm ───────────────────────────────────────────────────────
    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Elimina cura") },
            text = { Text("Eliminare la cura? Tutte le notifiche verranno cancellate.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete() }) {
                    Text("Elimina", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun DoseSlotRow(
    slot: DoseSlot,
    onMarkTaken: () -> Unit,
    onMarkSkipped: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("${slot.label} · ${slot.scheduledTime}", fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 14.sp)
            }
            when (slot.state) {
                DoseState.TAKEN -> {
                    IconButton(onClick = onUndo, modifier = Modifier.size(36.dp).clip(CircleShape).background(GREEN_DETAIL)) {
                        Icon(Icons.Default.Check, contentDescription = "Assunto", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                DoseState.SKIPPED -> {
                    IconButton(onClick = onUndo, modifier = Modifier.size(36.dp).clip(CircleShape).background(ORANGE_DETAIL)) {
                        Icon(Icons.Default.Close, contentDescription = "Saltato", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
                DoseState.PENDING -> {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = onMarkTaken,
                            colors = ButtonDefaults.buttonColors(containerColor = GREEN_DETAIL),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        ) { Text("✓ Assunto", fontSize = 12.sp, color = Color.White) }
                        OutlinedButton(
                            onClick = onMarkSkipped,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.height(32.dp),
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
                        ) { Text("✗ Saltato", fontSize = 12.sp) }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDayCard(day: DayEntry, kb: KidBoxColorScheme) {
    val kbColors = kb
    Card(
        colors = CardDefaults.cardColors(containerColor = kbColors.card),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 3.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Giorno ${day.dayNumber + 1} · ${DATE_FMT_DAY.format(Date(day.dateMillis))}",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = kbColors.title,
                modifier = Modifier.weight(1f),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                day.slots.forEach { slot ->
                    val (bg, fg) = when (slot.state) {
                        DoseState.TAKEN -> GREEN_DETAIL to Color.White
                        DoseState.SKIPPED -> ORANGE_DETAIL to Color.White
                        DoseState.PENDING -> kbColors.subtitle.copy(alpha = 0.2f) to kbColors.subtitle
                    }
                    Box(
                        modifier = Modifier.size(20.dp).clip(CircleShape).background(bg),
                        contentAlignment = Alignment.Center,
                    ) {
                        when (slot.state) {
                            DoseState.TAKEN -> Icon(Icons.Default.Check, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
                            DoseState.SKIPPED -> Icon(Icons.Default.Close, contentDescription = null, tint = fg, modifier = Modifier.size(12.dp))
                            DoseState.PENDING -> {}
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusChip(text: String, bg: Color, fg: Color) {
    Box(
        modifier = Modifier.clip(RoundedCornerShape(50.dp)).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, fontSize = 12.sp, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ExtendSheet(onDismiss: () -> Unit, onExtend: (Int) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    var customDays by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(kb.card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Estendi cura", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = kb.title)
            listOf(3 to "+3 giorni", 7 to "+7 giorni", 14 to "+14 giorni").forEach { (days, label) ->
                Button(
                    onClick = { onExtend(days) },
                    colors = ButtonDefaults.buttonColors(containerColor = PURPLE_DETAIL),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(label, color = Color.White) }
            }
            OutlinedTextField(
                value = customDays,
                onValueChange = { customDays = it },
                placeholder = { Text("Giorni personalizzati") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Annulla") }
                TextButton(onClick = {
                    val d = customDays.toIntOrNull()
                    if (d != null && d > 0) onExtend(d) else onDismiss()
                }) { Text("Conferma") }
            }
        }
    }
}
