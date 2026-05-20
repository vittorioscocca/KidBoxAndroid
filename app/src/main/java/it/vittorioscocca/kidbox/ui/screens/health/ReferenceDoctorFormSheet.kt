@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health

import android.content.res.Configuration
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.domain.model.KBDoctorOfficeHourSlot
import it.vittorioscocca.kidbox.domain.model.KBItalianWeekdays
import it.vittorioscocca.kidbox.domain.model.ReferenceDoctorDraft
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.util.Locale
import java.util.UUID

private val iosBlue = Color(0xFF0A84FF)
private val cardFieldColors @Composable get() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

/**
 * Schermata modale allineata a iOS [ReferenceDoctorFormView]:
 * toolbar Annulla / titolo / Salva, sezioni con card bianche e campi separati da divisori.
 */
@Composable
fun ReferenceDoctorFormScreen(
    isChild: Boolean,
    initial: ReferenceDoctorDraft,
    onDismiss: () -> Unit,
    onSave: (ReferenceDoctorDraft) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    var name by remember(initial) { mutableStateOf(initial.name) }
    var email by remember(initial) { mutableStateOf(initial.email) }
    var address by remember(initial) { mutableStateOf(initial.address) }
    var website by remember(initial) { mutableStateOf(initial.website) }
    val officeHours = remember(initial) {
        mutableStateListOf<KBDoctorOfficeHourSlot>().apply { addAll(initial.officeHours) }
    }

    val title = if (isChild) "Pediatra di riferimento" else "Medico di riferimento"
    val canSave = name.isNotBlank()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // ── Toolbar (Annulla · titolo · Salva) ─────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ToolbarPillButton(text = "Annulla", onClick = onDismiss)
                Text(
                    text = title,
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                ToolbarPillButton(
                    text = "Salva",
                    enabled = canSave,
                    onClick = {
                        onSave(
                            ReferenceDoctorDraft(
                                name = name.trim(),
                                email = email.trim(),
                                address = address.trim(),
                                website = website.trim(),
                                officeHours = officeHours.toList(),
                            ),
                        )
                    },
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 18.dp),
            ) {
                // ── Dati medico ──────────────────────────────────────────────────
                FormSectionLabel("Dati medico")
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    FormCardTextField(
                        value = name,
                        onValueChange = { name = it },
                        placeholder = "Nome e cognome",
                    )
                    FormDivider()
                    FormCardTextField(
                        value = email,
                        onValueChange = { email = it },
                        placeholder = "Email",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            capitalization = KeyboardCapitalization.None,
                            autoCorrect = false,
                        ),
                    )
                    FormDivider()
                    FormCardTextField(
                        value = address,
                        onValueChange = { address = it },
                        placeholder = "Indirizzo studio",
                        minLines = 2,
                    )
                    FormDivider()
                    FormCardTextField(
                        value = website,
                        onValueChange = { website = it },
                        placeholder = "Sito web (opzionale)",
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Uri,
                            capitalization = KeyboardCapitalization.None,
                            autoCorrect = false,
                        ),
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── Orari di studio ──────────────────────────────────────────────
                FormSectionLabel("Orari di studio")
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (officeHours.isEmpty()) {
                        Text(
                            text = "Nessun orario di ricevimento",
                            color = kb.subtitle,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
                        )
                    } else {
                        officeHours.forEachIndexed { index, slot ->
                            OfficeHourSlotEditor(
                                slot = slot,
                                onChange = { officeHours[index] = it },
                                onDelete = { officeHours.removeAt(index) },
                            )
                            if (index < officeHours.lastIndex) {
                                FormDivider()
                            }
                        }
                    }
                    FormDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = iosBlue,
                        )
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                officeHours.add(
                                    KBDoctorOfficeHourSlot(
                                        id = UUID.randomUUID().toString(),
                                        weekday = KBItalianWeekdays.all.first(),
                                        fromTime = "09:00",
                                        toTime = "13:00",
                                    ),
                                )
                            },
                        ) {
                            Text(
                                "Aggiungi fascia oraria",
                                color = iosBlue,
                                fontSize = 17.sp,
                            )
                        }
                    }
                }
                Text(
                    text = "Indica il giorno e scegli le fasce dalle/alle con i selettori orario.",
                    fontSize = 12.sp,
                    color = kb.subtitle,
                    modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
                )
                Spacer(Modifier.height(40.dp))
            }
        }
    }
}

/** @deprecated Usa [ReferenceDoctorFormScreen]; mantenuto per compatibilità. */
@Composable
fun ReferenceDoctorFormDialog(
    isChild: Boolean,
    initial: ReferenceDoctorDraft,
    onDismiss: () -> Unit,
    onSave: (ReferenceDoctorDraft) -> Unit,
) {
    ReferenceDoctorFormScreen(
        isChild = isChild,
        initial = initial,
        onDismiss = onDismiss,
        onSave = onSave,
    )
}

