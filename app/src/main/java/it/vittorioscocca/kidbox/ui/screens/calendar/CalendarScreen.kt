package it.vittorioscocca.kidbox.ui.screens.calendar

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.style.TextOverflow
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity
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
import it.vittorioscocca.kidbox.notifications.AppSection
import it.vittorioscocca.kidbox.notifications.TrackSectionPresence
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.layout.width
import kotlinx.coroutines.delay
import java.time.Duration
import it.vittorioscocca.kidbox.ui.components.KBEmptyState
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.AddCircle
import it.vittorioscocca.kidbox.ui.util.visibilityChipLabel
import it.vittorioscocca.kidbox.ui.permissions.FullScreenAlarmNoticeDialog
import it.vittorioscocca.kidbox.ui.permissions.rememberReminderSaveGate

/**
 * Cosa sta aspettando la risposta al permesso notifiche. Porta con sé anche
 * l'elemento in modifica: quando l'utente risponde, il foglio può essere già
 * stato chiuso e `editingEvent` azzerato.
 */
private sealed interface CalendarPendingSave {
    data class Event(
        val draft: CalendarDraftInput,
        val editing: KBCalendarEventEntity?,
    ) : CalendarPendingSave

    data class Reminder(
        val draft: CalendarReminderDraft,
        val editingId: String?,
    ) : CalendarPendingSave
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    familyId: String,
    onBack: () -> Unit,
    openEventId: String? = null,
    viewModel: CalendarViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    TrackSectionPresence(AppSection.CALENDAR, familyId)
    var showForm by remember { mutableStateOf(false) }
    var editingEvent by remember { mutableStateOf<KBCalendarEventEntity?>(null) }
    var editingReminder by remember { mutableStateOf<KBTodoItemEntity?>(null) }
    // Ora scelta toccando la griglia di Giorno/Settimana; null = mezzanotte.
    var newEventTime by remember { mutableStateOf<LocalTime?>(null) }
    val currentUid = remember { FirebaseAuth.getInstance().currentUser?.uid }

    // Un solo cancello per le due schede del foglio: evento e promemoria
    // accendono lo stesso tipo di avviso e chiedono gli stessi permessi. Se le
    // notifiche vengono negate l'elemento si salva comunque, senza avviso.
    val saveGate = rememberReminderSaveGate<CalendarPendingSave> { item, reminderAllowed ->
        when (item) {
            is CalendarPendingSave.Event -> viewModel.saveEvent(
                if (reminderAllowed) item.draft else item.draft.copy(reminderMinutes = null),
                item.editing,
            )

            is CalendarPendingSave.Reminder -> viewModel.saveReminder(
                draft = item.draft,
                editingId = item.editingId,
                reminderEnabled = reminderAllowed,
            )
        }
        showForm = false
    }

