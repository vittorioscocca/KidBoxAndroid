@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health.vaccines

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineType
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val Salmon = Color(0xFFF38B75)
private val GreenStatus = Color(0xFF2E7D32)
private val BlueStatus = Color(0xFF1565C0)
private val OrangeStatus = Color(0xFFE65100)

private val administrationSites = listOf(
    "Braccio sinistro", "Braccio destro", "Coscia sinistra", "Coscia destra", "Orale", "Nasale", "Altro",
)

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
    val isEditing = vaccineId != null

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

    val isDark = isSystemInDarkTheme()
    val fieldBg = if (isDark) Color.White.copy(alpha = 0.06f) else Color.Black.copy(alpha = 0.05f)
    val cardBg = if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.04f)

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = kb.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        if (isEditing) "Modifica Vaccino" else "Nuovo Vaccino",
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    TextButton(onClick = onBack) { Text("Annulla", color = kb.subtitle) }
                },
            )
        },
        bottomBar = {
            Surface(shadowElevation = 8.dp, tonalElevation = 2.dp) {
                Button(
                    onClick = { viewModel.save() },
                    enabled = !state.isSaving && state.canSave,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(999.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Salmon),
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                        Spacer(Modifier.width(10.dp))
                    }
                    Text(
                        if (isEditing) "Salva modifiche" else "Aggiungi vaccino",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp,
                    )
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (state.childName.isNotBlank()) {
                Text(state.childName, fontSize = 14.sp, color = kb.subtitle)
            }

            VaccineFormCard(cardBg) {
                FormSectionLabel(Icons.Default.Verified, "Stato vaccino")
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    StatusChip(
                        selected = state.formStatus == VaccineFormStatus.ADMINISTERED,
                        label = "Somministrato",
                        icon = Icons.Default.CheckCircle,
                        accent = GreenStatus,
                        fieldBg = fieldBg,
                        modifier = Modifier.weight(1f),
                    ) { viewModel.setFormStatus(VaccineFormStatus.ADMINISTERED) }
                    StatusChip(
                        selected = state.formStatus == VaccineFormStatus.SCHEDULED,
                        label = "Appuntamento",
                        icon = Icons.Default.Event,
                        accent = BlueStatus,
                        fieldBg = fieldBg,
                        modifier = Modifier.weight(1f),
                    ) { viewModel.setFormStatus(VaccineFormStatus.SCHEDULED) }
                    StatusChip(
                        selected = state.formStatus == VaccineFormStatus.PLANNED,
                        label = "Da programmare",
                        icon = Icons.Default.HelpOutline,
                        accent = OrangeStatus,
                        fieldBg = fieldBg,
                        modifier = Modifier.weight(1f),
                    ) { viewModel.setFormStatus(VaccineFormStatus.PLANNED) }
                }
            }

            VaccineFormCard(cardBg) {
                FormSectionLabel(Icons.Default.Vaccines, "Tipo di Vaccino")
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    userScrollEnabled = false,
                ) {
                    items(KBVaccineType.entries.toList(), key = { it.rawValue }) { type ->
                        VaccineTypeCell(
                            type = type,
                            selected = state.vaccineType == type,
                            salmon = Salmon,
                            fieldBg = fieldBg,
                        ) { viewModel.setVaccineType(type) }
                    }
                }
                Spacer(Modifier.height(4.dp))
                VaccineFilledField(
                    value = state.commercialName,
                    onValueChange = viewModel::setCommercialName,
                    placeholder = "Nome commerciale (opzionale)",
                    fieldBg = fieldBg,
                )
            }

            VaccineFormCard(cardBg) {
                FormSectionLabel(Icons.Outlined.Tag, "Informazioni Dose")
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Top,
                ) {
                    DoseStepper(
                        label = "Dose N°",
                        value = state.doseNumber,
                        onChange = viewModel::setDoseNumber,
                        salmon = Salmon,
                        modifier = Modifier.weight(1f),
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(48.dp)
                            .background(kb.subtitle.copy(alpha = 0.2f)),
                    )
                    DoseStepper(
                        label = "Dosi totali",
                        value = state.totalDoses,
                        onChange = viewModel::setTotalDoses,
                        salmon = Salmon,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            VaccineFormCard(cardBg) {
                FormSectionLabel(
                    Icons.Default.CalendarMonth,
                    when (state.formStatus) {
                        VaccineFormStatus.ADMINISTERED -> "Data Somministrazione"
                        VaccineFormStatus.SCHEDULED -> "Data Appuntamento"
                        VaccineFormStatus.PLANNED -> "Data"
                    },
                )
                when (state.formStatus) {
                    VaccineFormStatus.PLANNED -> {
                        Text(
                            "Nessuna data da impostare per vaccini da programmare",
                            fontSize = 12.sp,
                            color = kb.subtitle,
                        )
                    }
                    VaccineFormStatus.ADMINISTERED -> {
                        key(state.administeredDateEpochMillis) {
                            val dp = rememberDatePickerState(
                                initialSelectedDateMillis = state.administeredDateEpochMillis,
                            )
                            LaunchedEffect(dp.selectedDateMillis) {
                                val m = dp.selectedDateMillis ?: return@LaunchedEffect
                                if (m != state.administeredDateEpochMillis) {
                                    viewModel.setAdministeredDateEpochMillis(m)
                                }
                            }
                            DatePicker(state = dp, showModeToggle = false)
                        }
                    }
                    VaccineFormStatus.SCHEDULED -> {
                        key(state.scheduledDateEpochMillis) {
                            val dp = rememberDatePickerState(
                                initialSelectedDateMillis = state.scheduledDateEpochMillis,
                            )
                            LaunchedEffect(dp.selectedDateMillis) {
                                val m = dp.selectedDateMillis ?: return@LaunchedEffect
                                if (m != state.scheduledDateEpochMillis) {
                                    viewModel.setScheduledDateEpochMillis(m)
                                }
                            }
                            DatePicker(state = dp, showModeToggle = false)
                        }
                    }
                }
            }

            if (state.formStatus == VaccineFormStatus.PLANNED) {
                VaccineFormCard(cardBg) {
                    FormSectionLabel(Icons.Default.Notifications, "Promemoria")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Avviso il giorno prima",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = kb.title,
                            )
                            Text(
                                "Solo per vaccini da programmare: imposta la data prevista del richiamo.",
                                fontSize = 11.sp,
                                color = kb.subtitle,
                            )
                        }
                        Switch(
                            checked = state.reminderOn,
                            onCheckedChange = viewModel::setReminderOn,
                        )
                    }
                    if (state.reminderOn) {
                        Spacer(Modifier.height(8.dp))
                        Text("Data prevista prossima dose", fontSize = 12.sp, color = kb.subtitle)
                        Spacer(Modifier.height(6.dp))
                        key(state.nextDoseDateEpochMillis) {
                            val nd = state.nextDoseDateEpochMillis ?: System.currentTimeMillis()
                            val dp = rememberDatePickerState(initialSelectedDateMillis = nd)
                            LaunchedEffect(dp.selectedDateMillis) {
                                val m = dp.selectedDateMillis ?: return@LaunchedEffect
                                if (m != state.nextDoseDateEpochMillis) {
                                    viewModel.setNextDoseDateEpochMillis(m)
                                }
                            }
                            DatePicker(state = dp, showModeToggle = false)
                        }
                    }
                }
            }

            if (state.formStatus == VaccineFormStatus.ADMINISTERED) {
                VaccineFormCard(cardBg) {
                    FormSectionLabel(Icons.Default.Info, "Dettagli (opzionali)")
                    VaccineFilledField(
                        value = state.lotNumber,
                        onValueChange = viewModel::setLotNumber,
                        placeholder = "Numero lotto",
                        fieldBg = fieldBg,
                    )
                    Spacer(Modifier.height(10.dp))
                    VaccineFilledField(
                        value = state.administeredBy,
                        onValueChange = viewModel::setAdministeredBy,
                        placeholder = "Somministrato da",
                        fieldBg = fieldBg,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text("Sito somministrazione", fontSize = 12.sp, color = kb.subtitle)
                    Spacer(Modifier.height(6.dp))
                    val chipScroll = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(chipScroll),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        administrationSites.forEach { site ->
                            val sel = state.administrationSite == site
                            Text(
                                site,
                                fontSize = 12.sp,
                                color = if (sel) Color.White else kb.title,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(if (sel) Salmon else fieldBg)
                                    .clickable { viewModel.toggleAdministrationSite(site) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }

            VaccineFormCard(cardBg) {
                FormSectionLabel(Icons.Default.EditNote, "Note")
                OutlinedTextField(
                    value = state.notes,
                    onValueChange = viewModel::setNotes,
                    placeholder = { Text("Note aggiuntive", color = kb.subtitle) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedContainerColor = fieldBg,
                        focusedContainerColor = fieldBg,
                        unfocusedBorderColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun VaccineFormCard(cardBg: Color, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(16.dp),
        content = content,
    )
}

@Composable
private fun ColumnScope.FormSectionLabel(icon: ImageVector, text: String) {
    val kb = MaterialTheme.kidBoxColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = kb.title.copy(alpha = 0.85f), modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = kb.title)
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun StatusChip(
    selected: Boolean,
    label: String,
    icon: ImageVector,
    accent: Color,
    fieldBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when {
        selected -> accent.copy(alpha = 0.14f)
        else -> fieldBg
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .border(width = if (selected) 1.5.dp else 0.dp, color = if (selected) accent else Color.Transparent, shape = RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) accent else Color.Gray, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            color = if (selected) accent else Color.Gray,
            lineHeight = 12.sp,
        )
    }
}

private fun vaccineTypeIcon(type: KBVaccineType): ImageVector = when (type) {
    KBVaccineType.ESAVALENTE, KBVaccineType.HPV, KBVaccineType.INFLUENZA -> Icons.Default.Vaccines
    KBVaccineType.PNEUMOCOCCO, KBVaccineType.MENINGOCOCCO_B, KBVaccineType.MENINGOCOCCO_ACWY -> Icons.Outlined.Psychology
    KBVaccineType.MPR -> Icons.Default.Medication
    KBVaccineType.VARICELLA -> Icons.Outlined.Biotech
    KBVaccineType.ALTRO -> Icons.Outlined.LocalPharmacy
}

@Composable
private fun VaccineTypeCell(
    type: KBVaccineType,
    selected: Boolean,
    salmon: Color,
    fieldBg: Color,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val bg = if (selected) salmon else fieldBg
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp, horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            vaccineTypeIcon(type),
            contentDescription = null,
            tint = if (selected) Color.White else salmon,
            modifier = Modifier.size(24.dp),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            type.displayName,
            fontSize = 12.sp,
            color = if (selected) Color.White else kb.title,
            textAlign = TextAlign.Center,
            maxLines = 2,
        )
    }
}

@Composable
private fun DoseStepper(
    label: String,
    value: Int,
    onChange: (Int) -> Unit,
    salmon: Color,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = kb.subtitle)
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(
                Icons.Default.Remove,
                contentDescription = "Meno",
                tint = if (value > 1) salmon else Color.Gray,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(enabled = value > 1) { onChange(value - 1) },
            )
            Text("$value", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Icon(
                Icons.Default.Add,
                contentDescription = "Più",
                tint = if (value < 10) salmon else Color.Gray,
                modifier = Modifier
                    .size(28.dp)
                    .clickable(enabled = value < 10) { onChange(value + 1) },
            )
        }
    }
}

@Composable
private fun VaccineFilledField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    fieldBg: Color,
) {
    val kb = MaterialTheme.kidBoxColors
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = kb.subtitle) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            unfocusedContainerColor = fieldBg,
            focusedContainerColor = fieldBg,
            unfocusedBorderColor = Color.Transparent,
            focusedBorderColor = Color.Transparent,
        ),
    )
}
