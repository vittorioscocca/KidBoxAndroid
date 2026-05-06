@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class,
)

package it.vittorioscocca.kidbox.ui.screens.health.visits

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalPharmacy
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.RowScope
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import it.vittorioscocca.kidbox.data.local.mapper.KBDoctorSpecialization
import it.vittorioscocca.kidbox.data.local.mapper.KBVisitStatus
import it.vittorioscocca.kidbox.domain.health.DrugCatalog
import it.vittorioscocca.kidbox.domain.health.DrugCatalogEntry
import it.vittorioscocca.kidbox.domain.model.KBAsNeededDrug
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import it.vittorioscocca.kidbox.domain.model.KBTherapyType
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentSourcePickerSheet
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.screens.health.exams.MedicalExamFormScreen
import it.vittorioscocca.kidbox.ui.screens.health.treatments.MedicalTreatmentFormScreen
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID

private val VISIT_TINT = Color(0xFF5599D9)
/** Sfondo contenitori testo «Esito visita», allineato a iOS grouped secondary. */
private val VISIT_OUTCOME_FIELD_SURFACE = Color(0xFFF2F2F7)
/** Sheet «Farmaco al bisogno» (sfondo iOS-style). */
private val AS_NEEDED_SHEET_BG = Color(0xFFF2F2F7)
/** Traccia segmenti unità dosaggio. */
private val AS_NEEDED_UNIT_TRACK = Color(0xFFE5E5EA)
private val DATE_COMPACT = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private val TIME_COMPACT = SimpleDateFormat("HH:mm", Locale.ITALIAN)
private val SUMMARY_DT = SimpleDateFormat("d MMMM yyyy 'alle ore' HH:mm", Locale.ITALIAN)
private val NEXT_DATE_FMT = SimpleDateFormat("EEEE d MMMM yyyy", Locale.ITALIAN)
private val AS_NEEDED_UNITS = listOf("ml", "mg", "g", "cp", "bust")

