package it.vittorioscocca.kidbox.ui.screens.ai.planning

import android.app.DatePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.docintel.DocIntelAction
import it.vittorioscocca.kidbox.data.docintel.DocIntelPayload
import it.vittorioscocca.kidbox.data.docintel.DocIntelPerson
import it.vittorioscocca.kidbox.data.docintel.DocumentIntelligenceExecutor
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.DateFormat
import java.util.Calendar
import java.util.Date

private val Accent = Color(0xFFFF6B00)

/**
 * Scheda di conferma del documento caricato col «+»: cosa l'AI ha capito, le
 * azioni proposte nelle sezioni giuste, dove finirà il file. Si correggono
 * titolo, importo, data e persona prima di salvare. Gemella di
 * `DocumentIntelligenceSheet.swift`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentIntelligenceSheet(
    payload: DocIntelPayload,
    isSaving: Boolean,
    onConfirm: (List<DocIntelAction>) -> Unit,
    onDismiss: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val actions = remember(payload) { payload.result.actions.toMutableStateList() }
    val selected = remember(payload) { mutableStateListOf<String>().apply { addAll(payload.result.actions.map { it.id }) } }
    var expanded by remember(payload) { mutableStateOf<String?>(null) }

    fun personName(id: String?) = payload.persons.firstOrNull { it.id == id }?.name
    val missingPerson = actions.any { it.id in selected && it.needsPerson && personName(it.childId) == null }
    val attachLabelRes = DocIntelAction.ATTACH_ORDER
        .firstOrNull { t -> actions.any { it.id in selected && it.type == t } }
        ?.let { typeLabelRes(it) }

    ModalBottomSheet(
        onDismissRequest = { if (!isSaving) onDismiss() },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = kb.background,
        contentColor = kb.title,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Text(stringResource(R.string.docintel_sheet_title), color = kb.title, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().background(kb.card, RoundedCornerShape(14.dp)).padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.Description, null, tint = Accent, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        payload.result.documentType ?: stringResource(R.string.docintel_document_analyzed),
                        color = kb.title,
                        fontWeight = FontWeight.SemiBold,
                    )
                    payload.result.suggestedTitle?.let { Text(it, color = kb.subtitle, fontSize = 13.sp) }
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.docintel_proposed_actions), color = kb.subtitle, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                itemsIndexed(actions, key = { _, a -> a.id }) { index, action ->
                    Column(Modifier.fillMaxWidth().background(kb.card, RoundedCornerShape(14.dp)).padding(12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val isOn = action.id in selected
                            IconButton(onClick = { if (isOn) selected.remove(action.id) else selected.add(action.id) }) {
                                Icon(
                                    if (isOn) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                                    contentDescription = null,
                                    tint = if (isOn) Accent else kb.subtitle,
                                )
                            }
                            Icon(typeIcon(action.type), null, tint = Accent, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(typeLabelRes(action.type)), color = kb.title, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text(action.summary ?: action.title ?: "—", color = kb.subtitle, fontSize = 12.sp)
                                if (action.needsPerson) {
                                    val name = personName(action.childId)
                                    Text(
                                        name ?: stringResource(R.string.docintel_choose_person),
                                        color = if (name == null) Color(0xFFF59E0B) else kb.subtitle,
                                        fontSize = 12.sp,
                                        fontWeight = if (name == null) FontWeight.SemiBold else FontWeight.Normal,
                                    )
                                }
                            }
                            if (action.type != "rename_document") {
                                IconButton(onClick = { expanded = if (expanded == action.id) null else action.id }) {
                                    Icon(
                                        if (expanded == action.id) Icons.Default.ExpandLess else Icons.Default.Edit,
                                        contentDescription = stringResource(R.string.docintel_edit),
                                        tint = kb.subtitle,
                                    )
                                }
                            }
                        }
                        if (expanded == action.id) {
                            ActionEditor(
                                action = action,
                                persons = payload.persons,
                                onChange = { actions[index] = it },
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                if (attachLabelRes != null) {
                    stringResource(R.string.docintel_file_attached_to, stringResource(attachLabelRes))
                } else {
                    stringResource(R.string.docintel_file_to_documents)
                },
                color = kb.subtitle,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onDismiss, enabled = !isSaving) { Text(stringResource(R.string.docintel_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = { onConfirm(actions.filter { it.id in selected }) },
                    enabled = !isSaving && !missingPerson,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(stringResource(R.string.docintel_confirm))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

/** Campi correggibili di un'azione: titolo, importo, data, persona. */
@Composable
private fun ActionEditor(
    action: DocIntelAction,
    persons: List<DocIntelPerson>,
    onChange: (DocIntelAction) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    Column(Modifier.fillMaxWidth().padding(start = 12.dp, top = 6.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = action.title.orEmpty(),
            onValueChange = { onChange(action.copy(title = it)) },
            label = { Text(stringResource(R.string.docintel_field_title)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        if (action.type in setOf("expense_add", "house_payment", "vehicle_event")) {
            var text by remember(action.id) { mutableStateOf(action.amount?.let { "%.2f".format(it) }.orEmpty()) }
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onChange(action.copy(amount = it.replace(',', '.').trim().toDoubleOrNull()))
                },
                label = { Text(stringResource(R.string.docintel_field_amount)) },
                suffix = { Text("€") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (action.type != "note_add") {
            val epoch = DocumentIntelligenceExecutor.parseEpoch(action.date)
            val labelRes = when (action.type) {
                "house_payment" -> R.string.docintel_field_due
                "treatment_add" -> R.string.docintel_field_start
                else -> R.string.docintel_field_date
            }
            Row(
                Modifier.fillMaxWidth().clickable {
                    val cal = Calendar.getInstance().apply { timeInMillis = epoch ?: System.currentTimeMillis() }
                    DatePickerDialog(context, { _, y, m, d ->
                        val picked = Calendar.getInstance().apply {
                            timeInMillis = epoch ?: System.currentTimeMillis()
                            set(y, m, d)
                        }
                        onChange(action.copy(date = DocumentIntelligenceExecutor.formatIso(picked.timeInMillis)))
                    }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
                }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.CalendarMonth, null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(labelRes), color = kb.title, modifier = Modifier.weight(1f))
                Text(
                    epoch?.let { DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: "—",
                    color = Accent,
                )
            }
        }
        if (action.needsPerson) {
            var open by remember { mutableStateOf(false) }
            val choices = persons.filter { action.type != "vaccine_add" || !it.isAdult }
            Box {
                Row(
                    Modifier.fillMaxWidth().clickable { open = true }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Default.Person, null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.docintel_field_person), color = kb.title, modifier = Modifier.weight(1f))
                    Text(
                        choices.firstOrNull { it.id == action.childId }?.name ?: stringResource(R.string.docintel_choose),
                        color = Accent,
                    )
                }
                DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                    choices.forEach { p ->
                        DropdownMenuItem(
                            text = { Text(p.name) },
                            onClick = {
                                open = false
                                onChange(action.copy(childId = p.id))
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun typeLabelRes(type: String): Int = when (type) {
    "expense_add" -> R.string.docintel_type_expense
    "event_add" -> R.string.docintel_type_event
    "todo_add" -> R.string.docintel_type_todo
    "health_reminder" -> R.string.docintel_type_health_reminder
    "note_add" -> R.string.docintel_type_note
    "vehicle_event" -> R.string.docintel_type_vehicle
    "medical_visit" -> R.string.docintel_type_visit
    "exam_add" -> R.string.docintel_type_exam
    "treatment_add" -> R.string.docintel_type_treatment
    "house_payment" -> R.string.docintel_type_house
    "vaccine_add" -> R.string.docintel_type_vaccine
    "rename_document" -> R.string.docintel_type_rename
    else -> R.string.docintel_type_action
}

private fun typeIcon(type: String): ImageVector = when (type) {
    "expense_add" -> Icons.Default.Euro
    "event_add" -> Icons.Default.CalendarMonth
    "todo_add" -> Icons.Default.TaskAlt
    "health_reminder" -> Icons.Default.Notifications
    "note_add" -> Icons.Default.StickyNote2
    "vehicle_event" -> Icons.Default.DirectionsCar
    "medical_visit" -> Icons.Default.MedicalServices
    "exam_add" -> Icons.Default.Science
    "treatment_add" -> Icons.Default.Medication
    "house_payment" -> Icons.Default.Home
    "vaccine_add" -> Icons.Default.Vaccines
    else -> Icons.Default.Edit
}
