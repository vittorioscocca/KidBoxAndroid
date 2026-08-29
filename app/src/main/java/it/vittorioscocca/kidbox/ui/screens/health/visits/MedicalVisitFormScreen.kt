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
import androidx.compose.material.icons.filled.Euro
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
import it.vittorioscocca.kidbox.util.KBLocale
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private val VISIT_TINT = Color(0xFF5599D9)
/** Sfondo contenitori testo «Esito visita», allineato a iOS grouped secondary. */
private val VISIT_OUTCOME_FIELD_SURFACE = Color(0xFFF2F2F7)
/** Sheet «Farmaco al bisogno» (sfondo iOS-style). */
private val AS_NEEDED_SHEET_BG = Color(0xFFF2F2F7)
/** Traccia segmenti unità dosaggio. */
private val AS_NEEDED_UNIT_TRACK = Color(0xFFE5E5EA)
private fun DATE_COMPACT() = SimpleDateFormat("d MMM yyyy", KBLocale.current())
private fun TIME_COMPACT() = SimpleDateFormat("HH:mm", KBLocale.current())
private fun SUMMARY_DT() = SimpleDateFormat("d MMMM yyyy · HH:mm", KBLocale.current())
private fun NEXT_DATE_FMT() = SimpleDateFormat("EEEE d MMMM yyyy", KBLocale.current())
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
            Toast.makeText(context, context.getString(R.string.health_visit_saved), Toast.LENGTH_SHORT).show()
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
            Toast.makeText(context, context.getString(R.string.health_camera_denied), Toast.LENGTH_SHORT).show()
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
            petId = "",
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
            // Nuova visita: l'esame creato dentro la visita resta bozza nascosta finché non salvo la visita.
            saveAsDraftHidden = (visitId == null),
            bindNonce = examBindNonce,
            onBack = { showExamForm = false },
            onSaved = { eid, examName, examStatusRaw ->
                viewModel.appendLinkedExamId(
                    id = eid,
                    examName = examName,
                    examStatusRaw = examStatusRaw,
                )
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
            TextButton(onClick = onBack) { Text(stringResource(R.string.health_cancel), color = kb.title) }
            Text(
                if (visitId != null) stringResource(R.string.health_edit_visit) else stringResource(R.string.health_visit_medical),
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
                    Text(stringResource(R.string.health_back), color = kb.title)
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
                    Text(stringResource(R.string.health_next), color = Color.White, fontWeight = FontWeight.SemiBold)
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
                        Text(stringResource(R.string.health_save_check), color = Color.White, fontWeight = FontWeight.SemiBold)
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
    Text(stringResource(R.string.health_visit_type), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kb.title)
    Text(stringResource(R.string.health_visit_type_hint), fontSize = 12.sp, color = kb.subtitle)
    Spacer(Modifier.height(6.dp))
    OutlinedTextField(
        value = state.reason,
        onValueChange = vm::setReason,
        placeholder = { Text(stringResource(R.string.health_visit_ellipsis)) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        singleLine = true,
    )
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.Person, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.health_doctor_label), fontWeight = FontWeight.Bold, color = kb.title)
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
                TextButton(onClick = { vm.clearSelectedDoctor() }) { Text(stringResource(R.string.health_change), color = tint) }
            }
        }
    } else {
        OutlinedTextField(
            value = state.doctorSearchText,
            onValueChange = vm::setDoctorSearchText,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.health_search_doctor)) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
        )
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.health_recent_doctors), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = kb.subtitle)
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
                Text(stringResource(R.string.health_new_doctor), fontWeight = FontWeight.SemiBold, color = kb.title)
                Text(stringResource(R.string.health_doctor_spec_hint), fontSize = 12.sp, color = kb.subtitle)
            }
        }
        if (state.showNewDoctorForm) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.selectedDoctorName,
                onValueChange = vm::setSelectedDoctorName,
                placeholder = { Text(stringResource(R.string.health_doctor_name_hint)) },
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
            ) { Text(stringResource(R.string.health_confirm), color = Color.White) }
        }
    }
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(stringResource(R.string.health_visit_date), fontWeight = FontWeight.Bold, color = kb.title)
    }
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onPickDate, shape = RoundedCornerShape(10.dp)) {
            Text(DATE_COMPACT().format(Date(state.dateMillis)))
        }
        OutlinedButton(onClick = onPickDate, shape = RoundedCornerShape(10.dp)) {
            Text(TIME_COMPACT().format(Date(state.dateMillis)))
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
            Text(stringResource(R.string.health_reminder_day_before), fontSize = 14.sp, color = kb.title)
            Text(stringResource(R.string.health_notify_at_9), fontSize = 11.sp, color = kb.subtitle)
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
        Text(stringResource(R.string.health_visit_status), fontWeight = FontWeight.Bold, color = kb.title)
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
                    Text(stringResource(st.wizardChipLabelRes), fontSize = 13.sp, color = if (sel) Color.White else kb.title)
                }
            }
        }
    }
}

