package it.vittorioscocca.kidbox.ui.screens.health

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.data.health.HealthConnectGateway
import it.vittorioscocca.kidbox.data.health.HealthLinkStore
import it.vittorioscocca.kidbox.data.health.fitness.FitnessAdjustmentProposal
import it.vittorioscocca.kidbox.data.health.fitness.FitnessCompletionSource
import it.vittorioscocca.kidbox.data.health.fitness.FitnessHealthSync
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanAIUsageInfo
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDates
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDocument
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanError
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanGenerator
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanInput
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanRemoteStore
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanStore
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSession
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSessionStatus
import it.vittorioscocca.kidbox.data.health.fitness.FitnessWeeklyReport
import it.vittorioscocca.kidbox.data.health.fitness.FitnessWeeklyReportBuilder
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.data.remote.ai.AIAskAIPayload
import it.vittorioscocca.kidbox.data.remote.ai.AIUsageTracker
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.data.repository.PediatricProfileRepository
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.notifications.FitnessPlanReminderScheduler
import it.vittorioscocca.kidbox.ui.state.PullToRefreshController
import java.util.Calendar
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class FitnessPlanUiState(
    val subjectName: String = "",
    val input: FitnessPlanInput = FitnessPlanInput(),
    val plan: FitnessPlanDocument? = null,
    val selectedDayEpochMillis: Long = FitnessPlanDates.today(),
    val displayedMonthEpochMillis: Long = FitnessPlanDates.today(),
    val lastUsage: FitnessPlanAIUsageInfo? = null,
    val estimatedUnits: Int = AIAskAIPayload.FITNESS_PLAN_MIN_UNITS,
    val isPaidPlan: Boolean = false,
    val isGenerating: Boolean = false,
    val isSyncingHealth: Boolean = false,
    val isAdjusting: Boolean = false,
    val generatingMessageRes: Int = R.string.fitness_generating,
    val weeklyReport: FitnessWeeklyReport? = null,
    val adjustmentProposal: FitnessAdjustmentProposal? = null,
    val lastHealthSyncEpochMillis: Long? = null,
    val healthConnectAvailable: Boolean = false,
    val ageYears: Int? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val workoutCount: Int = 0,
    val visitCount: Int = 0,
    val examCount: Int = 0,
    val activeTreatmentCount: Int = 0,
    val banner: String? = null,
    val message: String? = null,
) {
    /** Peso e altezza: da Health Connect oppure inseriti a mano nel wizard. */
    val hasBodyMetrics: Boolean
        get() = (weightKg ?: input.manualWeightValue) != null &&
            (heightCm ?: input.manualHeightValue) != null

    /** Vero quando Health Connect non copre peso, altezza o età. */
    val needsManualMetrics: Boolean
        get() = weightKg == null || heightCm == null || ageYears == null

    val sessionsOfSelectedDay: List<FitnessSession>
        get() = plan?.sessionsOn(selectedDayEpochMillis).orEmpty()

    /** Il giorno selezionato ricade nelle quattro settimane del piano? */
    val selectedDayInPlan: Boolean
        get() = plan?.weekIndexFor(selectedDayEpochMillis) != null
}

