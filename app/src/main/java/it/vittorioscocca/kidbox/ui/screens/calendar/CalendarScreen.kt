package it.vittorioscocca.kidbox.ui.screens.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    familyId: String,
    onBack: () -> Unit,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<KBCalendarEventEntity?>(null) }

    LaunchedEffect(familyId) {
        viewModel.bindFamily(familyId)
        viewModel.onCalendarOpened()
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeaderCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        onClick = onBack,
                    )
                    Text(
                        text = "Calendario",
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    HeaderCircleButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Nuovo evento",
                        onClick = {
                            editingEvent = null
                            showForm = true
                        },
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.kidBoxColors.card)
                    .padding(3.dp),
            ) {
                TogglePill(
                    text = "Mese",
                    selected = state.mode == CalendarMode.MONTH,
                    modifier = Modifier.weight(1f),
                ) { viewModel.setMode(CalendarMode.MONTH) }
                TogglePill(
                    text = "Anno",
                    selected = state.mode == CalendarMode.YEAR,
                    modifier = Modifier.weight(1f),
                ) { viewModel.setMode(CalendarMode.YEAR) }
            }

            when (state.mode) {
                CalendarMode.MONTH -> CalendarMonthView(
                    selectedDate = state.selectedDate,
                    displayedMonth = state.displayedMonth,
                    events = state.events,
                    onSelectDate = viewModel::setSelectedDate,
                    onChangeDisplayedMonth = viewModel::setDisplayedMonth,
                    onEditEvent = {
                        editingEvent = it
                        showForm = true
                    },
                    onDeleteEvent = viewModel::deleteEvent,
                )

                CalendarMode.YEAR -> CalendarYearView(
                    selectedDate = state.selectedDate,
                    events = state.events,
                    onSelectDate = {
                        viewModel.setSelectedDate(it)
                        viewModel.setMode(CalendarMode.MONTH)
                    },
                )
            }
        }
    }

    if (showForm) {
        CalendarEventDialog(
            initial = editingEvent,
            selectedDate = state.selectedDate,
            onDismiss = { showForm = false },
            onSave = { draft ->
                viewModel.saveEvent(draft, editingEvent)
                showForm = false
            },
        )
    }
}

@Composable
private fun CalendarMonthView(
    selectedDate: LocalDate,
    displayedMonth: LocalDate,
    events: List<KBCalendarEventEntity>,
    onSelectDate: (LocalDate) -> Unit,
    onChangeDisplayedMonth: (LocalDate) -> Unit,
    onEditEvent: (KBCalendarEventEntity) -> Unit,
    onDeleteEvent: (KBCalendarEventEntity) -> Unit,
) {
    val eventsByDate = remember(events) {
        events.groupBy {
            Instant.ofEpochMilli(it.startDateEpochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }
    }

    val days = remember(displayedMonth) { monthGridDays(displayedMonth.withDayOfMonth(1)) }
    val locale = Locale("it", "IT")
    val monthLabel = displayedMonth.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onChangeDisplayedMonth(displayedMonth.minusMonths(1).withDayOfMonth(1)) }) {
                Icon(Icons.Default.ChevronLeft, contentDescription = "Mese precedente")
            }
            Text(
                monthLabel,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            IconButton(onClick = { onChangeDisplayedMonth(displayedMonth.plusMonths(1).withDayOfMonth(1)) }) {
                Icon(Icons.Default.ChevronRight, contentDescription = "Mese successivo")
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("D", "L", "M", "M", "G", "V", "S").forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.kidBoxColors.subtitle,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    fontSize = 13.sp,
                )
            }
        }

        days.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(modifier = Modifier.weight(1f).height(46.dp))
                    } else {
                        val hasEvents = eventsByDate[day].isNullOrEmpty().not()
                        val isSelected = day == selectedDate
                        val isToday = day == LocalDate.now()
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp)
                                .clickable { onSelectDate(day) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            isSelected -> Color(0xFF2196F3)
                                            isToday -> Color(0xFFE9F2FF)
                                            else -> Color.Transparent
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = day.dayOfMonth.toString(),
                                    color = if (isSelected) Color.White else MaterialTheme.kidBoxColors.title,
                                    fontWeight = if (isToday || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(4.dp)
                                    .background(if (hasEvents) Color(0xFF42A5F5) else Color.Transparent, CircleShape),
                            )
                        }
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(top = 6.dp))
        val selectedEvents = eventsByDate[selectedDate].orEmpty().sortedBy { it.startDateEpochMillis }
        if (selectedEvents.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint = MaterialTheme.kidBoxColors.subtitle,
                        modifier = Modifier.size(42.dp),
                    )
                    Text("Nessun evento", color = MaterialTheme.kidBoxColors.subtitle, modifier = Modifier.padding(top = 6.dp))
                }
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(selectedEvents, key = { it.id }) { event ->
                    CalendarEventCard(
                        event = event,
                        onEdit = { onEditEvent(event) },
                        onDelete = { onDeleteEvent(event) },
                    )
                }
            }
        }
    }
}

