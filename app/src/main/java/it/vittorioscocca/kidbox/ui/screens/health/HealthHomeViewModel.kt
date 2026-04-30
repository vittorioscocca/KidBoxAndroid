package it.vittorioscocca.kidbox.ui.screens.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.AiConsentStore
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.dao.KBVaccineDao
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class HealthHomeState(
    val subjectName: String = "",
    val activeTreatmentCount: Int = 0,
    val hasAnyHealthData: Boolean = false,
    val hasAiConsent: Boolean = false,
)

@HiltViewModel
class HealthHomeViewModel @Inject constructor(
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val treatmentDao: KBTreatmentDao,
    private val visitDao: KBMedicalVisitDao,
    private val examDao: KBMedicalExamDao,
    private val vaccineDao: KBVaccineDao,
    private val aiConsentStore: AiConsentStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthHomeState())
    val uiState: StateFlow<HealthHomeState> = _uiState.asStateFlow()

    private var loadedFamilyId = ""
    private var loadedChildId = ""

    fun load(familyId: String, childId: String) {
        if (loadedFamilyId == familyId && loadedChildId == childId) return
        loadedFamilyId = familyId
        loadedChildId = childId

        _uiState.value = _uiState.value.copy(hasAiConsent = aiConsentStore.hasHealthAiConsent())

        viewModelScope.launch {
            val child = childDao.getById(childId)
            val name: String = if (child != null) {
                child.name
            } else {
                memberDao.observeActiveByFamilyId(familyId)
                    .first()
                    .firstOrNull { it.userId == childId }
                    ?.displayName
                    ?.takeIf { it.isNotBlank() }
                    ?: "Profilo"
            }
            _uiState.value = _uiState.value.copy(subjectName = name)
        }

        val now = System.currentTimeMillis()

        treatmentDao.observeByFamilyAndChild(familyId, childId)
            .map { treatments ->
                treatments.count { t ->
                    t.isActive && !t.isDeleted && (t.isLongTerm || t.endDateEpochMillis == null || t.endDateEpochMillis >= now)
                }
            }
            .onEach { count -> _uiState.value = _uiState.value.copy(activeTreatmentCount = count) }
            .launchIn(viewModelScope)

        combine(
            visitDao.observeByFamilyAndChild(familyId, childId),
            examDao.observeByFamilyAndChild(familyId, childId),
            treatmentDao.observeByFamilyAndChild(familyId, childId),
            vaccineDao.observeByFamilyAndChild(familyId, childId),
        ) { visits, exams, treatments, vaccines ->
            visits.isNotEmpty() || exams.isNotEmpty() || treatments.isNotEmpty() || vaccines.isNotEmpty()
        }
            .onEach { hasAny -> _uiState.value = _uiState.value.copy(hasAnyHealthData = hasAny) }
            .launchIn(viewModelScope)
    }

    fun grantAiConsent() {
        aiConsentStore.setHealthAiConsent(true)
        _uiState.value = _uiState.value.copy(hasAiConsent = true)
    }
}
