package it.vittorioscocca.kidbox.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.NoteRepository
import it.vittorioscocca.kidbox.domain.model.KBNote
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotesHomeUiState(
    val familyId: String = "",
    val notes: List<KBNote> = emptyList(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class NotesHomeViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val badgeManager: HomeBadgeManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotesHomeUiState())
    val uiState: StateFlow<NotesHomeUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var boundFamilyId: String? = null

    fun bind(familyId: String) {
        if (familyId.isBlank()) {
            _uiState.value = NotesHomeUiState(isLoading = false, errorMessage = "Famiglia non disponibile")
            return
        }
        if (boundFamilyId == familyId && observeJob != null) return
        boundFamilyId = familyId

        badgeManager.clearLocal(CounterField.NOTES)
        viewModelScope.launch { badgeManager.resetRemote(familyId, CounterField.NOTES) }

        noteRepository.startRealtime(
            familyId = familyId,
            onPermissionDenied = {
                _uiState.value = _uiState.value.copy(errorMessage = "Accesso Note negato per questa famiglia")
            },
        )

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            noteRepository.observeByFamilyId(familyId).collect { notes ->
                _uiState.value = _uiState.value.copy(
                    familyId = familyId,
                    notes = notes.sortedByDescending { it.updatedAtEpochMillis },
                    isLoading = false,
                )
            }
        }
    }

    fun createEmptyNote(
        onCreated: (String) -> Unit,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            val noteId = noteRepository.upsertNote(
                familyId = familyId,
                title = "",
                body = "",
            )
            onCreated(noteId)
        }
    }

    fun deleteNote(
        noteId: String,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch { noteRepository.softDelete(familyId, noteId) }
    }

    fun deleteNotes(
        noteIds: Set<String>,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || noteIds.isEmpty()) return
        viewModelScope.launch {
            noteIds.forEach { noteRepository.softDelete(familyId, it) }
        }
    }

    override fun onCleared() {
        noteRepository.stopRealtime()
        super.onCleared()
    }
}
