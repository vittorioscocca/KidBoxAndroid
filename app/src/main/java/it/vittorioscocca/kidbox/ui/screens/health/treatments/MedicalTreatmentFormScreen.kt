@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health.treatments

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.slotLabelFor
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FMT_TREAT_FORM = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private val PURPLE_FORM = Color(0xFF9573D9)
private val ORANGE_FORM = Color(0xFFFF6B00)
private val DOSE_UNITS = listOf("ml", "mg", "gocce", "cpr", "bustine")

@Composable
fun MedicalTreatmentFormScreen(
    familyId: String,
    childId: String,
    treatmentId: String?,
    onBack: () -> Unit,
    onSaved: () -> Unit = onBack,
    viewModel: MedicalTreatmentFormViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, treatmentId) { viewModel.bind(familyId, childId, treatmentId) }
    LaunchedEffect(state.saved) { if (state.saved) { Toast.makeText(context, "Cura salvata", Toast.LENGTH_SHORT).show(); onSaved() } }
    LaunchedEffect(state.saveError) { state.saveError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }

    var showStartPicker by remember { mutableStateOf(false) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    var timePickerSlot by remember { mutableStateOf<Int?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(kb.background)) {
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
                if (treatmentId == null) "Nuova cura" else "Modifica cura",
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
            )
            if (state.childName.isNotBlank()) {
                Text(state.childName, fontSize = 14.sp, color = kb.subtitle)
            }
            Spacer(Modifier.height(20.dp))

            // ── 1. Farmaco ────────────────────────────────────────────────────
            TreatSectionLabel("Farmaco")
            OutlinedTextField(
                value = state.drugName,
                onValueChange = viewModel::setDrugName,
                placeholder = { Text("Nome farmaco *") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = state.activeIngredient,
                onValueChange = viewModel::setActiveIngredient,
                placeholder = { Text("Principio attivo (opzionale)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Spacer(Modifier.height(16.dp))

            // ── 2. Dosaggio ───────────────────────────────────────────────────
            TreatSectionLabel("Dosaggio")
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = state.dosageValue,
                    onValueChange = viewModel::setDosageValue,
                    placeholder = { Text("0") },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                )
                ExposedDropdownMenuBox(
                    expanded = unitMenuOpen,
                    onExpandedChange = { unitMenuOpen = it },
                    modifier = Modifier.weight(1f),
                ) {
                    OutlinedTextField(
                        value = state.dosageUnit,
                        onValueChange = {},
                        readOnly = true,
                        modifier = Modifier.menuAnchor().fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = unitMenuOpen) },
                    )
                    ExposedDropdownMenu(expanded = unitMenuOpen, onDismissRequest = { unitMenuOpen = false }) {
                        DOSE_UNITS.forEach { unit ->
                            DropdownMenuItem(
                                text = { Text(unit) },
                                onClick = { viewModel.setDosageUnit(unit); unitMenuOpen = false },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── 3. Tipo cura ──────────────────────────────────────────────────
            TreatSectionLabel("Tipo cura")
            TreatSwitchRow("Cura a lungo termine", state.isLongTerm, viewModel::setIsLongTerm)
            Spacer(Modifier.height(16.dp))

            // ── 4. Durata ─────────────────────────────────────────────────────
            if (!state.isLongTerm) {
                TreatSectionLabel("Durata")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("${state.durationDays} giorni", fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 14.sp)
                    Row {
                        IconButton(onClick = { viewModel.setDurationDays(state.durationDays - 1) }) {
                            Icon(Icons.Default.Remove, contentDescription = "Meno", tint = kb.title)
                        }
                        IconButton(onClick = { viewModel.setDurationDays(state.durationDays + 1) }) {
                            Icon(Icons.Default.Add, contentDescription = "Più", tint = kb.title)
                        }
                    }
                }
                Text(
                    "Fine: ${DATE_FMT_TREAT_FORM.format(Date(state.endDateEpochMillis))}",
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
                Spacer(Modifier.height(16.dp))
            }

            // ── 5. Data inizio ────────────────────────────────────────────────
            TreatSectionLabel("Data inizio")
            OutlinedTextField(
                value = DATE_FMT_TREAT_FORM.format(Date(state.startDateEpochMillis)),
                onValueChange = {},
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                trailingIcon = {
                    IconButton(onClick = { showStartPicker = true }) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Scegli data", tint = ORANGE_FORM)
                    }
                },
            )
            Spacer(Modifier.height(16.dp))

            // ── 6. Frequenza ──────────────────────────────────────────────────
            TreatSectionLabel("Frequenza giornaliera")
            val freqOptions = listOf(
                1 to "Mattina",
                2 to "Mattina + Sera",
                3 to "Mattina + Pranzo + Sera",
                4 to "Mattina + Pranzo + Sera + Notte",
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                freqOptions.forEach { (freq, subtitle) ->
                    val selected = state.dailyFrequency == freq
                    val borderColor = if (selected) PURPLE_FORM else kb.subtitle.copy(alpha = 0.3f)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
                            .background(if (selected) PURPLE_FORM.copy(alpha = 0.08f) else Color.Transparent)
                            .clickable { viewModel.setDailyFrequency(freq) }
                            .padding(12.dp),
                    ) {
                        Column {
                            Text("${freq}x/die", fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 14.sp)
                            Text(subtitle, fontSize = 12.sp, color = kb.subtitle)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── 7. Orari ──────────────────────────────────────────────────────
            TreatSectionLabel("Orari")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.scheduleTimes.forEachIndexed { idx, time ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(slotLabelFor(idx), fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 14.sp)
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .border(1.dp, PURPLE_FORM.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
                                .clickable { timePickerSlot = idx }
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                        ) {
                            Text(time, fontWeight = FontWeight.SemiBold, color = PURPLE_FORM, fontSize = 14.sp)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // ── 8. Promemoria ─────────────────────────────────────────────────
            TreatSectionLabel("Promemoria")
            TreatSwitchRow("Avvisami agli orari di somministrazione", state.reminderEnabled, viewModel::setReminderEnabled)
            Text(
                "Le notifiche vengono pianificate fino a 7 giorni in anticipo e si rinnovano automaticamente.",
                fontSize = 11.sp,
                color = kb.subtitle,
                modifier = Modifier.padding(top = 4.dp),
            )
            Spacer(Modifier.height(16.dp))

            // ── 9. Note ───────────────────────────────────────────────────────
            TreatSectionLabel("Note")
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::setNotes,
                placeholder = { Text("Note aggiuntive (opzionale)") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                minLines = 3,
            )
            Spacer(Modifier.height(24.dp))

            Button(
                onClick = { viewModel.save() },
                enabled = !state.isSaving && state.canSave,
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PURPLE_FORM),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Salvataggio...", color = Color.White, fontWeight = FontWeight.SemiBold)
                } else {
                    Text("Salva cura", color = Color.White, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(60.dp))
        }
    }

    if (showStartPicker) {
        TreatDatePickerDialog(
            initialMillis = state.startDateEpochMillis,
            onDismiss = { showStartPicker = false },
            onConfirm = { ms -> viewModel.setStartDate(ms); showStartPicker = false },
        )
    }

    timePickerSlot?.let { slot ->
        val timeParts = state.scheduleTimes.getOrNull(slot)?.split(":") ?: listOf("8", "0")
        val initialHour = timeParts.getOrNull(0)?.toIntOrNull() ?: 8
        val initialMinute = timeParts.getOrNull(1)?.toIntOrNull() ?: 0
        val pickerState = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
        Dialog(onDismissRequest = { timePickerSlot = null }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.kidBoxColors.card)
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = pickerState)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = { timePickerSlot = null }) { Text("Annulla") }
                    TextButton(onClick = {
                        val h = pickerState.hour.toString().padStart(2, '0')
                        val m = pickerState.minute.toString().padStart(2, '0')
                        viewModel.setScheduleTime(slot, "$h:$m")
                        timePickerSlot = null
                    }) { Text("OK") }
                }
            }
        }
    }
}

@Composable
private fun TreatSectionLabel(text: String) {
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
private fun TreatSwitchRow(label: String, checked: Boolean, onChecked: (Boolean) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = kb.title,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PURPLE_FORM),
        )
    }
}

@Composable
private fun TreatDatePickerDialog(initialMillis: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onConfirm(it) } ?: onDismiss()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    ) { DatePicker(state = pickerState) }
}
