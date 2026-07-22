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
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.screens.notes.VisibilityPickerFullscreenDialog
import it.vittorioscocca.kidbox.ui.screens.notes.VisibilityPickerMember
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.KBLocale

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
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    // Visibility state hoisted here so the picker dialog can be shown OUTSIDE the bottom sheet,
    // avoiding the nested-sheet issue on MIUI and other ROM variants.
    var showVisibilityPicker by remember { mutableStateOf(false) }
    var draftVisibilityScope by remember { mutableStateOf(KBVisibilityScope.FAMILY) }
    var draftVisibilityMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Sync draft visibility whenever the form is opened or the edited event changes.
    LaunchedEffect(showForm, editingEvent?.id) {
        if (showForm) {
            val evt = editingEvent
            draftVisibilityScope = KBVisibilityScope.normalized(evt?.visibilityScope)
            draftVisibilityMemberIds = decodeStringList(evt?.visibilityMemberIdsJson).toSet()
        } else {
            showVisibilityPicker = false
        }
    }

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
                        contentDescription = stringResource(R.string.calendar_back_cd),
                        onClick = onBack,
                    )
                    Text(
                        text = stringResource(R.string.calendar_title),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    HeaderCircleButton(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.calendar_new_event_cd),
                        onClick = {
                            editingEvent = null
                            showForm = true
                        },
                    )
                }
            }
        },
        containerColor = MaterialTheme.kidBoxColors.background,
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
                    text = stringResource(R.string.calendar_month_tab),
                    selected = state.mode == CalendarMode.MONTH,
                    modifier = Modifier.weight(1f),
                ) { viewModel.setMode(CalendarMode.MONTH) }
                TogglePill(
                    text = stringResource(R.string.calendar_year_tab),
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
            currentUid = currentUid,
            visibilityScope = draftVisibilityScope,
            visibilityMemberIds = draftVisibilityMemberIds,
            onRequestVisibilityPicker = { showVisibilityPicker = true },
            onDismiss = { showForm = false },
            onSave = { draft ->
                viewModel.saveEvent(draft, editingEvent)
                showForm = false
            },
        )
    }

    // The picker is a sibling of CalendarEventDialog (NOT nested inside its ModalBottomSheet).
    if (showVisibilityPicker && showForm) {
        VisibilityPickerFullscreenDialog(
            currentUid = currentUid,
            scopeSectionTitle = "Chi può vedere questo evento?",
            membersExcludingSelf = state.visibilityMembers,
            initialScope = draftVisibilityScope,
            initialMemberIds = draftVisibilityMemberIds.toList(),
            onDismiss = { showVisibilityPicker = false },
            onConfirmed = { scope, ids ->
                draftVisibilityScope = KBVisibilityScope.normalized(scope)
                draftVisibilityMemberIds = ids.toSet()
                showVisibilityPicker = false
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
        buildEventsByDay(events)
    }

    val days = remember(displayedMonth) { monthGridDays(displayedMonth.withDayOfMonth(1)) }
    val locale = KBLocale.current()
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
                    Text(stringResource(R.string.calendar_no_events), color = MaterialTheme.kidBoxColors.subtitle, modifier = Modifier.padding(top = 6.dp))
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
        buildEventDatesSet(events)
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
    val locale = KBLocale.current()
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
    val days = remember(firstDay) {
        monthGridDays(firstDay).toMutableList().apply {
            while (size < 42) add(null)
        }
    }
    val monthTitle = firstDay.format(DateTimeFormatter.ofPattern("MMM", locale))
        .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }

    Card(
        modifier = modifier.height(172.dp),
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
            Text(event.title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = MaterialTheme.kidBoxColors.title)
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
    currentUid: String?,
    /** Current visibility selection – owned by CalendarScreen so the picker can open outside this sheet. */
    visibilityScope: String,
    visibilityMemberIds: Set<String>,
    /** Called when the user taps "Cambia" – CalendarScreen will open the full-screen picker. */
    onRequestVisibilityPicker: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (CalendarDraftInput) -> Unit,
) {
    val context = LocalContext.current
    val locale = KBLocale.current()
    val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", locale) }
    val kb = MaterialTheme.kidBoxColors
    val colorScheme = MaterialTheme.colorScheme

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

    val canEditVisibility = remember(initial?.id, currentUid) {
        when {
            initial == null -> true
            initial.createdBy.isBlank() -> true
            else -> initial.createdBy == (currentUid?.takeIf { it.isNotBlank() } ?: "")
        }
    }

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
    val titleText = if (initial == null) stringResource(R.string.calendar_new_event_title) else stringResource(R.string.calendar_edit_event_title)

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
                Text(
                    text = titleText,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = kb.title,
                )
                Spacer(modifier = Modifier.weight(1f))
                Spacer(modifier = Modifier.size(74.dp))
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.calendar_field_title_label), fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
                    TextField(
                        value = title,
                        onValueChange = { title = it },
                        placeholder = { Text(stringResource(R.string.calendar_title_placeholder), color = kb.subtitle.copy(alpha = 0.72f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = calendarTextFieldColors(),
                    )
                    Divider()
                    Text(stringResource(R.string.calendar_field_category_label), fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
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
                        Text(
                            "Tutto il giorno",
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            color = kb.title,
                        )
                        Switch(
                            checked = isAllDay,
                            onCheckedChange = { isAllDay = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = colorScheme.surface,
                                checkedTrackColor = colorScheme.primary,
                                uncheckedThumbColor = kb.subtitle,
                                uncheckedTrackColor = kb.surfaceOverlay,
                            ),
                        )
                    }
                    Divider()
                    DateTimeRow(
                        label = stringResource(R.string.calendar_start_label),
                        dateText = startDate.format(formatter),
                        timeText = startTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        allDay = isAllDay,
                        labelColor = kb.title,
                        valueColor = kb.title,
                        onPickDate = { pickDate(startDate) { startDate = it } },
                        onPickTime = { pickTime(startTime) { startTime = it } },
                    )
                    Divider()
                    DateTimeRow(
                        label = stringResource(R.string.calendar_end_label),
                        dateText = endDate.format(formatter),
                        timeText = endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        allDay = isAllDay,
                        labelColor = kb.title,
                        valueColor = kb.title,
                        onPickDate = { pickDate(endDate) { endDate = it } },
                        onPickTime = { pickTime(endTime) { endTime = it } },
                    )
                    Divider()
                    Text(stringResource(R.string.section_recurrence), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            "none" to "Nessuna",
                            "daily" to "Giornaliera",
                            "weekly" to "Settimanale",
                            "monthly" to "Mensile",
                            "yearly" to "Annuale",
                        ).forEach { (raw, label) ->
                            SmallChip(
                                label,
                                selected = recurrence == raw,
                                selectedBg = colorScheme.primaryContainer,
                                selectedContent = colorScheme.onPrimaryContainer,
                                unselectedContent = kb.title,
                            ) { recurrence = raw }
                        }
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Promemoria",
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp,
                        color = kb.title,
                    )
                    Switch(
                        checked = reminderOn,
                        onCheckedChange = { reminderOn = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = colorScheme.surface,
                            checkedTrackColor = colorScheme.primary,
                            uncheckedThumbColor = kb.subtitle,
                            uncheckedTrackColor = kb.surfaceOverlay,
                        ),
                    )
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = kb.card)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        stringResource(R.string.calendar_field_visibility_label),
                        fontSize = 12.sp,
                        color = kb.subtitle,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    if (canEditVisibility) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(kb.surfaceOverlay)
                                .clickable { onRequestVisibilityPicker() }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    KBVisibilityScope.chipLabel(visibilityScope),
                                    color = kb.title,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                )
                            }
                            Text("Cambia ›", color = kb.subtitle, fontSize = 14.sp)
                        }
                    } else {
                        Text(
                            KBVisibilityScope.chipLabel(
                                KBVisibilityScope.normalized(initial?.visibilityScope ?: KBVisibilityScope.FAMILY),
                            ),
                            color = kb.title,
                            fontWeight = FontWeight.Medium,
                            fontSize = 15.sp,
                        )
                        Text(
                            "Solo chi ha creato l'evento può modificare la visibilità.",
                            color = kb.subtitle,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card)) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.calendar_field_location_label), fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
                    TextField(
                        value = location,
                        onValueChange = { location = it },
                        placeholder = { Text(stringResource(R.string.calendar_location_placeholder), color = kb.subtitle.copy(alpha = 0.72f)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = calendarTextFieldColors(),
                    )
                    Divider()
                    Text(stringResource(R.string.calendar_field_notes_label), fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
                    TextField(
                        value = notes,
                        onValueChange = { notes = it },
                        placeholder = { Text(stringResource(R.string.calendar_notes_placeholder), color = kb.subtitle.copy(alpha = 0.72f)) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        colors = calendarTextFieldColors(),
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
                            visibilityScope = visibilityScope,
                            visibilityMemberIds = visibilityMemberIds.toList().sorted(),
                        ),
                    )
                },
                enabled = title.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(999.dp),
            ) {
                Text(
                    if (initial == null) "Aggiungi evento" else "Salva evento",
                    color = colorScheme.onPrimary,
                )
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
    labelColor: Color,
    valueColor: Color,
    onPickDate: () -> Unit,
    onPickTime: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = labelColor,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PickerPill(
                value = dateText,
                textColor = valueColor,
                onClick = onPickDate,
                modifier = Modifier.weight(1f),
            )
            if (!allDay) {
                PickerPill(
                    value = timeText,
                    textColor = valueColor,
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
    textColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(kb.surfaceOverlay)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            value,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            fontSize = 15.sp,
            color = textColor,
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
    val kb = MaterialTheme.kidBoxColors
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
        Text(
            text,
            modifier = Modifier.padding(start = 6.dp),
            fontWeight = FontWeight.Medium,
            color = kb.title,
        )
    }
}

@Composable
private fun SmallChip(
    text: String,
    selected: Boolean,
    selectedBg: Color,
    selectedContent: Color,
    unselectedContent: Color,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val border = if (selected) MaterialTheme.colorScheme.primary else kb.divider
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) selectedBg else Color.Transparent)
            .border(1.dp, border, RoundedCornerShape(999.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) selectedContent else unselectedContent,
        )
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
            color = MaterialTheme.kidBoxColors.title,
        )
    }
}