    // Evento aperto da notifica: si attende che la sincronizzazione lo porti in
    // locale, poi si apre il suo dettaglio. Senza attendere si resterebbe sulla
    // vista mese come se la notifica non avesse portato da nessuna parte,
    // perché la push precede la sincronizzazione.
    var waitingForEvent by remember(openEventId) { mutableStateOf(!openEventId.isNullOrBlank()) }
    var openedEventId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(openEventId, state.events) {
        val target = openEventId?.takeIf { it.isNotBlank() } ?: return@LaunchedEffect
        // Una sola apertura: senza questo, chiudere il dettaglio lo farebbe
        // riaprire al primo aggiornamento della lista eventi.
        if (openedEventId == target) return@LaunchedEffect
        val event = state.events.firstOrNull { it.id == target } ?: return@LaunchedEffect
        openedEventId = target
        waitingForEvent = false
        editingEvent = event
        showForm = true
    }
    LaunchedEffect(openEventId) {
        if (openEventId.isNullOrBlank()) return@LaunchedEffect
        delay(EVENT_SYNC_WAIT_MS)
        // Scaduta l'attesa si smette di bloccare l'utente: resta la vista mese.
        waitingForEvent = false
    }
    if (waitingForEvent) {
        Dialog(onDismissRequest = { waitingForEvent = false }) {
            Surface(shape = RoundedCornerShape(18.dp), color = MaterialTheme.kidBoxColors.card) {
                Row(
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(14.dp))
                    Text(
                        text = stringResource(R.string.calendar_deeplink_opening),
                        color = MaterialTheme.kidBoxColors.title,
                    )
                }
            }
        }
    }

    // Visibility state hoisted here so the picker dialog can be shown OUTSIDE the bottom sheet,
    // avoiding the nested-sheet issue on MIUI and other ROM variants.
    var showVisibilityPicker by remember { mutableStateOf(false) }
    // Lo stesso selettore serve a entrambe le schede, ma il titolo cambia:
    // «questo evento» o «questo promemoria».
    var visibilityPickerForReminder by remember { mutableStateOf(false) }
    var draftVisibilityScope by remember { mutableStateOf(KBVisibilityScope.FAMILY) }
    var draftVisibilityMemberIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Sync draft visibility whenever the form is opened or the edited item changes.
    // Vale per entrambe le schede: un promemoria in modifica porta la sua
    // visibilità dentro il draft, altrimenti salvando la perderebbe.
    LaunchedEffect(showForm, editingEvent?.id, editingReminder?.id) {
        if (showForm) {
            val scopeRaw = editingEvent?.visibilityScope ?: editingReminder?.visibilityScope
            val memberIdsJson =
                editingEvent?.visibilityMemberIdsJson ?: editingReminder?.visibilityMemberIdsJson
            draftVisibilityScope = KBVisibilityScope.normalized(scopeRaw)
            draftVisibilityMemberIds = decodeStringList(memberIdsJson).toSet()
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
                        // Senza colore esplicito si eredita il nero di default:
                        // la schermata disegna il proprio sfondo e non sta dentro
                        // una Surface, quindi il tema scuro non viene applicato.
                        color = MaterialTheme.kidBoxColors.title,
                    )
                    HeaderCircleButton(
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.calendar_new_event_cd),
                        onClick = {
                            editingEvent = null
                            editingReminder = null
                            newEventTime = null
                            showForm = true
                        },
                    )
                }
            }
        },
        containerColor = MaterialTheme.kidBoxColors.background,
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.forceRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize(),
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
                        text = stringResource(R.string.calendar_day_tab),
                        selected = state.mode == CalendarMode.DAY,
                        modifier = Modifier.weight(1f),
                    ) { viewModel.setMode(CalendarMode.DAY) }
                    TogglePill(
                        text = stringResource(R.string.calendar_week_tab),
                        selected = state.mode == CalendarMode.WEEK,
                        modifier = Modifier.weight(1f),
                    ) { viewModel.setMode(CalendarMode.WEEK) }
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
                    CalendarMode.DAY, CalendarMode.WEEK -> CalendarTimeGridView(
                        selectedDate = state.selectedDate,
                        events = state.events,
                        reminders = state.reminders,
                        onEditReminder = {
                            editingReminder = it
                            editingEvent = null
                            newEventTime = null
                            showForm = true
                        },
                        isWeek = state.mode == CalendarMode.WEEK,
                        onSelectDate = viewModel::setSelectedDate,
                        onEditEvent = {
                            editingEvent = it
                            editingReminder = null
                            newEventTime = null
                            showForm = true
                        },
                        onAddEvent = { at ->
                            viewModel.setSelectedDate(at.toLocalDate())
                            editingEvent = null
                            editingReminder = null
                            newEventTime = at.toLocalTime()
                            showForm = true
                        },
                    )

                    CalendarMode.MONTH -> CalendarMonthView(
                        selectedDate = state.selectedDate,
                        displayedMonth = state.displayedMonth,
                        events = state.events,
                        reminders = state.reminders,
                        onEditReminder = {
                            editingReminder = it
                            editingEvent = null
                            newEventTime = null
                            showForm = true
                        },
                        onToggleReminder = { viewModel.toggleReminderDone(it.id) },
                        onDeleteReminder = { viewModel.deleteReminder(it.id) },
                        onSelectDate = viewModel::setSelectedDate,
                        onChangeDisplayedMonth = viewModel::setDisplayedMonth,
                        onEditEvent = {
                            editingEvent = it
                            editingReminder = null
                            newEventTime = null
                            showForm = true
                        },
                        onDeleteEvent = viewModel::deleteEvent,
                        onAddEvent = {
                            editingEvent = null
                            editingReminder = null
                            newEventTime = null
                            showForm = true
                        },
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
    }

    if (showForm) {
        CalendarItemSheet(
            editingEvent = editingEvent,
            editingReminder = editingReminder,
            selectedDate = state.selectedDate,
            initialTime = newEventTime,
            currentUid = currentUid,
            visibilityScope = draftVisibilityScope,
            visibilityMemberIds = draftVisibilityMemberIds,
            todoLists = state.todoLists,
            assignableMembers = state.assignableMembers,
            onRequestVisibilityPicker = {
                visibilityPickerForReminder = false
                showVisibilityPicker = true
            },
            onRequestReminderVisibilityPicker = {
                visibilityPickerForReminder = true
                showVisibilityPicker = true
            },
            onDismiss = { showForm = false },
            // Il salvataggio passa dal cancello dei permessi: senza notifiche
            // l'avviso non arriverebbe mai e l'interruttore direbbe «attivo».
            onSaveEvent = { draft ->
                saveGate.save(
                    item = CalendarPendingSave.Event(draft, editingEvent),
                    wantsReminder = draft.reminderMinutes != null,
                    isUrgent = draft.isUrgent,
                )
            },
            onSaveReminder = { draft ->
                saveGate.save(
                    item = CalendarPendingSave.Reminder(draft, editingReminder?.id),
                    // Un promemoria del calendario nasce con una scadenza:
                    // l'avviso è il motivo per cui esiste.
                    wantsReminder = true,
                    isUrgent = draft.isUrgent,
                )
            },
        )
    }

    if (saveGate.showFullScreenNotice) {
        FullScreenAlarmNoticeDialog(onDismiss = saveGate::dismissFullScreenNotice)
    }

    // The picker is a sibling of CalendarEventDialog (NOT nested inside its ModalBottomSheet).
    if (showVisibilityPicker && showForm) {
        VisibilityPickerFullscreenDialog(
            currentUid = currentUid,
            scopeSectionTitle = if (visibilityPickerForReminder) {
                stringResource(R.string.calendar_reminder_who_can_see)
            } else {
                stringResource(R.string.calendar_event_who_can_see)
            },
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
    reminders: List<KBTodoItemEntity>,
    onSelectDate: (LocalDate) -> Unit,
    onChangeDisplayedMonth: (LocalDate) -> Unit,
    onEditEvent: (KBCalendarEventEntity) -> Unit,
    onDeleteEvent: (KBCalendarEventEntity) -> Unit,
    onEditReminder: (KBTodoItemEntity) -> Unit,
    onToggleReminder: (KBTodoItemEntity) -> Unit,
    onDeleteReminder: (KBTodoItemEntity) -> Unit,
    onAddEvent: () -> Unit,
) {
    val eventsByDate = remember(events) {
        buildEventsByDay(events)
    }
    val remindersByDate = remember(reminders) { buildRemindersByDay(reminders) }

    val days = remember(displayedMonth) { monthGridDays(displayedMonth.withDayOfMonth(1)) }
    val kb = MaterialTheme.kidBoxColors
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
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.calendar_previous_month_cd),
                    tint = kb.title,
                )
            }
            Text(
                monthLabel,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                // Come il titolo: senza colore esplicito resterebbe nero anche
                // in tema scuro.
                color = kb.title,
            )
            IconButton(onClick = { onChangeDisplayedMonth(displayedMonth.plusMonths(1).withDayOfMonth(1)) }) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_month_cd),
                    tint = kb.title,
                )
            }
        }

        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            listOf("L", "M", "M", "G", "V", "S", "D").forEach { d ->
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
                                    .background(
                                        // Anche i promemoria accendono il
                                        // pallino: un giorno che ne ha uno non
                                        // può sembrare vuoto.
                                        if (hasEvents || remindersByDate[day]?.isNotEmpty() == true) {
                                            Color(0xFF42A5F5)
                                        } else {
                                            Color.Transparent
                                        },
                                        CircleShape,
                                    ),
                            )
                        }
                    }
                }
            }
        }

        Divider(modifier = Modifier.padding(top = 6.dp))
        val selectedEvents = eventsByDate[selectedDate].orEmpty().sortedBy { it.startDateEpochMillis }
        val selectedReminders = remindersByDate[selectedDate].orEmpty()
            // I fatti in fondo: restano visibili, ma non rubano la riga in
            // cima a quelli ancora da fare.
            .sortedWith(compareBy({ it.isDone }, { it.dueAtEpochMillis ?: Long.MAX_VALUE }))
        if (selectedEvents.isEmpty() && selectedReminders.isEmpty()) {
            // `weight` + scroll: senza, lo spazio residuo sotto la griglia del mese può
            // essere minore dell'empty state e il pulsante finisce schiacciato/tagliato.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.Center,
            ) {
                KBEmptyState(
                    icon = Icons.Filled.CalendarMonth,
                    title = stringResource(R.string.empty_calendar_title),
                    body = stringResource(R.string.empty_calendar_body),
                    primaryIcon = Icons.Filled.AddCircle,
                    primaryLabel = stringResource(R.string.empty_calendar_action),
                    onPrimary = onAddEvent,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 5.dp),
            ) {
                if (selectedEvents.isNotEmpty() && selectedReminders.isNotEmpty()) {
                    item(key = "events-header") {
                        CalendarSectionHeader(stringResource(R.string.calendar_events_section))
                    }
                }
                items(selectedEvents, key = { it.id }) { event ->
                    CalendarEventCard(
                        event = event,
                        onEdit = { onEditEvent(event) },
                        onDelete = { onDeleteEvent(event) },
                    )
                }
                if (selectedReminders.isNotEmpty()) {
                    item(key = "reminders-header") {
                        CalendarSectionHeader(stringResource(R.string.calendar_reminders_section))
                    }
                    items(selectedReminders, key = { "r-${it.id}" }) { reminder ->
                        CalendarReminderCard(
                            todo = reminder,
                            onEdit = { onEditReminder(reminder) },
                            onToggleDone = { onToggleReminder(reminder) },
                            onDelete = { onDeleteReminder(reminder) },
                        )
                    }
                }
            }
        }
    }
}

