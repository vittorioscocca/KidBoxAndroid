package it.vittorioscocca.kidbox.ui.screens.passwords

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.dao.PasswordGroupDao
import it.vittorioscocca.kidbox.data.local.entity.PasswordGroupEntity
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.util.UUID
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PasswordGroupDetailUiState(
    val familyId: String = "",
    val groupId: String? = null,
    val title: String = "",
    val selectedIcon: String = "folder",
    val selectedColor: String = "#7C6FDE",
    val visibility: String = KBVisibilityScope.FAMILY,
    val isSaving: Boolean = false,
) {
    val isEditMode: Boolean get() = !groupId.isNullOrBlank()
}

@HiltViewModel
class PasswordGroupDetailViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val groupDao: PasswordGroupDao,
    private val passwordCypher: PasswordCypher,
    private val auth: FirebaseAuth,
    private val repository: PasswordsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PasswordGroupDetailUiState())
    val uiState: StateFlow<PasswordGroupDetailUiState> = _uiState.asStateFlow()

    fun load(familyId: String, groupId: String?) {
        val gid = groupId?.trim()?.takeIf { it.isNotEmpty() }
        if (gid == null) {
            _uiState.value = PasswordGroupDetailUiState(familyId = familyId)
            return
        }
        viewModelScope.launch {
            val uid = auth.currentUser?.uid.orEmpty()
            val group = groupDao.getById(gid)
            if (group == null) {
                _uiState.value = PasswordGroupDetailUiState(familyId = familyId)
                return@launch
            }
            // Si prefilla il nome mostrato, così l'utente modifica quello che vede.
            val storedTitle = runCatching {
                passwordCypher.decrypt(group.nameCipher, group.familyId, group.visibility, group.createdBy, uid)
            }.getOrDefault("")
            val title = PasswordDefaultGroups.displayName(appContext, group.id, group.familyId, storedTitle)
            _uiState.value = PasswordGroupDetailUiState(
                familyId = familyId,
                groupId = gid,
                title = title,
                selectedIcon = group.icon.ifBlank { "folder" },
                selectedColor = group.color.ifBlank { "#7C6FDE" },
                visibility = KBVisibilityScope.normalizedPassword(group.visibility),
            )
        }
    }

    fun setTitle(value: String) = _uiState.update { it.copy(title = value.take(30)) }
    fun setIcon(icon: String) = _uiState.update { it.copy(selectedIcon = icon) }
    fun setColor(colorHex: String) = _uiState.update { it.copy(selectedColor = colorHex) }
    fun setVisibility(scope: String) = _uiState.update { it.copy(visibility = KBVisibilityScope.normalizedPassword(scope)) }

    fun save(onDone: () -> Unit) {
        val state = uiState.value
        val familyId = state.familyId
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        val cleanTitle = state.title.trim()
        if (familyId.isBlank() || uid.isBlank() || cleanTitle.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            val now = System.currentTimeMillis()
            val existing = state.groupId?.let { groupDao.getById(it) }
            val groupId = existing?.id ?: "kb.password.group.$familyId.${UUID.randomUUID()}"
            val createdBy = existing?.createdBy ?: uid
            val createdAt = existing?.createdAtEpochMillis ?: now

            val entity = PasswordGroupEntity(
                id = groupId,
                familyId = familyId,
                nameCipher = passwordCypher.encrypt(cleanTitle, familyId, state.visibility, createdBy),
                icon = state.selectedIcon,
                color = state.selectedColor,
                visibility = state.visibility,
                createdBy = createdBy,
                createdAtEpochMillis = createdAt,
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = null,
                syncStateRaw = 1,
                lastSyncError = null,
            )
            groupDao.upsert(entity)
            repository.pushUpsertGroup(entity)
            _uiState.update { it.copy(isSaving = false) }
            onDone()
        }
    }
}