@Composable
private fun Step2Outcome(state: MedicalVisitFormState, vm: MedicalVisitFormViewModel, kb: KidBoxColorScheme) {
    Text(stringResource(R.string.health_visit_outcome), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kb.title)
    Spacer(Modifier.height(12.dp))
    VisitOutcomeTextBlock(
        icon = Icons.Default.Medication,
        title = stringResource(R.string.health_diagnosis),
        value = state.diagnosis,
        onValueChange = vm::setDiagnosis,
        placeholder = stringResource(R.string.health_diagnosis_hint),
        kb = kb,
    )
    Spacer(Modifier.height(12.dp))
    VisitOutcomeTextBlock(
        icon = Icons.Default.Lightbulb,
        title = stringResource(R.string.health_recommendations),
        value = state.recommendations,
        onValueChange = vm::setRecommendations,
        placeholder = stringResource(R.string.health_recommendations_hint),
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
        Text(stringResource(R.string.health_prescriptions), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kb.title, modifier = Modifier.weight(1f))
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
            Triple(0, Icons.Default.Medication, stringResource(R.string.health_medications)),
            Triple(1, Icons.Default.Vaccines, stringResource(R.string.health_as_needed)),
            Triple(2, Icons.Default.DirectionsWalk, stringResource(R.string.health_therapy_type)),
            Triple(3, Icons.Default.Science, stringResource(R.string.health_exams_label)),
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
            Text(stringResource(R.string.health_scheduled_meds), fontWeight = FontWeight.Bold, color = kb.title)
            Text(stringResource(R.string.health_scheduled_meds_hint), fontSize = 12.sp, color = kb.subtitle)
            Spacer(Modifier.height(8.dp))
            if (state.linkedTreatmentIds.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Medication, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.health_no_scheduled_meds), color = kb.subtitle)
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
                Text(stringResource(R.string.health_add_treatment))
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { viewModel.setCurrentStep(state.currentStep + 1) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.health_skip_prescriptions), color = kb.subtitle) }
        }
        1 -> {
            Text(stringResource(R.string.health_as_needed), fontWeight = FontWeight.Bold, color = kb.title)
            Spacer(Modifier.height(8.dp))
            if (state.asNeededDrugs.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Vaccines, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.health_no_prn_meds), color = kb.subtitle)
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
                Text(stringResource(R.string.health_add_medication))
            }
        }
        2 -> {
            Text(stringResource(R.string.health_therapy_type), fontWeight = FontWeight.Bold, color = kb.title)
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
            Text(stringResource(R.string.health_prescribed_exams), fontWeight = FontWeight.Bold, color = kb.title)
            Text(stringResource(R.string.health_prescribed_exams_hint), fontSize = 12.sp, color = kb.subtitle)
            Spacer(Modifier.height(8.dp))
            if (state.linkedExamIds.isEmpty()) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Science, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.health_no_prescribed_exams), color = kb.subtitle)
                }
            } else {
                state.linkedExamIds.forEach { id ->
                    val meta = state.linkedExamSummaries[id]
                    val title = meta?.name?.takeIf { it.isNotBlank() } ?: stringResource(R.string.health_exam_syncing)
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(title, modifier = Modifier.weight(1f), color = kb.title)
                        if (meta?.isUrgent == true) Text(stringResource(R.string.health_urgent), fontSize = 11.sp, color = Color(0xFFFF6B00))
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
                Text(stringResource(R.string.health_add_exam))
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
                    contentDescription = stringResource(R.string.health_remove),
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
    val context = LocalContext.current
    if (state.navigationVisitId != null && state.visitAttachments.isNotEmpty()) {
        Text(stringResource(R.string.health_attachments_saved), fontWeight = FontWeight.Bold, color = kb.title)
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
                    Text(extractionStatusLabel(context, doc.extractionStatusRaw), fontSize = 11.sp, color = kb.subtitle)
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
                    Text(stringResource(R.string.health_visit_attachments), fontWeight = FontWeight.Bold, color = tint, fontSize = 14.sp)
                }
                Text("${state.pendingAttachmentUris.size}/5", fontSize = 12.sp, color = kb.subtitle)
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.health_visit_attachments_hint), fontSize = 12.sp, color = kb.subtitle)
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
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.health_attachments_ai_note),
                    fontSize = 11.sp,
                    color = kb.subtitle,
                )
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
                        stringResource(R.string.health_add_attachment),
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
                Text(stringResource(R.string.health_visit_notes), fontWeight = FontWeight.Bold, color = kb.title, fontSize = 14.sp)
            }
            Spacer(Modifier.height(10.dp))
            TextField(
                value = state.notes,
                onValueChange = vm::setNotes,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                placeholder = { Text(stringResource(R.string.health_visit_notes_hint), fontSize = 15.sp) },
                minLines = 4,
                maxLines = 8,
                colors = notesFieldColors,
                shape = RoundedCornerShape(0.dp),
            )

            Spacer(Modifier.height(14.dp))

            // Il costo genera la voce in Spese famiglia: si scrive qui, una
            // volta sola, e non va riportato a mano fra le spese.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Euro, contentDescription = null, tint = kb.title, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.health_visit_cost),
                    fontWeight = FontWeight.Bold,
                    color = kb.title,
                    fontSize = 14.sp,
                )
            }
            Spacer(Modifier.height(10.dp))
            TextField(
                value = state.costText,
                onValueChange = vm::setCostText,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("0,00", fontSize = 15.sp) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                colors = notesFieldColors,
                shape = RoundedCornerShape(0.dp),
            )
            Text(
                stringResource(R.string.health_cost_expense_hint),
                fontSize = 12.sp,
                color = kb.subtitle,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

private fun extractionStatusLabel(context: android.content.Context, raw: Int): String = when (KBTextExtractionStatus.fromRaw(raw)) {
    KBTextExtractionStatus.COMPLETED -> context.getString(R.string.health_ai_readable)
    KBTextExtractionStatus.FAILED -> "fallita"
    KBTextExtractionStatus.PROCESSING, KBTextExtractionStatus.PENDING -> context.getString(R.string.health_in_progress)
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
    Text(stringResource(R.string.health_visit_summary), fontSize = 20.sp, fontWeight = FontWeight.Bold, color = kb.title)
    Spacer(Modifier.height(10.dp))
    summaryCard(kb) {
        Icon(Icons.Default.Medication, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(stringResource(R.string.health_visit_type), fontSize = 11.sp, color = kb.subtitle)
            Text(state.reason, fontWeight = FontWeight.Bold, color = kb.title)
        }
    }
    if (state.selectedDoctorName.isNotBlank()) {
        summaryCard(kb) {
            Icon(Icons.Default.Person, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.health_doctor_name), fontSize = 11.sp, color = kb.subtitle)
                Text(state.selectedDoctorName, fontWeight = FontWeight.Bold, color = kb.title)
                state.selectedSpec?.let { Text(it.rawValue, fontSize = 12.sp, color = kb.subtitle) }
            }
        }
    }
    summaryCard(kb) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(stringResource(R.string.health_visit_datetime), fontSize = 11.sp, color = kb.subtitle)
            Text(SUMMARY_DT().format(Date(state.dateMillis)), fontWeight = FontWeight.Bold, color = kb.title)
        }
    }
    if (state.diagnosis.isNotBlank()) {
        summaryCard(kb) {
            Icon(Icons.Default.Medication, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.health_diagnosis), fontSize = 11.sp, color = kb.subtitle)
                Text(state.diagnosis, color = kb.title)
            }
        }
    }
    if (state.recommendations.isNotBlank()) {
        summaryCard(kb) {
            Icon(Icons.Default.Lightbulb, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Column {
                Text(stringResource(R.string.health_recommendations), fontSize = 11.sp, color = kb.subtitle)
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
                    Text("· ${meta?.name ?: id}${if (meta?.isUrgent == true) " ⚠" else ""}", fontSize = 13.sp, color = kb.title)
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
                Text(stringResource(R.string.health_next_appointment), fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                Switch(
                    checked = state.hasNextVisit,
                    onCheckedChange = vm::setHasNextVisit,
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = tint),
                )
            }
            if (state.hasNextVisit) {
                OutlinedButton(onClick = onPickNextDate, modifier = Modifier.fillMaxWidth()) {
                    Text(NEXT_DATE_FMT().format(Date(state.nextVisitDateMillis)))
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
                        Text(stringResource(R.string.health_reminder_day_before), fontSize = 14.sp)
                        Text(stringResource(R.string.health_notify_at_9), fontSize = 11.sp, color = kb.subtitle)
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
                        stringResource(R.string.health_cancel),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = kb.title,
                    )
                }
                Text(
                    if (initial == null) stringResource(R.string.health_prn_medication) else stringResource(R.string.health_edit_medication),
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
                        stringResource(R.string.health_save),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                        color = if (canSave) VISIT_TINT else kb.subtitle.copy(alpha = 0.42f),
                    )
                }
            }
            Spacer(Modifier.height(22.dp))
            Text(
                stringResource(R.string.health_medication),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(8.dp))
            AsNeededPillTextField(
                value = drugName,
                onValueChange = { drugName = it },
                placeholder = stringResource(R.string.health_medication_name),
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
                stringResource(R.string.health_dosage),
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
                stringResource(R.string.health_instructions),
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(8.dp))
            AsNeededPillTextField(
                value = instructions,
                onValueChange = { instructions = it },
                placeholder = stringResource(R.string.health_instructions_hint),
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
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.health_cancel)) } },
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
        title = { Text(stringResource(R.string.health_time)) },
        text = { TimePicker(state = timeState) },
        confirmButton = { TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.health_cancel)) } },
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
