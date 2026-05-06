@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health.exams

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FMT_EXAM_FORM = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private val ORANGE_EXAM_FORM = Color(0xFFFF6B00)

@Composable
fun MedicalExamFormScreen(
    familyId: String,
    childId: String,
    examId: String?,
    onBack: () -> Unit,
    prescribingVisitId: String? = null,
    bindNonce: Int = 0,
    onSaved: (examId: String) -> Unit = { _ -> onBack() },
    viewModel: MedicalExamFormViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, examId, prescribingVisitId, bindNonce) {
        viewModel.bind(familyId, childId, examId, prescribingVisitId, bindNonce)
    }

    LaunchedEffect(state.saved, state.examId) {
        if (state.saved) {
            Toast.makeText(context, "Esame salvato", Toast.LENGTH_SHORT).show()
            val id = state.examId
            viewModel.consumeSaved()
            onSaved(id)
        }
    }
    LaunchedEffect(state.saveError) {
        state.saveError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }
    LaunchedEffect(state.uploadError) {
        state.uploadError?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
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

    var showDeadlinePicker by remember { mutableStateOf(false) }
    var showResultDatePicker by remember { mutableStateOf(false) }
    var statusMenuOpen by remember { mutableStateOf(false) }
    val cameraFile = remember { File(File(context.cacheDir, "health-camera").apply { mkdirs() }, "exam_form_camera_tmp.jpg") }
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

    var currentStep by remember { mutableStateOf(0) }
    val totalSteps = 4
    val stepTitle = when (currentStep) {
        0 -> "Dettagli esame"
        1 -> "Stato e promemoria"
        2 -> "Referto"
        else -> "Riepilogo"
    }
    val stepSubtitle = when (currentStep) {
        0 -> "Nome, urgenza, scadenza e luogo"
        1 -> "Stato, alert e note preparazione"
        2 -> "Inserisci risultato se disponibile"
        else -> "Controlla tutto prima di salvare"
    }
    val canAdvance = when (currentStep) {
        0 -> state.name.isNotBlank()
        else -> true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ── Top bar ───────────────────────────────────────────────────────
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                KidBoxHeaderCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Annulla", color = kb.title) }
            }
            Text(
                if (examId == null) "Nuovo esame" else "Modifica esame",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
            )
            if (state.childName.isNotBlank()) {
                Text(state.childName, fontSize = 14.sp, color = kb.subtitle)
            }
            Spacer(Modifier.height(10.dp))
            Text("Step ${currentStep + 1} di $totalSteps · $stepTitle", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = kb.subtitle)
            Text(stepSubtitle, fontSize = 12.sp, color = kb.subtitle)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
                repeat(totalSteps) { index ->
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                if (index <= currentStep) ORANGE_EXAM_FORM else kb.subtitle.copy(alpha = 0.25f),
                                RoundedCornerShape(100.dp),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            if (currentStep == 0) {
            // ── 1. Nome esame ─────────────────────────────────────────────────
            ExamSectionLabel("Nome esame")
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                placeholder = { Text("es. Esame del sangue") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // ── 2. Urgente ────────────────────────────────────────────────────
            ExamSectionLabel("Urgente")
            ExamSwitchRow(label = "Esame urgente", checked = state.isUrgent, onChecked = viewModel::setIsUrgent)
            Spacer(Modifier.height(16.dp))

            // ── 3. Scadenza ───────────────────────────────────────────────────
            ExamSectionLabel("Scadenza")
            ExamSwitchRow(label = "Imposta scadenza", checked = state.hasDeadline, onChecked = viewModel::setHasDeadline)
            if (state.hasDeadline) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = DATE_FMT_EXAM_FORM.format(Date(state.deadlineEpochMillis)),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { showDeadlinePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Scegli data", tint = ORANGE_EXAM_FORM)
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))

            }

            if (currentStep == 1) {
            // ── 4. Stato ──────────────────────────────────────────────────────
            ExamSectionLabel("Stato")
            ExposedDropdownMenuBox(
                expanded = statusMenuOpen,
                onExpandedChange = { statusMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = state.status.rawValue,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor().fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusMenuOpen) },
                )
                ExposedDropdownMenu(
                    expanded = statusMenuOpen,
                    onDismissRequest = { statusMenuOpen = false },
                ) {
                    KBExamStatus.values().forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.rawValue) },
                            onClick = { viewModel.setStatus(s); statusMenuOpen = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── 5. Promemoria ─────────────────────────────────────────────────
            ExamSectionLabel("Promemoria")
            ExamSwitchRow(
                label = "Avvisami il giorno prima della scadenza",
                checked = state.reminderOn,
                onChecked = { if (state.hasDeadline) viewModel.setReminderOn(it) },
                enabled = state.hasDeadline,
            )
            if (!state.hasDeadline) {
                Text(
                    "Imposta una scadenza per attivare il promemoria.",
                    fontSize = 11.sp,
                    color = kb.subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            // ── 6. Luogo ──────────────────────────────────────────────────────
            ExamSectionLabel("Luogo")
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::setLocation,
                placeholder = { Text("es. Laboratorio Centrale") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // ── 7. Preparazione ───────────────────────────────────────────────
            ExamSectionLabel("Preparazione")
            OutlinedTextField(
                value = state.preparation,
                onValueChange = viewModel::setPreparation,
                placeholder = { Text("es. A digiuno da 8 ore") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
            )
            Spacer(Modifier.height(16.dp))

            // ── 8. Note ───────────────────────────────────────────────────────
            ExamSectionLabel("Note")
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                placeholder = { Text("Note aggiuntive") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
            )
            Spacer(Modifier.height(16.dp))

            }

            if (currentStep == 2) {
            // ── 9. Risultato ──────────────────────────────────────────────────
            ExamSectionLabel("Risultato")
            ExamSwitchRow(
                label = "Risultato disponibile",
                checked = state.hasResult,
                onChecked = viewModel::setHasResult,
            )
            if (state.hasResult) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.resultText,
                    onValueChange = viewModel::setResultText,
                    placeholder = { Text("Trascrivi il referto qui") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 5,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = DATE_FMT_EXAM_FORM.format(Date(state.resultDateEpochMillis)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data referto") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { showResultDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Scegli data", tint = ORANGE_EXAM_FORM)
                        }
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            ExamSectionLabel("Referto allegato")
            Text(
                "Aggiungi qui PDF/foto del referto.",
                fontSize = 12.sp,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(8.dp))
            HealthAttachmentsCard(
                attachments = state.attachments,
                tintColor = ORANGE_EXAM_FORM,
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
            }

            if (currentStep == 3) {
                ExamSectionLabel("Riepilogo")
                OutlinedTextField(value = state.name, onValueChange = {}, readOnly = true, label = { Text("Nome esame") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = state.status.rawValue, onValueChange = {}, readOnly = true, label = { Text("Stato") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (state.hasDeadline) {
                    OutlinedTextField(value = DATE_FMT_EXAM_FORM.format(Date(state.deadlineEpochMillis)), onValueChange = {}, readOnly = true, label = { Text("Scadenza") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                if (state.hasResult) {
                    OutlinedTextField(value = state.resultText, onValueChange = {}, readOnly = true, label = { Text("Risultato") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
                }
                Spacer(Modifier.height(12.dp))
                ExamSectionLabel("Referto allegato")
                OutlinedTextField(
                    value = if (state.attachments.isEmpty()) "Nessun allegato" else "${state.attachments.size} allegato/i",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Totale allegati") },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.attachments.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    state.attachments.forEach { doc ->
                        OutlinedTextField(
                            value = doc.title.ifBlank { doc.fileName },
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Allegato") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (currentStep > 0) {
                    Button(
                        onClick = { currentStep -= 1 },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = kb.card),
                    ) { Text("Indietro", color = kb.title) }
                }
                if (currentStep < totalSteps - 1) {
                    Button(
                        onClick = { currentStep += 1 },
                        enabled = canAdvance,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ORANGE_EXAM_FORM),
                    ) { Text("Avanti", color = Color.White, fontWeight = FontWeight.SemiBold) }
                } else {
                    Button(
                        onClick = { viewModel.save() },
                        enabled = !state.isSaving && state.canSave,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ORANGE_EXAM_FORM),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Salvataggio...", color = Color.White, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Salva esame", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }

    if (showKidBoxDocPicker) {
        KidBoxDocumentPickerSheet(
            familyId = familyId,
            onDismiss = { showKidBoxDocPicker = false },
            onPickedUri = { viewModel.uploadAttachment(it) },
        )
    }

    if (showDeadlinePicker) {
        ExamDatePickerDialog(
            initialMillis = state.deadlineEpochMillis,
            onDismiss = { showDeadlinePicker = false },
            onConfirm = { ms -> viewModel.setDeadlineEpochMillis(ms); showDeadlinePicker = false },
        )
    }
    if (showResultDatePicker) {
        ExamDatePickerDialog(
            initialMillis = state.resultDateEpochMillis,
            onDismiss = { showResultDatePicker = false },
            onConfirm = { ms -> viewModel.setResultDateEpochMillis(ms); showResultDatePicker = false },
        )
    }
}

// ── Composable helpers ────────────────────────────────────────────────────────

@Composable
private fun ExamSectionLabel(text: String) {
    Text(
        text.uppercase(),
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = MaterialTheme.kidBoxColors.subtitle,
        letterSpacing = 0.8.sp,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun ExamSwitchRow(
    label: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = if (enabled) kb.title else kb.subtitle,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = ORANGE_EXAM_FORM),
        )
    }
}

@Composable
private fun ExamDatePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    ) {
        DatePicker(state = pickerState)
    }
}
