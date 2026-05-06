@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health.treatments

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.schedulePeriodLabel
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FMT_TREAT_FORM = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private val PURPLE_FORM = Color(0xFF9573D9)
private val DOSE_UNITS = listOf("ml", "mg", "gocce", "cpr", "bustine")
private val COMMON_DRUGS_TREAT = listOf("Tachipirina", "Nurofen", "Augmentin", "Bentelan", "Aerosol", "Vitamina D")

@Composable
fun MedicalTreatmentFormScreen(
    familyId: String,
    childId: String,
    treatmentId: String?,
    onBack: () -> Unit,
    onSaved: (treatmentId: String) -> Unit = { _ -> onBack() },
    viewModel: MedicalTreatmentFormViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, treatmentId) { viewModel.bind(familyId, childId, treatmentId) }
    LaunchedEffect(state.saved, state.treatmentId) {
        if (state.saved) {
            Toast.makeText(context, "Cura salvata", Toast.LENGTH_SHORT).show()
            val id = state.treatmentId
            viewModel.consumeSaved()
            onSaved(id)
        }
    }
    LaunchedEffect(state.saveError) { state.saveError?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() } }
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

    var showStartPicker by remember { mutableStateOf(false) }
    var unitMenuOpen by remember { mutableStateOf(false) }
    var timePickerSlot by remember { mutableStateOf<Int?>(null) }

    val cameraFile = remember { File(File(context.cacheDir, "health-camera").apply { mkdirs() }, "treatment_form_camera_tmp.jpg") }
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
        0 -> "Farmaco"
        1 -> "Dose e frequenza"
        2 -> "Orari"
        else -> "Riepilogo"
    }
    val stepSubtitle = when (currentStep) {
        0 -> "Scegli il farmaco"
        1 -> "Configura dose, durata e ritmo"
        2 -> "Imposta inizio e orari"
        else -> "Promemoria, note e allegati"
    }
    val canAdvance = when (currentStep) {
        0 -> state.drugName.isNotBlank()
        else -> true
    }

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
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                KidBoxHeaderCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onBack) { Text("Annulla", color = kb.title) }
            }

            Text(if (treatmentId == null) "Nuova cura" else "Modifica cura", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = kb.title)
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
                                if (index <= currentStep) PURPLE_FORM else kb.subtitle.copy(alpha = 0.25f),
                                RoundedCornerShape(100.dp),
                            ),
                    )
                }
            }
            Spacer(Modifier.height(20.dp))

            if (currentStep == 0) {
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
                Text("Farmaci suggeriti", fontSize = 12.sp, color = kb.subtitle)
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    COMMON_DRUGS_TREAT.forEach { drug ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(if (state.drugName == drug) PURPLE_FORM.copy(alpha = 0.18f) else kb.card)
                                .border(1.dp, if (state.drugName == drug) PURPLE_FORM else kb.subtitle.copy(alpha = 0.25f), RoundedCornerShape(999.dp))
                                .clickable { viewModel.setDrugName(drug) }
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Text(drug, fontSize = 12.sp, color = if (state.drugName == drug) PURPLE_FORM else kb.title)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.activeIngredient,
                    onValueChange = viewModel::setActiveIngredient,
                    placeholder = { Text("Principio attivo (opzionale)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                )
            }

            if (currentStep == 1) {
                Card(colors = CardDefaults.cardColors(containerColor = PURPLE_FORM.copy(alpha = 0.10f)), shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Medication, contentDescription = null, tint = PURPLE_FORM)
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(if (state.drugName.isBlank()) "Farmaco" else state.drugName, fontWeight = FontWeight.SemiBold, color = kb.title)
                            if (state.activeIngredient.isNotBlank()) Text(state.activeIngredient, fontSize = 12.sp, color = kb.subtitle)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

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
                    ExposedDropdownMenuBox(expanded = unitMenuOpen, onExpandedChange = { unitMenuOpen = it }, modifier = Modifier.weight(1f)) {
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
                                DropdownMenuItem(text = { Text(unit) }, onClick = { viewModel.setDosageUnit(unit); unitMenuOpen = false })
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                TreatSectionLabel("Tipo cura")
                TreatSwitchRow("Cura a lungo termine", state.isLongTerm, viewModel::setIsLongTerm)
                Spacer(Modifier.height(16.dp))

                if (!state.isLongTerm) {
                    TreatSectionLabel("Durata")
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${state.durationDays} giorni", fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 14.sp)
                        Row {
                            IconButton(onClick = { viewModel.setDurationDays(state.durationDays - 1) }) { Icon(Icons.Default.Remove, contentDescription = "Meno", tint = kb.title) }
                            IconButton(onClick = { viewModel.setDurationDays(state.durationDays + 1) }) { Icon(Icons.Default.Add, contentDescription = "Più", tint = kb.title) }
                        }
                    }
                    Text("Fine: ${DATE_FMT_TREAT_FORM.format(Date(state.endDateEpochMillis))}", fontSize = 12.sp, color = kb.subtitle)
                    Spacer(Modifier.height(16.dp))
                }

                TreatSectionLabel("Frequenza giornaliera")
                val freqOptions = listOf(1 to "Mattina", 2 to "Mattina + Sera", 3 to "Mattina + Pranzo + Sera", 4 to "Mattina + Pranzo + Sera + Notte")
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    freqOptions.forEach { (freq, subtitle) ->
                        val selected = state.dailyFrequency == freq
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, if (selected) PURPLE_FORM else kb.subtitle.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
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
            }

            if (currentStep == 2) {
                TreatSectionLabel("Data inizio")
                OutlinedTextField(
                    value = DATE_FMT_TREAT_FORM.format(Date(state.startDateEpochMillis)),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    trailingIcon = {
                        IconButton(onClick = { showStartPicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Scegli data", tint = PURPLE_FORM)
                        }
                    },
                )
                Spacer(Modifier.height(16.dp))

                TreatSectionLabel("Orari")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.scheduleTimes.forEachIndexed { idx, time ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(
                                schedulePeriodLabel(state.scheduleTimes[idx], idx),
                                fontWeight = FontWeight.SemiBold,
                                color = kb.title,
                                fontSize = 14.sp,
                            )
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
                Spacer(Modifier.height(12.dp))
                TreatSectionLabel("Riepilogo rapido")
                Text("${state.dailyFrequency} dosi al giorno · ${if (state.isLongTerm) "Lungo termine" else "${state.durationDays} giorni"}", fontSize = 12.sp, color = kb.subtitle)
            }

            if (currentStep == 3) {
                TreatSectionLabel("Riepilogo")
                OutlinedTextField(value = state.drugName, onValueChange = {}, readOnly = true, label = { Text("Farmaco") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = "${state.dosageValue} ${state.dosageUnit}", onValueChange = {}, readOnly = true, label = { Text("Dosaggio") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = "${state.dailyFrequency}x/die", onValueChange = {}, readOnly = true, label = { Text("Frequenza") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = DATE_FMT_TREAT_FORM.format(Date(state.startDateEpochMillis)), onValueChange = {}, readOnly = true, label = { Text("Inizio") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))

                TreatSectionLabel("Promemoria")
                TreatSwitchRow("Avvisami agli orari di somministrazione", state.reminderEnabled, viewModel::setReminderEnabled)
                Text("Le notifiche vengono pianificate per tutti i giorni della cura.", fontSize = 11.sp, color = kb.subtitle, modifier = Modifier.padding(top = 4.dp))
                Spacer(Modifier.height(16.dp))

                TreatSectionLabel("Note")
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    placeholder = { Text("Note aggiuntive (opzionale)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    minLines = 3,
                )
                Spacer(Modifier.height(16.dp))

                HealthAttachmentsCard(
                    attachments = state.attachments,
                    tintColor = PURPLE_FORM,
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
                        colors = ButtonDefaults.buttonColors(containerColor = PURPLE_FORM),
                    ) { Text("Avanti", color = Color.White, fontWeight = FontWeight.SemiBold) }
                } else {
                    Button(
                        onClick = { viewModel.save() },
                        enabled = !state.isSaving && state.canSave,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = PURPLE_FORM),
                        modifier = Modifier.weight(1f).height(52.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Salvataggio...", color = Color.White, fontWeight = FontWeight.SemiBold)
                        } else {
                            Text("Salva cura", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
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
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
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

    if (showKidBoxDocPicker) {
        KidBoxDocumentPickerSheet(
            familyId = familyId,
            onDismiss = { showKidBoxDocPicker = false },
            onPickedUri = { viewModel.uploadAttachment(it) },
        )
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
        Text(label, fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 14.sp, modifier = Modifier.weight(1f))
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
            TextButton(onClick = { pickerState.selectedDateMillis?.let(onConfirm) ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    ) { DatePicker(state = pickerState) }
}
