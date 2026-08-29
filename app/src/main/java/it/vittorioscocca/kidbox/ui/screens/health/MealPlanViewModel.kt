package it.vittorioscocca.kidbox.ui.screens.health

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.data.health.HealthLinkStore
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanAIUsageInfo
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanDocument
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanError
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanGenerator
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanInput
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanStore
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.remote.ai.AIAskAIPayload
import it.vittorioscocca.kidbox.data.remote.ai.AIUsageTracker
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.data.repository.PediatricProfileRepository
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBPlan
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MealPlanUiState(
    val subjectName: String = "",
    val input: MealPlanInput = MealPlanInput(),
    val document: MealPlanDocument? = null,
    val lastUsage: MealPlanAIUsageInfo? = null,
    val estimatedUnits: Int = AIAskAIPayload.MEAL_PLAN_MIN_UNITS,
    val isPaidPlan: Boolean = false,
    val isGenerating: Boolean = false,
    val ageYears: Int? = null,
    val weightKg: Double? = null,
    val heightCm: Double? = null,
    val workoutCount: Int = 0,
    val visitCount: Int = 0,
    val examCount: Int = 0,
    val activeTreatmentCount: Int = 0,
    val message: String? = null,
) {
    val hasBodyMetrics: Boolean get() = weightKg != null && heightCm != null
}

@HiltViewModel
class MealPlanViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mealPlanStore: MealPlanStore,
    private val childDao: KBChildDao,
    private val treatmentDao: KBTreatmentDao,
    private val visitDao: KBMedicalVisitDao,
    private val examDao: KBMedicalExamDao,
    private val profileRepository: PediatricProfileRepository,
    private val healthLinkStore: HealthLinkStore,
    private val aiRepository: AiRepository,
    private val usageTracker: AIUsageTracker,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MealPlanUiState())
    val uiState: StateFlow<MealPlanUiState> = _uiState.asStateFlow()

    private var familyId = ""
    private var childId = ""

    fun bind(familyId: String, childId: String, subjectName: String) {
        if (this.childId == childId && this.familyId == familyId) return
        this.familyId = familyId
        this.childId = childId
        _uiState.value = _uiState.value.copy(
            subjectName = subjectName,
            isPaidPlan = CurrentPlanStore.plan.value != KBPlan.FREE,
        )
        load()
    }

    fun updateInput(transform: (MealPlanInput) -> MealPlanInput) {
        _uiState.value = _uiState.value.copy(input = transform(_uiState.value.input))
    }

    fun consumeMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun generate() {
        if (familyId.isBlank() || childId.isBlank() || _uiState.value.isGenerating) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isGenerating = true, message = null)
            val input = _uiState.value.input
            val subjectName = _uiState.value.subjectName.ifBlank {
                context.getString(R.string.health_profile)
            }
            runCatching {
                withContext(Dispatchers.IO) {
                    val inputs = loadInputs()
                    val payload = MealPlanGenerator.buildPayload(
                        subjectName = subjectName,
                        birthDateEpochMillis = inputs.birthMillis,
                        input = input,
                        snapshot = inputs.health,
                        profile = inputs.profile,
                        treatments = inputs.activeTreatments,
                        visits = inputs.visits,
                        exams = inputs.exams,
                    )
                    MealPlanGenerator.generate(
                        aiRepository = aiRepository,
                        usageTracker = usageTracker,
                        familyId = familyId,
                        subjectName = subjectName,
                        input = input,
                        payload = payload,
                    )
                }
            }.onSuccess { result ->
                withContext(Dispatchers.IO) { mealPlanStore.save(childId, result.document) }
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    document = result.document,
                    lastUsage = result.usage,
                    estimatedUnits = result.usage.messageUnitsConsumed,
                )
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isGenerating = false,
                    message = errorMessage(error),
                )
            }
        }
    }

    private fun load() {
        viewModelScope.launch {
            val cached = withContext(Dispatchers.IO) { mealPlanStore.load(childId) }
            runCatching {
                withContext(Dispatchers.IO) {
                    val inputs = loadInputs()
                    val payload = MealPlanGenerator.buildPayload(
                        subjectName = _uiState.value.subjectName.ifBlank { inputs.name },
                        birthDateEpochMillis = inputs.birthMillis,
                        input = cached?.input ?: _uiState.value.input,
                        snapshot = inputs.health,
                        profile = inputs.profile,
                        treatments = inputs.activeTreatments,
                        visits = inputs.visits,
                        exams = inputs.exams,
                    )
                    inputs to MealPlanGenerator.estimate(payload).messageUnits
                }
            }.onSuccess { (inputs, units) ->
                _uiState.value = _uiState.value.copy(
                    subjectName = _uiState.value.subjectName.ifBlank { inputs.name },
                    document = cached,
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
                )
            }
        }
    }

    private data class MealPlanInputs(
        val name: String,
        val birthMillis: Long?,
        val ageYears: Int?,
        val profile: it.vittorioscocca.kidbox.domain.model.KBPediatricProfile?,
        val health: HealthImportSnapshot?,
        val activeTreatments: List<it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity>,
        val visits: List<it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity>,
        val exams: List<it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity>,
    )

    private suspend fun loadInputs(): MealPlanInputs {
        val child = childDao.getById(childId)
        val profile = profileRepository.loadOnce(childId)
        val health = healthLinkStore.load(childId)
        val treatments = treatmentDao.observeByFamilyAndChild(familyId, childId).first()
            .filter { it.isActive && !it.isDeleted && it.petId.isBlank() }
        val visits = visitDao.observeByFamilyAndChild(familyId, childId).first().filter { !it.isDeleted }
        val exams = examDao.observeByFamilyAndChild(familyId, childId).first().filter { !it.isDeleted }
        val birthMillis = child?.birthDateEpochMillis ?: health?.birthDateEpochMillis
        return MealPlanInputs(
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
        val birth = java.util.Calendar.getInstance().apply { timeInMillis = epochMillis }
        val now = java.util.Calendar.getInstance()
        var years = now.get(java.util.Calendar.YEAR) - birth.get(java.util.Calendar.YEAR)
        if (now.get(java.util.Calendar.DAY_OF_YEAR) < birth.get(java.util.Calendar.DAY_OF_YEAR)) years--
        return years
    }

    private fun errorMessage(error: Throwable): String = when (error) {
        is MealPlanError.PlanNotIncluded -> context.getString(R.string.meal_plan_error_plan)
        is MealPlanError.MissingHealthData -> context.getString(R.string.meal_plan_error_missing_data)
        is MealPlanError.PayloadTooLarge -> context.getString(
            R.string.meal_plan_error_payload,
            error.chars,
            error.maxChars,
        )
        is MealPlanError.QuotaWouldExceed -> context.getString(
            R.string.meal_plan_error_quota,
            error.needed,
            error.remaining,
            error.dailyLimit,
        )
        else -> error.message?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.meal_plan_error_generic)
    }
}
