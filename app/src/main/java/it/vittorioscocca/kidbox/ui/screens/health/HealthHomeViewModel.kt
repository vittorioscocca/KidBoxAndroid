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
import it.vittorioscocca.kidbox.data.sync.TreatmentSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
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
    val vaccineCount: Int = 0,
    val visitCount: Int = 0,
    val examCount: Int = 0,
    val pendingExamCount: Int = 0,
    /** Allineato a iOS [PediatricHomeView.timelineEvents]: visite + esami + vaccini + cure attive (non eliminate). */
    val timelineEventCount: Int = 0,
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
    private val treatmentSyncCenter: TreatmentSyncCenter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthHomeState())
    val uiState: StateFlow<HealthHomeState> = _uiState.asStateFlow()

    private var loadedFamilyId = ""
    private var loadedChildId = ""

    fun load(familyId: String, childId: String) {
        if (loadedFamilyId == familyId && loadedChildId == childId) return
        loadedFamilyId = familyId
        loadedChildId = childId

        if (familyId.isNotBlank()) {
            treatmentSyncCenter.start(familyId)
        }

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

        combine(
            treatmentDao.observeByFamilyAndChild(familyId, childId),
            visitDao.observeByFamilyAndChild(familyId, childId),
            examDao.observeByFamilyAndChild(familyId, childId),
            vaccineDao.observeByFamilyAndChild(familyId, childId),
        ) { treatments, visits, exams, vaccines ->
            val activeForCureCard = treatments.count { t ->
                t.isActive && !t.isDeleted && (t.isLongTerm || t.endDateEpochMillis == null || t.endDateEpochMillis >= now)
            }
            val activeForTimeline = treatments.count { it.isActive && !it.isDeleted }
            val pendingExams = exams.count { e ->
                when (KBExamStatus.entries.firstOrNull { it.rawValue == e.statusRaw } ?: KBExamStatus.PENDING) {
                    KBExamStatus.PENDING, KBExamStatus.BOOKED -> true
                    else -> false
                }
            }
            val timelineTotal = visits.size + exams.size + vaccines.size + activeForTimeline
            val hasAny = visits.isNotEmpty() || exams.isNotEmpty() || treatments.isNotEmpty() || vaccines.isNotEmpty()
            _uiState.value = _uiState.value.copy(
                activeTreatmentCount = activeForCureCard,
                vaccineCount = vaccines.size,
                visitCount = visits.size,
                examCount = exams.size,
                pendingExamCount = pendingExams,
                timelineEventCount = timelineTotal,
                hasAnyHealthData = hasAny,
            )
        }
            .launchIn(viewModelScope)
    }

    fun grantAiConsent() {
        aiConsentStore.setHealthAiConsent(true)
        _uiState.value = _uiState.value.copy(hasAiConsent = true)
    }
}
