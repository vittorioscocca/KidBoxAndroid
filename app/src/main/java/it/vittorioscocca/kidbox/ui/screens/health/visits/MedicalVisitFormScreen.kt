@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health.visits

import android.widget.Toast
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import it.vittorioscocca.kidbox.data.local.mapper.KBDoctorSpecialization
import it.vittorioscocca.kidbox.data.local.mapper.KBVisitStatus
import it.vittorioscocca.kidbox.ui.screens.health.exams.MedicalExamFormScreen
import it.vittorioscocca.kidbox.ui.screens.health.treatments.MedicalTreatmentFormScreen
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val DATE_TIME_FMT_FORM = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.ITALIAN)
private val ORANGE_FORM = Color(0xFFFF6B00)

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
            onSaved()
        }
    }
    LaunchedEffect(state.saveError) {
        state.saveError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    // Date picker state for main visit date
    var showMainDatePicker by remember { mutableStateOf(false) }
    var showMainTimePicker by remember { mutableStateOf(false) }
    var pendingMainDateMillis by remember { mutableLongStateOf(0L) }

    // Date picker state for next visit date
    var showNextDatePicker by remember { mutableStateOf(false) }
    var showNextTimePicker by remember { mutableStateOf(false) }
    var pendingNextDateMillis by remember { mutableLongStateOf(0L) }

    // Dropdown visibility
    var specMenuOpen by remember { mutableStateOf(false) }
    var statusMenuOpen by remember { mutableStateOf(false) }
    var showAddTreatmentDialog by remember { mutableStateOf(false) }
    var showAddExamDialog by remember { mutableStateOf(false) }
    var showNestedTreatmentForm by remember { mutableStateOf(false) }
    var showNestedExamForm by remember { mutableStateOf(false) }

    if (showNestedTreatmentForm) {
        MedicalTreatmentFormScreen(
            familyId = familyId,
            childId = childId,
            treatmentId = null,
            onBack = { showNestedTreatmentForm = false },
            onSaved = {
                showNestedTreatmentForm = false
                viewModel.linkMostRecentTreatmentFromModule()
            },
        )
        return
    }
    if (showNestedExamForm) {
        MedicalExamFormScreen(
            familyId = familyId,
            childId = childId,
            examId = null,
            onBack = { showNestedExamForm = false },
            onSaved = {
                showNestedExamForm = false
                viewModel.linkMostRecentExamFromModule()
            },
        )
        return
    }

    var currentStep by remember { mutableStateOf(0) }
    val totalSteps = 4
    val stepTitle = when (currentStep) {
        0 -> "Dettagli visita"
        1 -> "Esito e note"
        2 -> "Follow-up"
        else -> "Riepilogo"
    }
    val stepSubtitle = when (currentStep) {
        0 -> "Motivo, medico, data e stato"
        1 -> "Promemoria, diagnosi e raccomandazioni"
        2 -> "Pianifica la prossima visita"
        else -> "Controlla tutto prima di salvare"
    }
    val canAdvance = when (currentStep) {
        0 -> state.reason.isNotBlank()
        2 -> !state.hasNextVisit || state.nextVisitReason.isNotBlank()
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
                if (visitId == null) "Nuova visita" else "Modifica visita",
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
                                if (index <= currentStep) ORANGE_FORM else kb.subtitle.copy(alpha = 0.25f),
                                RoundedCornerShape(100.dp),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            if (currentStep == 0) {
            // ── 1. Motivo della visita ────────────────────────────────────────
            FormSectionLabel("Motivo della visita")
            OutlinedTextField(
                value = state.reason,
                onValueChange = viewModel::setReason,
                placeholder = { Text("es. Visita di controllo") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // ── 2. Medico ─────────────────────────────────────────────────────
            FormSectionLabel("Medico")
            OutlinedTextField(
                value = state.doctorName,
                onValueChange = viewModel::setDoctorName,
                placeholder = { Text("Nome del medico") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // ── 3. Specializzazione ───────────────────────────────────────────
            FormSectionLabel("Specializzazione")
            ExposedDropdownMenuBox(
                expanded = specMenuOpen,
                onExpandedChange = { specMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = state.specialization?.rawValue ?: "Nessuna",
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = specMenuOpen) },
                )
                ExposedDropdownMenu(
                    expanded = specMenuOpen,
                    onDismissRequest = { specMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Nessuna") },
                        onClick = { viewModel.setSpecialization(null); specMenuOpen = false },
                    )
                    KBDoctorSpecialization.entries.forEach { spec ->
                        DropdownMenuItem(
                            text = { Text(spec.rawValue) },
                            onClick = { viewModel.setSpecialization(spec); specMenuOpen = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── 4. Data e ora ─────────────────────────────────────────────────
            FormSectionLabel("Data e ora")
            OutlinedTextField(
                value = DATE_TIME_FMT_FORM.format(Date(state.dateMillis)),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { showMainDatePicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Scegli data", tint = ORANGE_FORM)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))

            // ── 5. Stato visita ───────────────────────────────────────────────
            FormSectionLabel("Stato visita")
            ExposedDropdownMenuBox(
                expanded = statusMenuOpen,
                onExpandedChange = { statusMenuOpen = it },
            ) {
                OutlinedTextField(
                    value = state.visitStatus.displayLabel,
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = statusMenuOpen) },
                )
                ExposedDropdownMenu(
                    expanded = statusMenuOpen,
                    onDismissRequest = { statusMenuOpen = false },
                ) {
                    KBVisitStatus.entries.forEach { status ->
                        DropdownMenuItem(
                            text = { Text(status.displayLabel) },
                            onClick = { viewModel.setVisitStatus(status); statusMenuOpen = false },
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            }

            if (currentStep == 1) {
            // ── 6. Promemoria visita ──────────────────────────────────────────
            FormSectionLabel("Promemoria visita")
            FormSwitchRow(
                label = "Avvisami il giorno prima",
                checked = state.reminderOn,
                onChecked = viewModel::setReminderOn,
            )
            if (state.reminderOn && state.dateMillis < System.currentTimeMillis()) {
                Text(
                    "La data è già passata: il promemoria non verrà inviato.",
                    fontSize = 11.sp,
                    color = Color(0xFFFF8C00),
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            // ── 7. Esito ──────────────────────────────────────────────────────
            FormSectionLabel("Esito")
            OutlinedTextField(
                value = state.diagnosis,
                onValueChange = viewModel::setDiagnosis,
                placeholder = { Text("Diagnosi") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.recommendations,
                onValueChange = viewModel::setRecommendations,
                placeholder = { Text("Raccomandazioni") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
            )
            Spacer(Modifier.height(16.dp))

            // ── 8. Note ───────────────────────────────────────────────────────
            FormSectionLabel("Note")
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                placeholder = { Text("Note aggiuntive") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
            )
            Spacer(Modifier.height(20.dp))

            }

            if (currentStep == 2) {
            // ── 9. Prossima visita ────────────────────────────────────────────
            FormSectionLabel("Prossima visita")
            FormSwitchRow(
                label = "Pianifica prossima visita",
                checked = state.hasNextVisit,
                onChecked = viewModel::setHasNextVisit,
            )
            if (state.hasNextVisit) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = DATE_TIME_FMT_FORM.format(Date(state.nextVisitDateMillis)),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { showNextDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Scegli data", tint = ORANGE_FORM)
                        }
                    },
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.nextVisitReason,
                    onValueChange = viewModel::setNextVisitReason,
                    placeholder = { Text("Motivo prossima visita") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                FormSwitchRow(
                    label = "Avvisami il giorno prima della prossima visita",
                    checked = state.nextVisitReminderOn,
                    onChecked = viewModel::setNextVisitReminderOn,
                )
            }
            }

            if (currentStep == 3) {
                FormSectionLabel("Riepilogo")
                OutlinedTextField(value = state.reason, onValueChange = {}, readOnly = true, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = state.doctorName, onValueChange = {}, readOnly = true, label = { Text("Medico") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = DATE_TIME_FMT_FORM.format(Date(state.dateMillis)), onValueChange = {}, readOnly = true, label = { Text("Data visita") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                if (state.hasNextVisit) {
                    OutlinedTextField(value = DATE_TIME_FMT_FORM.format(Date(state.nextVisitDateMillis)), onValueChange = {}, readOnly = true, label = { Text("Prossima visita") }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                }
                Spacer(Modifier.height(20.dp))

                FormSectionLabel("Cure prescritte")
                Button(
                    onClick = { showNestedTreatmentForm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ORANGE_FORM.copy(alpha = 0.12f)),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ORANGE_FORM)
                    Spacer(Modifier.width(8.dp))
                    Text("Apri form cura", color = ORANGE_FORM)
                }
                state.prescribedTreatments.forEach { draft ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${draft.name} · ${draft.dosage} mg · ${draft.frequencyPerDay}x/die · ${draft.durationDays} gg",
                            modifier = Modifier.weight(1f),
                            color = kb.title,
                            fontSize = 13.sp,
                        )
                        IconButton(onClick = { viewModel.removePrescribedTreatment(draft.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Rimuovi", tint = kb.subtitle)
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                FormSectionLabel("Analisi prescritte")
                Button(
                    onClick = { showNestedExamForm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = ORANGE_FORM.copy(alpha = 0.12f)),
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = ORANGE_FORM)
                    Spacer(Modifier.width(8.dp))
                    Text("Apri form analisi", color = ORANGE_FORM)
                }
                state.prescribedExams.forEach { draft ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "${draft.name}${if (draft.isUrgent) " · Urgente" else ""}",
                            modifier = Modifier.weight(1f),
                            color = kb.title,
                            fontSize = 13.sp,
                        )
                        IconButton(onClick = { viewModel.removePrescribedExam(draft.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Rimuovi", tint = kb.subtitle)
                        }
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
                        colors = ButtonDefaults.buttonColors(containerColor = ORANGE_FORM),
                    ) { Text("Avanti", color = Color.White, fontWeight = FontWeight.SemiBold) }
                } else {
                    Button(
                        onClick = { viewModel.save() },
                        enabled = !state.isSaving && state.canSave,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ORANGE_FORM),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Salvataggio...", color = Color.White, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Salva visita", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }

    // ── Date + Time pickers for main visit ────────────────────────────────────
    if (showMainDatePicker) {
        KBDatePickerDialog(
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
        KBTimePickerDialog(
            initialMillis = state.dateMillis,
            onDismiss = { showMainTimePicker = false },
            onConfirm = { h, m ->
                viewModel.setDateMillis(combineDateAndTime(pendingMainDateMillis, h, m))
                showMainTimePicker = false
            },
        )
    }

    // ── Date + Time pickers for next visit ────────────────────────────────────
    if (showNextDatePicker) {
        KBDatePickerDialog(
            initialMillis = state.nextVisitDateMillis,
            onDismiss = { showNextDatePicker = false },
            onConfirm = { ms ->
                pendingNextDateMillis = ms
                showNextDatePicker = false
                showNextTimePicker = true
            },
        )
    }
    if (showNextTimePicker) {
        KBTimePickerDialog(
            initialMillis = state.nextVisitDateMillis,
            onDismiss = { showNextTimePicker = false },
            onConfirm = { h, m ->
                viewModel.setNextVisitDateMillis(combineDateAndTime(pendingNextDateMillis, h, m))
                showNextTimePicker = false
            },
        )
    }
}

// ── Composable helpers ────────────────────────────────────────────────────────

@Composable
private fun FormSectionLabel(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Text(
        text.uppercase(),
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        color = kb.subtitle,
        letterSpacing = 0.8.sp,
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun FormSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 14.sp,
            modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ORANGE_FORM,
            ),
        )
    }
}

@Composable
private fun KBDatePickerDialog(
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
            }) { Text("Avanti") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    ) {
        DatePicker(state = pickerState)
    }
}

@Composable
private fun KBTimePickerDialog(
    initialMillis: Long,
    onDismiss: () -> Unit,
    onConfirm: (hour: Int, minute: Int) -> Unit,
) {
    val cal = Calendar.getInstance().apply { timeInMillis = initialMillis }
    val timeState = rememberTimePickerState(
        initialHour = cal.get(Calendar.HOUR_OF_DAY),
        initialMinute = cal.get(Calendar.MINUTE),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Seleziona orario") },
        text = { TimePicker(state = timeState) },
        confirmButton = {
            TextButton(onClick = { onConfirm(timeState.hour, timeState.minute) }) { Text("OK") }
        },
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

