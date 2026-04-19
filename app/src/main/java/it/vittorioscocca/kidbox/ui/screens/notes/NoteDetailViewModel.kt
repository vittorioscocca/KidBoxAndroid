package it.vittorioscocca.kidbox.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.NoteRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NoteDetailUiState(
    val familyId: String = "",
    val noteId: String = "",
    val title: String = "",
    val body: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val badgeManager: HomeBadgeManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NoteDetailUiState())
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var boundKey: Pair<String, String>? = null

    fun bind(
        familyId: String,
        noteId: String,
    ) {
        if (familyId.isBlank() || noteId.isBlank()) {
            _uiState.value = NoteDetailUiState(isLoading = false, errorMessage = "Nota non disponibile")
            return
        }
        val key = familyId to noteId
        if (boundKey == key && observeJob != null) return
        boundKey = key
        badgeManager.clearLocal(CounterField.NOTES)
        viewModelScope.launch { badgeManager.resetRemote(familyId, CounterField.NOTES) }
        _uiState.value = _uiState.value.copy(familyId = familyId, noteId = noteId, isLoading = true)
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            noteRepository.observeById(noteId).collect { note ->
                val current = _uiState.value
                if (note == null) {
                    _uiState.value = current.copy(isLoading = false)
                } else {
                    if (current.isDirty) {
                        _uiState.value = current.copy(isLoading = false)
                        return@collect
                    }
                    val titlePlain = note.title.htmlToPlainText()
                    _uiState.value = current.copy(
                        title = titlePlain,
                        body = note.body,
                        isLoading = false,
                        isDirty = false,
                    )
                }
            }
        }
    }

    fun updateTitle(value: String) {
        _uiState.value = _uiState.value.copy(title = value, isDirty = true)
    }

    fun updateBody(value: String) {
        _uiState.value = _uiState.value.copy(body = value, isDirty = true)
    }

    fun save(
        onDone: () -> Unit,
    ) {
        val current = _uiState.value
        if (current.familyId.isBlank() || current.noteId.isBlank()) return
        _uiState.value = current.copy(isSaving = true)
        viewModelScope.launch {
            runCatching {
                noteRepository.upsertNote(
                    familyId = current.familyId,
                    noteId = current.noteId,
                    title = current.title.htmlToPlainText().trim(),
                    body = current.body.trimEnd(),
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isSaving = false, isDirty = false, errorMessage = null)
                onDone()
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(isSaving = false, errorMessage = err.message ?: "Errore salvataggio")
            }
        }
    }

    fun saveSilently() {
        val current = _uiState.value
        if (current.familyId.isBlank() || current.noteId.isBlank() || !current.isDirty) return
        viewModelScope.launch {
            runCatching {
                noteRepository.upsertNote(
                    familyId = current.familyId,
                    noteId = current.noteId,
                    title = current.title.htmlToPlainText().trim(),
                    body = current.body.trimEnd(),
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isDirty = false)
            }
        }
    }
}