@Composable
fun MedicalVisitFormScreen(
    familyId: String,
    childId: String,
    visitId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    viewModel: MedicalVisitFormViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, visitId) { viewModel.bind(familyId, childId, visitId) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(context, "Visita salvata", Toast.LENGTH_SHORT).show()
            viewModel.consumeSaved()
            onSaved()
        }
    }
    LaunchedEffect(state.saveError) {
        state.saveError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    var showTreatmentForm by remember { mutableStateOf(false) }
    var showExamForm by remember { mutableStateOf(false) }
    var examBindNonce by remember { mutableIntStateOf(0) }

    var showMainDatePicker by remember { mutableStateOf(false) }
    var showMainTimePicker by remember { mutableStateOf(false) }
    var pendingMainDateMillis by remember { mutableStateOf(0L) }

    var showNextDatePicker by remember { mutableStateOf(false) }
    var pendingNextDateMillis by remember { mutableStateOf(0L) }

    var showAsNeededSheet by remember { mutableStateOf(false) }
    var editingAsNeeded by remember { mutableStateOf<KBAsNeededDrug?>(null) }

    var showAttachSheet by remember { mutableStateOf(false) }
    var showKidBoxDocPicker by remember { mutableStateOf(false) }
    val cameraFile = remember {
        File(File(context.cacheDir, "health-camera").apply { mkdirs() }, "visit_form_camera_tmp.jpg")
    }
    val cameraUri = remember(cameraFile) {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile)
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) viewModel.addPendingAttachment(cameraUri)
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            takePictureLauncher.launch(cameraUri)
        } else {
            Toast.makeText(context, "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
        }
    }
    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.addPendingAttachment(it) }
    }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    u,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            viewModel.addPendingAttachment(u)
        }
    }

    if (showTreatmentForm) {
        MedicalTreatmentFormScreen(
            familyId = familyId,
            childId = childId,
            treatmentId = null,
            onBack = { showTreatmentForm = false },
            onSaved = { tid ->
                viewModel.appendLinkedTreatmentId(tid)
                showTreatmentForm = false
            },
        )
        return
    }
    if (showExamForm) {
        MedicalExamFormScreen(
            familyId = familyId,
            childId = childId,
            examId = null,
            // Nuova visita: riga visita non esiste ancora in Room → FK su prescribingVisitId fallirebbe.
            // Collegamento differito in MedicalVisitFormViewModel dopo repository.save(visit).
            prescribingVisitId = if (visitId != null) state.visitId else null,
            bindNonce = examBindNonce,
            onBack = { showExamForm = false },
            onSaved = { eid ->
                viewModel.appendLinkedExamId(eid)
                showExamForm = false
            },
        )
        return
    }

    if (showAsNeededSheet || editingAsNeeded != null) {
        AsNeededDrugSheet(
            initial = editingAsNeeded,
            onDismiss = {
                showAsNeededSheet = false
                editingAsNeeded = null
            },
            onSave = { drug ->
                viewModel.addOrUpdateAsNeededDrug(drug)
                showAsNeededSheet = false
                editingAsNeeded = null
            },
        )
    }

    if (showAttachSheet) {
        HealthAttachmentSourcePickerSheet(
            onDismiss = { showAttachSheet = false },
            tintColor = VISIT_TINT,
            onTakePhoto = {
                when {
                    ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                        PackageManager.PERMISSION_GRANTED -> takePictureLauncher.launch(cameraUri)
                    else -> cameraPermLauncher.launch(Manifest.permission.CAMERA)
                }
            },
            onPickPhoto = {
                pickPhotoLauncher.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo),
                )
            },
            onPickFile = { pickFileLauncher.launch(arrayOf("*/*")) },
            onPickFromKidBoxDocuments = { showKidBoxDocPicker = true },
        )
    }
    if (showKidBoxDocPicker) {
        KidBoxDocumentPickerSheet(
            familyId = familyId,
            onDismiss = { showKidBoxDocPicker = false },
            onPickedUri = {
                viewModel.addPendingAttachment(it)
                showKidBoxDocPicker = false
            },
        )
    }

    if (showMainDatePicker) {
        VisitDatePickerDialog(
            initialMillis = state.dateMillis,
            onDismiss = { showMainDatePicker = false },
            onConfirm = { ms ->
                pendingMainDateMillis = ms
                showMainDatePicker = false
                showMainTimePicker = true
            },
        )
    }
    if (showMainTimePicker) {
        VisitTimePickerDialog(
            initialMillis = state.dateMillis,
            onDismiss = { showMainTimePicker = false },
            onConfirm = { h, m ->
                viewModel.setDateMillis(combineDateAndTime(pendingMainDateMillis, h, m))
                showMainTimePicker = false
            },
        )
    }
    if (showNextDatePicker) {
        VisitDatePickerDialog(
            initialMillis = state.nextVisitDateMillis,
            onDismiss = { showNextDatePicker = false },
            onConfirm = { ms ->
                pendingNextDateMillis = ms
                viewModel.setNextVisitDateMillis(startOfDayMillis(ms))
                showNextDatePicker = false
            },
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("Annulla", color = kb.title) }
            Text(
                if (visitId != null) "Modifica Visita" else "Visita Medica",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                color = kb.title,
            )
            Spacer(Modifier.width(72.dp))
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            repeat(state.totalSteps) { i ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .background(
                            if (i <= state.currentStep) VISIT_TINT else kb.subtitle.copy(alpha = 0.25f),
                            RoundedCornerShape(100.dp),
                        ),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            when (state.currentStep) {
                0 -> Step1InfoVisit(state, viewModel, VISIT_TINT, kb) {
                    showMainDatePicker = true
                }
                1 -> Step2Outcome(state, viewModel, kb)
                2 -> Step3Prescriptions(
                    state = state,
                    viewModel = viewModel,
                    tint = VISIT_TINT,
                    kb = kb,
                    onAddTreatment = { showTreatmentForm = true },
                    onAddExam = {
                        examBindNonce++
                        showExamForm = true
                    },
                    onAddAsNeeded = {
                        editingAsNeeded = null
                        showAsNeededSheet = true
                    },
                    onEditAsNeeded = { editingAsNeeded = it; showAsNeededSheet = true },
                )
                3 -> Step4AttachmentsNotes(state, viewModel, VISIT_TINT, kb) { showAttachSheet = true }
                else -> Step5Summary(
                    state = state,
                    vm = viewModel,
                    tint = VISIT_TINT,
                    kb = kb,
                    visitIdParam = visitId,
                    onPickNextDate = { showNextDatePicker = true },
                )
            }
            Spacer(Modifier.height(100.dp))
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (state.currentStep > 0) {
                OutlinedButton(
                    onClick = { viewModel.setCurrentStep(state.currentStep - 1) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    Spacer(Modifier.width(4.dp))
                    Text("Indietro", color = kb.title)
                }
            }
            if (state.currentStep < state.totalSteps - 1) {
                Button(
                    onClick = { viewModel.setCurrentStep(state.currentStep + 1) },
                    enabled = state.canAdvance,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = VISIT_TINT,
                        disabledContainerColor = VISIT_TINT.copy(alpha = 0.35f),
                    ),
                ) {
                    Text("Avanti", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            } else {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.isSaving && state.canSave,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = VISIT_TINT),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Salva ✓", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

@Composable
private fun Step1InfoVisit(
    state: MedicalVisitFormState,
    vm: MedicalVisitFormViewModel,
    tint: Color,
    kb: KidBoxColorScheme,
    onPickDate: () -> Unit,
) {
    Text("Tipo di Visita", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kb.title)
    Text("Es. Visita Urologica, Controllo Pediatrico...", fontSize = 12.sp, color = kb.subtitle)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = state.reason,
        onValueChange = vm::setReason,
        placeholder = { Text("Visita...") },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
    )
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Person, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Medico", fontWeight = FontWeight.Bold, color = kb.title)
    }
    Spacer(Modifier.height(8.dp))
    if (state.selectedDoctorName.isNotBlank() && !state.showNewDoctorForm) {
        Card(
            colors = CardDefaults.cardColors(containerColor = kb.card),
            shape = RoundedCornerShape(12.dp),
        ) {
            Row(
                Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = tint)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(state.selectedDoctorName, fontWeight = FontWeight.Bold, color = kb.title)
                    state.selectedSpec?.let {
                        Text(it.rawValue, fontSize = 12.sp, color = kb.subtitle)
                    }
                }
                TextButton(onClick = { vm.clearSelectedDoctor() }) { Text("Cambia", color = tint) }
            }
        }
    } else {
        OutlinedTextField(
            value = state.doctorSearchText,
            onValueChange = vm::setDoctorSearchText,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text("Cerca medico...") },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Text("Medici Recenti", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = kb.subtitle)
        state.recentDoctors.forEach { (name, spec) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { vm.pickRecentDoctor(name, spec) }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = kb.subtitle)
                Spacer(Modifier.width(8.dp))
                Column {
                    Text(name, fontWeight = FontWeight.Medium, color = kb.title)
                    Text(spec.orEmpty(), fontSize = 12.sp, color = kb.subtitle)
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { vm.setShowNewDoctorForm(true) }
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Nuovo Medico", fontWeight = FontWeight.SemiBold, color = kb.title)
                Text("es. Pediatra, Dermatologo", fontSize = 12.sp, color = kb.subtitle)
            }
        }
        if (state.showNewDoctorForm) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.selectedDoctorName,
                onValueChange = vm::setSelectedDoctorName,
                placeholder = { Text("es. Dott. Rossi") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                KBDoctorSpecialization.entries.forEach { spec ->
                    val sel = state.selectedSpec == spec
                    Surface(
                        modifier = Modifier.clickable { vm.setSelectedSpec(spec) },
                        shape = RoundedCornerShape(20.dp),
                        color = if (sel) tint else kb.card,
                    ) {
                        Text(
                            spec.rawValue,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            fontSize = 12.sp,
                            color = if (sel) Color.White else kb.title,
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.confirmNewDoctorForm() },
                enabled = state.selectedDoctorName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = tint),
            ) { Text("Conferma", color = Color.White) }
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Data Visita", fontWeight = FontWeight.Bold, color = kb.title)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPickDate, shape = RoundedCornerShape(10.dp)) {
            Text(DATE_COMPACT.format(Date(state.dateMillis)))
        }
        OutlinedButton(onClick = onPickDate, shape = RoundedCornerShape(10.dp)) {
            Text(TIME_COMPACT.format(Date(state.dateMillis)))
        }
    }
    Spacer(Modifier.height(12.dp))
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            if (state.visitReminderOn) Icons.Default.Notifications else Icons.Default.NotificationsNone,
            contentDescription = null,
            tint = tint,
        )
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Text("Promemoria il giorno prima", fontSize = 14.sp, color = kb.title)
            Text("Notifica alle 09:00", fontSize = 11.sp, color = kb.subtitle)
        }
        Switch(
            checked = state.visitReminderOn,
            onCheckedChange = vm::setVisitReminderOn,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = tint),
        )
    }
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Flag, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text("Stato Visita", fontWeight = FontWeight.Bold, color = kb.title)
    }
    Spacer(Modifier.height(8.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KBVisitStatus.entries.filter { it != KBVisitStatus.UNKNOWN_STATUS }.forEach { st ->
            val sel = state.visitStatus == st
            val icon: ImageVector = when (st) {
                KBVisitStatus.PENDING -> Icons.Default.Schedule
                KBVisitStatus.BOOKED -> Icons.Default.Event
                KBVisitStatus.COMPLETED -> Icons.Default.CheckCircle
                KBVisitStatus.RESULT_AVAILABLE -> Icons.Default.Description
                else -> Icons.Default.Schedule
            }
            Surface(
                modifier = Modifier.clickable { vm.setVisitStatus(st) },
                shape = RoundedCornerShape(20.dp),
                color = if (sel) tint else kb.subtitle.copy(alpha = 0.10f),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(icon, contentDescription = null, tint = if (sel) Color.White else kb.title, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(st.wizardChipLabel, fontSize = 13.sp, color = if (sel) Color.White else kb.title)
                }
            }
        }
    }
}

