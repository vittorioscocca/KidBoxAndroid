package it.vittorioscocca.kidbox.ui.screens.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.screens.notes.VisibilityPickerMember
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.ui.util.visibilityChipLabel
import it.vittorioscocca.kidbox.util.KBLocale
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

/**
 * Il ramo «Promemoria» del calendario: un to-do vero, con la lista in cui
 * finisce e i campi standard dei to-do. Gemello di `CalendarReminderFormView`
 * su iOS.
 *
 * La forma ricalca Promemoria di Apple — nome, note, data, ora con
 * interruttore, urgente, elenco. L'unica differenza dichiarata è che qui
 * «Urgente» è anche la scelta della **sveglia**, non un'etichetta di colore.
 */
@Composable
fun CalendarReminderFormContent(
    initial: KBTodoItemEntity?,
    selectedDate: LocalDate,
    initialTime: LocalTime?,
    currentUid: String?,
    todoLists: List<KBTodoListEntity>,
    members: List<VisibilityPickerMember>,
    /**
     * Visibilità in lavorazione. Vive nella schermata e non qui perché il
     * selettore deve aprirsi **fuori** dal foglio: annidare una finestra
     * dentro un `ModalBottomSheet` si rompe su MIUI e derivate.
     */
    visibilityScope: String,
    visibilityMemberIds: Set<String>,
    onRequestVisibilityPicker: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (CalendarReminderDraft) -> Unit,
    kindSelector: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val kb = MaterialTheme.kidBoxColors
    val colorScheme = MaterialTheme.colorScheme
    val locale = KBLocale.current()

    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var hasTime by remember(initial?.id) { mutableStateOf(initial?.dueHasTime ?: true) }
    var urgent by remember(initial?.id) { mutableStateOf((initial?.priorityRaw ?: 0) == 1) }
    var dueAt by remember(initial?.id) {
        mutableLongStateOf(
            initial?.dueAtEpochMillis ?: suggestedReminderMillis(selectedDate, initialTime),
        )
    }
    var listId by remember(initial?.id, todoLists) {
        mutableStateOf(initial?.listId ?: todoLists.firstOrNull()?.id.orEmpty())
    }
    var assignedTo by remember(initial?.id) { mutableStateOf(initial?.assignedTo) }
    var showListPicker by remember { mutableStateOf(false) }
    var showAssigneePicker by remember { mutableStateOf(false) }

    var showVisibilityLocked by remember { mutableStateOf(false) }

    val displayScope = KBVisibilityScope.normalized(visibilityScope)
    // Un promemoria «solo io» non lo vede nessun altro: l'unico assegnatario
    // sensato è chi lo ha creato. Stessa regola del form To-Do e di iOS, dove
    // la scheda dell'assegnatario proprio non compare.
    val isPrivateScope = displayScope == KBVisibilityScope.ONLY_CREATOR
    // La visibilità la cambia solo chi l'ha creato, come nel form To-Do.
    val canEditVisibility = initial == null ||
        initial.createdBy.isNullOrBlank() ||
        initial.createdBy == currentUid

    fun pickDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = dueAt }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                dueAt = cal.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun pickTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = dueAt }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                dueAt = cal.timeInMillis
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true,
        ).show()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            // Il foglio è a tutta altezza e vive in una finestra propria:
            // senza questi padding la prima riga finisce sotto la barra di
            // stato. Stessa cura della scheda evento.
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 16.dp, bottom = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.calendar_cancel), color = kb.title)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = if (initial == null) {
                    stringResource(R.string.calendar_new_reminder_title)
                } else {
                    stringResource(R.string.calendar_edit_reminder_title)
                },
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                color = kb.title,
            )
            Spacer(modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.size(74.dp))
        }

        kindSelector?.invoke()

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = kb.card)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.calendar_reminder_title_placeholder),
                            color = kb.subtitle.copy(alpha = 0.72f),
                        )
                    },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = reminderTextFieldColors(),
                )
                Divider()
                TextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = {
                        Text(
                            stringResource(R.string.calendar_notes_placeholder),
                            color = kb.subtitle.copy(alpha = 0.72f),
                        )
                    },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    colors = reminderTextFieldColors(),
                )
                Divider()
                // La visibilità sta qui, in fondo alla scheda del testo, dove
                // la mette anche iOS (`textCard`): è una proprietà del
                // promemoria, non della sua scadenza.
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(R.string.todo_visibility),
                        fontSize = 12.sp,
                        color = kb.subtitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                if (canEditVisibility) {
                                    onRequestVisibilityPicker()
                                } else {
                                    showVisibilityLocked = true
                                }
                            }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            visibilityChipLabel(displayScope),
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(kb.surfaceOverlay)
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            fontSize = 14.sp,
                            color = kb.title,
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        if (canEditVisibility) {
                            Text(
                                stringResource(R.string.health_change),
                                fontSize = 14.sp,
                                color = kb.subtitle,
                            )
                        }
                    }
                }
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = kb.card)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    stringResource(R.string.calendar_date_and_time),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                    fontWeight = FontWeight.SemiBold,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.calendar_date_label),
                        modifier = Modifier.weight(1f),
                        color = kb.title,
                    )
                    Card(
                        modifier = Modifier.clickable { pickDate() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = kb.surfaceOverlay),
                    ) {
                        Text(
                            text = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault())
                                .format(DateTimeFormatter.ofPattern("d MMM yyyy", locale)),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            fontSize = 14.sp,
                            color = kb.title,
                        )
                    }
                }
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.calendar_time_label),
                        modifier = Modifier.weight(1f),
                        color = kb.title,
                    )
                    if (hasTime) {
                        Card(
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .clickable { pickTime() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = kb.surfaceOverlay),
                        ) {
                            Text(
                                text = Instant.ofEpochMilli(dueAt).atZone(ZoneId.systemDefault())
                                    .format(DateTimeFormatter.ofPattern("HH:mm")),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                fontSize = 14.sp,
                                color = kb.title,
                            )
                        }
                    }
                    Switch(
                        checked = hasTime,
                        onCheckedChange = { hasTime = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.surface,
                            checkedTrackColor = colorScheme.primary,
                            uncheckedThumbColor = kb.subtitle,
                            uncheckedTrackColor = kb.surfaceOverlay,
                        ),
                    )
                }
                Divider()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.todo_urgent),
                        modifier = Modifier.weight(1f),
                        color = kb.title,
                    )
                    Switch(
                        checked = urgent,
                        onCheckedChange = { urgent = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.surface,
                            checkedTrackColor = colorScheme.primary,
                            uncheckedThumbColor = kb.subtitle,
                            uncheckedTrackColor = kb.surfaceOverlay,
                        ),
                    )
                }
                Text(
                    stringResource(R.string.urgent_reminder_hint),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
            }
        }

        Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = kb.card)) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(kb.surfaceOverlay)
                        .clickable(enabled = todoLists.isNotEmpty()) { showListPicker = true }
                        .padding(horizontal = 12.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(R.string.calendar_reminder_list_label),
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        color = kb.subtitle,
                    )
                    Text(
                        todoLists.firstOrNull { it.id == listId }?.name
                            ?: stringResource(R.string.calendar_reminder_list_default),
                        fontSize = 15.sp,
                        color = kb.title,
                    )
                }
                if (!isPrivateScope) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(kb.surfaceOverlay)
                            .clickable { showAssigneePicker = true }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.todo_assigned_to),
                            modifier = Modifier.weight(1f),
                            fontSize = 13.sp,
                            color = kb.subtitle,
                        )
                        Text(
                            members.firstOrNull { it.uid == assignedTo }?.displayName
                                ?: stringResource(R.string.todo_nobody),
                            fontSize = 15.sp,
                            color = kb.title,
                        )
                    }
                }
            }
        }

        Button(
            onClick = {
                val clean = title.trim()
                if (clean.isBlank()) return@Button
                onSave(
                    CalendarReminderDraft(
                        title = clean,
                        notes = notes.trim().takeIf { it.isNotEmpty() },
                        // Senza orario il promemoria suona alle 9:00, come
                        // «tutto il giorno» in Promemoria di Apple: un avviso
                        // a mezzanotte non lo legge nessuno.
                        dueAtEpochMillis = if (hasTime) dueAt else atNineInTheMorning(dueAt),
                        dueHasTime = hasTime,
                        isUrgent = urgent,
                        listId = listId,
                        assignedTo = if (isPrivateScope) {
                            currentUid?.takeIf { it.isNotBlank() }
                        } else {
                            assignedTo
                        },
                        // Questo form non ha il selettore di visibilità (su iOS
                        // sì): quindi la visibilità si **riporta**, scope e
                        // membri insieme. Lasciare i membri vuoti non voleva
                        // dire «non li tocco» — `updateTodo` distingue `null`
                        // (mantieni) da lista vuota (sovrascrivi), e un
                        // promemoria condiviso con persone scelte usciva di qui
                        // con lo scope «members» e nessuno dentro: sparito
                        // dalla vista di chi lo vedeva.
                        visibilityScope = displayScope,
                        // I membri contano solo per «alcuni membri»: negli
                        // altri scope una lista piena sarebbe rumore che
                        // qualcuno prima o poi legge per sbaglio.
                        visibilityMemberIds = if (displayScope == KBVisibilityScope.MEMBERS) {
                            visibilityMemberIds.toList().sorted()
                        } else {
                            emptyList()
                        },
                    ),
                )
                onDismiss()
            },
            enabled = title.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(999.dp),
        ) {
            Text(
                if (initial == null) {
                    stringResource(R.string.calendar_add_reminder)
                } else {
                    stringResource(R.string.calendar_save_reminder)
                },
                color = colorScheme.onPrimary,
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
    }

    if (showVisibilityLocked) {
        AlertDialog(
            onDismissRequest = { showVisibilityLocked = false },
            title = { Text(stringResource(R.string.todo_visibility_locked)) },
            text = { Text(stringResource(R.string.calendar_reminder_visibility_locked_hint)) },
            confirmButton = {
                TextButton(onClick = { showVisibilityLocked = false }) {
                    Text(stringResource(R.string.subscription_ok))
                }
            },
        )
    }

    if (showListPicker) {
        AlertDialog(
            onDismissRequest = { showListPicker = false },
            title = { Text(stringResource(R.string.calendar_reminder_list_label)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    todoLists.forEach { list ->
                        Text(
                            text = list.name,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    listId = list.id
                                    showListPicker = false
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showListPicker = false }) {
                    Text(stringResource(R.string.photos_close))
                }
            },
        )
    }

    if (showAssigneePicker) {
        AlertDialog(
            onDismissRequest = { showAssigneePicker = false },
            title = { Text(stringResource(R.string.todo_assign_to)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.todo_nobody),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                assignedTo = null
                                showAssigneePicker = false
                            }
                            .padding(vertical = 8.dp),
                    )
                    members.forEach { member ->
                        Text(
                            text = if (member.uid == currentUid) {
                                stringResource(R.string.todo_me)
                            } else {
                                member.displayName
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    assignedTo = member.uid
                                    showAssigneePicker = false
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showAssigneePicker = false }) {
                    Text(stringResource(R.string.photos_close))
                }
            },
        )
    }
}

