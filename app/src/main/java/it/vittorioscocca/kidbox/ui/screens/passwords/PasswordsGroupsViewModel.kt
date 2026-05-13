package it.vittorioscocca.kidbox.ui.screens.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.dao.PasswordGroupDao
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.data.local.entity.PasswordGroupEntity
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PasswordGroupRowUi(
    val id: String,
    val label: String,
    val iconRaw: String,
    val colorHex: String,
    val count: Int,
)

data class PasswordsGroupsUiState(
    val rows: List<PasswordGroupRowUi> = emptyList(),
)

@HiltViewModel
class PasswordsGroupsViewModel @Inject constructor(
    private val passwordsRepository: PasswordsRepository,
    private val passwordGroupDao: PasswordGroupDao,
    private val passwordCypher: PasswordCypher,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val familyIdFlow = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PasswordsGroupsUiState> = familyIdFlow
        .filterNotNull()
        .flatMapLatest { fid ->
            combine(
                passwordsRepository.observeVisibleGroups(fid),
                passwordsRepository.observeVisibleEntries(fid),
            ) { groups, entries ->
                val uid = auth.currentUser?.uid.orEmpty()
                val visibleGroups = groups.filter { it.deletedAtEpochMillis == null }
                val visibleEntries = entries.filter { it.deletedAtEpochMillis == null }
                val unassignedId = PasswordGroupIds.id(fid, PasswordGroupIds.UNASSIGNED_SLUG)
                val counts = buildCounts(visibleEntries, unassignedId)
                val sorted = visibleGroups.sortedWith { a, b ->
                    val aUnassigned = a.id == unassignedId
                    val bUnassigned = b.id == unassignedId
                    when {
                        aUnassigned && !bUnassigned -> 1
                        !aUnassigned && bUnassigned -> -1
                        else -> decryptName(a, uid).compareTo(decryptName(b, uid), ignoreCase = true)
                    }
                }
                PasswordsGroupsUiState(
                    rows = sorted.map { g ->
                        PasswordGroupRowUi(
                            id = g.id,
                            label = decryptName(g, uid).ifBlank { "Gruppo" },
                            iconRaw = g.icon.ifBlank { "folder" },
                            colorHex = g.color.ifBlank { "#7C6FDE" },
                            count = counts[g.id] ?: 0,
                        )
                    },
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PasswordsGroupsUiState(),
        )

    private fun decryptName(g: PasswordGroupEntity, uid: String): String = runCatching {
        passwordCypher.decrypt(g.nameCipher, g.familyId, g.visibility, g.createdBy, uid)
    }.getOrElse { "" }

    fun bind(familyId: String) {
        familyIdFlow.value = familyId
    }

    fun createGroup(name: String) {
        val familyId = familyIdFlow.value ?: return
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        val cleanName = name.trim()
        if (uid.isEmpty() || cleanName.isEmpty()) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val entity = PasswordGroupEntity(
                id = "kb.password.group.$familyId.${UUID.randomUUID()}",
                familyId = familyId,
                nameCipher = passwordCypher.encrypt(cleanName, familyId, KBVisibilityScope.FAMILY, uid),
                icon = "folder",
                color = "#7C6FDE",
                visibility = KBVisibilityScope.FAMILY,
                createdBy = uid,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                deletedAtEpochMillis = null,
                syncStateRaw = 1,
                lastSyncError = null,
            )
            passwordGroupDao.upsert(entity)
            passwordsRepository.pushUpsertGroup(entity)
        }
    }

    private fun buildCounts(
        entries: List<PasswordEntryEntity>,
        unassignedId: String,
    ): Map<String, Int> {
        val result = linkedMapOf<String, Int>()
        entries.forEach { e ->
            val gid = e.groupId ?: unassignedId
            result[gid] = (result[gid] ?: 0) + 1
        }
        return result
    }
}