@Composable
private fun Step2Outcome(state: MedicalVisitFormState, vm: MedicalVisitFormViewModel, kb: KidBoxColorScheme) {
    Text("Esito della Visita", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kb.title)
    Spacer(Modifier.height(12.dp))
    VisitOutcomeTextBlock(
        icon = Icons.Default.Medication,
        title = "Diagnosi",
        value = state.diagnosis,
        onValueChange = vm::setDiagnosis,
        placeholder = "Diagnosi o conclusioni del medico",
        kb = kb,
    )
    Spacer(Modifier.height(12.dp))
    VisitOutcomeTextBlock(
        icon = Icons.Default.Lightbulb,
        title = "Raccomandazioni",
        value = state.recommendations,
        onValueChange = vm::setRecommendations,
        placeholder = "Consigli generali del medico",
        kb = kb,
    )
}

@Composable
private fun VisitOutcomeTextBlock(
    icon: ImageVector,
    title: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    kb: KidBoxColorScheme,
) {
    val phColor = kb.subtitle.copy(alpha = 0.72f)
    val outcomeFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = kb.title,
        focusedTextColor = kb.title,
        unfocusedTextColor = kb.title,
        focusedPlaceholderColor = phColor,
        unfocusedPlaceholderColor = phColor,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = VISIT_OUTCOME_FIELD_SURFACE,
    ) {
        Column(
            Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = kb.subtitle,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(title, fontWeight = FontWeight.Bold, color = kb.title, fontSize = 16.sp)
            }
            Spacer(Modifier.height(6.dp))
            TextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                placeholder = { Text(placeholder, color = phColor, fontSize = 15.sp) },
                minLines = 3,
                maxLines = 6,
                colors = outcomeFieldColors,
                shape = RoundedCornerShape(0.dp),
            )
        }
    }
}

