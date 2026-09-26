package it.vittorioscocca.kidbox.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.calendarfeed.CalendarFeedRepository
import it.vittorioscocca.kidbox.data.calendarfeed.CalendarFeedResult
import it.vittorioscocca.kidbox.data.calendarfeed.CalendarFeedState
import it.vittorioscocca.kidbox.data.devicecalendar.DeviceCalendarRepository
import it.vittorioscocca.kidbox.data.devicecalendar.DeviceCalendarState
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.CountersService
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.local.CalendarViewModePreference
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoListEntity
import it.vittorioscocca.kidbox.data.notification.CalendarEventReminderScheduler
import it.vittorioscocca.kidbox.data.repository.CalendarRepository
import it.vittorioscocca.kidbox.data.repository.TodoRepository
import it.vittorioscocca.kidbox.domain.calendar.EventRecurrence
import it.vittorioscocca.kidbox.domain.calendar.occurrencesIn
import it.vittorioscocca.kidbox.domain.calendar.seriesOf
import it.vittorioscocca.kidbox.domain.model.TodoListExposure
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.ui.screens.notes.VisibilityPickerMember
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import it.vittorioscocca.kidbox.ui.state.PullToRefreshController
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

enum class CalendarMode { DAY, WEEK, MONTH, YEAR }

/** Le quattro sorgenti che disegnano il calendario, tenute insieme. */
private data class CalendarSources(
    val familyId: String = "",
    val events: List<KBCalendarEventEntity> = emptyList(),
    val todos: List<KBTodoItemEntity> = emptyList(),
    val lists: List<KBTodoListEntity> = emptyList(),
    val allMembers: List<VisibilityPickerMember> = emptyList(),
)