/** Altezza di un'ora nella griglia: gemella di `HOUR_HEIGHT` in calendarUtils.js. */
private val HOUR_HEIGHT = 52.dp
private val HOUR_GUTTER_WIDTH = 42.dp

/**
 * Viste Giorno e Settimana: la stessa griglia oraria della web app
 * (`TimeGridView` di Calendario.jsx) e di `TimeGridView` su iOS.
 */
@Composable
private fun CalendarTimeGridView(
    selectedDate: LocalDate,
    events: List<KBCalendarEventEntity>,
    reminders: List<KBTodoItemEntity>,
    isWeek: Boolean,
    onSelectDate: (LocalDate) -> Unit,
    onEditEvent: (KBCalendarEventEntity) -> Unit,
    onEditReminder: (KBTodoItemEntity) -> Unit,
    onAddEvent: (LocalDateTime) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val locale = KBLocale.current()
    val days = remember(selectedDate, isWeek) {
        if (isWeek) {
            // Prima colonna lunedì, come la griglia del mese.
            val first = selectedDate.minusDays((selectedDate.dayOfWeek.value - 1).toLong())
            (0L..6L).map { first.plusDays(it) }
        } else {
            listOf(selectedDate)
        }
    }
    val eventsByDate = remember(events) { buildEventsByDay(events) }
    val remindersByDate = remember(reminders) { buildRemindersByDay(reminders) }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    // Si apre sull'orario utile: a mezzanotte non c'è niente da vedere.
    LaunchedEffect(Unit) {
        scrollState.scrollTo(with(density) { (HOUR_HEIGHT * 7).roundToPx() })
    }

    val step = if (isWeek) 7L else 1L
    val title = remember(days, locale) {
        if (!isWeek) {
            selectedDate.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
        } else {
            val first = days.first()
            val last = days.last()
            val startPattern = if (first.month == last.month) "d" else "d MMM"
            "${first.format(DateTimeFormatter.ofPattern(startPattern, locale))} – " +
                last.format(DateTimeFormatter.ofPattern("d MMMM yyyy", locale))
        }.replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { onSelectDate(selectedDate.minusDays(step)) }) {
                Icon(
                    Icons.Default.ChevronLeft,
                    contentDescription = stringResource(R.string.calendar_previous_month_cd),
                    tint = kb.title,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!isWeek) {
                    Text(
                        selectedDate.format(DateTimeFormatter.ofPattern("EEEE", locale))
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase(locale) else it.toString() },
                        color = kb.subtitle,
                        fontSize = 12.sp,
                    )
                }
            }
            PillButton(text = stringResource(R.string.calendar_today_btn)) {
                onSelectDate(LocalDate.now())
            }
            IconButton(onClick = { onSelectDate(selectedDate.plusDays(step)) }) {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = stringResource(R.string.calendar_next_month_cd),
                    tint = kb.title,
                )
            }
        }

        if (isWeek) {
            Row(modifier = Modifier.fillMaxWidth().padding(end = 4.dp)) {
                Spacer(modifier = Modifier.width(HOUR_GUTTER_WIDTH))
                days.forEach { day ->
                    val isToday = day == LocalDate.now()
                    Column(
                        modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            day.format(DateTimeFormatter.ofPattern("EEE", locale)),
                            color = kb.subtitle,
                            fontSize = 11.sp,
                            maxLines = 1,
                        )
                        Text(
                            day.dayOfMonth.toString(),
                            color = if (isToday) Color(0xFF2196F3) else kb.title,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            Divider()
        }

        // Riga "tutto il giorno": gli eventi senza orario non stanno nella griglia.
        // Esiste solo se c'è qualcosa da metterci, altrimenti ruba una striscia
        // di spazio sopra la griglia (come su iOS).
        val hasAllDayEvents = days.any { day ->
            eventsByDate[day].orEmpty().any { it.isAllDay }
        }
        if (hasAllDayEvents) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp, horizontal = 0.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    stringResource(R.string.calendar_all_day_short),
                    modifier = Modifier.width(HOUR_GUTTER_WIDTH).padding(end = 4.dp),
                    color = kb.subtitle,
                    fontSize = 9.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                days.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 1.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        eventsByDate[day].orEmpty().filter { it.isAllDay }.forEach { event ->
                            Text(
                                event.title,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(categoryColor(event.categoryRaw))
                                    .clickable { onEditEvent(event) }
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                                color = Color.White,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
            Divider()
        }

        // I promemoria stanno in una riga propria sopra la griglia, come fa
        // Calendario di Apple: hanno un istante, non una durata, e disegnarli
        // come blocchi li farebbe sembrare appuntamenti di un'ora.
        if (days.any { remindersByDate[it].orEmpty().isNotEmpty() }) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    stringResource(R.string.calendar_reminders_section),
                    modifier = Modifier.width(HOUR_GUTTER_WIDTH).padding(end = 4.dp),
                    color = kb.subtitle,
                    fontSize = 9.sp,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                )
                days.forEach { day ->
                    Column(
                        modifier = Modifier.weight(1f).padding(horizontal = 1.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        remindersByDate[day].orEmpty()
                            .sortedBy { it.dueAtEpochMillis ?: Long.MAX_VALUE }
                            .forEach { todo ->
                                Text(
                                    todo.title,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            MaterialTheme.colorScheme.primary
                                                .copy(alpha = if (todo.isDone) 0.08f else 0.16f),
                                        )
                                        .clickable { onEditReminder(todo) }
                                        .padding(horizontal = 6.dp, vertical = 3.dp),
                                    // Senza colore esplicito dentro un
                                    // contenitore tinto il testo resta nero
                                    // anche in tema scuro.
                                    color = if (todo.isDone) kb.subtitle else kb.title,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                    }
                }
            }
            Divider()
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(end = 4.dp),
        ) {
            Column(modifier = Modifier.width(HOUR_GUTTER_WIDTH)) {
                (0..23).forEach { hour ->
                    Text(
                        text = "%02d:00".format(hour),
                        modifier = Modifier
                            .height(HOUR_HEIGHT)
                            .fillMaxWidth()
                            .padding(end = 4.dp),
                        color = kb.subtitle,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End,
                    )
                }
            }
            days.forEach { day ->
                TimeGridDayColumn(
                    day = day,
                    events = eventsByDate[day].orEmpty().filterNot { it.isAllDay },
                    onEditEvent = onEditEvent,
                    onAddEvent = onAddEvent,
                )
            }
        }
    }
}

@Composable
private fun RowScope.TimeGridDayColumn(
    day: LocalDate,
    events: List<KBCalendarEventEntity>,
    onEditEvent: (KBCalendarEventEntity) -> Unit,
    onAddEvent: (LocalDateTime) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val laid = remember(events, day) { layoutTimedEvents(events, day) }
    val hourHeightPx = with(LocalDensity.current) { HOUR_HEIGHT.toPx() }

    BoxWithConstraints(
        modifier = Modifier
            .weight(1f)
            .height(HOUR_HEIGHT * 24)
            // Doppio tocco su uno spazio vuoto: nuovo evento a quell'ora, come
            // il doppio click della web app.
            .pointerInput(day) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        val hour = (offset.y / hourHeightPx).toInt().coerceIn(0, 23)
                        onAddEvent(LocalDateTime.of(day, LocalTime.of(hour, 0)))
                    },
                )
            },
    ) {
        val columnWidth = maxWidth

        Column {
            repeat(24) {
                Box(modifier = Modifier.fillMaxWidth().height(HOUR_HEIGHT)) {
                    Divider(color = kb.subtitle.copy(alpha = 0.18f), thickness = 0.5.dp)
                }
            }
        }

        laid.forEach { item ->
            val slot = columnWidth / item.columns
            val color = categoryColor(item.event.categoryRaw)
            val height = maxOf(HOUR_HEIGHT * (item.heightMinutes / 60f), 18.dp)
            val start = Instant.ofEpochMilli(item.event.startDateEpochMillis)
                .atZone(ZoneId.systemDefault()).toLocalTime()
            val end = Instant.ofEpochMilli(item.event.endDateEpochMillis)
                .atZone(ZoneId.systemDefault()).toLocalTime()

            Column(
                modifier = Modifier
                    .offset(x = slot * item.column + 1.dp, y = HOUR_HEIGHT * (item.topMinutes / 60f))
                    .width(maxOf(slot - 3.dp, 20.dp))
                    .height(height)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color.copy(alpha = 0.26f))
                    .clickable { onEditEvent(item.event) }
                    .padding(start = 5.dp, end = 3.dp, top = 2.dp),
            ) {
                Text(
                    item.event.title,
                    color = color,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (height > 32.dp) 2 else 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (height > 32.dp) {
                    Text(
                        "${start.format(DateTimeFormatter.ofPattern("HH:mm"))} - " +
                            end.format(DateTimeFormatter.ofPattern("HH:mm")),
                        color = kb.subtitle,
                        fontSize = 9.sp,
                        maxLines = 1,
                    )
                }
            }
            // Barra della categoria a sinistra del blocco.
            Box(
                modifier = Modifier
                    .offset(x = slot * item.column + 1.dp, y = HOUR_HEIGHT * (item.topMinutes / 60f))
                    .width(3.dp)
                    .height(height)
                    .background(color),
            )
        }
    }
}