@Composable
private fun Step3Prescriptions(
    state: MedicalVisitFormState,
    viewModel: MedicalVisitFormViewModel,
    tint: Color,
    kb: KidBoxColorScheme,
    onAddTreatment: () -> Unit,
    onAddExam: () -> Unit,
    onAddAsNeeded: () -> Unit,
    onEditAsNeeded: (KBAsNeededDrug) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text("Prescrizioni", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kb.title, modifier = Modifier.weight(1f))
        if (state.prescriptionsBadgeCount > 0) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(tint),
                contentAlignment = Alignment.Center,
            ) {
                Text("${state.prescriptionsBadgeCount}", color = Color.White, fontSize = 11.sp)
            }
        }
    }
    Spacer(Modifier.height(10.dp))
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val tabs = listOf(
            Triple(0, Icons.Default.Medication, "Farmaci"),
            Triple(1, Icons.Default.Vaccines, "Al bisogno"),
            Triple(2, Icons.Default.DirectionsWalk, "Tipo di Terapia"),
            Triple(3, Icons.Default.Science, "Esami"),
        )
        tabs.forEach { (idx, icon, label) ->
            val sel = state.prescriptionsTab == idx
            val c = when (idx) {
                0 -> state.linkedTreatmentIds.size
                1 -> state.asNeededDrugs.size
                2 -> state.therapyTypes.size
                else -> state.linkedExamIds.size
            }
            Surface(
                modifier = Modifier
                    .defaultMinSize(minWidth = 88.dp)
                    .clickable { viewModel.setPrescriptionsTab(idx) },
                shape = RoundedCornerShape(12.dp),
                color = if (sel) tint else Color.Transparent,
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            icon,
                            contentDescription = null,
                            tint = if (sel) Color.White else kb.subtitle,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            label,
                            fontSize = 10.sp,
                            lineHeight = 12.sp,
                            color = if (sel) Color.White else kb.title,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                        )
                    }
                    if (c > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(2.dp)
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (sel) Color.White.copy(alpha = 0.25f) else tint.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text("$c", fontSize = 9.sp, color = if (sel) Color.White else tint)
                        }
                    }
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    when (state.prescriptionsTab) {
        0 -> {
            Text("Farmaci Programmati", fontWeight = FontWeight.Bold, color = kb.title)
            Text("Farmaci con orari programmati da assumere regolarmente", fontSize = 12.sp, color = kb.subtitle)
            Spacer(Modifier.height(8.dp))
            if (state.linkedTreatmentIds.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Medication, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(40.dp))
                    Text("Nessun farmaco programmato", color = kb.subtitle)
                }
            } else {
                state.linkedTreatmentIds.forEach { id ->
                    val label = state.linkedTreatmentSummaries[id] ?: id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(label, modifier = Modifier.weight(1f), color = kb.title)
                        IconButton(onClick = { viewModel.removeLinkedTreatmentId(id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = kb.subtitle)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAddTreatment,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = tint)
                Spacer(Modifier.width(6.dp))
                Text("+ Aggiungi Cura")
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { viewModel.setCurrentStep(state.currentStep + 1) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salta le prescrizioni", color = kb.subtitle) }
        }
        1 -> {
            Text("Al bisogno", fontWeight = FontWeight.Bold, color = kb.title)
            Spacer(Modifier.height(8.dp))
            if (state.asNeededDrugs.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Vaccines, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(40.dp))
                    Text("Nessun farmaco al bisogno", color = kb.subtitle)
                }
            } else {
                state.asNeededDrugs.forEach { d ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(tint.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Default.LocalPharmacy, contentDescription = null, tint = tint)
                        }
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(d.drugName, fontWeight = FontWeight.Bold, color = kb.title)
                            val dose = if (d.dosageValue % 1.0 == 0.0) "%.0f".format(d.dosageValue) else "%.1f".format(d.dosageValue)
                            Text("$dose ${d.dosageUnit}", fontSize = 12.sp, color = kb.subtitle)
                            d.instructions?.let { Text(it, fontSize = 11.sp, color = kb.subtitle) }
                        }
                        IconButton(onClick = { onEditAsNeeded(d) }) {
                            Icon(Icons.Default.Edit, contentDescription = null, tint = tint)
                        }
                        IconButton(onClick = { viewModel.removeAsNeededDrug(d.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = kb.subtitle)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAddAsNeeded,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = tint)
                Text("+ Aggiungi Farmaco")
            }
        }
        2 -> {
            Text("Tipo di Terapia", fontWeight = FontWeight.Bold, color = kb.title)
            Spacer(Modifier.height(10.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                KBTherapyType.entries.forEach { tt ->
                    val sel = tt in state.therapyTypes
                    Surface(
                        modifier = Modifier.clickable { viewModel.toggleTherapyType(tt) },
                        shape = RoundedCornerShape(12.dp),
                        color = if (sel) tint else kb.subtitle.copy(alpha = 0.10f),
                    ) {
                        Text(
                            tt.rawValue,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            color = if (sel) Color.White else kb.title,
                        )
                    }
                }
            }
        }
        else -> {
            Text("Esami Prescritti", fontWeight = FontWeight.Bold, color = kb.title)
            Text("Esami del sangue, ecografie e altri controlli prescritti", fontSize = 12.sp, color = kb.subtitle)
            Spacer(Modifier.height(8.dp))
            if (state.linkedExamIds.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(40.dp))
                    Text("Nessun esame prescritto", color = kb.subtitle)
                }
            } else {
                state.linkedExamIds.forEach { id ->
                    val meta = state.linkedExamSummaries[id]
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(meta?.first ?: id, modifier = Modifier.weight(1f), color = kb.title)
                        if (meta?.second == true) Text("Urgente", fontSize = 11.sp, color = Color(0xFFFF6B00))
                        IconButton(onClick = { viewModel.removeLinkedExamId(id) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = kb.subtitle)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAddExam,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = tint),
            ) {
                Text("+ Aggiungi un esame")
            }
        }
    }
}

