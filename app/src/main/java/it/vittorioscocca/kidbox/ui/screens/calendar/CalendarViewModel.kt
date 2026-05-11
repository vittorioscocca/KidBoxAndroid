package it.vittorioscocca.kidbox.ui.screens.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.CountersService
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.CalendarRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.ui.screens.notes.VisibilityPickerMember
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
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

enum class CalendarMode { MONTH, YEAR }

data class CalendarUiState(
    val familyId: String = "",
    val mode: CalendarMode = CalendarMode.MONTH,
    val selectedDate: LocalDate = LocalDate.now(),
    val displayedMonth: LocalDate = LocalDate.now().withDayOfMonth(1),
    val events: List<KBCalendarEventEntity> = emptyList(),
    /** Membri (escluso utente corrente) per il foglio visibilità in creazione evento. */
    val visibilityMembers: List<VisibilityPickerMember> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

data class CalendarDraftInput(
    val title: String,
    val notes: String?,
    val location: String?,
    val categoryRaw: String,
    val recurrenceRaw: String,
    val isAllDay: Boolean,
    val reminderMinutes: Int?,
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
    private val countersService: CountersService,
    private val homeBadgeManager: HomeBadgeManager,
    private val auth: FirebaseAuth,
) : ViewModel() {
    private val _uiState = MutableStateFlow(CalendarUiState())
    val uiState: StateFlow<CalendarUiState> = _uiState.asStateFlow()
    private val forcedFamilyId = MutableStateFlow<String?>(null)

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
                    clearCalendarBadge(familyId)
                    runCatching { calendarRepository.flushPending(familyId) }
                }
                .flatMapLatest { familyId ->
                    if (familyId.isBlank()) {
                        flowOf(Triple("", emptyList(), emptyList()))
                    } else {
                        combine(
                            calendarRepository.observeEvents(familyId),
                            familyMemberDao.observeActiveByFamilyId(familyId),
                        ) { events, members ->
                            val uid = auth.currentUser?.uid
                            val visibilityMembers = members
                                .asSequence()
                                .filter { it.userId.isNotBlank() && (uid == null || it.userId != uid) }
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
                            Triple(familyId, events, visibilityMembers)
                        }
                    }
                }
                .collect { (familyId, rawEvents, visibilityMembers) ->
                    if (familyId.isBlank()) return@collect
                    val uid = auth.currentUser?.uid
                    val visible = rawEvents.filterNot { it.isDeleted }.filter { event ->
                        KBVisibilityScope.isVisible(
                            scope = KBVisibilityScope.normalized(event.visibilityScope),
                            memberIds = decodeStringList(event.visibilityMemberIdsJson),
                            createdBy = event.createdBy.takeIf { it.isNotBlank() },
                            currentUid = uid,
                        )
                    }
                    _uiState.value = _uiState.value.copy(
                        familyId = familyId,
                        events = visible,
                        visibilityMembers = visibilityMembers,
                        isLoading = false,
                        errorMessage = null,
                    )
                }
        }
    }

    fun bindFamily(familyId: String) {
        forcedFamilyId.value = familyId.takeIf { it.isNotBlank() }
    }

    fun setMode(mode: CalendarMode) {
        _uiState.value = _uiState.value.copy(mode = mode)
    }

    fun setSelectedDate(date: LocalDate) {
        val updated = _uiState.value.copy(selectedDate = date)
        _uiState.value = if (
            updated.displayedMonth.year != date.year ||
            updated.displayedMonth.month != date.month
        ) {
            updated.copy(displayedMonth = date.withDayOfMonth(1))
        } else {
            updated
        }
    }

    fun setDisplayedMonth(date: LocalDate) {
        _uiState.value = _uiState.value.copy(displayedMonth = date.withDayOfMonth(1))
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
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore salvataggio evento")
            }
        }
    }

    fun deleteEvent(event: KBCalendarEventEntity) {
        viewModelScope.launch {
            runCatching {
                calendarRepository.deleteEventLocal(event)
                calendarRepository.flushPending(event.familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore eliminazione evento")
            }
        }
    }

    fun onCalendarOpened() {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        clearCalendarBadge(familyId)
    }

    private fun clearCalendarBadge(familyId: String) {
        homeBadgeManager.clearLocal(CounterField.CALENDAR)
        viewModelScope.launch {
            runCatching { countersService.reset(familyId, CounterField.CALENDAR) }
        }
    }

    override fun onCleared() {
        calendarRepository.stopRealtime()
        super.onCleared()
    }
}

fun LocalDate.toEpochStartOfDay(zoneId: ZoneId = ZoneId.systemDefault()): Long =
    atStartOfDay(zoneId).toInstant().toEpochMilli()

