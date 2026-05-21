package it.vittorioscocca.kidbox.ui.screens.travel

import it.vittorioscocca.kidbox.util.KBLog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.TravelProfilePreferences
import it.vittorioscocca.kidbox.data.local.TravelStyle
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBPackingItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBPediatricProfileDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripDayPlanDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripLegDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPackingItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripDayPlanEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.remote.ai.AIUsageTracker
import it.vittorioscocca.kidbox.data.remote.ai.TravelPlanRequest
import it.vittorioscocca.kidbox.data.remote.travel.TripRemoteStore
import it.vittorioscocca.kidbox.data.travel.TravelTripExtrasRepository
import it.vittorioscocca.kidbox.data.repository.PhotoVideoRepository
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.domain.model.KBPlan
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.json.JSONArray

@HiltViewModel
class TravelPlanningViewModel @Inject constructor(
    private val tripDao: KBTripDao,
    private val tripLegDao: KBTripLegDao,
    private val tripDayPlanDao: KBTripDayPlanDao,
    private val packingItemDao: KBPackingItemDao,
    private val aiService: AIService,
    private val familySessionPreferences: FamilySessionPreferences,
    private val childDao: KBChildDao,
    private val pediatricProfileDao: KBPediatricProfileDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val tripRemoteStore: TripRemoteStore,
    private val subscriptionRepository: SubscriptionRepository,
    private val aiUsageTracker: AIUsageTracker,
    private val travelProfilePreferences: TravelProfilePreferences,
    private val tripExtrasRepository: TravelTripExtrasRepository,
    private val photoVideoRepository: PhotoVideoRepository,
) : ViewModel() {

    private val _usageToday = MutableStateFlow(0)
    val usageToday = _usageToday.asStateFlow()
    private val _dailyLimit = MutableStateFlow(0)
    val dailyLimit = _dailyLimit.asStateFlow()

    private val _familyPlan = MutableStateFlow(KBPlan.FREE)
    val familyPlan = _familyPlan.asStateFlow()

    var destinationName by mutableStateOf("")
    var destinationRegion by mutableStateOf("")
    var tripName by mutableStateOf("")
    var startDate by mutableStateOf(System.currentTimeMillis())
    var endDate by mutableStateOf(System.currentTimeMillis() + 6L * 24 * 3600 * 1000)
    var primaryTransport by mutableStateOf(WizardPrimaryTransport.CAR)
    var selectedParticipantIds by mutableStateOf(setOf<String>())
    var legs by mutableStateOf(listOf(LegDraft()))
    var budgetTotal by mutableStateOf(4000.0)
    var currency by mutableStateOf("EUR")
    var usesCustomBudget by mutableStateOf(false)
    var customBudgetInput by mutableStateOf("")
    var tripStyles by mutableStateOf(setOf<TravelStyle>())
    var freeTextPrompt by mutableStateOf("")

    private val _proposalNarrative = MutableStateFlow<String?>(null)
    val proposalNarrative = _proposalNarrative.asStateFlow()
    private val _proposalPlan = MutableStateFlow<Map<String, Any>?>(null)
    val proposalPlan = _proposalPlan.asStateFlow()
    private val _isGenerating = MutableStateFlow(false)
    val isGenerating = _isGenerating.asStateFlow()
    private val _error = MutableStateFlow<String?>(null)
    val error = _error.asStateFlow()

    private val _children = MutableStateFlow<List<KBChildEntity>>(emptyList())
    val children = _children.asStateFlow()
    private val _members = MutableStateFlow<List<KBFamilyMemberEntity>>(emptyList())
    val members = _members.asStateFlow()

    private var wizardFamilyId: String = ""
    private var familyPlanJob: Job? = null

    sealed class Event {
        data class TripAccepted(val tripId: String) : Event()
        data class Error(val message: String) : Event()
        data object PlanReady : Event()
    }

    private val _events = MutableSharedFlow<Event>()
    val events = _events.asSharedFlow()

    data class LegDraft(
        val id: String = UUID.randomUUID().toString(),
        val fromLocation: String = "",
        val toLocation: String = "",
        val transportMode: String = TransportMode.CAR.raw,
        val days: Int = 1,
    )

    val tripDayCount: Int
        get() {
            val days = TimeUnit.MILLISECONDS.toDays(endDate - startDate).coerceAtLeast(0)
            return (days + 1).toInt().coerceAtLeast(1)
        }

    val canGenerate: Boolean
        get() = destinationName.isNotBlank() &&
            selectedParticipantIds.isNotEmpty() &&
            tripStyles.isNotEmpty() &&
            budgetTotal > 0

    fun canProceed(
        step: Int,
        members: List<KBFamilyMemberEntity>,
        children: List<KBChildEntity>,
    ): Boolean = when (step) {
        0 -> destinationName.trim().length >= 2
        1 -> endDate >= startDate
        2 -> true
        3 -> selectedParticipantIds.isNotEmpty()
        4 -> budgetTotal > 0
        5 -> tripStyles.isNotEmpty()
        else -> canGenerate
    }

    fun applyPrefill(destination: String, region: String = "") {
        destinationName = destination.trim()
        destinationRegion = region
        syncTripFromWizardInputs()
    }

    fun loadTripStylesFromProfile() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        travelProfilePreferences.loadProfile(uid)?.let { profile ->
            tripStyles = profile.styles.toSet()
        }
    }

    fun syncTripFromWizardInputs() {
        val place = destinationName.trim()
        if (place.isNotEmpty()) {
            tripName = "Viaggio a $place"
        }
        val days = tripDayCount
        val mode = primaryTransport.transportModeRaw()
        legs = if (legs.isEmpty()) {
            listOf(LegDraft(toLocation = place, transportMode = mode, days = days))
        } else {
            legs.mapIndexed { index, leg ->
                if (index == 0) leg.copy(toLocation = place, transportMode = mode, days = days) else leg
            }
        }
    }

    fun applyBudgetPreset(preset: TravelWizardBudgetPreset) {
        usesCustomBudget = false
        budgetTotal = preset.amount(currency)
        customBudgetInput = budgetTotal.toInt().toString()
    }

    fun enableCustomBudget() {
        usesCustomBudget = true
        if (customBudgetInput.isBlank()) {
            customBudgetInput = budgetTotal.toInt().coerceAtLeast(0).toString()
        }
    }

    fun updateCustomBudget(text: String) {
        val digits = text.filter { it.isDigit() }
        customBudgetInput = digits
        digits.toDoubleOrNull()?.takeIf { it > 0 }?.let { budgetTotal = it }
    }

    fun matchesBudgetPreset(preset: TravelWizardBudgetPreset): Boolean {
        if (usesCustomBudget) return false
        return budgetTotal.toInt() == preset.amount(currency).toInt()
    }

    fun participantLines(
        members: List<KBFamilyMemberEntity>,
        children: List<KBChildEntity>,
    ): List<TravelWizardParticipantLine> {
        val adults = members.map { member ->
            TravelWizardParticipantLine(
                id = member.userId,
                name = member.displayName ?: "Membro",
                ageLabel = "Adulto",
                emoji = "🧑",
            )
        }
        val kids = children.map { child ->
            val ageLabel = child.birthDateEpochMillis?.let { birth ->
                val years = ((System.currentTimeMillis() - birth) / (365.25 * 24 * 3600 * 1000)).toInt()
                if (years > 0) "$years anni" else "Bambino"
            } ?: "Bambino"
            TravelWizardParticipantLine(
                id = child.id,
                name = child.name,
                ageLabel = ageLabel,
                emoji = "👶",
            )
        }
        return adults + kids
    }

    fun selectedParticipantSummary(
        members: List<KBFamilyMemberEntity>,
        children: List<KBChildEntity>,
    ): String {
        val selected = participantLines(members, children).filter { it.id in selectedParticipantIds }
        if (selected.isEmpty()) return "Nessun viaggiatore selezionato"
        return selected.joinToString(", ") { line ->
            if (line.ageLabel == "Adulto") line.name else "${line.name} (${line.ageLabel})"
        }
    }

    fun budgetFootnote(
        members: List<KBFamilyMemberEntity>,
        children: List<KBChildEntity>,
    ): String {
        val count = selectedParticipantIds.size
        val perDay = budgetTotal / tripDayCount.coerceAtLeast(1)
        val symbol = if (currency == "EUR") "€" else "$"
        return "per $tripDayCount giorni · $count persone · ${selectedParticipantSummary(members, children)} · ~${perDay.toInt()} $symbol/giorno"
    }

    fun composedFreeTextPrompt(): String = buildString {
        if (freeTextPrompt.isNotBlank()) append(freeTextPrompt.trim())
        if (tripStyles.isNotEmpty()) {
            if (isNotEmpty()) append("\n")
            append("Stili per questo viaggio: ${tripStyles.joinToString { it.title }}.")
        }
        if (destinationRegion.isNotBlank()) {
            if (isNotEmpty()) append("\n")
            append("Destinazione: $destinationName, $destinationRegion.")
        }
    }

    fun loadParticipants(familyId: String) {
        wizardFamilyId = familyId
        if (familyId.isNotBlank()) {
            familySessionPreferences.setActiveFamilyId(familyId)
        }
        viewModelScope.launch {
            _children.value = childDao.getChildrenByFamilyId(familyId)
            _members.value = familyMemberDao.observeActiveByFamilyId(familyId).first()
        }
        observeFamilyPlan(familyId)
    }

    private fun resolvedFamilyId(): String =
        wizardFamilyId.ifBlank { familySessionPreferences.getActiveFamilyId().orEmpty() }

    private fun observeFamilyPlan(familyId: String) {
        familyPlanJob?.cancel()
        familyPlanJob = viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            if (familyId.isBlank() || uid.isBlank()) {
                _familyPlan.value = KBPlan.FREE
                return@launch
            }
            _familyPlan.value = subscriptionRepository.getPlan(familyId)
            subscriptionRepository.planFlow(familyId, uid).collect { _familyPlan.value = it }
        }
    }

    fun addLeg() {
        legs = legs + LegDraft()
    }

    fun applyDestinationPrefill(destinationName: String) {
        applyPrefill(destinationName)
    }

    fun removeLeg(id: String) {
        legs = legs.filter { it.id != id }
    }

    fun updateLeg(updated: LegDraft) {
        legs = legs.map { if (it.id == updated.id) updated else it }
    }

    fun selectAllParticipants(childIds: List<String>, memberUserIds: List<String>) {
        selectedParticipantIds = (childIds + memberUserIds).toSet()
    }

    fun clearParticipants() {
        selectedParticipantIds = emptySet()
    }

    fun toggleParticipant(id: String, selected: Boolean) {
        selectedParticipantIds = if (selected) {
            selectedParticipantIds + id
        } else {
            selectedParticipantIds - id
        }
    }

    fun generatePlan() {
        viewModelScope.launch {
            _error.value = null
            if (!canGenerate) {
                val missing = buildList {
                    if (destinationName.isBlank()) add("destinazione")
                    if (selectedParticipantIds.isEmpty()) add("viaggiatori")
                    if (tripStyles.isEmpty()) add("stile viaggio")
                    if (budgetTotal <= 0) add("budget")
                }
                val message = if (missing.isEmpty()) {
                    "Completa tutti i passaggi del wizard prima di generare."
                } else {
                    "Manca ancora: ${missing.joinToString(", ")}. Torna al wizard e completa i passaggi."
                }
                _error.value = message
                _events.emit(Event.Error(message))
                return@launch
            }
            val familyId = resolvedFamilyId()
            if (familyId.isBlank()) {
                val message = "Famiglia non trovata. Torna alla home e riprova."
                _error.value = message
                _events.emit(Event.Error(message))
                return@launch
            }

            _familyPlan.value = subscriptionRepository.getPlan(familyId)
            if (!_familyPlan.value.includesAI) {
                val message =
                    "La pianificazione AI richiede Pro o Max sulla famiglia attiva " +
                        "(campo planOverride o plan su Firestore). Verifica la console admin."
                _error.value = message
                _events.emit(Event.Error(message))
                return@launch
            }

            aiService.fetchUsage(familyId).getOrNull()?.let { usage ->
                _usageToday.value = usage.usageToday
                _dailyLimit.value = usage.dailyLimit
                aiUsageTracker.apply(usage.usageToday, usage.dailyLimit)
            }

            val messageCost = TravelPlanningCountdown.messageCost(tripDayCount)
            val usageSnapshot = aiUsageTracker.state.value
            if (usageSnapshot.dailyLimit > 0 &&
                usageSnapshot.usageToday + messageCost > usageSnapshot.dailyLimit
            ) {
                val message =
                    "Questo viaggio di $tripDayCount giorni richiede $messageCost messaggi AI " +
                        "(${usageSnapshot.usageToday}/${usageSnapshot.dailyLimit} usati oggi). " +
                        "Accorcia il viaggio o riprova domani."
                _error.value = message
                _events.emit(Event.Error(message))
                return@launch
            }

            _isGenerating.value = true

            val children = childDao.getChildrenByFamilyId(familyId)
            val profiles = pediatricProfileDao.observeByFamilyId(familyId).first()
            val members = familyMemberDao.observeActiveByFamilyId(familyId).first()

            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val wizardData = mapOf(
                "tripName" to tripName,
                "startDate" to fmt.format(Date(startDate)),
                "endDate" to fmt.format(Date(endDate)),
                "budgetTotal" to budgetTotal,
                "currency" to currency,
                "legs" to legs.mapIndexed { index, leg ->
                    mapOf(
                        "order" to (index + 1),
                        "fromLocation" to leg.fromLocation,
                        "toLocation" to leg.toLocation,
                        "transportMode" to leg.transportMode,
                        "days" to leg.days,
                    )
                },
            )

            val selectedChildren = children.filter { selectedParticipantIds.contains(it.id) }
            val childrenContext = selectedChildren.map { child ->
                val profile = profiles.firstOrNull { it.childId == child.id }
                val birth = child.birthDateEpochMillis ?: System.currentTimeMillis()
                val ageMs = System.currentTimeMillis() - birth
                val age = (ageMs / (365.25 * 24 * 3600 * 1000)).toInt()
                buildMap<String, Any> {
                    put("name", child.name)
                    put("age", age)
                    profile?.allergies?.takeIf { it.isNotBlank() }?.let { put("allergies", it) }
                    profile?.medicalNotes?.takeIf { it.isNotBlank() }?.let { put("medicalNotes", it) }
                }
            }

            val participantNames = members
                .filter { selectedParticipantIds.contains(it.userId) }
                .mapNotNull { it.displayName }

            val familyContext = buildMap<String, Any> {
                put("children", childrenContext)
                put("participants", participantNames)
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                travelProfilePreferences.loadProfile(uid)?.let { profile ->
                    put("travelProfile", profile.familyContextValue())
                }
            }

            val result = aiService.generateTravelPlan(
                TravelPlanRequest(wizardData, composedFreeTextPrompt(), familyContext),
                familyId,
            )

            result.onSuccess { response ->
                val rawText = response.narrativeText
                val resolvedPlan = response.travelPlan.takeIf { it.isStructuredTravelPlan() }
                    ?: TravelAIResponseParser.parseTravelPlan(rawText)
                _proposalNarrative.value = TravelAIResponseParser.sanitizedNarrative(rawText)
                _proposalPlan.value = resolvedPlan
                _usageToday.value = response.usageToday
                _dailyLimit.value = response.dailyLimit
                aiUsageTracker.apply(response.usageToday, response.dailyLimit)

                val hasPlan = resolvedPlan != null
                val hasNarrative = !_proposalNarrative.value.isNullOrBlank()
                _isGenerating.value = false
                if (hasPlan || hasNarrative) {
                    _events.emit(Event.PlanReady)
                } else {
                    val message = "Risposta vuota dal server. Controlla la connessione e riprova."
                    _error.value = message
                    _events.emit(Event.Error(message))
                }
            }.onFailure { e ->
                _isGenerating.value = false
                val message = e.message ?: "Errore durante la generazione del piano"
                _error.value = message
                _events.emit(Event.Error(message))
            }
        }
    }

    fun acceptPreviewFromSuggestion(destination: TravelDestination) {
        viewModelScope.launch {
            val plan = destination.previewPlanMap()?.takeIf { it.isStructuredTravelPlan() }
            if (plan == null) {
                val message = "Itinerario non disponibile."
                _error.value = message
                _events.emit(Event.Error(message))
                return@launch
            }

            val familyId = resolvedFamilyId().ifBlank { return@launch }
            applySuggestionPrefill(destination, plan)

            val kids = childDao.getChildrenByFamilyId(familyId)
            val adults = familyMemberDao.observeActiveByFamilyId(familyId).first()
            if (selectedParticipantIds.isEmpty()) {
                selectAllParticipants(kids.map { it.id }, adults.map { it.userId })
            }

            persistAcceptedTrip(plan, familyId)?.let { tripId ->
                _events.emit(Event.TripAccepted(tripId))
            }
        }
    }

    fun acceptProposal() {
        viewModelScope.launch {
            val plan = _proposalPlan.value ?: return@launch
            val familyId = resolvedFamilyId().ifBlank { return@launch }
            persistAcceptedTrip(plan, familyId)?.let { tripId ->
                _events.emit(Event.TripAccepted(tripId))
            }
        }
    }

    private suspend fun persistAcceptedTrip(plan: Map<String, Any>, familyId: String): String? {
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        val now = System.currentTimeMillis()
        val tripId = UUID.randomUUID().toString()

        val trip = KBTripEntity(
            id = tripId,
            familyId = familyId,
            name = tripName,
            startDateEpoch = startDate,
            endDateEpoch = endDate,
            participantIdsJson = JSONArray(selectedParticipantIds.toList()).toString(),
            budgetTotal = budgetTotal,
            currency = currency,
            statusRaw = "planning",
            aiProposalJson = TravelItineraryBuilder.proposalToJson(plan),
            createdAtEpoch = now,
            updatedAtEpoch = now,
            createdBy = uid,
            updatedBy = uid,
            syncStateRaw = 1,
            lastSyncError = null,
        )
        tripDao.upsert(trip)

        val legsJson = plan["legs"].asMapList()
        val legEntities = legsJson.map { legMap ->
            KBTripLegEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                tripId = tripId,
                order = (legMap["order"] as? Number)?.toInt() ?: 0,
                fromLocation = legMap["fromLocation"]?.toString().orEmpty(),
                toLocation = legMap["toLocation"]?.toString().orEmpty(),
                transportModeRaw = legMap["transportMode"]?.toString() ?: "car",
                departureAtEpoch = null,
                arrivalAtEpoch = null,
                notes = legMap["notes"]?.toString(),
                updatedAtEpoch = now,
            )
        }
        tripLegDao.upsertAll(legEntities)

        val parsedDayPlans = plan["dayPlans"].asMapList().map { dayMap ->
            KBTripDayPlanEntity(
                id = UUID.randomUUID().toString(),
                familyId = familyId,
                tripId = tripId,
                dateString = dayMap["date"]?.toString().orEmpty(),
                location = dayMap["location"]?.toString().orEmpty(),
                morningPlan = dayMap["morningPlan"]?.toString().orEmpty(),
                afternoonPlan = dayMap["afternoonPlan"]?.toString().orEmpty(),
                eveningPlan = dayMap["eveningPlan"]?.toString().orEmpty(),
                accommodationName = dayMap["accommodationName"]?.toString(),
                accommodationType = dayMap["accommodationType"]?.toString(),
                accommodationCostPerNight = (dayMap["accommodationCostPerNight"] as? Number)?.toDouble(),
                weatherBackupPlan = dayMap["weatherBackupPlan"]?.toString(),
                estimatedDailyCost = (dayMap["estimatedDailyCost"] as? Number)?.toDouble(),
                updatedAtEpoch = now,
            )
        }
        val normalizedDayPlans = TravelItineraryBuilder.normalizeDayPlansForTrip(trip, parsedDayPlans, plan)
        tripDayPlanDao.upsertAll(normalizedDayPlans)

        plan["packingList"].asMapList().forEach { itemMap ->
            packingItemDao.upsert(
                KBPackingItemEntity(
                    id = UUID.randomUUID().toString(),
                    familyId = familyId,
                    tripId = tripId,
                    label = itemMap["label"]?.toString().orEmpty(),
                    categoryRaw = itemMap["category"]?.toString() ?: "other",
                    isChecked = false,
                    isAIGenerated = true,
                    fromMedicalProfile = itemMap["fromMedicalProfile"] as? Boolean ?: false,
                    updatedAtEpoch = now,
                ),
            )
        }

        val displayName = familyMemberDao.observeActiveByFamilyId(familyId).first()
            .firstOrNull { it.userId == uid }?.displayName.orEmpty()
        tripExtrasRepository.ensureAlbum(trip, uid)
        photoVideoRepository.flushPending(familyId)
        tripExtrasRepository.ensureNote(trip, uid, displayName)
        childDao.getChildrenByFamilyId(familyId).firstOrNull()?.id?.let { childId ->
            tripExtrasRepository.ensureTodoList(trip, childId)
        }

        tripRemoteStore.syncTrip(tripId).onFailure { err ->
            val message = err.message ?: "Sincronizzazione viaggio su Firestore non riuscita"
            _error.value = message
            _events.emit(Event.Error(message))
            return null
        }
        return tripId
    }

    private fun applySuggestionPrefill(destination: TravelDestination, plan: Map<String, Any>) {
        destinationName = destination.name
        destinationRegion = destination.region
        tripName = "Viaggio a ${destination.name}"

        @Suppress("UNCHECKED_CAST")
        val tripMeta = plan["trip"] as? Map<String, Any>
        (tripMeta?.get("estimatedTotalCost") as? Number)?.toDouble()?.let { budgetTotal = it }
        tripMeta?.get("currency")?.toString()?.takeIf { it.isNotBlank() }?.let { currency = it }
        if (budgetTotal <= 0) {
            budgetTotal = TravelItineraryBuilder.parseEstimatedCost(destination.estimatedCost) ?: 4000.0
        }

        val dayPlans = plan["dayPlans"].asMapList()
        val dayCount = maxOf(
            if (dayPlans.isNotEmpty()) dayPlans.size else TravelItineraryBuilder.parseDurationDays(destination.durationDays),
            1,
        )

        val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val firstDate = dayPlans.firstOrNull()?.get("date")?.toString()?.let { fmt.parse(it)?.time }
        if (firstDate != null) {
            startDate = firstDate
            endDate = startDate + (dayCount - 1) * TimeUnit.DAYS.toMillis(1)
        } else {
            startDate = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(7)
            endDate = startDate + (dayCount - 1) * TimeUnit.DAYS.toMillis(1)
        }

        _proposalPlan.value = plan
        _proposalNarrative.value = tripMeta?.get("summary")?.toString()?.takeIf { it.isNotBlank() }
            ?: destination.aiHeadline.takeIf { it.isNotBlank() }
        syncTripFromWizardInputs()
    }

    fun regenerate() {
        generatePlan()
    }

    private val _regeneratingDayIndex = MutableStateFlow<Int?>(null)
    val regeneratingDayIndex = _regeneratingDayIndex.asStateFlow()

    private val _proposalRevision = MutableStateFlow(0)
    val proposalRevision = _proposalRevision.asStateFlow()

    fun regenerateDayPlan(day: TravelItineraryDay) {
        viewModelScope.launch {
            KBLog.ui.info("regenerateDayPlan tapped dayIndex=${day.dayIndex} date=${day.dateString}", PLAN_TAG)
            val plan = _proposalPlan.value ?: run {
                KBLog.ui.warning("regenerateDayPlan ABORT: proposal plan null", PLAN_TAG)
                return@launch
            }
            val familyId = resolvedFamilyId().ifBlank {
                KBLog.ui.warning("regenerateDayPlan ABORT: no familyId", PLAN_TAG)
                return@launch
            }
            _regeneratingDayIndex.value = day.dayIndex

            val otherPlaces = TravelDayRegeneration.collectOtherDaysPlaces(plan, day.dateString)
            val prompt = TravelDayRegeneration.regenerationPrompt(day, otherPlaces)
            val legs = TravelDayRegeneration.legsPayload(legs, emptyList(), day.location)
            val wizardData = TravelDayRegeneration.wizardData(
                tripName = tripName,
                day = day,
                budgetPerDay = budgetTotal / tripDayCount.coerceAtLeast(1),
                currency = currency,
                legs = legs,
            )

            val children = childDao.getChildrenByFamilyId(familyId)
            val profiles = pediatricProfileDao.observeByFamilyId(familyId).first()
            val members = familyMemberDao.observeActiveByFamilyId(familyId).first()
            val selectedChildren = children.filter { selectedParticipantIds.contains(it.id) }
            val childrenContext = selectedChildren.map { child ->
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
            val participantNames = members
                .filter { selectedParticipantIds.contains(it.userId) }
                .mapNotNull { it.displayName }
            val familyCtx = buildMap<String, Any> {
                put("children", childrenContext)
                put("participants", participantNames)
                FirebaseAuth.getInstance().currentUser?.uid?.let { uid ->
                    travelProfilePreferences.loadProfile(uid)?.let { profile ->
                        put("travelProfile", profile.familyContextValue())
                    }
                }
            }

            aiService.generateTravelPlan(
                TravelPlanRequest(wizardData, prompt, familyCtx, regenerateSingleDay = true),
                familyId,
            )
                .onSuccess { response ->
                    val newDay = TravelDayRegeneration.extractRegeneratedDay(response, day.dateString)
                        ?: run {
                            KBLog.ui.error("regenerateDayPlan parse failed date=${day.dateString}", PLAN_TAG)
                            _error.value = "Rigenerazione giorno fallita: risposta AI non valida."
                            return@onSuccess
                        }
                    val morningStops = (newDay["morningStops"] as? List<*>)?.size ?: 0
                    val afternoonStops = (newDay["afternoonStops"] as? List<*>)?.size ?: 0
                    val eveningStops = (newDay["eveningStops"] as? List<*>)?.size ?: 0
                    KBLog.ui.info("regenerateDayPlan success date=${day.dateString} morning=$morningStops afternoon=$afternoonStops evening=$eveningStops", PLAN_TAG)
                    val current = _proposalPlan.value ?: return@onSuccess
                    _proposalPlan.value = TravelDayRegeneration.mergeDay(current, newDay, day.dateString)
                    _proposalRevision.value = _proposalRevision.value + 1
                    _error.value = null
                }
                .onFailure { err ->
                    KBLog.ui.error("regenerateDayPlan failed", PLAN_TAG, err)
                    _error.value = "Rigenerazione giorno fallita: ${err.message}"
                }

            _regeneratingDayIndex.value = null
        }
    }

    private companion object {
        const val PLAN_TAG = "TravelPlanningVM"
    }

    fun refinementSeed(): String {
        val fmt = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
        val intro = "Sto pianificando il viaggio \"$tripName\" dal ${fmt.format(Date(startDate))} al ${fmt.format(Date(endDate))}."
        val narrative = _proposalNarrative.value?.take(1200).orEmpty()
        return buildString {
            append(intro)
            if (narrative.isNotBlank()) {
                append("\n\nProposta attuale:\n")
                append(narrative)
            }
            append("\n\nAiutami a modificare o migliorare questo itinerario.")
        }
    }
}
