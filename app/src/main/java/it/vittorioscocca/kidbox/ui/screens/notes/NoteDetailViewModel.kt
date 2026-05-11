package it.vittorioscocca.kidbox.ui.screens.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.repository.NoteRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class NoteDetailUiState(
    val familyId: String = "",
    val noteId: String = "",
    val title: String = "",
    val body: String = "",
    val visibilityScope: String = KBVisibilityScope.FAMILY,
    val visibilityMemberIds: List<String> = emptyList(),
    val pickerMembersExcludingSelf: List<VisibilityPickerMember> = emptyList(),
    /** Creatore: solo lui può cambiare visibilità (anche dopo il primo salvataggio). */
    val noteCreatedBy: String = "",
    val currentUid: String = "",
    val isNewRoute: Boolean = false,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val isDirty: Boolean = false,
    val errorMessage: String? = null,
) {
    val canEditVisibility: Boolean
        get() =
            currentUid.isNotBlank() &&
                (noteCreatedBy.isBlank() || noteCreatedBy == currentUid)
}

@HiltViewModel
class NoteDetailViewModel @Inject constructor(
    private val noteRepository: NoteRepository,
    private val badgeManager: HomeBadgeManager,
    private val auth: FirebaseAuth,
    private val familyMemberDao: KBFamilyMemberDao,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NoteDetailUiState())
    val uiState: StateFlow<NoteDetailUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var boundKey: Triple<String, String, Boolean>? = null

    fun bind(
        familyId: String,
        noteId: String,
        isNewRoute: Boolean,
    ) {
        if (familyId.isBlank() || noteId.isBlank()) {
            _uiState.value = NoteDetailUiState(isLoading = false, errorMessage = "Nota non disponibile")
            return
        }
        val key = Triple(familyId, noteId, isNewRoute)
        if (boundKey == key && observeJob != null) return
        boundKey = key
        badgeManager.clearLocal(CounterField.NOTES)
        viewModelScope.launch { badgeManager.resetRemote(familyId, CounterField.NOTES) }
        _uiState.value = _uiState.value.copy(
            familyId = familyId,
            noteId = noteId,
            isNewRoute = isNewRoute,
            isLoading = true,
            currentUid = auth.currentUser?.uid.orEmpty(),
        )
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                noteRepository.observeById(noteId),
                familyMemberDao.observeActiveByFamilyId(familyId),
            ) { note, members ->
                val me = auth.currentUser?.uid.orEmpty()
                val pickerMembers = members
                    .filter { it.userId != me }
                    .map { row ->
                        val label = row.displayName?.trim()?.takeIf { it.isNotEmpty() }
                            ?: row.email?.trim()?.takeIf { it.isNotEmpty() }
                            ?: "Membro"
                        VisibilityPickerMember(uid = row.userId, displayName = label)
                    }
                    .sortedBy { it.displayName.lowercase() }
                note to pickerMembers
            }.collect { (note, pickerMembers) ->
                val cur = _uiState.value
                if (note == null) {
                    _uiState.value = cur.copy(isLoading = false, pickerMembersExcludingSelf = pickerMembers)
                } else {
                    if (cur.isDirty) {
                        _uiState.value = cur.copy(
                            isLoading = false,
                            pickerMembersExcludingSelf = pickerMembers,
                            noteCreatedBy = note.createdBy,
                        )
                        return@collect
                    }
                    val titlePlain = note.title.htmlToPlainText()
                    _uiState.value = cur.copy(
                        title = titlePlain,
                        body = note.body,
                        visibilityScope = KBVisibilityScope.normalized(note.visibilityScope),
                        visibilityMemberIds = note.visibilityMemberIds,
                        pickerMembersExcludingSelf = pickerMembers,
                        noteCreatedBy = note.createdBy,
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

    fun setVisibilityConfirmed(
        scope: String,
        memberIds: List<String>,
    ) {
        val norm = KBVisibilityScope.normalized(scope)
        val ids = if (norm == KBVisibilityScope.MEMBERS) memberIds else emptyList()
        _uiState.value = _uiState.value.copy(
            visibilityScope = norm,
            visibilityMemberIds = ids,
            isDirty = true,
        )
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
                    visibilityScope = current.visibilityScope,
                    visibilityMemberIds = current.visibilityMemberIds,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    isDirty = false,
                    errorMessage = null,
                )
                onDone()
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = err.message ?: "Errore salvataggio",
                )
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
                    visibilityScope = current.visibilityScope,
                    visibilityMemberIds = current.visibilityMemberIds,
                )
            }.onSuccess {
                _uiState.value = _uiState.value.copy(isDirty = false)
            }
        }
    }
}