@HiltViewModel
class FitnessPlanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val planStore: FitnessPlanStore,
    private val remoteStore: FitnessPlanRemoteStore,
    private val healthSync: FitnessHealthSync,
    private val healthConnect: HealthConnectGateway,
    private val reminderScheduler: FitnessPlanReminderScheduler,
    private val childDao: KBChildDao,
    private val treatmentDao: KBTreatmentDao,
    private val visitDao: KBMedicalVisitDao,
    private val examDao: KBMedicalExamDao,
    private val profileRepository: PediatricProfileRepository,
    private val healthLinkStore: HealthLinkStore,
    private val aiRepository: AiRepository,
    private val usageTracker: AIUsageTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(FitnessPlanUiState())
    val uiState: StateFlow<FitnessPlanUiState> = _uiState.asStateFlow()

    private val pullToRefresh = PullToRefreshController(viewModelScope)
    val isRefreshing: StateFlow<Boolean> = pullToRefresh.isRefreshing

    private var familyId = ""
    private var childId = ""

    fun bind(familyId: String, childId: String, subjectName: String) {
        if (this.childId == childId && this.familyId == familyId) return
        this.familyId = familyId
        this.childId = childId
        _uiState.value = _uiState.value.copy(
            subjectName = subjectName,
            isPaidPlan = CurrentPlanStore.plan.value != KBPlan.FREE,
            healthConnectAvailable = healthConnect.isAvailable(),
        )
        load()
    }

    fun forceRefresh() = pullToRefresh.refresh {
        if (childId.isBlank()) return@refresh
        syncFromRemote(withContext(Dispatchers.IO) { planStore.load(childId) })
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun dismissBanner() {
        _uiState.value = _uiState.value.copy(banner = null)
    }

    fun selectDay(epochMillis: Long) {
        _uiState.value = _uiState.value.copy(
            selectedDayEpochMillis = FitnessPlanDates.startOfDay(epochMillis),
            displayedMonthEpochMillis = startOfMonth(epochMillis),
        )
    }

    fun showMonth(epochMillis: Long) {
        _uiState.value = _uiState.value.copy(displayedMonthEpochMillis = startOfMonth(epochMillis))
    }

    // ── Generazione ────────────────────────────────────────────────────────

    fun generate(input: FitnessPlanInput) {
        if (familyId.isBlank() || childId.isBlank() || _uiState.value.isGenerating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                input = input,
                isGenerating = true,
                generatingMessageRes = R.string.fitness_generating,
                message = null,
            )
            val subjectName = _uiState.value.subjectName.ifBlank {
                context.getString(R.string.health_profile)
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val inputs = loadInputs()
                    val payload = FitnessPlanGenerator.buildPayload(
                        subjectName = subjectName,
                        birthDateEpochMillis = inputs.birthMillis,
                        input = input,
                        snapshot = inputs.health,
                        profile = inputs.profile,
                        treatments = inputs.activeTreatments,
                        visits = inputs.visits,
                        exams = inputs.exams,
                    )
                    FitnessPlanGenerator.generate(
                        aiRepository = aiRepository,
                        usageTracker = usageTracker,
                        familyId = familyId,
                        subjectName = subjectName,
                        input = input,
                        payload = payload,
                    )
                }
            }.onSuccess { result ->
                persist(result.document)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    lastUsage = result.usage,
                    estimatedUnits = result.usage.messageUnitsConsumed,
                    adjustmentProposal = null,
                    selectedDayEpochMillis = FitnessPlanDates.today(),
                    displayedMonthEpochMillis = startOfMonth(System.currentTimeMillis()),
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    message = errorMessage(error),
                )
            }
        }
    }

    // ── Stato delle sedute ─────────────────────────────────────────────────

    fun markSession(sessionId: String, status: FitnessSessionStatus) {
        val plan = _uiState.value.plan ?: return
        val updated = plan.updateSession(sessionId) { session ->
            session.copy(
                status = status,
                completedAtEpochMillis = if (status == FitnessSessionStatus.DONE) {
                    System.currentTimeMillis()
                } else {
                    null
                },
                completionSource = if (status == FitnessSessionStatus.DONE) {
                    FitnessCompletionSource.MANUAL
                } else {
                    null
                },
                matchedWorkoutId = if (status == FitnessSessionStatus.DONE) session.matchedWorkoutId else null,
                actualMinutes = if (status == FitnessSessionStatus.DONE) session.actualMinutes else null,
                actualKcal = if (status == FitnessSessionStatus.DONE) session.actualKcal else null,
            )
        }
        viewModelScope.launch { persist(updated) }
    }

    /**
     * Sposta una seduta e chiede all'AI di riorganizzare il resto della
     * settimana. Se l'AI non risponde lo spostamento resta valido: è la data
     * scelta dall'utente, non una proposta.
     */
    fun moveSession(sessionId: String, newDateEpochMillis: Long) {
        val plan = _uiState.value.plan ?: return
        if (_uiState.value.isGenerating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isGenerating = true,
                generatingMessageRes = R.string.fitness_reorganizing,
            )
            runCatching {
                withContext(Dispatchers.IO) {
                    FitnessPlanGenerator.reschedule(
                        aiRepository = aiRepository,
                        usageTracker = usageTracker,
                        familyId = familyId,
                        plan = plan,
                        sessionId = sessionId,
                        newDateEpochMillis = newDateEpochMillis,
                    )
                }
            }.onSuccess { outcome ->
                persist(outcome.plan)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    lastUsage = outcome.usage,
                    selectedDayEpochMillis = FitnessPlanDates.startOfDay(newDateEpochMillis),
                    displayedMonthEpochMillis = startOfMonth(newDateEpochMillis),
                    banner = outcome.rationale.takeIf { it.isNotBlank() },
                )
            }.onFailure { error ->
                val fallback = plan.updateSession(sessionId) { session ->
                    session.copy(
                        originalDateEpochMillis = session.originalDateEpochMillis
                            ?: session.dateEpochMillis,
                        dateEpochMillis = FitnessPlanDates.startOfDay(newDateEpochMillis),
                        status = FitnessSessionStatus.PLANNED,
                    )
                }
                persist(fallback)
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    message = errorMessage(error),
                )
            }
        }
    }

    // ── Health Connect ─────────────────────────────────────────────────────

    fun syncHealthNow() {
        val plan = _uiState.value.plan ?: return
        if (_uiState.value.isSyncingHealth) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncingHealth = true)
            val result = withContext(Dispatchers.IO) { healthSync.reconcile(plan) }
            withContext(Dispatchers.IO) {
                planStore.setLastHealthSync(childId, System.currentTimeMillis())
            }
            if (result.didChange) {
                persist(result.plan)
                _uiState.value = _uiState.value.copy(
                    isSyncingHealth = false,
                    lastHealthSyncEpochMillis = planStore.lastHealthSync(childId),
                    banner = context.getString(
                        R.string.fitness_sync_matched,
                        result.matchedSessions.size,
                    ),
                )
            } else {
                _uiState.value = _uiState.value.copy(
                    isSyncingHealth = false,
                    lastHealthSyncEpochMillis = planStore.lastHealthSync(childId),
                    banner = context.getString(R.string.fitness_sync_no_match),
                )
            }
        }
    }

    // ── Report settimanale ─────────────────────────────────────────────────

    fun askWeeklyAdjustment() {
        val plan = _uiState.value.plan ?: return
        val report = _uiState.value.weeklyReport ?: return
        if (_uiState.value.isAdjusting) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isAdjusting = true)
            runCatching {
                withContext(Dispatchers.IO) {
                    FitnessPlanGenerator.weeklyAdjustment(
                        aiRepository = aiRepository,
                        usageTracker = usageTracker,
                        familyId = familyId,
                        plan = plan,
                        report = report,
                    )
                }
            }.onSuccess { outcome ->
                _uiState.value = _uiState.value.copy(
                    isAdjusting = false,
                    adjustmentProposal = outcome.proposal,
                    lastUsage = outcome.usage,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isAdjusting = false,
                    message = errorMessage(error),
                )
            }
        }
    }

    fun applyProposal() {
        val plan = _uiState.value.plan ?: return
        val proposal = _uiState.value.adjustmentProposal ?: return
        viewModelScope.launch {
            persist(FitnessPlanGenerator.apply(proposal.updatedSessions, plan))
            markCurrentWeekReviewed()
            _uiState.value = _uiState.value.copy(
                adjustmentProposal = null,
                banner = context.getString(R.string.fitness_proposal_applied),
            )
        }
    }

    fun keepCurrentPlan() {
        markCurrentWeekReviewed()
        _uiState.value = _uiState.value.copy(adjustmentProposal = null)
    }

    private fun markCurrentWeekReviewed() {
        val report = _uiState.value.weeklyReport ?: return
        planStore.markWeekReviewed(childId, report.weekIndex)
        _uiState.value = _uiState.value.copy(weeklyReport = null)
    }

    // ── Eliminazione ───────────────────────────────────────────────────────

    fun deletePlan() {
        val id = childId
        if (id.isBlank()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                reminderScheduler.cancelAll(id)
                planStore.clear(id)
                remoteStore.delete(id)
            }
            _uiState.value = _uiState.value.copy(
                plan = null,
                weeklyReport = null,
                adjustmentProposal = null,
                lastUsage = null,
            )
        }
    }

    // ── Caricamento ────────────────────────────────────────────────────────

    private fun load() {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { planStore.load(childId) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val inputs = loadInputs()
                    val payload = FitnessPlanGenerator.buildPayload(
                        subjectName = _uiState.value.subjectName.ifBlank { inputs.name },
                        birthDateEpochMillis = inputs.birthMillis,
                        input = cached?.input ?: _uiState.value.input,
                        snapshot = inputs.health,
                        profile = inputs.profile,
                        treatments = inputs.activeTreatments,
                        visits = inputs.visits,
                        exams = inputs.exams,
                    )
                    inputs to FitnessPlanGenerator.estimate(payload).messageUnits
                }
            }.onSuccess { (inputs, units) ->
                _uiState.value = _uiState.value.copy(
                    subjectName = _uiState.value.subjectName.ifBlank { inputs.name },
                    plan = cached,
                    input = cached?.input ?: _uiState.value.input,
                    estimatedUnits = units,
                    isPaidPlan = CurrentPlanStore.plan.value != KBPlan.FREE,
                    ageYears = inputs.ageYears,
                    weightKg = inputs.health?.weightKg,
                    heightCm = inputs.health?.heightCm,
                    workoutCount = inputs.health?.recentWorkouts?.size ?: 0,
                    visitCount = inputs.visits.size,
                    examCount = inputs.exams.size,
                    activeTreatmentCount = inputs.activeTreatments.size,
                    lastHealthSyncEpochMillis = planStore.lastHealthSync(childId),
                )
                refreshWeeklyReport()
                syncFromRemote(cached)
                runPassiveHealthSync()
                runPendingRescheduleIfNeeded()
            }
        }
    }

    /**
     * Allinea la copia locale con Firestore: vince il piano generato più di
     * recente, e un'eliminazione fatta su un altro device svuota anche qui.
     */
    private suspend fun syncFromRemote(local: FitnessPlanDocument?) {
        val id = childId
        if (id.isBlank()) return
        when (val remote = withContext(Dispatchers.IO) { remoteStore.fetch(id) }) {
            is FitnessPlanRemoteStore.Remote.None ->
                if (local != null) {
                    withContext(Dispatchers.IO) { remoteStore.upsert(id, local) }
                }

            is FitnessPlanRemoteStore.Remote.Deleted ->
                if (local != null) {
                    withContext(Dispatchers.IO) {
                        planStore.clear(id)
                        reminderScheduler.cancelAll(id)
                    }
                    _uiState.value = _uiState.value.copy(plan = null, weeklyReport = null)
                }

            is FitnessPlanRemoteStore.Remote.Plan -> {
                val document = remote.document
                val localGenerated = local?.generatedAtEpochMillis ?: Long.MIN_VALUE
                if (document.generatedAtEpochMillis < localGenerated) return
                // Stessa generazione: vince chi ha più sedute chiuse, così un
                // "Fatto" segnato su un altro device non viene riportato indietro.
                if (document.generatedAtEpochMillis == localGenerated &&
                    doneCount(document) <= doneCount(local)
                ) {
                    return
                }
                withContext(Dispatchers.IO) { planStore.save(id, document) }
                _uiState.value = _uiState.value.copy(plan = document, input = document.input)
                refreshWeeklyReport()
            }
        }
    }

    /** Riconciliazione passiva all'apertura, con la stessa soglia di iOS. */
    private suspend fun runPassiveHealthSync() {
        val plan = _uiState.value.plan ?: return
        val last = planStore.lastHealthSync(childId)
        if (last != null && System.currentTimeMillis() - last < PASSIVE_SYNC_INTERVAL_MILLIS) return
        val result = withContext(Dispatchers.IO) { healthSync.reconcile(plan) }
        withContext(Dispatchers.IO) {
            planStore.setLastHealthSync(childId, System.currentTimeMillis())
        }
        if (!result.didChange) return
        persist(result.plan)
        _uiState.value = _uiState.value.copy(
            lastHealthSyncEpochMillis = planStore.lastHealthSync(childId),
            banner = context.getString(R.string.fitness_sync_matched, result.matchedSessions.size),
        )
    }

    /**
     * Seduta spostata da una notifica: la riorganizzazione AI è rimasta in
     * sospeso perché richiede rete, e la eseguiamo alla prima apertura.
     */
    private fun runPendingRescheduleIfNeeded() {
        if (!_uiState.value.isPaidPlan) return
        val plan = _uiState.value.plan ?: return
        val sessionId = planStore.pendingReschedule(childId) ?: return
        val session = plan.session(sessionId) ?: run {
            planStore.clearPendingReschedule(childId)
            return
        }
        planStore.clearPendingReschedule(childId)
        moveSession(sessionId, session.dateEpochMillis)
    }

    private fun refreshWeeklyReport() {
        val plan = _uiState.value.plan
        if (plan == null) {
            _uiState.value = _uiState.value.copy(weeklyReport = null)
            return
        }
        val weekIndex = FitnessWeeklyReportBuilder.lastCompletedWeekIndex(plan)
        val report = weekIndex?.let { FitnessWeeklyReportBuilder.report(it, plan) }
        val alreadyReviewed = report != null && report.weekIndex in planStore.reviewedWeeks(childId)
        _uiState.value = _uiState.value.copy(weeklyReport = if (alreadyReviewed) null else report)
    }

    private suspend fun persist(document: FitnessPlanDocument) {
        _uiState.value = _uiState.value.copy(plan = document, input = document.input)
        withContext(Dispatchers.IO) {
            planStore.save(childId, document)
            remoteStore.upsert(childId, document)
            reminderScheduler.reschedule(childId, familyId, document)
        }
        refreshWeeklyReport()
    }

    private fun doneCount(document: FitnessPlanDocument?): Int =
        document?.allSessions?.count { it.status == FitnessSessionStatus.DONE } ?: 0

    private fun startOfMonth(epochMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.DAY_OF_MONTH, 1)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private data class FitnessInputs(
        val name: String,
        val birthMillis: Long?,
        val ageYears: Int?,
        val profile: KBPediatricProfile?,
        val health: HealthImportSnapshot?,
        val activeTreatments: List<KBTreatmentEntity>,
        val visits: List<KBMedicalVisitEntity>,
        val exams: List<KBMedicalExamEntity>,
    )

    private suspend fun loadInputs(): FitnessInputs {
        val child = childDao.getById(childId)
        val profile = profileRepository.loadOnce(childId)
        val health = healthLinkStore.load(childId)
        val treatments = treatmentDao.observeByFamilyAndChild(familyId, childId).first()
            .filter { it.isActive && !it.isDeleted && it.petId.isBlank() }
        val visits = visitDao.observeByFamilyAndChild(familyId, childId).first().filter { !it.isDeleted }
        val exams = examDao.observeByFamilyAndChild(familyId, childId).first().filter { !it.isDeleted }
        val birthMillis = child?.birthDateEpochMillis ?: health?.birthDateEpochMillis
        return FitnessInputs(
            name = child?.name ?: context.getString(R.string.health_profile),
            birthMillis = birthMillis,
            ageYears = birthMillis?.let { yearsSince(it) },
            profile = profile,
            health = health,
            activeTreatments = treatments,
            visits = visits,
            exams = exams,
        )
    }

    private fun yearsSince(epochMillis: Long): Int {
        val birth = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val now = Calendar.getInstance()
        var years = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) years--
        return years
    }

    private fun errorMessage(error: Throwable): String = when (error) {
        is FitnessPlanError.PlanNotIncluded -> context.getString(R.string.fitness_error_plan)
        is FitnessPlanError.MissingHealthData -> context.getString(R.string.fitness_error_missing_data)
        is FitnessPlanError.IncompleteSetup -> context.getString(R.string.fitness_error_incomplete)
        is FitnessPlanError.InvalidPlanFormat -> context.getString(R.string.fitness_error_format)
        is FitnessPlanError.PayloadTooLarge -> context.getString(
            R.string.fitness_error_payload,
            error.chars,
            error.maxChars,
        )
        is FitnessPlanError.QuotaWouldExceed -> context.getString(
            R.string.fitness_error_quota,
            error.needed,
            error.remaining,
            error.dailyLimit,
        )
        is FirebaseFunctionsException ->
            if (error.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED) {
                context.getString(R.string.fitness_error_timeout)
            } else {
                error.message?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.fitness_error_generic)
            }
        else -> error.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.fitness_error_generic)
    }

    private companion object {
        /** Non ha senso rileggere Health Connect a ogni apertura della schermata. */
        const val PASSIVE_SYNC_INTERVAL_MILLIS = 30L * 60 * 1000
    }
}