data class CalendarUiState(
    val familyId: String = "",
    val mode: CalendarMode = CalendarMode.MONTH,
    val selectedDate: LocalDate = LocalDate.now(),
    val displayedMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    /** Gli eventi come sono salvati: una riga per serie. Si modificano questi. */
    val events: List<KBCalendarEventEntity> = emptyList(),
    /**
     * Gli eventi da disegnare: le serie espanse nelle ripetizioni che cadono
     * attorno al giorno e al mese guardati (copie con lo stesso id e le date
     * spostate). Mai salvarne una: si torna alla serie con `seriesOf`.
     */
    val displayEvents: List<KBCalendarEventEntity> = emptyList(),
    /**
     * I promemoria del calendario **sono** to-do con una scadenza: stessa
     * collezione, stesse liste, stessa visibilità. Il calendario è solo
     * un'altra porta d'ingresso.
     */
    val reminders: List<KBTodoItemEntity> = emptyList(),
    /** Liste To-Do in cui si può mettere un promemoria creato da qui. */
    val todoLists: List<KBTodoListEntity> = emptyList(),
    /** Membri (escluso utente corrente) per il foglio visibilità in creazione evento. */
    val visibilityMembers: List<VisibilityPickerMember> = emptyList(),
    /** Tutti i membri, incluso me: serve al selettore «Assegnato a». */
    val assignableMembers: List<VisibilityPickerMember> = emptyList(),
    val currentUid: String = "",
    val childId: String = "",
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

/** I campi di un promemoria creato o modificato dal calendario. */
data class CalendarReminderDraft(
    val title: String,
    val notes: String?,
    val dueAtEpochMillis: Long,
    val dueHasTime: Boolean,
    val isUrgent: Boolean,
    val listId: String,
    val assignedTo: String?,
    val visibilityScope: String = KBVisibilityScope.FAMILY,
    val visibilityMemberIds: List<String> = emptyList(),
)

data class CalendarDraftInput(
    val title: String,
    val notes: String?,
    val location: String?,
    val categoryRaw: String,
    val recurrenceRaw: String,
    val isAllDay: Boolean,
    val reminderMinutes: Int?,
    /** Urgente: il promemoria dell'evento diventa una sveglia. */
    val isUrgent: Boolean = false,
    val startEpochMillis: Long,
    val endEpochMillis: Long,
    val visibilityScope: String = KBVisibilityScope.FAMILY,
    val visibilityMemberIds: List<String> = emptyList(),
)

@HiltViewModel
class CalendarViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val calendarRepository: CalendarRepository,
    private val todoRepository: TodoRepository,
    private val calendarReminderScheduler: CalendarEventReminderScheduler,
    private val deviceCalendars: DeviceCalendarRepository,
    private val calendarFeeds: CalendarFeedRepository,
    private val viewModePreference: CalendarViewModePreference,
    private val countersService: CountersService,
    private val homeBadgeManager: HomeBadgeManager,
    private val auth: FirebaseAuth,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        // La vista scelta l'ultima volta, non sempre il mese: è una
        // preferenza del dispositivo e sopravvive alla chiusura dell'app.
        CalendarUiState(
            mode = runCatching { CalendarMode.valueOf(viewModePreference.read().orEmpty()) }
                .getOrDefault(CalendarMode.MONTH),
        ),
    )
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()

    /** I calendari del telefono, in sola lettura: vedi `DeviceCalendarRepository`. */
    val deviceCalendarState: StateFlow<DeviceCalendarState> = deviceCalendars.state

    fun onDeviceCalendarPermissionResult(granted: Boolean) {
        deviceCalendars.onPermissionResult(granted)
        deviceCalendars.showAround(_uiState.value.selectedDate)
    }

    fun setDeviceCalendarsEnabled(enabled: Boolean) {
        deviceCalendars.setEnabled(enabled)
        deviceCalendars.showAround(_uiState.value.selectedDate)
    }

    fun setDeviceCalendarVisible(calendarId: Long, visible: Boolean) =
        deviceCalendars.setCalendarVisible(calendarId, visible)

    fun dismissDeviceCalendarPrompt() = deviceCalendars.dismissPrompt()

    // ── Calendari iscritti da link (feed ICS), per tutta la famiglia ──────
    val feedState: StateFlow<CalendarFeedState> = calendarFeeds.state
    private val _feedBusy = MutableStateFlow(false)
    val feedBusy: StateFlow<Boolean> = _feedBusy.asStateFlow()
    /** Codice dell'ultimo errore d'iscrizione (vedi `calendarFeeds.js`), da tradurre in UI. */
    private val _feedError = MutableStateFlow<String?>(null)
    val feedError: StateFlow<String?> = _feedError.asStateFlow()

    fun clearFeedError() {
        _feedError.value = null
    }

    /** Il server scarica subito il link: se è sbagliato lo si sa adesso. */
    fun subscribeFeed(name: String, url: String, colorHex: String, onDone: (Boolean) -> Unit) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || url.isBlank()) return
        viewModelScope.launch {
            _feedBusy.value = true
            _feedError.value = null
            val result = calendarFeeds.subscribe(familyId, name.trim(), url.trim(), colorHex)
            _feedBusy.value = false
            when (result) {
                is CalendarFeedResult.Ok -> onDone(true)
                is CalendarFeedResult.Failed -> {
                    _feedError.value = result.reason
                    onDone(false)
                }
            }
        }
    }

    fun deleteFeed(feedId: String) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            _feedBusy.value = true
            val result = calendarFeeds.delete(familyId, feedId)
            _feedBusy.value = false
            if (result is CalendarFeedResult.Failed) _feedError.value = result.reason
        }
    }

    /** Al rientro in primo piano: il permesso può essere cambiato da Impostazioni. */
    fun refreshDeviceCalendars() {
        deviceCalendars.refreshPermission()
        deviceCalendars.showAround(_uiState.value.selectedDate)
    }
    private val forcedFamilyId = MutableStateFlow<String?>(null)

    private val pullToRefresh = PullToRefreshController(viewModelScope)
    val isRefreshing: StateFlow<Boolean> = pullToRefresh.isRefreshing

    /** Pull-to-refresh: rilegge gli eventi del calendario da Firestore. */
    fun forceRefresh() = pullToRefresh.refresh {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return@refresh
        calendarRepository.awaitForceRestartRealtime(familyId)
        runCatching { calendarRepository.flushPending(familyId) }
    }

    init {
        viewModelScope.launch {
            combine(
                familyDao.observeAll(),
                forcedFamilyId,
            ) { families, forced ->
                forced?.takeIf { it.isNotBlank() } ?: families.firstOrNull()?.id.orEmpty()
            }
                .distinctUntilChanged()
                .onEach { familyId ->
                    if (familyId.isBlank()) {
                        calendarRepository.stopRealtime()
                        _uiState.value = _uiState.value.copy(
                            familyId = "",
                            events = emptyList(),
                            visibilityMembers = emptyList(),
                            isLoading = false,
                            errorMessage = "Nessuna famiglia attiva",
                        )
                        return@onEach
                    }
                    calendarRepository.startRealtime(
                        familyId = familyId,
                        onPermissionDenied = {
                            _uiState.value = _uiState.value.copy(
                                errorMessage = "Accesso Calendario negato per questa famiglia",
                            )
                        },
                    )
                    startTodoRealtime(familyId)
                    calendarFeeds.start(familyId)
                    clearCalendarBadge(familyId)
                    runCatching { calendarRepository.flushPending(familyId) }
                }
                .flatMapLatest { familyId ->
                    if (familyId.isBlank()) {
                        flowOf(CalendarSources())
                    } else {
                        combine(
                            calendarRepository.observeEvents(familyId),
                            familyMemberDao.observeActiveByFamilyId(familyId),
                            todoRepository.observeTodos(familyId, ""),
                            todoRepository.observeLists(familyId, ""),
                        ) { events, members, todos, lists ->
                            val uid = auth.currentUser?.uid
                            val allMembers = members
                                .asSequence()
                                .filter { it.userId.isNotBlank() }
                                .map { row ->
                                    VisibilityPickerMember(
                                        uid = row.userId,
                                        displayName = row.displayName?.takeIf { it.isNotBlank() }
                                            ?: row.email?.takeIf { it.isNotBlank() }
                                            ?: row.userId,
                                    )
                                }
                                .distinctBy { it.uid }
                                .sortedBy { it.displayName.lowercase() }
                                .toList()
                            CalendarSources(
                                familyId = familyId,
                                events = events,
                                todos = todos,
                                lists = lists,
                                allMembers = allMembers,
                            )
                        }
                    }
                }
                .collect { sources ->
                    val familyId = sources.familyId
                    if (familyId.isBlank()) return@collect
                    val uid = auth.currentUser?.uid
                    val visible = sources.events.filterNot { it.isDeleted }.filter { event ->
                        KBVisibilityScope.isVisible(
                            scope = KBVisibilityScope.normalized(event.visibilityScope),
                            memberIds = decodeStringList(event.visibilityMemberIdsJson),
                            createdBy = event.createdBy.takeIf { it.isNotBlank() },
                            currentUid = uid,
                        )
                    }
                    val visibleTodos = sources.todos.filterNot { it.isDeleted }.filter { todo ->
                        KBVisibilityScope.isVisible(
                            scope = KBVisibilityScope.normalized(todo.visibilityScope),
                            memberIds = decodeStringList(todo.visibilityMemberIdsJson),
                            createdBy = todo.createdBy?.takeIf { it.isNotBlank() },
                            currentUid = uid,
                        )
                    }
                    // Solo i to-do con una scadenza: gli altri vivono nel
                    // backlog e non hanno un giorno in cui disegnarli.
                    val reminders = visibleTodos.filter { it.dueAtEpochMillis != null }
                    // Le stesse liste che si vedono in To-Do: una lista di soli
                    // elementi privati di un altro membro non deve comparire.
                    val visibleLists = sources.lists.filterNot { it.isDeleted }.filter { list ->
                        TodoListExposure.memberCanSeeListRow(
                            listId = list.id,
                            todosForChild = sources.todos.filterNot { it.isDeleted },
                            currentUid = uid,
                            listCreatedBy = list.createdBy,
                        )
                    }
                    _uiState.value = withDisplayEvents(_uiState.value.copy(
                        familyId = familyId,
                        events = visible,
                        reminders = reminders,
                        todoLists = visibleLists,
                        visibilityMembers = sources.allMembers.filter { it.uid != uid },
                        assignableMembers = sources.allMembers,
                        currentUid = uid.orEmpty(),
                        // `childId` non filtra più niente ma i to-do lo
                        // pretendono ancora nel documento: si riusa quello che
                        // la famiglia sta già scrivendo.
                        childId = sources.todos.firstOrNull { it.childId.isNotBlank() }?.childId.orEmpty(),
                        isLoading = false,
                        errorMessage = null,
                    ))
                }
        }
    }

    /**
     * Espande le serie nella finestra attorno al giorno selezionato e al mese
     * sfogliato: le frecce del mese spostano la griglia senza cambiare giorno.
     */
    private fun withDisplayEvents(state: CalendarUiState): CalendarUiState {
        // Anche il telefono legge attorno al giorno e al mese guardati.
        deviceCalendars.showAround(state.selectedDate)
        deviceCalendars.showAround(state.displayedMonth)
        val (a1, b1) = EventRecurrence.windowAround(state.selectedDate)
        val (a2, b2) = EventRecurrence.windowAround(state.displayedMonth)
        val from = minOf(a1, a2)
        val to = maxOf(b1, b2)
        val expanded = state.events
            .flatMap { it.occurrencesIn(from, to) }
            .sortedBy { it.startDateEpochMillis }
        return state.copy(displayEvents = expanded)
    }

    fun bindFamily(familyId: String) {
        forcedFamilyId.value = familyId.takeIf { it.isNotBlank() }
    }

    fun setMode(mode: CalendarMode) {
        viewModePreference.write(mode.name)
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun setSelectedDate(date: LocalDate) {
        val updated = _uiState.value.copy(selectedDate = date)
        _uiState.value = withDisplayEvents(
            if (
                updated.displayedMonth.year != date.year ||
                updated.displayedMonth.month != date.month
            ) {
                updated.copy(displayedMonth = date.withDayOfMonth(1))
            } else {
                updated
            },
        )
    }

    fun setDisplayedMonth(date: LocalDate) {
        _uiState.value = withDisplayEvents(_uiState.value.copy(displayedMonth = date.withDayOfMonth(1)))
    }

    fun saveEvent(
        input: CalendarDraftInput,
        editing: KBCalendarEventEntity? = null,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return

        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        var effectiveScope = KBVisibilityScope.normalized(input.visibilityScope)
        var memberIds = input.visibilityMemberIds
        if (effectiveScope == KBVisibilityScope.MEMBERS && memberIds.isEmpty()) {
            effectiveScope = KBVisibilityScope.FAMILY
            memberIds = emptyList()
        }
        val memberIdsJson = encodeStringList(
            if (effectiveScope == KBVisibilityScope.MEMBERS) memberIds else emptyList(),
        )
        val entity = if (editing == null) {
            KBCalendarEventEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                childId = null,
                title = input.title.trim(),
                notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
                location = input.location?.trim()?.takeIf { it.isNotEmpty() },
                startDateEpochMillis = input.startEpochMillis,
                endDateEpochMillis = input.endEpochMillis,
                isAllDay = input.isAllDay,
                categoryRaw = input.categoryRaw,
                recurrenceRaw = input.recurrenceRaw,
                reminderMinutes = input.reminderMinutes,
                priorityRaw = if (input.isUrgent) 1 else 0,
                linkedHealthItemId = null,
                linkedHealthItemType = null,
                visibilityScope = effectiveScope,
                visibilityMemberIdsJson = memberIdsJson,
                isDeleted = false,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                createdBy = uid,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
        } else {
            editing.copy(
                title = input.title.trim(),
                notes = input.notes?.trim()?.takeIf { it.isNotEmpty() },
                location = input.location?.trim()?.takeIf { it.isNotEmpty() },
                startDateEpochMillis = input.startEpochMillis,
                endDateEpochMillis = input.endEpochMillis,
                isAllDay = input.isAllDay,
                categoryRaw = input.categoryRaw,
                recurrenceRaw = input.recurrenceRaw,
                reminderMinutes = input.reminderMinutes,
                priorityRaw = if (input.isUrgent) 1 else 0,
                visibilityScope = effectiveScope,
                visibilityMemberIdsJson = memberIdsJson,
                isDeleted = false,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
        }

        viewModelScope.launch {
            runCatching {
                calendarRepository.upsertEventLocal(entity)
                calendarRepository.flushPending(familyId)
                // Fino a oggi `reminderMinutes` veniva salvato e basta: nessuno
                // lo leggeva, su nessun client. Qui l'avviso viene armato.
                calendarReminderScheduler.sync(
                    eventId = entity.id,
                    familyId = familyId,
                    title = entity.title,
                    startEpochMillis = entity.startDateEpochMillis,
                    reminderMinutes = entity.reminderMinutes,
                    isUrgent = entity.priorityRaw == 1,
                    recurrenceRaw = entity.recurrenceRaw,
                )
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore salvataggio evento")
            }
        }
    }

    fun deleteEvent(occurrence: KBCalendarEventEntity) {
        // Dalla griglia può arrivare una ripetizione: si cancella la serie
        // com'è salvata, non la copia con le date spostate.
        val event = _uiState.value.events.seriesOf(occurrence)
        viewModelScope.launch {
            runCatching {
                // L'evento sparisce: il suo avviso non deve sopravvivergli.
                calendarReminderScheduler.cancel(event.id)
                calendarRepository.deleteEventLocal(event)
                calendarRepository.flushPending(event.familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore eliminazione evento")
            }
        }
    }

    /**
     * Salva un promemoria creato o modificato dal calendario. È un to-do vero,
     * nella lista scelta: chi lo apre da To-Do lo trova identico.
     */
    fun saveReminder(
        draft: CalendarReminderDraft,
        editingId: String?,
        /**
         * `false` quando l'utente ha negato le notifiche: il promemoria si
         * salva lo stesso — è pur sempre un to-do con una scadenza — ma senza
         * armare un avviso che il sistema scarterebbe in silenzio.
         */
        reminderEnabled: Boolean = true,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || draft.title.isBlank()) return
        val childId = _uiState.value.childId
        viewModelScope.launch {
            runCatching {
                val listId = draft.listId.ifBlank {
                    // Senza `listId` il to-do esisterebbe ma non si vedrebbe in
                    // nessuna lista: è la trappola degli orfani già nota.
                    _uiState.value.todoLists.firstOrNull()?.id
                        ?: todoRepository.addList(familyId, childId, "Promemoria")
                }
                if (editingId == null) {
                    todoRepository.addTodo(
                        familyId = familyId,
                        childId = childId,
                        listId = listId,
                        title = draft.title,
                        notes = draft.notes,
                        dueAtEpochMillis = draft.dueAtEpochMillis,
                        dueHasTime = draft.dueHasTime,
                        assignedTo = draft.assignedTo,
                        priorityRaw = if (draft.isUrgent) 1 else 0,
                        // Un promemoria creato dal calendario ha una scadenza:
                        // l'avviso è il motivo per cui esiste — salvo che le
                        // notifiche siano state negate.
                        reminderEnabled = reminderEnabled,
                        visibilityScope = draft.visibilityScope,
                        visibilityMemberIds = draft.visibilityMemberIds,
                    )
                } else {
                    todoRepository.updateTodo(
                        todoId = editingId,
                        title = draft.title,
                        notes = draft.notes,
                        dueAtEpochMillis = draft.dueAtEpochMillis,
                        dueHasTime = draft.dueHasTime,
                        assignedTo = draft.assignedTo,
                        priorityRaw = if (draft.isUrgent) 1 else 0,
                        reminderEnabled = reminderEnabled,
                        visibilityScope = draft.visibilityScope,
                        visibilityMemberIds = draft.visibilityMemberIds,
                    )
                }
            }.onFailure {
                _uiState.value = _uiState.value.copy(
                    errorMessage = it.localizedMessage ?: "Errore salvataggio promemoria",
                )
            }
        }
    }

    /** Spunta o despunta un promemoria dal calendario, senza aprirlo. */
    fun toggleReminderDone(todoId: String) {
        viewModelScope.launch {
            runCatching { todoRepository.toggleTodoDone(todoId) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = it.localizedMessage ?: "Errore aggiornamento promemoria",
                    )
                }
        }
    }

    fun deleteReminder(todoId: String) {
        viewModelScope.launch {
            runCatching { todoRepository.deleteTodo(todoId) }
                .onFailure {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = it.localizedMessage ?: "Errore eliminazione promemoria",
                    )
                }
        }
    }

    fun onCalendarOpened() {
        // Il telefono non dipende dalla famiglia: va letto anche prima che
        // la famiglia attiva sia arrivata.
        refreshDeviceCalendars()
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        // Si riaggancia anche il listener dei to-do: `TodoRepository` è un
        // singleton con UN solo listener, e `TodoListViewModel.onCleared()`
        // lo spegne uscendo da To-Do. Senza questo, chi passa da To-Do al
        // calendario non vedrebbe più arrivare promemoria nuovi — è la stessa
        // trappola del listener condiviso già pagata su iOS.
        startTodoRealtime(familyId)
        clearCalendarBadge(familyId)
    }

    /**
     * I promemoria sono to-do: per vederli arrivare serve il loro listener,
     * che altrimenti parte solo entrando nella sezione To-Do. `startRealtime`
     * è idempotente (salta se è già agganciato sulla stessa famiglia).
     */
    private fun startTodoRealtime(familyId: String) {
        if (familyId.isBlank()) return
        todoRepository.startRealtime(
            familyId = familyId,
            childId = _uiState.value.childId,
        )
    }

    private fun clearCalendarBadge(familyId: String) {
        homeBadgeManager.clearLocal(CounterField.CALENDAR)
        viewModelScope.launch {
            runCatching { countersService.reset(familyId, CounterField.CALENDAR) }
        }
    }

    override fun onCleared() {
        calendarRepository.stopRealtime()
        calendarFeeds.stop()
        // `todoRepository.stopRealtime()` NON si chiama qui: il listener è
        // condiviso con le schermate To-Do, e spegnerlo uscendo dal calendario
        // lascerebbe cieca la sezione To-Do aperta subito dopo.
        super.onCleared()
    }
}

fun LocalDate.toEpochStartOfDay(zoneId: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zoneId).toInstant().toEpochMilli()