private data class TimedEventLayout(
    val event: KBCalendarEventEntity,
    val topMinutes: Float,
    val heightMinutes: Float,
    val column: Int,
    val columns: Int,
)

/**
 * Posizione, altezza e colonna di ogni evento a orario dentro un giorno.
 * Porting di `layoutOverlaps` (calendarUtils.js): gli eventi che si sovrappongono
 * si dividono la larghezza invece di coprirsi.
 */
private fun layoutTimedEvents(
    events: List<KBCalendarEventEntity>,
    day: LocalDate,
): List<TimedEventLayout> {
    data class Box(val event: KBCalendarEventEntity, val top: Float, val height: Float)

    val dayStart = day.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val boxes = events.map { event ->
        val startMillis = minOf(event.startDateEpochMillis, event.endDateEpochMillis)
        val endMillis = maxOf(event.startDateEpochMillis, event.endDateEpochMillis)
        // Un evento su più giorni viene tagliato agli estremi del giorno, così si
        // vede su ognuno di essi.
        val from = ((startMillis - dayStart) / 60_000f).coerceIn(0f, 1440f)
        val to = minOf(maxOf((endMillis - dayStart) / 60_000f, from + 15f), 1440f)
        Box(event = event, top = from, height = to - from)
    }.sortedBy { it.top }

    val result = mutableListOf<TimedEventLayout>()

    fun flush(group: List<Box>) {
        if (group.isEmpty()) return
        val columnEnds = mutableListOf<Float>()
        val assigned = mutableListOf<Pair<Box, Int>>()
        group.forEach { item ->
            var col = columnEnds.indexOfFirst { item.top >= it }
            if (col == -1) {
                columnEnds.add(0f)
                col = columnEnds.lastIndex
            }
            columnEnds[col] = item.top + item.height
            assigned += item to col
        }
        val total = maxOf(columnEnds.size, 1)
        assigned.forEach { (box, col) ->
            result += TimedEventLayout(
                event = box.event,
                topMinutes = box.top,
                heightMinutes = box.height,
                column = col,
                columns = total,
            )
        }
    }

    val group = mutableListOf<Box>()
    var groupEnd = -1f
    boxes.forEach { item ->
        if (group.isNotEmpty() && item.top >= groupEnd) {
            flush(group.toList())
            group.clear()
            groupEnd = -1f
        }
        group += item
        groupEnd = maxOf(groupEnd, item.top + item.height)
    }
    flush(group.toList())

    return result
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
        stringResource(R.string.calendar_all_day)
    } else {
        "${start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))} - ${end.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))}"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
        shape = RoundedCornerShape(12.dp),
    ) {
        // Riga singola come su iOS (`CalendarEventRow`): barra colorata della
        // categoria, titolo, e sotto orario e categoria sulla stessa riga. Prima
        // erano tre testi impilati più un pulsante "Elimina" a tutta larghezza:
        // ogni evento occupava più del doppio dello spazio e in una giornata piena
        // se ne vedevano tre per schermata.
        Row(
            modifier = Modifier.height(IntrinsicSize.Min),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(categoryColor(event.categoryRaw), RoundedCornerShape(2.dp)),
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    event.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = MaterialTheme.kidBoxColors.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        timeLabel,
                        color = MaterialTheme.kidBoxColors.subtitle,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                    Text("·", color = MaterialTheme.kidBoxColors.subtitle, fontSize = 12.sp)
                    Text(
                        categoryLabel(event.categoryRaw),
                        color = categoryColor(event.categoryRaw),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.calendar_delete),
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

/** Titoletto «Eventi» / «Promemoria» nell'elenco del giorno. */
@Composable
private fun CalendarSectionHeader(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(start = 20.dp, top = 10.dp, bottom = 4.dp),
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.kidBoxColors.subtitle,
    )
}

/**
 * Riga di un promemoria nell'elenco del giorno. Il cerchio a sinistra spunta
 * senza aprire la scheda, come nelle liste To-Do.
 */
@Composable
private fun CalendarReminderCard(
    todo: KBTodoItemEntity,
    onEdit: () -> Unit,
    onToggleDone: () -> Unit,
    onDelete: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val due = todo.dueAtEpochMillis
    val timeLabel = when {
        due == null -> ""
        todo.dueHasTime -> Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault())
            .format(DateTimeFormatter.ofPattern("HH:mm"))
        else -> stringResource(R.string.calendar_all_day)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .clickable(onClick = onEdit),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleDone, modifier = Modifier.size(38.dp)) {
                Icon(
                    if (todo.isDone) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                    contentDescription = stringResource(R.string.calendar_reminders_section),
                    tint = if (todo.isDone) MaterialTheme.colorScheme.primary else kb.subtitle,
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(vertical = 10.dp, horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(1.dp),
            ) {
                Text(
                    todo.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = if (todo.isDone) kb.subtitle else kb.title,
                    textDecoration = if (todo.isDone) TextDecoration.LineThrough else null,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (timeLabel.isNotBlank()) {
                        Text(timeLabel, color = kb.subtitle, fontSize = 12.sp, maxLines = 1)
                    }
                    if (todo.priorityRaw == 1) {
                        Text("·", color = kb.subtitle, fontSize = 12.sp)
                        Text(
                            stringResource(R.string.todo_urgent),
                            color = Color(0xFFEF6C00),
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.calendar_delete),
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun CalendarEventFormContent(
    initial: KBCalendarEventEntity?,
    selectedDate: LocalDate,
    /** Ora scelta toccando la griglia oraria; null = mezzanotte. */
    initialTime: LocalTime?,
    currentUid: String?,
    /** Current visibility selection – owned by CalendarScreen so the picker can open outside this sheet. */
    visibilityScope: String,
    visibilityMemberIds: Set<String>,
    /** Called when the user taps "Cambia" – CalendarScreen will open the full-screen picker. */
    onRequestVisibilityPicker: () -> Unit,
    onDismiss: () -> Unit,
    onSave: (CalendarDraftInput) -> Unit,
    /** Il selettore Evento/Promemoria, mostrato solo in creazione. */
    kindSelector: (@Composable () -> Unit)? = null,
) {
    val context = LocalContext.current
    val locale = KBLocale.current()
    val formatter = remember { DateTimeFormatter.ofPattern("dd MMM yyyy", locale) }
    val kb = MaterialTheme.kidBoxColors
    val colorScheme = MaterialTheme.colorScheme

    val initialStart = initial?.let {
        Instant.ofEpochMilli(it.startDateEpochMillis).atZone(ZoneId.systemDefault()).toLocalDateTime()
    } ?: LocalDateTime.of(selectedDate, initialTime ?: LocalTime.of(0, 0))

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
    var urgent by remember { mutableStateOf(initial?.priorityRaw == 1) }
    var startDate by remember { mutableStateOf(initialStart.toLocalDate()) }
    var startTime by remember { mutableStateOf(initialStart.toLocalTime().withSecond(0).withNano(0)) }
    var endDate by remember { mutableStateOf(initialEnd.toLocalDate()) }
    var endTime by remember { mutableStateOf(initialEnd.toLocalTime().withSecond(0).withNano(0)) }

    // Un evento non può finire prima di iniziare.
    //
    // Spostando l'INIZIO si trascina la fine mantenendo la durata: cambiando la
    // data di inizio senza toccare la fine si otteneva altrimenti un evento che
    // comincia dopo essere finito. Toccando invece direttamente la FINE la si
    // blocca all'inizio, che è il minimo sensato.
    fun moveEndKeepingDuration(previousStart: LocalDateTime, newStart: LocalDateTime) {
        val currentEnd = LocalDateTime.of(endDate, endTime)
        val duration = Duration.between(previousStart, currentEnd)
        val kept = if (duration.isNegative) Duration.ZERO else duration
        val newEnd = newStart.plus(kept)
        endDate = newEnd.toLocalDate()
        endTime = newEnd.toLocalTime()
    }

    fun clampEndNotBeforeStart(candidate: LocalDateTime) {
        val start = LocalDateTime.of(startDate, startTime)
        val fixed = if (candidate.isBefore(start)) start else candidate
        endDate = fixed.toLocalDate()
        endTime = fixed.toLocalTime()
    }

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

    val titleText = if (initial == null) stringResource(R.string.calendar_new_event_title) else stringResource(R.string.calendar_edit_event_title)

    Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Il foglio è a tutta altezza (`skipPartiallyExpanded`) e vive
                    // in una finestra propria, quindi NON eredita i padding di
                    // sistema applicati alla radice dell'app: senza questo la riga
                    // con "Annulla" finiva sotto la barra di stato.
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(top = 16.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PillButton(text = stringResource(R.string.calendar_cancel), onClick = onDismiss)
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

            kindSelector?.invoke()

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
                        onPickDate = {
                            pickDate(startDate) { picked ->
                                val previousStart = LocalDateTime.of(startDate, startTime)
                                startDate = picked
                                moveEndKeepingDuration(previousStart, LocalDateTime.of(picked, startTime))
                            }
                        },
                        onPickTime = {
                            pickTime(startTime) { picked ->
                                val previousStart = LocalDateTime.of(startDate, startTime)
                                startTime = picked
                                moveEndKeepingDuration(previousStart, LocalDateTime.of(startDate, picked))
                            }
                        },
                    )
                    Divider()
                    DateTimeRow(
                        label = stringResource(R.string.calendar_end_label),
                        dateText = endDate.format(formatter),
                        timeText = endTime.format(DateTimeFormatter.ofPattern("HH:mm")),
                        allDay = isAllDay,
                        labelColor = kb.title,
                        valueColor = kb.title,
                        onPickDate = {
                            pickDate(endDate) { picked ->
                                clampEndNotBeforeStart(LocalDateTime.of(picked, endTime))
                            }
                        },
                        onPickTime = {
                            pickTime(endTime) { picked ->
                                clampEndNotBeforeStart(LocalDateTime.of(endDate, picked))
                            }
                        },
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
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.calendar_reminder_label),
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
                    if (reminderOn) {
                        Divider(modifier = Modifier.padding(vertical = 6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                stringResource(R.string.todo_urgent),
                                modifier = Modifier.weight(1f),
                                fontSize = 16.sp,
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
                                    visibilityChipLabel(visibilityScope),
                                    color = kb.title,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 15.sp,
                                )
                            }
                            Text(stringResource(R.string.calendar_change_visibility), color = kb.subtitle, fontSize = 14.sp)
                        }
                    } else {
                        Text(
                            visibilityChipLabel(
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
                    // Rete di sicurezza: i picker già impediscono una fine
                    // anteriore all'inizio, ma qui si chiude comunque la porta a
                    // un evento salvato con le date invertite.
                    val endDateTime = LocalDateTime.of(endDate, if (isAllDay) LocalTime.of(23, 59) else endTime)
                        .coerceAtLeast(startDateTime)
                    onSave(
                        CalendarDraftInput(
                            title = title,
                            notes = notes,
                            location = location,
                            categoryRaw = category,
                            recurrenceRaw = recurrence,
                            isAllDay = isAllDay,
                            reminderMinutes = if (reminderOn) 30 else null,
                            // Urgente senza promemoria non vuol dire niente:
                            // non c'è nulla da far suonare.
                            isUrgent = reminderOn && urgent,
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
    // La pillola selezionata era `kb.card` **sopra un contenitore `kb.card`**:
    // stesso identico colore, quindi la scelta non si vedeva — né qui fra
    // Evento e Promemoria, né nella barra Giorno/Settimana/Mese/Anno, che usa
    // questo stesso componente. Ora la selezionata è piena di primario, come
    // il pulsante di salvataggio dei form.
    val colorScheme = MaterialTheme.colorScheme
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(999.dp))
            .background(if (selected) colorScheme.primary else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (selected) colorScheme.onPrimary else MaterialTheme.kidBoxColors.subtitle,
        )
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
    // Prima colonna Lunedì (L), come iOS e convenzione italiana.
    val leadingEmpty = monthFirstDate.dayOfWeek.value - 1
    val daysInMonth = monthFirstDate.lengthOfMonth()
    val result = mutableListOf<LocalDate?>()
    repeat(leadingEmpty) { result.add(null) }
    for (d in 1..daysInMonth) {
        result.add(monthFirstDate.withDayOfMonth(d))
    }
    while (result.size % 7 != 0) result.add(null)
    return result
}

@Composable
private fun categoryLabel(raw: String): String = when (raw) {
    "children" -> stringResource(R.string.calendar_category_children)
    "school" -> stringResource(R.string.calendar_category_school)
    "health" -> stringResource(R.string.calendar_category_health)
    "family" -> stringResource(R.string.calendar_category_family)
    "admin" -> stringResource(R.string.calendar_category_admin)
    "leisure" -> stringResource(R.string.calendar_category_leisure)
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

/** Un promemoria cade in un giorno solo: ha un istante, non una durata. */
private fun buildRemindersByDay(reminders: List<KBTodoItemEntity>): Map<LocalDate, List<KBTodoItemEntity>> {
    val map = mutableMapOf<LocalDate, MutableList<KBTodoItemEntity>>()
    reminders.forEach { todo ->
        val due = todo.dueAtEpochMillis ?: return@forEach
        val day = Instant.ofEpochMilli(due).atZone(ZoneId.systemDefault()).toLocalDate()
        map.getOrPut(day) { mutableListOf() }.add(todo)
    }
    return map
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

/** Quanto si attende che la sincronizzazione porti l'evento aperto da notifica. */
private const val EVENT_SYNC_WAIT_MS = 25_000L

/** Quale dei due si sta inserendo dal calendario. */
enum class CalendarNewItemKind { EVENT, REMINDER }

/**
 * Il foglio di inserimento del calendario. In modifica mostra la scheda
 * dell'elemento; in creazione mostra sopra il selettore `Evento | Promemoria`,
 * come in Calendario di Apple.
 *
 * Le due schede non condividono nulla se non quella barra: un evento è un
 * `KBCalendarEventEntity`, un promemoria è un to-do in una lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarItemSheet(
    editingEvent: KBCalendarEventEntity?,
    editingReminder: KBTodoItemEntity?,
    selectedDate: LocalDate,
    initialTime: LocalTime?,
    currentUid: String?,
    visibilityScope: String,
    visibilityMemberIds: Set<String>,
    todoLists: List<KBTodoListEntity>,
    assignableMembers: List<VisibilityPickerMember>,
    onRequestVisibilityPicker: () -> Unit,
    /**
     * Evento e promemoria chiedono lo stesso selettore ma con un titolo
     * diverso: la schermata deve sapere chi dei due l'ha aperto.
     */
    onRequestReminderVisibilityPicker: () -> Unit,
    onDismiss: () -> Unit,
    onSaveEvent: (CalendarDraftInput) -> Unit,
    onSaveReminder: (CalendarReminderDraft) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val isNew = editingEvent == null && editingReminder == null
    var kind by remember(isNew, editingReminder?.id) {
        mutableStateOf(
            if (editingReminder != null) CalendarNewItemKind.REMINDER else CalendarNewItemKind.EVENT,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.kidBoxColors.background,
        dragHandle = null,
    ) {
        val selector: (@Composable () -> Unit)? = if (!isNew) {
            null
        } else {
            {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(MaterialTheme.kidBoxColors.card)
                        .padding(3.dp),
                ) {
                    TogglePill(
                        text = stringResource(R.string.calendar_kind_event),
                        selected = kind == CalendarNewItemKind.EVENT,
                        modifier = Modifier.weight(1f),
                    ) { kind = CalendarNewItemKind.EVENT }
                    TogglePill(
                        text = stringResource(R.string.calendar_kind_reminder),
                        selected = kind == CalendarNewItemKind.REMINDER,
                        modifier = Modifier.weight(1f),
                    ) { kind = CalendarNewItemKind.REMINDER }
                }
            }
        }

        when {
            kind == CalendarNewItemKind.REMINDER || editingReminder != null ->
                CalendarReminderFormContent(
                    initial = editingReminder,
                    selectedDate = selectedDate,
                    initialTime = initialTime,
                    currentUid = currentUid,
                    todoLists = todoLists,
                    members = assignableMembers,
                    visibilityScope = visibilityScope,
                    visibilityMemberIds = visibilityMemberIds,
                    onRequestVisibilityPicker = onRequestReminderVisibilityPicker,
                    onDismiss = onDismiss,
                    onSave = onSaveReminder,
                    kindSelector = selector,
                )

            else -> CalendarEventFormContent(
                initial = editingEvent,
                selectedDate = selectedDate,
                initialTime = initialTime,
                currentUid = currentUid,
                visibilityScope = visibilityScope,
                visibilityMemberIds = visibilityMemberIds,
                onRequestVisibilityPicker = onRequestVisibilityPicker,
                onDismiss = onDismiss,
                onSave = onSaveEvent,
                kindSelector = selector,
            )
        }
    }
}
