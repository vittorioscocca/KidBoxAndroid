@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health.vaccines

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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineType
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FMT_VACCINE_FORM = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private val SALMON_FORM = Color(0xFFF38D73)

@Composable
fun MedicalVaccineFormScreen(
    familyId: String,
    childId: String,
    vaccineId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    viewModel: MedicalVaccineFormViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, vaccineId) { viewModel.bind(familyId, childId, vaccineId) }
    LaunchedEffect(state.saved) {
        if (state.saved) {
            Toast.makeText(context, "Vaccino salvato", Toast.LENGTH_SHORT).show()
            onSaved()
        }
    }
    LaunchedEffect(state.saveError) {
        state.saveError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    var showScheduledPicker by remember { mutableStateOf(false) }
    var showAdministeredPicker by remember { mutableStateOf(false) }
    var showNextDosePicker by remember { mutableStateOf(false) }

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
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = kb.title)
                }
            }
            Text(
                if (vaccineId == null) "Nuovo vaccino" else "Modifica vaccino",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
            )
            if (state.childName.isNotBlank()) {
                Text(state.childName, fontSize = 14.sp, color = kb.subtitle)
            }
            Spacer(Modifier.height(20.dp))

            // ── 1. Nome vaccino ───────────────────────────────────────────────
            VaccineSectionLabel("Nome vaccino")
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                placeholder = { Text("es. Esavalente, MPRV") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // ── 2. Tipo ────────────────────────────────────────────────────────
            VaccineSectionLabel("Tipo")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                KBVaccineType.entries.forEach { type ->
                    FilterChip(
                        selected = state.vaccineType == type,
                        onClick = { viewModel.setVaccineType(type) },
                        label = { Text(type.rawValue) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SALMON_FORM,
                            selectedLabelColor = Color.White,
                        ),
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── 3. Data programmata ────────────────────────────────────────────
            VaccineSectionLabel("Data programmata")
            VaccineSwitchRow(
                label = "Imposta data programmata",
                checked = state.hasScheduledDate,
                onChecked = viewModel::setHasScheduledDate,
            )
            if (state.hasScheduledDate) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = DATE_FMT_VACCINE_FORM.format(Date(state.scheduledDateEpochMillis)),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { showScheduledPicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Scegli data", tint = SALMON_FORM)
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))

            // ── 4. Già somministrato ───────────────────────────────────────────
            VaccineSectionLabel("Stato somministrazione")
            VaccineSwitchRow(
                label = "Già somministrato",
                checked = state.isAdministered,
                onChecked = viewModel::setIsAdministered,
            )
            if (state.isAdministered) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = DATE_FMT_VACCINE_FORM.format(Date(state.administeredDateEpochMillis)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data somministrazione") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { showAdministeredPicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Scegli data", tint = SALMON_FORM)
                        }
                    },
                )
            }
            Spacer(Modifier.height(16.dp))

            // ── 5. Promemoria ──────────────────────────────────────────────────
            VaccineSectionLabel("Promemoria")
            val reminderEnabled = state.hasScheduledDate && !state.isAdministered
            VaccineSwitchRow(
                label = "Avvisami il giorno prima",
                checked = state.reminderOn,
                onChecked = { if (reminderEnabled) viewModel.setReminderOn(it) },
                enabled = reminderEnabled,
            )
            if (!reminderEnabled) {
                Text(
                    when {
                        state.isAdministered -> "Il vaccino è già stato somministrato."
                        else -> "Imposta una data programmata per attivare il promemoria."
                    },
                    fontSize = 11.sp,
                    color = kb.subtitle,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Spacer(Modifier.height(16.dp))

            // ── 6. Medico ──────────────────────────────────────────────────────
            VaccineSectionLabel("Medico")
            OutlinedTextField(
                value = state.doctorName,
                onValueChange = viewModel::setDoctorName,
                placeholder = { Text("Nome del medico") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // ── 7. Luogo ───────────────────────────────────────────────────────
            VaccineSectionLabel("Luogo")
            OutlinedTextField(
                value = state.location,
                onValueChange = viewModel::setLocation,
                placeholder = { Text("es. ASL Roma 1, Centro Vaccinale") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // ── 8. Lotto ───────────────────────────────────────────────────────
            VaccineSectionLabel("Lotto")
            OutlinedTextField(
                value = state.lotNumber,
                onValueChange = viewModel::setLotNumber,
                placeholder = { Text("es. ABC123") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // ── 9. Note ────────────────────────────────────────────────────────
            VaccineSectionLabel("Note")
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                placeholder = { Text("Note aggiuntive") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
            )
            Spacer(Modifier.height(16.dp))

            // ── 10. Prossimo richiamo ──────────────────────────────────────────
            VaccineSectionLabel("Prossimo richiamo")
            VaccineSwitchRow(
                label = "Pianifica prossimo richiamo",
                checked = state.hasNextDose,
                onChecked = viewModel::setHasNextDose,
            )
            if (state.hasNextDose) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = DATE_FMT_VACCINE_FORM.format(Date(state.nextDoseDateEpochMillis)),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Data prossima dose") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { showNextDosePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Scegli data", tint = SALMON_FORM)
                        }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))

            // ── Save button ────────────────────────────────────────────────────
            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving && state.canSave,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SALMON_FORM),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Salvataggio...", color = Color.White, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Salva vaccino", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }

    if (showScheduledPicker) {
        VaccineDatePickerDialog(
            initialMillis = state.scheduledDateEpochMillis,
            onDismiss = { showScheduledPicker = false },
            onConfirm = { ms -> viewModel.setScheduledDateEpochMillis(ms); showScheduledPicker = false },
        )
    }
    if (showAdministeredPicker) {
        VaccineDatePickerDialog(
            initialMillis = state.administeredDateEpochMillis,
            onDismiss = { showAdministeredPicker = false },
            onConfirm = { ms -> viewModel.setAdministeredDateEpochMillis(ms); showAdministeredPicker = false },
        )
    }
    if (showNextDosePicker) {
        VaccineDatePickerDialog(
            initialMillis = state.nextDoseDateEpochMillis,
            onDismiss = { showNextDosePicker = false },
            onConfirm = { ms -> viewModel.setNextDoseDateEpochMillis(ms); showNextDosePicker = false },
        )
    }
}

@Composable
private fun VaccineSectionLabel(text: String) {
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
private fun VaccineSwitchRow(
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
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = SALMON_FORM),
        )
    }
}

@Composable
private fun VaccineDatePickerDialog(
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