@Composable
private fun ToolbarPillButton(
    text: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier
            .clip(RoundedCornerShape(22.dp))
            .background(Color.White),
    ) {
        Text(
            text = text,
            fontSize = 17.sp,
            fontWeight = FontWeight.Medium,
            color = if (enabled) iosBlue else kb.subtitle.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun FormSectionLabel(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = kb.subtitle)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun FormDivider() {
    HorizontalDivider(
        color = MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.15f),
        modifier = Modifier.padding(horizontal = 16.dp),
    )
}

@Composable
private fun FormCardTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    val kb = MaterialTheme.kidBoxColors
    TextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = kb.subtitle) },
        modifier = Modifier.fillMaxWidth(),
        minLines = minLines,
        singleLine = minLines == 1,
        keyboardOptions = keyboardOptions,
        colors = cardFieldColors,
        textStyle = androidx.compose.ui.text.TextStyle(
            fontSize = 17.sp,
            color = kb.title,
        ),
    )
}

@Composable
private fun OfficeHourSlotEditor(
    slot: KBDoctorOfficeHourSlot,
    onChange: (KBDoctorOfficeHourSlot) -> Unit,
    onDelete: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var weekdayExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ExposedDropdownMenuBox(
                expanded = weekdayExpanded,
                onExpandedChange = { weekdayExpanded = it },
                modifier = Modifier.weight(1f),
            ) {
                TextField(
                    value = slot.weekday,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Giorno", fontSize = 12.sp, color = kb.subtitle) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = weekdayExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor()
                        .fillMaxWidth(),
                    colors = cardFieldColors,
                    textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = kb.title),
                )
                ExposedDropdownMenu(
                    expanded = weekdayExpanded,
                    onDismissRequest = { weekdayExpanded = false },
                ) {
                    KBItalianWeekdays.all.forEach { day ->
                        DropdownMenuItem(
                            text = { Text(day) },
                            onClick = {
                                onChange(slot.copy(weekday = day))
                                weekdayExpanded = false
                            },
                        )
                    }
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Rimuovi fascia", tint = Color(0xFFFF3B30))
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OfficeHourTimePickerField(
                label = "Dalle",
                time = slot.fromTime,
                onTimeChange = { onChange(slot.copy(fromTime = it)) },
                modifier = Modifier.weight(1f),
            )
            OfficeHourTimePickerField(
                label = "Alle",
                time = slot.toTime,
                onTimeChange = { onChange(slot.copy(toTime = it)) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private val OfficeHourLocaleIt = Locale.forLanguageTag("it-IT")

@Composable
private fun OfficeHourTimePickerField(
    label: String,
    time: String,
    onTimeChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    var showPicker by remember { mutableStateOf(false) }
    val (hour, minute) = remember(time) { parseOfficeHourTime(time) }

    Column(modifier = modifier) {
        Text(label, fontSize = 12.sp, color = kb.subtitle, modifier = Modifier.padding(start = 12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { showPicker = true },
        ) {
            TextField(
                value = time,
                onValueChange = {},
                readOnly = true,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = cardFieldColors,
                textStyle = androidx.compose.ui.text.TextStyle(fontSize = 17.sp, color = kb.title),
            )
        }
    }

    if (showPicker) {
        val pickerState = rememberTimePickerState(
            initialHour = hour,
            initialMinute = minute,
            is24Hour = true,
        )
        val timeConfig = Configuration(LocalConfiguration.current).apply {
            setLocale(OfficeHourLocaleIt)
        }
        Dialog(onDismissRequest = { showPicker = false }) {
            CompositionLocalProvider(LocalConfiguration provides timeConfig) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(label, fontWeight = FontWeight.SemiBold, fontSize = 17.sp, color = kb.title)
                    Spacer(Modifier.height(8.dp))
                    TimePicker(state = pickerState)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                    ) {
                        TextButton(onClick = { showPicker = false }) {
                            Text("Annulla", color = iosBlue)
                        }
                        TextButton(
                            onClick = {
                                onTimeChange(formatOfficeHourTime(pickerState.hour, pickerState.minute))
                                showPicker = false
                            },
                        ) {
                            Text("OK", color = iosBlue)
                        }
                    }
                }
            }
        }
    }
}

private fun parseOfficeHourTime(time: String): Pair<Int, Int> {
    val parts = time.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 9
    val minute = parts.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0
    return hour to minute
}

private fun formatOfficeHourTime(hour: Int, minute: Int): String =
    "%02d:%02d".format(hour, minute)