/** Prossima mezz'ora tonda se è oggi, altrimenti le 9:00 del giorno scelto. */
private fun suggestedReminderMillis(day: LocalDate, initialTime: LocalTime?): Long {
    val zone = ZoneId.systemDefault()
    if (initialTime != null) {
        return day.atTime(initialTime.withSecond(0).withNano(0)).atZone(zone).toInstant().toEpochMilli()
    }
    if (day == LocalDate.now()) {
        val next = LocalTime.now().plusMinutes(30).withSecond(0).withNano(0)
        val rounded = if (next.minute < 30) next.withMinute(30) else next.plusHours(1).withMinute(0)
        return day.atTime(rounded).atZone(zone).toInstant().toEpochMilli()
    }
    return day.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
}

/** L'ora a cui suona un promemoria senza orario. */
private fun atNineInTheMorning(epochMillis: Long): Long {
    val zone = ZoneId.systemDefault()
    return Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        .atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
}

/**
 * Campi di testo senza riquadro, come nella scheda evento: gemello privato di
 * `calendarTextFieldColors`, che vive in CalendarScreen.kt.
 */
@Composable
private fun reminderTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    errorContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
    errorIndicatorColor = Color.Transparent,
    cursorColor = MaterialTheme.kidBoxColors.title,
    focusedTextColor = MaterialTheme.kidBoxColors.title,
    unfocusedTextColor = MaterialTheme.kidBoxColors.title,
)
