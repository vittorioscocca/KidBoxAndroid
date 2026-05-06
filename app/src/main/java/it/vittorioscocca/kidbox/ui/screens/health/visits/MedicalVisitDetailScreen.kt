@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

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
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ai.AskAiButton
import it.vittorioscocca.kidbox.data.local.mapper.KBDoctorSpecialization
import it.vittorioscocca.kidbox.data.local.mapper.KBVisitStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_LONG_FMT = SimpleDateFormat("d MMMM yyyy · HH:mm", Locale.ITALIAN)
private val NEXT_VISIT_DATE_FMT = SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN)
private val ORANGE_DETAIL = Color(0xFFFF6B00)
private val DANGER_RED = Color(0xFFD32F2F)
/** Tint sezioni dettaglio visita (allineato a iOS). */
private val VISIT_DETAIL_TINT = Color(0xFF5599D9)
private val NEXT_APPT_GREEN = Color(0xFF2E7D32)

@Composable
fun MedicalVisitDetailScreen(
    familyId: String,
    childId: String,
    visitId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenTreatment: (treatmentId: String) -> Unit = {},
    onOpenExam: (examId: String) -> Unit = {},
    onOpenVisitAiChat: (
        subjectName: String,
        visitTitle: String,
        visitDate: String,
        diagnosis: String,
        notes: String,
    ) -> Unit = { _, _, _, _, _ -> },
    viewModel: MedicalVisitDetailViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isAiGloballyEnabled by viewModel.isAiGloballyEnabled.collectAsStateWithLifecycle()
    var showAiChat by remember { mutableStateOf(false) }

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

    LaunchedEffect(showAiChat) {
        if (!showAiChat) return@LaunchedEffect
        val v = state.visit
        if (v != null) {
            onOpenVisitAiChat(
                state.childName.ifBlank { "Profilo" },
                v.reason.ifBlank { "Visita medica" },
                DATE_LONG_FMT.format(Date(v.dateEpochMillis)),
                v.diagnosis.orEmpty(),
                v.notes.orEmpty(),
            )
        }
        showAiChat = false
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

                    // ── Esito (stesso ordine iOS) ───────────────────────────────
                    val hasDiagnosis = !visit.diagnosis.isNullOrBlank()
                    val hasRecommendations = !visit.recommendations.isNullOrBlank()
                    if (hasDiagnosis || hasRecommendations) {
                        DetailSectionCard(title = "Esito della Visita", titleAllCaps = false) {
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

                    // ── Farmaci programmati (cure collegate) ───────────────────
                    if (state.linkedTreatments.isNotEmpty()) {
                        FarmaciProgrammatiCard(
                            rows = state.linkedTreatments,
                            tint = VISIT_DETAIL_TINT,
                            kb = kb,
                            onOpenTreatment = onOpenTreatment,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Prescrizioni (al bisogno, terapie, esami) ───────────────
                    val hasPrescriptions =
                        state.asNeededDrugRows.isNotEmpty() ||
                            state.therapyTypeLabels.isNotEmpty() ||
                            state.linkedExams.isNotEmpty()
                    if (hasPrescriptions) {
                        PrescrizioniDetailCard(
                            asNeeded = state.asNeededDrugRows,
                            therapies = state.therapyTypeLabels,
                            exams = state.linkedExams,
                            tint = VISIT_DETAIL_TINT,
                            kb = kb,
                            onOpenExam = onOpenExam,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Allegati ──────────────────────────────────────────────
                    HealthAttachmentsCard(
                        attachments = state.attachments,
                        tintColor = VISIT_DETAIL_TINT,
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
                    Spacer(Modifier.height(12.dp))

                    // ── Prossimo appuntamento (dopo allegati, come iOS) ─────────
                    if (visit.nextVisitDateEpochMillis != null) {
                        NextAppointmentCard(
                            visit = visit,
                            kb = kb,
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Appunti ─────────────────────────────────────────────────
                    if (!visit.notes.isNullOrBlank()) {
                        DetailSectionCard(title = "Appunti", titleAllCaps = false) {
                            Text(visit.notes!!, fontSize = 14.sp, color = kb.title)
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Spacer(Modifier.height(24.dp))
                }

                // ── Sticky bottom action bar (stile iOS) ───────────────────────
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
                        colors = ButtonDefaults.buttonColors(containerColor = VISIT_DETAIL_TINT),
                    ) {
                        Text("Modifica", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { viewModel.requestDelete() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, DANGER_RED.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DANGER_RED),
                    ) {
                        Text("Elimina", fontWeight = FontWeight.SemiBold)
                    }
                }

                if (isAiGloballyEnabled) {
                    AskAiButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 96.dp),
                        isEnabled = true,
                        contentDescription = "Chiedi all'AI sulla visita",
                        onTap = { showAiChat = true },
                    )
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
private fun VisitDetailSectionHeader(title: String, icon: ImageVector, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
private fun FarmaciProgrammatiCard(
    rows: List<LinkedPrescriptionRow>,
    tint: Color,
    kb: KidBoxColorScheme,
    onOpenTreatment: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            VisitDetailSectionHeader(
                title = "Farmaci Programmati (${rows.size})",
                icon = Icons.Default.Medication,
                tint = tint,
            )
            Spacer(Modifier.height(10.dp))
            rows.forEach { row ->
                LinkedRow(
                    title = row.title,
                    subtitle = row.subtitle,
                    leadingIconTint = tint,
                    onClick = { onOpenTreatment(row.id) },
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun PrescrizioniDetailCard(
    asNeeded: List<LinkedPrescriptionRow>,
    therapies: List<String>,
    exams: List<LinkedPrescriptionRow>,
    tint: Color,
    kb: KidBoxColorScheme,
    onOpenExam: (String) -> Unit,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            VisitDetailSectionHeader(
                title = "Prescrizioni",
                icon = Icons.Default.LocalPharmacy,
                tint = tint,
            )
            Spacer(Modifier.height(12.dp))

            if (asNeeded.isNotEmpty()) {
                PrescriptionSubHeader(title = "Al Bisogno", icon = Icons.Default.LocalPharmacy, kb = kb)
                Spacer(Modifier.height(8.dp))
                asNeeded.forEach { drug ->
                    AsNeededDrugRow(drug = drug, tint = tint, kb = kb)
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (therapies.isNotEmpty()) {
                if (asNeeded.isNotEmpty()) {
                    HorizontalDivider(color = kb.subtitle.copy(alpha = 0.12f))
                    Spacer(Modifier.height(12.dp))
                }
                PrescriptionSubHeader(title = "Terapie", icon = Icons.Default.DirectionsWalk, kb = kb)
                Spacer(Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    therapies.forEach { label ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = tint.copy(alpha = 0.10f),
                        ) {
                            Text(
                                label,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                fontSize = 12.sp,
                                color = tint,
                            )
                        }
                    }
                }
            }

            if (exams.isNotEmpty()) {
                if (asNeeded.isNotEmpty() || therapies.isNotEmpty()) {
                    HorizontalDivider(color = kb.subtitle.copy(alpha = 0.12f))
                    Spacer(Modifier.height(12.dp))
                }
                PrescriptionSubHeader(
                    title = "Esami Prescritti (${exams.size})",
                    icon = Icons.Default.Science,
                    kb = kb,
                )
                Spacer(Modifier.height(8.dp))
                exams.forEach { row ->
                    LinkedExamRow(
                        row = row,
                        tint = tint,
                        onClick = { onOpenExam(row.id) },
                    )
                    Spacer(Modifier.height(8.dp))
                }
            }
        }
    }
}

@Composable
private fun PrescriptionSubHeader(title: String, icon: ImageVector, kb: KidBoxColorScheme) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(6.dp))
        Text(title, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = kb.subtitle)
    }
}

@Composable
private fun AsNeededDrugRow(drug: LinkedPrescriptionRow, tint: Color, kb: KidBoxColorScheme) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(kb.subtitle.copy(alpha = 0.08f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(tint.copy(alpha = 0.15f)),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(drug.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
            if (!drug.subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(drug.subtitle, fontSize = 12.sp, color = kb.subtitle, maxLines = 2)
            }
        }
    }
}

@Composable
private fun NextAppointmentCard(visit: KBMedicalVisit, kb: KidBoxColorScheme) {
    val nextMs = visit.nextVisitDateEpochMillis ?: return
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(NEXT_APPT_GREEN.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = NEXT_APPT_GREEN,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("Prossimo Appuntamento", fontSize = 12.sp, color = kb.subtitle)
                Spacer(Modifier.height(4.dp))
                Text(
                    NEXT_VISIT_DATE_FMT.format(Date(nextMs)),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                )
                visit.nextVisitReason?.takeIf { it.isNotBlank() }?.let { reason ->
                    Spacer(Modifier.height(4.dp))
                    Text(reason, fontSize = 12.sp, color = kb.subtitle)
                }
                if (visit.nextVisitReminderOn) {
                    Spacer(Modifier.height(8.dp))
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
        }
    }
}

@Composable
private fun DetailSectionCard(
    title: String,
    titleAllCaps: Boolean = true,
    content: @Composable () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (titleAllCaps) title.uppercase() else title,
                fontWeight = FontWeight.Bold,
                fontSize = if (titleAllCaps) 11.sp else 14.sp,
                color = if (titleAllCaps) kb.subtitle else kb.title,
                letterSpacing = if (titleAllCaps) 0.8.sp else 0.sp,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun LinkedRow(
    title: String,
    subtitle: String?,
    leadingIconTint: Color = VISIT_DETAIL_TINT,
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
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(leadingIconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Medication,
                contentDescription = null,
                tint = leadingIconTint,
                modifier = Modifier.size(18.dp),
            )
        }
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
private fun LinkedExamRow(
    row: LinkedPrescriptionRow,
    tint: Color,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val iconTint = if (row.examUrgent) Color(0xFFE53935) else tint
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(kb.subtitle.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Science,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(row.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
                if (row.examUrgent) {
                    Spacer(Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = Color(0xFFE53935),
                    ) {
                        Text(
                            "Urgente",
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
            }
            if (!row.subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(row.subtitle, fontSize = 12.sp, color = kb.subtitle)
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