private fun uriLooksLikeImage(uri: Uri, mime: String?): Boolean {
    if (mime?.startsWith("image/") == true) return true
    val p = uri.lastPathSegment?.lowercase().orEmpty()
    return p.endsWith(".jpg") || p.endsWith(".jpeg") || p.endsWith(".png") ||
        p.endsWith(".webp") || p.endsWith(".heic") || p.endsWith(".gif")
}

@Composable
private fun VisitPendingAttachmentThumb(
    uri: Uri,
    tint: Color,
    kb: KidBoxColorScheme,
    onRemove: () -> Unit,
    showRemove: Boolean = true,
) {
    val context = LocalContext.current
    val mime = remember(uri) {
        runCatching { context.contentResolver.getType(uri) }.getOrNull()
    }
    val showImage = uriLooksLikeImage(uri, mime)
    Box(modifier = Modifier.size(56.dp)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(8.dp))
                .background(kb.subtitle.copy(alpha = 0.28f)),
            contentAlignment = Alignment.Center,
        ) {
            if (showImage) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(uri)
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    imageVector = if (mime == "application/pdf") Icons.Default.Description else Icons.Default.InsertDriveFile,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        if (showRemove) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(2.dp)
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.55f))
                    .clickable(onClick = onRemove),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Rimuovi",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun Step4AttachmentsNotes(
    state: MedicalVisitFormState,
    vm: MedicalVisitFormViewModel,
    tint: Color,
    kb: KidBoxColorScheme,
    onAddAttachment: () -> Unit,
) {
    if (state.navigationVisitId != null && state.visitAttachments.isNotEmpty()) {
        Text("Allegati salvati", fontWeight = FontWeight.Bold, color = kb.title)
        state.visitAttachments.forEach { doc ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = kb.subtitle)
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(doc.title, color = kb.title, maxLines = 1)
                    Text(extractionStatusLabel(doc.extractionStatusRaw), fontSize = 11.sp, color = kb.subtitle)
                }
                IconButton(onClick = { vm.deleteVisitAttachment(doc) }) {
                    Icon(Icons.Default.Delete, contentDescription = null, tint = kb.subtitle)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    Card(colors = CardDefaults.cardColors(containerColor = VISIT_OUTCOME_FIELD_SURFACE), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.AttachFile,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Allegati della Visita", fontWeight = FontWeight.Bold, color = tint, fontSize = 14.sp)
                }
                Text("${state.pendingAttachmentUris.size}/5", fontSize = 12.sp, color = kb.subtitle)
            }
            Spacer(Modifier.height(6.dp))
            Text("Aggiungi ricette, referti, esami o foto della visita", fontSize = 12.sp, color = kb.subtitle)
            Spacer(Modifier.height(10.dp))
            if (state.pendingAttachmentUris.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(
                        items = state.pendingAttachmentUris,
                        key = { it.toString() },
                    ) { uri ->
                        VisitPendingAttachmentThumb(
                            uri = uri,
                            tint = tint,
                            kb = kb,
                            onRemove = { vm.removePendingAttachment(uri) },
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            val canAddAttachment = state.pendingAttachmentUris.size < 5
            val addBtnBg = if (canAddAttachment) tint.copy(alpha = 0.14f) else kb.subtitle.copy(alpha = 0.1f)
            val addContentColor = if (canAddAttachment) tint else kb.subtitle.copy(alpha = 0.45f)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = canAddAttachment) { onAddAttachment() },
                shape = RoundedCornerShape(22.dp),
                color = addBtnBg,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (canAddAttachment) tint.copy(alpha = 0.22f) else kb.subtitle.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = null,
                            tint = addContentColor,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "Aggiungi allegato",
                        color = addContentColor,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
        }
    }
    Spacer(Modifier.height(14.dp))
    val notesFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = kb.title,
        focusedTextColor = kb.title,
        unfocusedTextColor = kb.title,
        focusedPlaceholderColor = kb.subtitle.copy(alpha = 0.75f),
        unfocusedPlaceholderColor = kb.subtitle.copy(alpha = 0.75f),
    )
    Card(colors = CardDefaults.cardColors(containerColor = VISIT_OUTCOME_FIELD_SURFACE), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Edit, contentDescription = null, tint = kb.title, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Appunti della Visita", fontWeight = FontWeight.Bold, color = kb.title, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            TextField(
                value = state.notes,
                onValueChange = vm::setNotes,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                placeholder = { Text("Aggiungi note sulla visita...", fontSize = 15.sp) },
                minLines = 4,
                maxLines = 8,
                colors = notesFieldColors,
                shape = RoundedCornerShape(0.dp),
            )
        }
    }
}