@Composable
private fun calendarTextFieldColors() = TextFieldDefaults.colors(
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
    focusedPlaceholderColor = MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.72f),
    unfocusedPlaceholderColor = MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.72f),
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

private fun buildEventsByDay(events: List<KBCalendarEventEntity>): Map<LocalDate, List<KBCalendarEventEntity>> {
    val grouped = linkedMapOf<LocalDate, MutableList<KBCalendarEventEntity>>()
    events.forEach { event ->
        eventCoveredDates(event).forEach { day ->
            grouped.getOrPut(day) { mutableListOf() }.add(event)
        }
    }
    return grouped.mapValues { (_, list) -> list.sortedBy { it.startDateEpochMillis } }
}

private fun buildEventDatesSet(events: List<KBCalendarEventEntity>): Set<LocalDate> =
    buildSet {
        events.forEach { addAll(eventCoveredDates(it)) }
    }

private fun eventCoveredDates(event: KBCalendarEventEntity): List<LocalDate> {
    val zone = ZoneId.systemDefault()
    var start = Instant.ofEpochMilli(event.startDateEpochMillis).atZone(zone).toLocalDate()
    var end = Instant.ofEpochMilli(event.endDateEpochMillis).atZone(zone).toLocalDate()
    if (end.isBefore(start)) {
        val temp = start
        start = end
        end = temp
    }

    val result = mutableListOf<LocalDate>()
    var cursor = start
    while (!cursor.isAfter(end)) {
        result += cursor
        cursor = cursor.plusDays(1)
    }
    return result
}

