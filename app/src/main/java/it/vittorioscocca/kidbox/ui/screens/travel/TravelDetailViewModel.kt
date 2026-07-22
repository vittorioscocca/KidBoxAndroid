package it.vittorioscocca.kidbox.ui.screens.travel

import it.vittorioscocca.kidbox.util.KBLog

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.remote.ai.TravelPlanRequest
import it.vittorioscocca.kidbox.data.remote.travel.TripRemoteStore
import it.vittorioscocca.kidbox.data.repository.PhotoVideoRepository
import it.vittorioscocca.kidbox.data.repository.TripRepository
import it.vittorioscocca.kidbox.data.travel.TravelTripAlbumTitles
import it.vittorioscocca.kidbox.data.travel.TravelTripExtrasRepository
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBPediatricProfileDao
import it.vittorioscocca.kidbox.data.local.TravelProfilePreferences
import it.vittorioscocca.kidbox.data.local.dao.KBPackingItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripDayPlanDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripExpenseDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripLegDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPackingItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripDayPlanEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripExpenseEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import it.vittorioscocca.kidbox.util.KBLocale

data class TravelTripExtrasUiState(
    val photoCount: Int = 0,
    val noteHasContent: Boolean = false,
    val openTodoCount: Int = 0,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TravelDetailViewModel @Inject constructor(
    private val tripDao: KBTripDao,
    private val tripLegDao: KBTripLegDao,
    private val tripDayPlanDao: KBTripDayPlanDao,
    private val packingItemDao: KBPackingItemDao,
    private val tripExpenseDao: KBTripExpenseDao,
    private val childDao: KBChildDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val pediatricProfileDao: KBPediatricProfileDao,
    private val travelProfilePreferences: TravelProfilePreferences,
    private val extrasRepository: TravelTripExtrasRepository,
    private val tripRepository: TripRepository,
    private val photoVideoRepository: PhotoVideoRepository,
    private val tripRemoteStore: TripRemoteStore,
    private val aiService: AIService,
) : ViewModel() {

    fun tripAlbumTitle(tripName: String): String = TravelTripAlbumTitles.forTrip(tripName)

    private val tripIdFlow = MutableStateFlow("")

    fun setTripId(tripId: String) {
        tripIdFlow.value = tripId
    }

    val trip: StateFlow<KBTripEntity?> = tripIdFlow
        .flatMapLatest { id -> if (id.isBlank()) flowOf(null) else tripDao.observeById(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val dayPlans: StateFlow<List<KBTripDayPlanEntity>> = tripIdFlow
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else tripDayPlanDao.observeByTrip(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val packingItems: StateFlow<List<KBPackingItemEntity>> = tripIdFlow
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else packingItemDao.observeByTrip(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val expenses: StateFlow<List<KBTripExpenseEntity>> = tripIdFlow
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else tripExpenseDao.observeByTrip(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val legs: StateFlow<List<KBTripLegEntity>> = tripIdFlow
        .flatMapLatest { id -> if (id.isBlank()) flowOf(emptyList()) else tripLegDao.observeByTrip(id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val members: StateFlow<List<KBFamilyMemberEntity>> = trip
        .flatMapLatest { t ->
            val familyId = t?.familyId.orEmpty()
            if (familyId.isBlank()) flowOf(emptyList()) else familyMemberDao.observeActiveByFamilyId(familyId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val children: StateFlow<List<KBChildEntity>> = trip
        .flatMapLatest { t ->
            val familyId = t?.familyId.orEmpty()
            if (familyId.isBlank()) {
                flowOf(emptyList())
            } else {
                flow { emit(childDao.getChildrenByFamilyId(familyId)) }.flowOn(Dispatchers.IO)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _extrasState = MutableStateFlow(TravelTripExtrasUiState())
    val extrasState: StateFlow<TravelTripExtrasUiState> = _extrasState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(trip, children) { currentTrip, kids -> currentTrip to kids }.collect { (currentTrip, kids) ->
                val tripEntity = currentTrip ?: return@collect
                refreshExtras(tripEntity, kids.firstOrNull()?.id)
            }
        }
    }

    fun ensureTripExtras(trip: KBTripEntity, userDisplayName: String) {
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            if (uid.isBlank()) return@launch
            extrasRepository.ensureAlbum(trip, uid)
            extrasRepository.ensureNote(trip, uid, userDisplayName)
            children.value.firstOrNull()?.id?.let { childId ->
                extrasRepository.ensureTodoList(trip, childId)
            }
            refreshExtras(tripDao.observeById(trip.id).first() ?: trip, children.value.firstOrNull()?.id)
        }
    }

    fun travelExpenseCategoryId(familyId: String): String =
        extrasRepository.travelExpenseCategoryId(familyId)

    suspend fun ensureAlbumForTrip(trip: KBTripEntity): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val albumId = extrasRepository.ensureAlbum(trip, uid) ?: return null
        photoVideoRepository.flushPending(trip.familyId)
        tripRemoteStore.syncTrip(trip.id)
        return albumId
    }

    suspend fun ensureNoteForTrip(trip: KBTripEntity, userDisplayName: String): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        return extrasRepository.ensureNote(trip, uid, userDisplayName)
    }

    suspend fun ensureTodoListForTrip(trip: KBTripEntity, childId: String): String? =
        extrasRepository.ensureTodoList(trip, childId)

    private suspend fun refreshExtras(trip: KBTripEntity, childId: String?) {
        val albumId = trip.photoAlbumId.orEmpty()
        val photoCount = if (albumId.isNotBlank()) {
            extrasRepository.photoCount(albumId, trip.familyId)
        } else {
            0
        }
        val noteHasContent = trip.notesNoteId?.let { extrasRepository.noteHasUserContent(it) } ?: false
        val openTodoCount = trip.todoListId?.takeIf { it.isNotBlank() && !childId.isNullOrBlank() }
            ?.let { extrasRepository.openTodoCount(it, trip.familyId, childId!!) }
            ?: 0
        _extrasState.value = TravelTripExtrasUiState(
            photoCount = photoCount,
            noteHasContent = noteHasContent,
            openTodoCount = openTodoCount,
        )
    }

    fun itineraryOverview(
        trip: KBTripEntity,
        dayPlans: List<KBTripDayPlanEntity>,
        legs: List<KBTripLegEntity>,
        members: List<KBFamilyMemberEntity>,
        children: List<KBChildEntity>,
    ): TravelItineraryOverview = TravelItineraryBuilder.build(trip, dayPlans, legs, members, children)

    fun setPackingChecked(id: String, checked: Boolean) {
        viewModelScope.launch {
            packingItemDao.setChecked(id, checked)
        }
    }

    fun addPackingItem(familyId: String, tripId: String, label: String) {
        if (label.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            packingItemDao.upsert(
                KBPackingItemEntity(
                    id = UUID.randomUUID().toString(),
                    familyId = familyId,
                    tripId = tripId,
                    label = label.trim(),
                    categoryRaw = "other",
                    isChecked = false,
                    isAIGenerated = false,
                    fromMedicalProfile = false,
                    updatedAtEpoch = now,
                ),
            )
        }
    }

    fun addExpense(familyId: String, tripId: String, amount: Double, currency: String) {
        if (amount <= 0) return
        viewModelScope.launch {
            val fmt = SimpleDateFormat("yyyy-MM-dd", KBLocale.current())
            tripExpenseDao.upsert(
                KBTripExpenseEntity(
                    id = UUID.randomUUID().toString(),
                    familyId = familyId,
                    tripId = tripId,
                    dateString = fmt.format(Date()),
                    amount = amount,
                    currency = currency,
                    categoryRaw = "other",
                    description = null,
                    paidBy = "",
                    updatedAtEpoch = System.currentTimeMillis(),
                ),
            )
        }
    }

    private val _regeneratingDayId = MutableStateFlow<String?>(null)
    val regeneratingDayId: StateFlow<String?> = _regeneratingDayId.asStateFlow()

    private val _dayRegenerateError = MutableStateFlow<String?>(null)
    val dayRegenerateError: StateFlow<String?> = _dayRegenerateError.asStateFlow()

    private val _dayRegenerateSuccess = MutableStateFlow<String?>(null)
    val dayRegenerateSuccess: StateFlow<String?> = _dayRegenerateSuccess.asStateFlow()

    fun clearDayRegenerateSuccess() {
        _dayRegenerateSuccess.value = null
    }

    fun regenerateDayPlan(day: TravelItineraryDay) {
        viewModelScope.launch {
            KBLog.ui.info("regenerateDayPlan tapped dayIndex=${day.dayIndex} date=${day.dateString}", TAG)
            val tripEntity = trip.value ?: run {
                KBLog.ui.warning("regenerateDayPlan ABORT: trip is null", TAG)
                return@launch
            }
            val familyId = tripEntity.familyId.ifBlank {
                KBLog.ui.warning("regenerateDayPlan ABORT: no familyId", TAG)
                return@launch
            }
            _regeneratingDayId.value = day.id
            _dayRegenerateError.value = null

            val otherPlaces = TravelDayRegeneration.collectOtherDaysPlaces(tripEntity.aiProposalJson, day.dateString)
            val tripLegs = tripLegDao.observeByTrip(tripEntity.id).first()
            val legs = TravelDayRegeneration.legsPayload(emptyList(), tripLegs, day.location)
            val dayCount = dayPlans.value.size.coerceAtLeast(1)
            val wizardData = TravelDayRegeneration.wizardData(
                tripName = tripEntity.name,
                day = day,
                budgetPerDay = tripEntity.budgetTotal / dayCount,
                currency = tripEntity.currency,
                legs = legs,
            )
            val prompt = TravelDayRegeneration.regenerationPrompt(day, otherPlaces)
            val familyCtx = buildFamilyContextForRegeneration(familyId)

            aiService.generateTravelPlan(
                TravelPlanRequest(wizardData, prompt, familyCtx, regenerateSingleDay = true),
                familyId,
            )
                .onSuccess { response ->
                    val newDayMap = TravelDayRegeneration.extractRegeneratedDay(response, day.dateString)
                        ?: run {
                            KBLog.ui.error("regenerateDayPlan parse failed date=${day.dateString} narrativeLen=${response.narrativeText.length}", TAG)
                            _dayRegenerateError.value = "Rigenerazione giorno fallita: risposta AI non valida."
                            return@onSuccess
                        }
                    val morningStops = (newDayMap["morningStops"] as? List<*>)?.size ?: 0
                    val afternoonStops = (newDayMap["afternoonStops"] as? List<*>)?.size ?: 0
                    val eveningStops = (newDayMap["eveningStops"] as? List<*>)?.size ?: 0
                    val firstStop = (newDayMap["morningStops"] as? List<*>)?.firstOrNull() as? Map<*, *>
                    KBLog.ui.info("regenerateDayPlan success date=${day.dateString} morning=$morningStops afternoon=$afternoonStops evening=$eveningStops firstStopKeys=${firstStop?.keys}", TAG)

                    val currentDayPlan = dayPlans.value.firstOrNull { it.dateString == day.dateString }
                    if (currentDayPlan != null) {
                        tripDayPlanDao.upsert(
                            currentDayPlan.copy(
                                morningPlan = (newDayMap["morningPlan"] as? String).orEmpty()
                                    .ifBlank { currentDayPlan.morningPlan },
                                afternoonPlan = (newDayMap["afternoonPlan"] as? String).orEmpty()
                                    .ifBlank { currentDayPlan.afternoonPlan },
                                eveningPlan = (newDayMap["eveningPlan"] as? String).orEmpty()
                                    .ifBlank { currentDayPlan.eveningPlan },
                                location = (newDayMap["location"] as? String)
                                    .takeIf { !it.isNullOrBlank() } ?: currentDayPlan.location,
                                estimatedDailyCost = (newDayMap["estimatedDailyCost"] as? Number)
                                    ?.toDouble() ?: currentDayPlan.estimatedDailyCost,
                                updatedAtEpoch = System.currentTimeMillis(),
                            ),
                        )
                    }
                    val newJson = updateDayInProposalJson(tripEntity.aiProposalJson, day.dateString, newDayMap)
                    tripDao.upsert(tripEntity.copy(aiProposalJson = newJson))
                    _dayRegenerateSuccess.value = "Giorno ${day.dayIndex} rigenerato"
                    tripRemoteStore.syncTrip(tripEntity.id)
                    KBLog.ui.info("regenerateDayPlan synced tripId=${tripEntity.id}", TAG)
                }
                .onFailure { err ->
                    KBLog.ui.error("regenerateDayPlan failed", TAG, err)
                    _dayRegenerateError.value = err.message ?: "Rigenerazione giorno fallita."
                }
            _regeneratingDayId.value = null
        }
    }

    private suspend fun buildFamilyContextForRegeneration(familyId: String): Map<String, Any> {
        val children = childDao.getChildrenByFamilyId(familyId)
        val profiles = pediatricProfileDao.observeByFamilyId(familyId).first()
        val members = familyMemberDao.observeActiveByFamilyId(familyId).first()
        val childrenContext = children.map { child ->
            val profile = profiles.firstOrNull { it.childId == child.id }
            val birth = child.birthDateEpochMillis ?: System.currentTimeMillis()
            val age = ((System.currentTimeMillis() - birth) / (365.25 * 24 * 3600 * 1000)).toInt()
            buildMap<String, Any> {
                put("name", child.name)
                put("age", age)
                profile?.allergies?.takeIf { it.isNotBlank() }?.let { put("allergies", it) }
                profile?.medicalNotes?.takeIf { it.isNotBlank() }?.let { put("medicalNotes", it) }
            }
        }
        val participantNames = members.mapNotNull { it.displayName }
        return buildMap {
            put("children", childrenContext)
            put("participants", participantNames)
            FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                travelProfilePreferences.loadProfile(uid)?.let { profile ->
                    put("travelProfile", profile.familyContextValue())
                }
            }
        }
    }

    private fun updateDayInProposalJson(currentJson: String?, date: String, newDayMap: Map<String, Any>): String {
        return try {
            val root = if (currentJson.isNullOrBlank()) JSONObject() else JSONObject(currentJson)
            val days = root.optJSONArray("dayPlans") ?: JSONArray()
            var found = false
            for (i in 0 until days.length()) {
                val d = days.optJSONObject(i) ?: continue
                if (d.optString("date") == date) {
                    days.put(i, mapToJsonObject(newDayMap))
                    found = true
                    break
                }
            }
            if (!found) days.put(mapToJsonObject(newDayMap))
            root.put("dayPlans", days)
            root.toString()
        } catch (e: Exception) {
            currentJson.orEmpty()
        }
    }

    private fun mapToJsonObject(map: Map<String, Any?>): JSONObject {
        val obj = JSONObject()
        map.forEach { (k, v) -> obj.put(k, valueToJson(v)) }
        return obj
    }

    private fun valueToJson(v: Any?): Any? = when (v) {
        null -> JSONObject.NULL
        is Map<*, *> -> @Suppress("UNCHECKED_CAST") mapToJsonObject(v as Map<String, Any?>)
        is List<*> -> JSONArray().also { arr -> v.forEach { item -> arr.put(valueToJson(item)) } }
        else -> v
    }

    companion object {
        private const val TAG = "TravelDetailVM"
    }

    fun deleteTrip(trip: KBTripEntity, onDeleted: () -> Unit) {
        viewModelScope.launch {
            tripRepository.deleteTrip(trip)
            onDeleted()
        }
    }
}
