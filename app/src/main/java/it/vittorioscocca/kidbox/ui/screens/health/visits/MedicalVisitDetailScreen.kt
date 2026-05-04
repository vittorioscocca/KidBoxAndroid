package it.vittorioscocca.kidbox.ui.screens.health.visits

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import androidx.compose.material3.MaterialTheme
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.mapper.KBDoctorSpecialization
import it.vittorioscocca.kidbox.data.local.mapper.KBVisitStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_LONG_FMT = SimpleDateFormat("d MMMM yyyy · HH:mm", Locale.ITALIAN)
private val ORANGE_DETAIL = Color(0xFFFF6B00)
private val DANGER_RED = Color(0xFFD32F2F)

@Composable
fun MedicalVisitDetailScreen(
    familyId: String,
    childId: String,
    visitId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTreatment: (treatmentId: String) -> Unit = {},
    onOpenExam: (examId: String) -> Unit = {},
    viewModel: MedicalVisitDetailViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, visitId) { viewModel.bind(familyId, childId, visitId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }
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

    val cameraFile = remember { File(File(context.cacheDir, "health-camera").apply { mkdirs() }, "visit_camera_tmp.jpg") }
    val cameraUri = remember(cameraFile) { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile) }

    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) viewModel.uploadAttachment(cameraUri)
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            takePictureLauncher.launch(cameraUri)
        } else {
            Toast.makeText(context, "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
        }
    }
    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadAttachment(it) }
    }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.uploadAttachment(it) }
    }
    var showKidBoxDocPicker by remember { mutableStateOf(false) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.visit == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(18.dp),
                ) {
                    KidBoxHeaderCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        onClick = onBack,
                    )
                    Spacer(Modifier.height(40.dp))
                    Text(state.error ?: "Visita non trovata.", color = kb.subtitle, fontSize = 16.sp)
                }
            }
            else -> {
                val visit = state.visit!!
                val status = KBVisitStatus.fromRaw(visit.visitStatusRaw)

                // ── Scrollable content ─────────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = 88.dp) // reserve space for sticky bottom bar
                        .padding(horizontal = 18.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(8.dp))
                    // Top bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        KidBoxHeaderCircleButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro",
                            onClick = onBack,
                        )
                        Spacer(Modifier.weight(1f))
                        KidBoxHeaderCircleButton(
                            icon = Icons.Default.Edit,
                            contentDescription = "Modifica",
                            onClick = onEdit,
                            iconTint = ORANGE_DETAIL,
                        )
                        Spacer(Modifier.width(8.dp))
                        KidBoxHeaderCircleButton(
                            icon = Icons.Default.Delete,
                            contentDescription = "Elimina",
                            onClick = { viewModel.requestDelete() },
                            iconTint = Color(0xFFE53935),
                        )
                    }
                    Spacer(Modifier.height(8.dp))

                    // ── Header card ────────────────────────────────────────────
                    Card(
                        colors = CardDefaults.cardColors(containerColor = kb.card),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val statusColor = statusColor(status)
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(statusColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.MedicalServices,
                                        contentDescription = null,
                                        tint = statusColor,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        visit.reason.ifBlank { "Visita medica" },
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = kb.title,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    VisitStatusBadge(status)
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            Text(
                                DATE_LONG_FMT.format(Date(visit.dateEpochMillis)),
                                fontSize = 13.sp,
                                color = kb.subtitle,
                            )

                            val doctorLabel = buildDoctorLabel(visit)
                            if (doctorLabel.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(doctorLabel, fontSize = 13.sp, color = kb.subtitle)
                            }

                            if (visit.reminderOn || visit.nextVisitReminderOn) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = ORANGE_DETAIL,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Promemoria attivo", fontSize = 12.sp, color = ORANGE_DETAIL)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // ── Esito card ─────────────────────────────────────────────
                    val hasDiagnosis = !visit.diagnosis.isNullOrBlank()
                    val hasRecommendations = !visit.recommendations.isNullOrBlank()
                    if (hasDiagnosis || hasRecommendations) {
                        DetailSectionCard(title = "Esito") {
                            if (hasDiagnosis) {
                                DetailBlock("Diagnosi", visit.diagnosis!!)
                            }
                            if (hasRecommendations) {
                                if (hasDiagnosis) Spacer(Modifier.height(8.dp))
                                DetailBlock("Raccomandazioni", visit.recommendations!!)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Note card ──────────────────────────────────────────────
                    if (!visit.notes.isNullOrBlank()) {
                        DetailSectionCard(title = "Appunti") {
                            Text(visit.notes!!, fontSize = 14.sp, color = kb.title)
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Prossima visita card ────────────────────────────────────
                    if (visit.nextVisitDateEpochMillis != null) {
                        DetailSectionCard(title = "Prossima visita") {
                            Text(
                                DATE_LONG_FMT.format(Date(visit.nextVisitDateEpochMillis)),
                                fontSize = 13.sp,
                                color = kb.subtitle,
                            )
                            if (!visit.nextVisitReason.isNullOrBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(visit.nextVisitReason!!, fontSize = 14.sp, color = kb.title)
                            }
                            if (visit.nextVisitReminderOn) {
                                Spacer(Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = ORANGE_DETAIL,
                                        modifier = Modifier.size(14.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Promemoria attivo", fontSize = 12.sp, color = ORANGE_DETAIL)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    if (state.linkedTreatments.isNotEmpty() || state.linkedExams.isNotEmpty()) {
                        DetailSectionCard(title = "Prescritto in questa visita") {
                            if (state.linkedTreatments.isNotEmpty()) {
                                Text("Cure", fontSize = 12.sp, color = kb.subtitle, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                state.linkedTreatments.forEach { row ->
                                    LinkedRow(
                                        title = row.title,
                                        subtitle = row.subtitle,
                                        leadingIcon = { Icon(Icons.Default.Medication, contentDescription = null, tint = ORANGE_DETAIL, modifier = Modifier.size(22.dp)) },
                                        onClick = { onOpenTreatment(row.id) },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                            if (state.linkedExams.isNotEmpty()) {
                                if (state.linkedTreatments.isNotEmpty()) Spacer(Modifier.height(8.dp))
                                Text("Analisi / esami", fontSize = 12.sp, color = kb.subtitle, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                state.linkedExams.forEach { row ->
                                    LinkedRow(
                                        title = row.title,
                                        subtitle = row.subtitle,
                                        leadingIcon = { Icon(Icons.Default.Science, contentDescription = null, tint = Color(0xFF40A6BF), modifier = Modifier.size(22.dp)) },
                                        onClick = { onOpenExam(row.id) },
                                    )
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Allegati card ──────────────────────────────────────────
                    HealthAttachmentsCard(
                        attachments = state.attachments,
                        tintColor = ORANGE_DETAIL,
                        isUploading = state.isUploading,
                        onPickFile = { pickFileLauncher.launch(arrayOf("*/*")) },
                        onPickPhoto = { pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                        onTakePhoto = {
                            when {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                                    PackageManager.PERMISSION_GRANTED -> takePictureLauncher.launch(cameraUri)
                                else -> cameraPermLauncher.launch(Manifest.permission.CAMERA)
                            }
                        },
                        onOpenAttachment = { viewModel.openAttachment(it) },
                        onDeleteAttachment = { viewModel.deleteAttachment(it) },
                        onPickFromKidBoxDocuments = { showKidBoxDocPicker = true },
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // ── Sticky bottom action bar ───────────────────────────────────
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ORANGE_DETAIL),
                    ) {
                        Text("Modifica", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { viewModel.requestDelete() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DANGER_RED),
                    ) {
                        Text("Elimina", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (showKidBoxDocPicker) {
        KidBoxDocumentPickerSheet(
            familyId = familyId,
            onDismiss = { showKidBoxDocPicker = false },
            onPickedUri = { viewModel.uploadAttachment(it) },
        )
    }

    // ── Delete confirmation dialog ─────────────────────────────────────────────
    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Eliminare la visita?") },
            text = { Text("L'azione non può essere annullata.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Elimina", color = DANGER_RED)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Annulla") }
            },
        )
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

@Composable
private fun DetailSectionCard(title: String, content: @Composable () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title.uppercase(), fontWeight = FontWeight.Bold, fontSize = 11.sp,
                color = kb.subtitle, letterSpacing = 0.8.sp)
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LinkedRow(
    title: String,
    subtitle: String?,
    leadingIcon: @Composable () -> Unit,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(kb.subtitle.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leadingIcon()
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(subtitle, fontSize = 12.sp, color = kb.subtitle)
            }
        }
        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = kb.subtitle.copy(alpha = 0.7f), modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun DetailBlock(label: String, value: String) {
    val kb = MaterialTheme.kidBoxColors
    Text(label, fontSize = 12.sp, color = kb.subtitle, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(2.dp))
    Text(value, fontSize = 14.sp, color = kb.title)
}

private fun buildDoctorLabel(visit: KBMedicalVisit): String = buildString {
    visit.doctorName?.takeIf { it.isNotBlank() }?.let { append(it) }
    val spec = KBDoctorSpecialization.fromRaw(visit.doctorSpecializationRaw)
    if (spec != null) {
        if (isNotEmpty()) append(" · ")
        append(spec.rawValue)
    }
}

private fun statusColor(status: KBVisitStatus): Color = when (status) {
    KBVisitStatus.PENDING -> Color(0xFF9E9E9E)
    KBVisitStatus.BOOKED -> Color(0xFF1565C0)
    KBVisitStatus.COMPLETED -> Color(0xFF2E7D32)
    KBVisitStatus.RESULT_AVAILABLE -> Color(0xFF6A1B9A)
    KBVisitStatus.UNKNOWN_STATUS -> Color(0xFF9E9E9E)
}
