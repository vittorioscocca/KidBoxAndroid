@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health.exams

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
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
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
    onSaved: () -> Unit = onBack,
    viewModel: MedicalExamFormViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, examId) { viewModel.bind(familyId, childId, examId) }

    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(context, "Esame salvato", Toast.LENGTH_SHORT).show()
            onSaved()
        }
    }
    LaunchedEffect(state.saveError) {
        state.saveError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    var showDeadlinePicker by remember { mutableStateOf(false) }
    var showResultDatePicker by remember { mutableStateOf(false) }
    var statusMenuOpen by remember { mutableStateOf(false) }

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
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = kb.title)
                }
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
            Spacer(Modifier.height(20.dp))

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
            Spacer(Modifier.height(24.dp))

            // ── Save button ───────────────────────────────────────────────────
            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving && state.canSave,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ORANGE_EXAM_FORM),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Salvataggio...", color = Color.White, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Salva esame", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(60.dp))
        }
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