@Composable
private fun CalendarYearView(
    selectedDate: LocalDate,
    events: List<KBCalendarEventEntity>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val currentYear = LocalDate.now().year
    val years = remember(currentYear) { ((currentYear - 80)..(currentYear + 80)).toList() }
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = years.indexOf(currentYear).coerceAtLeast(0),
    )
    val eventDates = remember(events) {
        events.map {
            Instant.ofEpochMilli(it.startDateEpochMillis)
                .atZone(ZoneId.systemDefault())
                .toLocalDate()
        }.toSet()
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(years, key = { it }) { year ->
            YearBlock(
                year = year,
                selectedDate = selectedDate,
                eventDates = eventDates,
                onSelectDate = onSelectDate,
            )
        }
    }
}

@Composable
private fun YearBlock(
    year: Int,
    selectedDate: LocalDate,
    eventDates: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val locale = Locale("it", "IT")
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            if (year == LocalDate.now().year) {
                Text(
                    text = "oggi",
                    color = Color.White,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(Color(0xFF2196F3), RoundedCornerShape(999.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        (1..12).chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { month ->
                    MiniMonthCard(
                        modifier = Modifier.weight(1f),
                        year = year,
                        month = month,
                        locale = locale,
                        selectedDate = selectedDate,
                        eventDates = eventDates,
                        onSelectDate = onSelectDate,
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

@Composable
private fun MiniMonthCard(
    modifier: Modifier,
    year: Int,
    month: Int,
    locale: Locale,
    selectedDate: LocalDate,
    eventDates: Set<LocalDate>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val firstDay = LocalDate.of(year, month, 1)
    val days = monthGridDays(firstDay)
    val monthTitle = firstDay.format(DateTimeFormatter.ofPattern("MMM", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    Card(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(monthTitle, fontWeight = FontWeight.Bold)
            Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
                listOf("L", "M", "M", "G", "V", "S", "D").forEach {
                    Text(
                        text = it,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.kidBoxColors.subtitle,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        fontSize = 9.sp,
                    )
                }
            }
            days.chunked(7).forEach { week ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    week.forEach { day ->
                        if (day == null) {
                            Spacer(modifier = Modifier.weight(1f).height(16.dp))
                        } else {
                            val hasEvent = eventDates.contains(day)
                            val isSelected = selectedDate == day
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(16.dp)
                                    .clickable { onSelectDate(day) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    day.dayOfMonth.toString(),
                                    color = if (isSelected) Color(0xFF1E88E5) else MaterialTheme.kidBoxColors.title,
                                    fontSize = 10.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                )
                                if (hasEvent) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .size(3.dp)
                                            .background(Color(0xFF42A5F5), CircleShape),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarEventCard(
    event: KBCalendarEventEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val start = Instant.ofEpochMilli(event.startDateEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    val end = Instant.ofEpochMilli(event.endDateEpochMillis)
        .atZone(ZoneId.systemDefault())
        .toLocalDateTime()
    val timeLabel = if (event.isAllDay) {
        "Tutto il giorno"
    } else {
        "${start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))} - ${end.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        shape = RoundedCornerShape(14.dp),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(event.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(timeLabel, color = MaterialTheme.kidBoxColors.subtitle, fontSize = 12.sp)
            Text(categoryLabel(event.categoryRaw), color = categoryColor(event.categoryRaw), fontSize = 12.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDelete) {
                    Text("Elimina", color = Color(0xFFD32F2F))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEventDialog(
    initial: KBCalendarEventEntity?,
    selectedDate: LocalDate,
    onDismiss: () -> Unit,
    onSave: (CalendarDraftInput) -> Unit,
) {
    val context = LocalContext.current
    val locale = Locale("it", "IT")
    val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", locale) }

    val initialStart = initial?.let {
        Instant.ofEpochMilli(it.startDateEpochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    } ?: LocalDateTime.of(selectedDate, LocalTime.of(0, 0))

    val initialEnd = initial?.let {
        Instant.ofEpochMilli(it.endDateEpochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    } ?: initialStart.plusHours(1)

    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var notes by remember { mutableStateOf(initial?.notes.orEmpty()) }
    var location by remember { mutableStateOf(initial?.location.orEmpty()) }
    var category by remember { mutableStateOf(initial?.categoryRaw ?: "family") }
    var recurrence by remember { mutableStateOf(initial?.recurrenceRaw ?: "none") }
    var isAllDay by remember { mutableStateOf(initial?.isAllDay ?: false) }
    var reminderOn by remember { mutableStateOf((initial?.reminderMinutes ?: 0) > 0) }
    var startDate by remember { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember { mutableStateOf(initialStart.toLocalTime().withSecond(0).withNano(0)) }
    var endDate by remember { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember { mutableStateOf(initialEnd.toLocalTime().withSecond(0).withNano(0)) }

    fun pickDate(current: LocalDate, onPicked: (LocalDate) -> Unit) {
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth -> onPicked(LocalDate.of(year, month + 1, dayOfMonth)) },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).show()
    }

    fun pickTime(current: LocalTime, onPicked: (LocalTime) -> Unit) {
        TimePickerDialog(
            context,
            { _, hour, minute -> onPicked(LocalTime.of(hour, minute)) },
            current.hour,
            current.minute,
            true,
        ).show()
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val titleText = if (initial == null) "Nuovo evento" else "Modifica evento"

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.kidBoxColors.background,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(text = "Annulla", onClick = onDismiss)
                Spacer(modifier = Modifier.weight(1f))
                Text(text = titleText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(74.dp))
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("TITOLO", fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text("Es. Visita pediatrica") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors(),
                    )
                    Divider()
                    Text("CATEGORIA", fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("children", "school", "health", "family").forEach { raw ->
                            CategoryPill(
                                text = categoryLabel(raw),
                                selected = category == raw,
                                color = categoryColor(raw),
                            ) { category = raw }
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Tutto il giorno", modifier = Modifier.weight(1f), fontSize = 16.sp)
                        Switch(checked = isAllDay, onCheckedChange = { isAllDay = it })
                    }
                    Divider()
                    DateTimeRow(
                        label = "Inizio",
                        dateText = startDate.format(formatter),
                        timeText = startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        allDay = isAllDay,
                        onPickDate = { pickDate(startDate) { startDate = it } },
                        onPickTime = { pickTime(startTime) { startTime = it } },
                    )
                    Divider()
                    DateTimeRow(
                        label = "Fine",
                        dateText = endDate.format(formatter),
                        timeText = endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        allDay = isAllDay,
                        onPickDate = { pickDate(endDate) { endDate = it } },
                        onPickTime = { pickTime(endTime) { endTime = it } },
                    )
                    Divider()
                    Text("RICORRENZA", fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "none" to "Nessuna",
                            "daily" to "Giornaliera",
                            "weekly" to "Settimanale",
                            "monthly" to "Mensile",
                            "yearly" to "Annuale",
                        ).forEach { (raw, label) ->
                            SmallChip(label, selected = recurrence == raw) { recurrence = raw }
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Promemoria", modifier = Modifier.weight(1f), fontSize = 16.sp)
                    Switch(checked = reminderOn, onCheckedChange = { reminderOn = it })
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("LUOGO", fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
                    TextField(
                        value = location,
                        onValueChange = { location = it },
                        placeholder = { Text("Indirizzo o luogo") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = textFieldColors(),
                    )
                    Divider()
                    Text("NOTE", fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
                    TextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text("Aggiungi note...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = textFieldColors(),
                    )
                }
            }

            Button(
                onClick = {
                    if (title.isBlank()) return@Button
                    val startDateTime = LocalDateTime.of(startDate, if (isAllDay) LocalTime.MIDNIGHT else startTime)
                    val endDateTime = LocalDateTime.of(endDate, if (isAllDay) LocalTime.of(23, 59) else endTime)
                    onSave(
                        CalendarDraftInput(
                            title = title,
                            notes = notes,
                            location = location,
                            categoryRaw = category,
                            recurrenceRaw = recurrence,
                            isAllDay = isAllDay,
                            reminderMinutes = if (reminderOn) 30 else null,
                            startEpochMillis = startDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                            endEpochMillis = endDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli(),
                        ),
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(if (initial == null) "Aggiungi evento" else "Salva evento")
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DateTimeRow(
    label: String,
    dateText: String,
    timeText: String,
    allDay: Boolean,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerPill(
                value = dateText,
                onClick = onPickDate,
                modifier = Modifier.weight(1f),
            )
            if (!allDay) {
                PickerPill(
                    value = timeText,
                    onClick = onPickTime,
                    modifier = Modifier.weight(0.65f),
                )
            }
        }
    }
}

@Composable
private fun PickerPill(
    value: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(MaterialTheme.kidBoxColors.card)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
        )
    }
}

@Composable
private fun CategoryPill(
    text: String,
    selected: Boolean,
    color: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) color.copy(alpha = 0.2f) else MaterialTheme.kidBoxColors.card)
            .border(1.dp, if (selected) color else MaterialTheme.kidBoxColors.divider, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Text(text, modifier = Modifier.padding(start = 6.dp), fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun SmallChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) Color(0xFFE9F2FF) else MaterialTheme.kidBoxColors.card)
            .border(1.dp, if (selected) Color(0xFF64B5F6) else MaterialTheme.kidBoxColors.divider, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(text, fontSize = 13.sp, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

@Composable
private fun TogglePill(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) MaterialTheme.kidBoxColors.card else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, fontWeight = FontWeight.SemiBold, color = MaterialTheme.kidBoxColors.title)
    }
}

@Composable
private fun HeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.size(44.dp).clickable(onClick = onClick),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = contentDescription, tint = MaterialTheme.kidBoxColors.title)
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.kidBoxColors.card,
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun textFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
    disabledIndicatorColor = Color.Transparent,
)

private fun monthGridDays(monthFirstDate: LocalDate): List<LocalDate?> {
    // iOS usa prima colonna Domenica (D)
    val leadingEmpty = monthFirstDate.dayOfWeek.value % 7
    val daysInMonth = monthFirstDate.lengthOfMonth()
    val result = mutableListOf<LocalDate?>()
    repeat(leadingEmpty) { result.add(null) }
    for (d in 1..daysInMonth) {
        result.add(monthFirstDate.withDayOfMonth(d))
    }
    while (result.size % 7 != 0) result.add(null)
    return result
}

private fun categoryLabel(raw: String): String = when (raw) {
    "children" -> "Bambini"
    "school" -> "Scuola"
    "health" -> "Salute"
    "family" -> "Famiglia"
    "admin" -> "Amministrazione"
    "leisure" -> "Tempo libero"
    else -> raw
}

private fun categoryColor(raw: String): Color = when (raw) {
    "children" -> Color(0xFFF1C40F)
    "school" -> Color(0xFF3498DB)
    "health" -> Color(0xFFE74C3C)
    "family" -> Color(0xFF2ECC71)
    "admin" -> Color(0xFF7F8C8D)
    "leisure" -> Color(0xFF9B59B6)
    else -> Color(0xFF9E9E9E)
}