private fun extractionStatusLabel(raw: Int): String = when (KBTextExtractionStatus.fromRaw(raw)) {
    KBTextExtractionStatus.COMPLETED -> "Leggibile dall'AI ✓"
    KBTextExtractionStatus.FAILED -> "fallita"
    KBTextExtractionStatus.PROCESSING, KBTextExtractionStatus.PENDING -> "in corso"
    else -> "—"
}

@Composable
private fun Step5Summary(
    state: MedicalVisitFormState,
    vm: MedicalVisitFormViewModel,
    tint: Color,
    kb: KidBoxColorScheme,
    visitIdParam: String?,
    onPickNextDate: () -> Unit,
) {
    Text("Riepilogo Visita", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kb.title)
    Spacer(Modifier.height(10.dp))
    summaryCard(kb) {
        Icon(Icons.Default.Medication, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("Tipo di Visita", fontSize = 11.sp, color = kb.subtitle)
            Text(state.reason, fontWeight = FontWeight.Bold, color = kb.title)
        }
    }
    if (state.selectedDoctorName.isNotBlank()) {
        summaryCard(kb) {
            Icon(Icons.Default.Person, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Nome Medico", fontSize = 11.sp, color = kb.subtitle)
                Text(state.selectedDoctorName, fontWeight = FontWeight.Bold, color = kb.title)
                state.selectedSpec?.let { Text(it.rawValue, fontSize = 12.sp, color = kb.subtitle) }
            }
        }
    }
    summaryCard(kb) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text("Data e Ora Visita", fontSize = 11.sp, color = kb.subtitle)
            Text(SUMMARY_DT.format(Date(state.dateMillis)), fontWeight = FontWeight.Bold, color = kb.title)
        }
    }
    if (state.diagnosis.isNotBlank()) {
        summaryCard(kb) {
            Icon(Icons.Default.Medication, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Diagnosi", fontSize = 11.sp, color = kb.subtitle)
                Text(state.diagnosis, color = kb.title)
            }
        }
    }
    if (state.recommendations.isNotBlank()) {
        summaryCard(kb) {
            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text("Raccomandazioni", fontSize = 11.sp, color = kb.subtitle)
                Text(state.recommendations, color = kb.title)
            }
        }
    }
    if (state.linkedTreatmentIds.isNotEmpty()) {
        summaryCard(kb) {
            Column {
                Text("Farmaci Programmati (${state.linkedTreatmentIds.size})", fontSize = 11.sp, color = kb.subtitle)
                state.linkedTreatmentIds.forEach { id ->
                    val t = state.linkedTreatmentSummaries[id] ?: return@forEach
                    Text("· $t", fontSize = 13.sp, color = kb.title)
                }
            }
        }
    }
    if (state.asNeededDrugs.isNotEmpty()) {
        summaryCard(kb) {
            Column {
                Text("Al Bisogno (${state.asNeededDrugs.size})", fontSize = 11.sp, color = kb.subtitle)
                state.asNeededDrugs.forEach { d ->
                    val dose = if (d.dosageValue % 1.0 == 0.0) "%.0f".format(d.dosageValue) else "%.1f".format(d.dosageValue)
                    Text("· ${d.drugName} $dose ${d.dosageUnit}", fontSize = 13.sp, color = kb.title)
                }
            }
        }
    }
    if (state.therapyTypes.isNotEmpty()) {
        summaryCard(kb) {
            Column {
                Text("Terapie (${state.therapyTypes.size})", fontSize = 11.sp, color = kb.subtitle)
                Text(state.therapyTypes.joinToString(", ") { it.rawValue }, color = kb.title)
            }
        }
    }
    if (state.linkedExamIds.isNotEmpty()) {
        summaryCard(kb) {
            Column {
                Text("Esami Prescritti (${state.linkedExamIds.size})", fontSize = 11.sp, color = kb.subtitle)
                state.linkedExamIds.forEach { id ->
                    val meta = state.linkedExamSummaries[id]
                    Text("· ${meta?.first ?: id}${if (meta?.second == true) " ⚠" else ""}", fontSize = 13.sp, color = kb.title)
                }
            }
        }
    }
    if (state.pendingAttachmentUris.isNotEmpty() || (visitIdParam != null && state.visitAttachments.isNotEmpty())) {
        summaryCard(kb) {
            Column {
                val n = state.pendingAttachmentUris.size + state.visitAttachments.size
                Text("Allegati ($n)", fontSize = 11.sp, color = kb.subtitle)
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(
                        items = state.pendingAttachmentUris,
                        key = { it.toString() },
                    ) { u ->
                        VisitPendingAttachmentThumb(
                            uri = u,
                            tint = tint,
                            kb = kb,
                            onRemove = {},
                            showRemove = false,
                        )
                    }
                }
            }
        }
    }
    Card(colors = CardDefaults.cardColors(containerColor = kb.subtitle.copy(alpha = 0.07f)), shape = RoundedCornerShape(12.dp)) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.DateRange, contentDescription = null, tint = tint)
                Spacer(Modifier.width(8.dp))
                Text("Prossimo Appuntamento", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(
                    checked = state.hasNextVisit,
                    onCheckedChange = vm::setHasNextVisit,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = tint),
                )
            }
            if (state.hasNextVisit) {
                OutlinedButton(onClick = onPickNextDate, modifier = Modifier.fillMaxWidth()) {
                    Text(NEXT_DATE_FMT.format(Date(state.nextVisitDateMillis)))
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Icon(
                        if (state.nextVisitReminder) Icons.Default.Notifications else Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = tint,
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Promemoria il giorno prima", fontSize = 14.sp)
                        Text("Notifica alle 09:00", fontSize = 11.sp, color = kb.subtitle)
                    }
                    Switch(
                        checked = state.nextVisitReminder,
                        onCheckedChange = vm::setNextVisitReminder,
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = tint),
                    )
                }
            }
        }
    }
}

