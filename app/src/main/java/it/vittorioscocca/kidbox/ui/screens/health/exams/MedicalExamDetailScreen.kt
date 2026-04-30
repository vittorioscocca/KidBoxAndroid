package it.vittorioscocca.kidbox.ui.screens.health.exams

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.FileProvider
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import java.io.File
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_LONG_EXAM = SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN)
private val TEAL_DETAIL = Color(0xFF40A6BF)
private val ORANGE_DETAIL_EXAM = Color(0xFFFF6B00)
private val DANGER_EXAM = Color(0xFFD32F2F)

@Composable
fun MedicalExamDetailScreen(
    familyId: String,
    childId: String,
    examId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: MedicalExamDetailViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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

    val cameraFile = remember { File(File(context.cacheDir, "health-camera").apply { mkdirs() }, "exam_camera_tmp.jpg") }
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
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = kb.title)
                    }
                    Spacer(Modifier.height(40.dp))
                    Text(state.error ?: "Esame non trovato.", color = kb.subtitle, fontSize = 16.sp)
                }
            }
            else -> {
                val exam = state.exam!!
                val status = KBExamStatus.values().firstOrNull { it.rawValue == exam.statusRaw }
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
                    // Top bar
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = kb.title)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifica", tint = ORANGE_DETAIL_EXAM)
                        }
                        IconButton(onClick = { viewModel.requestDelete() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = kb.subtitle)
                        }
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
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(TEAL_DETAIL.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Science,
                                        contentDescription = null,
                                        tint = TEAL_DETAIL,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        exam.name,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = kb.title,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    ExamStatusBadge(status)
                                    if (exam.isUrgent) {
                                        Spacer(Modifier.height(4.dp))
                                        UrgentChip()
                                    }
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                exam.deadlineEpochMillis?.let { DATE_LONG_EXAM.format(Date(it)) }
                                    ?: "Senza scadenza",
                                fontSize = 13.sp,
                                color = kb.subtitle,
                            )
                            if (exam.reminderOn) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = ORANGE_DETAIL_EXAM,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Promemoria attivo", fontSize = 12.sp, color = ORANGE_DETAIL_EXAM)
                                }
                            }
                        }
                    }
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

                    // ── Allegati card ──────────────────────────────────────────
                    HealthAttachmentsCard(
                        attachments = state.attachments,
                        tintColor = TEAL_DETAIL,
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
                        colors = ButtonDefaults.buttonColors(containerColor = ORANGE_DETAIL_EXAM),
                    ) {
                        Text("Modifica", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { viewModel.requestDelete() },
                        modifier = Modifier.weight(1f).height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DANGER_EXAM),
                    ) {
                        Text("Elimina", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
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

@Composable
private fun ExamDetailCard(title: String, content: @Composable () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
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
