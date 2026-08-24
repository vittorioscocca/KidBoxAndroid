package it.vittorioscocca.kidbox.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.NoteRepository
import it.vittorioscocca.kidbox.domain.model.KBNote
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class NoteAuthor(
    val uid: String,
    val displayName: String,
)

data class NotesHomeUiState(
    val familyId: String = "",
    val notes: List<KBNote> = emptyList(),
    // Chiave = uid, valore = nome da mostrare. Risolto dai membri famiglia
    // correnti (non dal campo denormalizzato `updatedByName`, che alla
    // creazione resta vuoto — stessa logica di `resolvedName(uid:)` su iOS).
    val memberNames: Map<String, String> = emptyMap(),
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class NotesHomeViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val familyMemberDao: KBFamilyMemberDao,
    private val badgeManager: HomeBadgeManager,
    private val auth: FirebaseAuth,
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
            combine(
                noteRepository.observeByFamilyId(familyId),
                familyMemberDao.observeActiveByFamilyId(familyId),
            ) { notes, members -> notes to members }
                .collect { (notes, members) ->
                    val uid = auth.currentUser?.uid
                    val visible = notes.filter { it.isVisibleTo(uid) }.sortedByDescending { it.updatedAtEpochMillis }
                    val names = members.associate { m ->
                        val name = m.displayName?.trim()?.takeIf { it.isNotEmpty() }
                            ?: m.email?.trim().orEmpty()
                        m.userId to name
                    }
                    _uiState.value = _uiState.value.copy(
                        familyId = familyId,
                        notes = visible,
                        memberNames = names,
                        isLoading = false,
                    )
                }
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