@Composable
private fun summaryCard(kb: KidBoxColorScheme, content: @Composable RowScope.() -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = kb.subtitle.copy(alpha = 0.07f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, content = content)
    }
}

@Composable
private fun AsNeededPillTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 6,
    kb: KidBoxColorScheme,
) {
    val ph = kb.subtitle.copy(alpha = 0.65f)
    val colors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = kb.title,
        focusedTextColor = kb.title,
        unfocusedTextColor = kb.title,
        focusedPlaceholderColor = ph,
        unfocusedPlaceholderColor = ph,
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 16.dp),
            placeholder = { Text(placeholder, color = ph, fontSize = 16.sp) },
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            colors = colors,
            shape = RoundedCornerShape(22.dp),
        )
    }
}

@Composable
private fun AsNeededDosagePillRow(
    dosageStr: String,
    onDosageStrChange: (String) -> Unit,
    unit: String,
    onUnitChange: (String) -> Unit,
    kb: KidBoxColorScheme,
) {
    val ph = kb.subtitle.copy(alpha = 0.65f)
    val fieldColors = TextFieldDefaults.colors(
        focusedContainerColor = Color.Transparent,
        unfocusedContainerColor = Color.Transparent,
        disabledContainerColor = Color.Transparent,
        errorContainerColor = Color.Transparent,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledIndicatorColor = Color.Transparent,
        errorIndicatorColor = Color.Transparent,
        cursorColor = kb.title,
        focusedTextColor = kb.title,
        unfocusedTextColor = kb.title,
        focusedPlaceholderColor = ph,
        unfocusedPlaceholderColor = ph,
    )
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = Color.White,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextField(
                value = dosageStr,
                onValueChange = { raw ->
                    onDosageStrChange(
                        raw.filter { ch -> ch.isDigit() || ch == '.' || ch == ',' },
                    )
                },
                modifier = Modifier
                    .width(80.dp)
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = fieldColors,
                shape = RoundedCornerShape(16.dp),
            )
            Spacer(Modifier.width(4.dp))
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp),
                shape = RoundedCornerShape(18.dp),
                color = AS_NEEDED_UNIT_TRACK,
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .horizontalScroll(rememberScrollState())
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    AS_NEEDED_UNITS.forEach { u ->
                        val sel = unit == u
                        Surface(
                            modifier = Modifier.clickable { onUnitChange(u) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (sel) Color.White else Color.Transparent,
                        ) {
                            Text(
                                u,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                fontSize = 13.sp,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (sel) kb.title else kb.subtitle,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun formatAsNeededDosageInitial(initial: KBAsNeededDrug?): String {
    if (initial == null) return "0"
    return if (initial.dosageValue % 1.0 == 0.0) {
        "%.0f".format(initial.dosageValue)
    } else {
        "%.1f".format(initial.dosageValue)
    }
}

@Composable
private fun AsNeededDrugSheet(
    initial: KBAsNeededDrug?,
    onDismiss: () -> Unit,
    onSave: (KBAsNeededDrug) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var drugName by remember { mutableStateOf(initial?.drugName.orEmpty()) }
    var dosageStr by remember { mutableStateOf(formatAsNeededDosageInitial(initial)) }
    var unit by remember { mutableStateOf(initial?.dosageUnit ?: "ml") }
    var instructions by remember { mutableStateOf(initial?.instructions.orEmpty()) }
    val id = remember(initial?.id) { initial?.id ?: UUID.randomUUID().toString() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val catalogHits = remember(drugName) {
        if (drugName.isBlank()) emptyList() else DrugCatalog.search(drugName).take(8)
    }
    val canSave = drugName.isNotBlank()

    fun commitSave() {
        if (!canSave) return
        val dv = dosageStr.replace(',', '.').trim().takeIf { it.isNotEmpty() }?.toDoubleOrNull() ?: 0.0
        onSave(
            KBAsNeededDrug(
                id = id,
                drugName = drugName.trim(),
                dosageValue = dv,
                dosageUnit = unit,
                instructions = instructions.takeIf { it.isNotBlank() },
            ),
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AS_NEEDED_SHEET_BG,
        dragHandle = {
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 40.dp, height = 4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(kb.subtitle.copy(alpha = 0.35f)),
                )
            }
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = kb.subtitle.copy(alpha = 0.14f),
                    modifier = Modifier.clickable(onClick = onDismiss),
                ) {
                    Text(
                        "Annulla",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = kb.title,
                    )
                }
                Text(
                    if (initial == null) "Farmaco al bisogno" else "Modifica farmaco",
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = kb.title,
                )
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.White,
                    modifier = Modifier.clickable(enabled = canSave, onClick = { commitSave() }),
                ) {
                    Text(
                        "Salva",
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = if (canSave) VISIT_TINT else kb.subtitle.copy(alpha = 0.42f),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                "Farmaco",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(8.dp))
            AsNeededPillTextField(
                value = drugName,
                onValueChange = { drugName = it },
                placeholder = "Nome farmaco",
                kb = kb,
            )
            if (catalogHits.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                ) {
                    Column {
                        catalogHits.forEachIndexed { index, e: DrugCatalogEntry ->
                            if (index > 0) {
                                HorizontalDivider(color = kb.subtitle.copy(alpha = 0.12f))
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { drugName = e.name }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(e.name, color = kb.title, fontWeight = FontWeight.Medium, fontSize = 16.sp)
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text(
                "Dosaggio",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(8.dp))
            AsNeededDosagePillRow(
                dosageStr = dosageStr,
                onDosageStrChange = { dosageStr = it },
                unit = unit,
                onUnitChange = { unit = it },
                kb = kb,
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Istruzioni",
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(8.dp))
            AsNeededPillTextField(
                value = instructions,
                onValueChange = { instructions = it },
                placeholder = "Es: In caso di febbre > 38°",
                singleLine = false,
                minLines = 3,
                maxLines = 6,
                kb = kb,
            )
        }
    }
}

@Composable
private fun VisitDatePickerDialog(initialMillis: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val state = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    ) { DatePicker(state = state) }
}

@Composable
private fun VisitTimePickerDialog(initialMillis: Long, onDismiss: () -> Unit, onConfirm: (Int, Int) -> Unit) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    val timeState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Ora") },
        text = { TimePicker(state = timeState) },
        confirmButton = { TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )
}

private fun combineDateAndTime(dateMidnightMillis: Long, hour: Int, minute: Int): Long =
    Calendar.getInstance().apply {
        timeInMillis = dateMidnightMillis
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

private fun startOfDayMillis(ms: Long): Long =
    Calendar.getInstance().apply {
        timeInMillis = ms
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
