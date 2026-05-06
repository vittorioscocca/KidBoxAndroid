package it.vittorioscocca.kidbox.ui.screens.health.exams

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ai.AskAiButton
import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.ui.screens.health.common.PrescribingVisitLinkCard
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_LONG_EXAM = SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN)
private val DATE_CREATED_EXAM = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private val TEAL_DETAIL = Color(0xFF40A6BF)
private val ORANGE_STATUS = Color(0xFFFF9800)
private val MINT_RESULT = Color(0xFF66BB6A)
private val DANGER_EXAM = Color(0xFFD32F2F)
private val VISIT_PRESCRIBING_TINT = Color(0xFF5996D9)

@Composable
fun MedicalExamDetailScreen(
    familyId: String,
    childId: String,
    examId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onOpenVisit: (visitId: String) -> Unit = {},
    onOpenExamAiChat: (
        subjectName: String,
        examName: String,
        examStatus: String,
        deadline: String,
        preparation: String,
        resultText: String,
        notes: String,
        attachmentsSummary: String,
    ) -> Unit = { _, _, _, _, _, _, _, _ -> },
    viewModel: MedicalExamDetailViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isAiGloballyEnabled by viewModel.isAiGloballyEnabled.collectAsStateWithLifecycle()
    var showAiChat by remember { mutableStateOf(false) }

    LaunchedEffect(familyId, childId, examId) { viewModel.bind(familyId, childId, examId) }
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
        val ex = state.exam
        if (ex != null) {
            val status = KBExamStatus.entries.firstOrNull { it.rawValue == ex.statusRaw }
                ?: KBExamStatus.PENDING
            val deadlineStr = ex.deadlineEpochMillis?.let { DATE_LONG_EXAM.format(Date(it)) } ?: "—"
            val resultForAi = buildExamResultSummaryForAi(ex)
            val attachN = state.attachments.size
            val attachStr = if (attachN == 0) "Nessun allegato" else "$attachN file allegati"
            onOpenExamAiChat(
                state.childName.ifBlank { "Profilo" },
                ex.name.ifBlank { "Esame" },
                status.rawValue,
                deadlineStr,
                ex.preparation.orEmpty().ifBlank { "—" },
                resultForAi,
                ex.notes.orEmpty().ifBlank { "—" },
                attachStr,
            )
        }
        showAiChat = false
    }

    val cameraFile = remember { File(File(context.cacheDir, "health-camera").apply { mkdirs() }, "exam_camera_tmp.jpg") }
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
            state.exam == null -> {
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
                    Text(state.error ?: "Esame non trovato.", color = kb.subtitle, fontSize = 16.sp)
                }
            }
            else -> {
                val exam = state.exam!!
                val status = KBExamStatus.entries.firstOrNull { it.rawValue == exam.statusRaw }
                    ?: KBExamStatus.PENDING

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = 88.dp)
                        .padding(horizontal = 18.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        KidBoxHeaderCircleButton(
                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Indietro",
                            onClick = onBack,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Esami",
                        fontWeight = FontWeight.Bold,
                        fontSize = 28.sp,
                        color = kb.title,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(12.dp))

                    MedicalExamHeaderCard(
                        exam = exam,
                        status = status,
                        onToggleReminder = { viewModel.toggleExamReminder() },
                    )
                    Spacer(Modifier.height(12.dp))

                    // ── Luogo card ─────────────────────────────────────────────
                    if (!exam.location.isNullOrBlank()) {
                        ExamDetailCard(title = "Luogo") { Text(exam.location, fontSize = 14.sp, color = kb.title) }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Preparazione card ──────────────────────────────────────
                    if (!exam.preparation.isNullOrBlank()) {
                        ExamDetailCard(title = "Preparazione") { Text(exam.preparation, fontSize = 14.sp, color = kb.title) }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Note card ──────────────────────────────────────────────
                    if (!exam.notes.isNullOrBlank()) {
                        ExamDetailCard(title = "Note") { Text(exam.notes, fontSize = 14.sp, color = kb.title) }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Risultato card ─────────────────────────────────────────
                    val hasResult = !exam.resultText.isNullOrBlank() || exam.resultDateEpochMillis != null
                    if (hasResult) {
                        ExamDetailCard(title = "Risultato") {
                            exam.resultDateEpochMillis?.let { ms ->
                                Text(DATE_LONG_EXAM.format(Date(ms)), fontSize = 12.sp, color = kb.subtitle)
                                Spacer(Modifier.height(4.dp))
                            }
                            if (!exam.resultText.isNullOrBlank()) {
                                Text(exam.resultText!!, fontSize = 14.sp, color = kb.title)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    state.prescribingVisitSummary?.let { pv ->
                        PrescribingVisitLinkCard(
                            summary = pv,
                            tint = VISIT_PRESCRIBING_TINT,
                            onClick = { onOpenVisit(pv.visitId) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Allegati card ──────────────────────────────────────────
                    HealthAttachmentsCard(
                        attachments = state.attachments,
                        tintColor = TEAL_DETAIL,
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
                        .background(kb.background)
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = TEAL_DETAIL),
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Modifica", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                    OutlinedButton(
                        onClick = { viewModel.requestDelete() },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.5.dp, DANGER_EXAM.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = DANGER_EXAM),
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, tint = DANGER_EXAM, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Elimina", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    }
                }

                if (isAiGloballyEnabled) {
                    val examLabel = exam.name.ifBlank { "esame" }
                    AskAiButton(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 20.dp, bottom = 96.dp),
                        isEnabled = true,
                        contentDescription = "Chiedi all'AI sull'analisi $examLabel",
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

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Eliminare l'esame?") },
            text = { Text("L'azione non può essere annullata.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Elimina", color = DANGER_EXAM)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Annulla") }
            },
        )
    }
}

// ── Sub-composables ────────────────────────────────────────────────────────────

private fun buildExamResultSummaryForAi(exam: KBMedicalExam): String {
    val parts = mutableListOf<String>()
    exam.resultDateEpochMillis?.let { parts.add(DATE_LONG_EXAM.format(Date(it))) }
    exam.resultText?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
        parts.add(HealthAiDocumentText.prepareExtractedTextForAi(raw))
    }
    return parts.joinToString(" · ").ifBlank { "—" }
}

private fun examDetailStatusIcon(status: KBExamStatus): ImageVector = when (status) {
    KBExamStatus.PENDING -> Icons.Default.Schedule
    KBExamStatus.BOOKED -> Icons.Default.EventAvailable
    KBExamStatus.DONE -> Icons.Default.CheckCircle
    KBExamStatus.RESULT_IN -> Icons.Default.Description
}

private fun examDetailStatusTint(status: KBExamStatus): Color = when (status) {
    KBExamStatus.PENDING -> ORANGE_STATUS
    KBExamStatus.BOOKED -> TEAL_DETAIL
    KBExamStatus.DONE -> Color(0xFF43A047)
    KBExamStatus.RESULT_IN -> MINT_RESULT
}

/** Card principale come iOS [PediatricExamDetailView.headerCard]. */
@Composable
private fun MedicalExamHeaderCard(
    exam: KBMedicalExam,
    status: KBExamStatus,
    onToggleReminder: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val tint = examDetailStatusTint(status)
    val deadlineMs = exam.deadlineEpochMillis
    val now = System.currentTimeMillis()
    val overdue = deadlineMs != null && deadlineMs < now &&
        (status == KBExamStatus.PENDING || status == KBExamStatus.BOOKED)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = examDetailStatusIcon(status),
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            exam.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = kb.title,
                            modifier = Modifier.weight(1f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (exam.isUrgent) {
                            Spacer(Modifier.width(8.dp))
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = Color(0xFFD32F2F),
                            ) {
                                Text(
                                    "Urgente",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            examDetailStatusIcon(status),
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(14.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(status.rawValue, fontSize = 13.sp, color = tint, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Creato: ${DATE_CREATED_EXAM.format(Date(exam.createdAtEpochMillis))}",
                        fontSize = 12.sp,
                        color = kb.subtitle,
                    )
                }
            }

            if (deadlineMs != null) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = kb.subtitle.copy(alpha = 0.12f))
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        tint = if (overdue) DANGER_EXAM else TEAL_DETAIL,
                        modifier = Modifier.size(22.dp),
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Da eseguire entro", fontSize = 12.sp, color = kb.subtitle)
                        Text(
                            DATE_LONG_EXAM.format(Date(deadlineMs)),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (overdue) DANGER_EXAM else kb.title,
                        )
                        if (overdue) {
                            Spacer(Modifier.height(2.dp))
                            Text("Scaduto", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = DANGER_EXAM)
                        }
                    }
                    IconButton(onClick = onToggleReminder) {
                        Icon(
                            imageVector = if (exam.reminderOn) Icons.Default.Notifications else Icons.Outlined.Notifications,
                            contentDescription = if (exam.reminderOn) "Rimuovi promemoria" else "Aggiungi promemoria",
                            tint = if (exam.reminderOn) Color(0xFFFF9800) else kb.subtitle,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
            }

            if (exam.syncStateRaw == 1) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = kb.subtitle.copy(alpha = 0.12f))
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text("In sincronizzazione…", fontSize = 12.sp, color = kb.subtitle)
                }
            }
        }
    }
}

@Composable
private fun ExamDetailCard(title: String, content: @Composable () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = kb.subtitle,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}
