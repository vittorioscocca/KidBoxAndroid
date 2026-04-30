package it.vittorioscocca.kidbox.ui.screens.health.exams

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.mapper.examStatusFromRaw
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.sync.MedicalExamSyncCenter
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

data class MedicalExamsState(
    val isLoading: Boolean = true,
    val urgentPending: List<KBMedicalExam> = emptyList(),
    val pending: List<KBMedicalExam> = emptyList(),
    val booked: List<KBMedicalExam> = emptyList(),
    val done: List<KBMedicalExam> = emptyList(),
    val resultIn: List<KBMedicalExam> = emptyList(),
    val unknownStatus: List<KBMedicalExam> = emptyList(),
)

@HiltViewModel
class MedicalExamsViewModel @Inject constructor(
    private val repository: MedicalExamRepository,
    private val syncCenter: MedicalExamSyncCenter,
) : ViewModel() {

    private val _uiState = MutableStateFlow(MedicalExamsState())
    val uiState: StateFlow<MedicalExamsState> = _uiState.asStateFlow()

    private var familyId: String = ""
    private var childId: String = ""

    fun bind(familyId: String, childId: String) {
        if (this.familyId == familyId && this.childId == childId) return
        this.familyId = familyId
        this.childId = childId
        syncCenter.start(familyId)
        val knownRaws = KBExamStatus.values().map { it.rawValue }.toSet()
        repository.observe(familyId, childId)
            .onEach { exams ->
                val urgentStatuses = setOf(KBExamStatus.PENDING.rawValue, KBExamStatus.BOOKED.rawValue)
                _uiState.value = MedicalExamsState(
                    isLoading = false,
                    urgentPending = exams.filter { it.isUrgent && it.statusRaw in urgentStatuses }
                        .sortedBy { it.deadlineEpochMillis },
                    pending = exams.filter { it.statusRaw == KBExamStatus.PENDING.rawValue }
                        .sortedBy { it.deadlineEpochMillis },
                    booked = exams.filter { it.statusRaw == KBExamStatus.BOOKED.rawValue }
                        .sortedBy { it.deadlineEpochMillis },
                    done = exams.filter { it.statusRaw == KBExamStatus.DONE.rawValue }
                        .sortedByDescending { it.deadlineEpochMillis },
                    resultIn = exams.filter { it.statusRaw == KBExamStatus.RESULT_IN.rawValue }
                        .sortedByDescending { it.deadlineEpochMillis },
                    unknownStatus = exams.filter { it.statusRaw !in knownRaws }
                        .sortedByDescending { it.deadlineEpochMillis },
                )
            }
            .launchIn(viewModelScope)
    }

    override fun onCleared() {
        super.onCleared()
        if (familyId.isNotBlank()) syncCenter.stop(familyId)
    }
}
