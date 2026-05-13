package it.vittorioscocca.kidbox.ui.screens.passwords

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.dao.PasswordEntryDao
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.data.local.entity.PasswordGroupEntity
import it.vittorioscocca.kidbox.data.passwords.security.PasswordSecurityPreferences
import it.vittorioscocca.kidbox.data.passwords.security.SecurityScanScheduler
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

sealed class PasswordHomeFilter {
    data object All : PasswordHomeFilter()

    data object Favorites : PasswordHomeFilter()

    /** Visibilità «tutta la famiglia». */
    data object FamilyShared : PasswordHomeFilter()

    /** Visibilità «solo io» e creatore = utente corrente. */
    data object OnlyMineVisibility : PasswordHomeFilter()

    data class ByGroup(val groupId: String) : PasswordHomeFilter()
}

private const val TAG = "PasswordsHomeVM"

data class PasswordGroupChipUi(
    val id: String,
    val label: String,
)

data class PasswordRowUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val isFavorite: Boolean,
    val iconURL: String?,
    /** Allineato a iOS: avviso (es. password scaduta). */
    val hasWarning: Boolean,
)

data class PasswordSectionUi(
    val id: String,
    val title: String,
    /** Nome icona stile SF Symbol da PasswordGroup (es. folder, briefcase.fill). */
    val headerIconRaw: String,
    /** Esadecimale gruppo iOS, es. #5E5CE6. */
    val headerColorHex: String,
    val isExpanded: Boolean = true,
    val rows: List<PasswordRowUi>,
)

data class PasswordsHomeUiState(
    val sections: List<PasswordSectionUi> = emptyList(),
    val filter: PasswordHomeFilter = PasswordHomeFilter.All,
    val groupChips: List<PasswordGroupChipUi> = emptyList(),
    val searchQuery: String = "",
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val securityIssuesCount: Int = 0,
)

@HiltViewModel
class PasswordsHomeViewModel @Inject constructor(
    private val passwordsRepository: PasswordsRepository,
    private val passwordEntryDao: PasswordEntryDao,
    private val passwordCypher: PasswordCypher,
    private val auth: FirebaseAuth,
    private val securityScanScheduler: SecurityScanScheduler,
    private val securityPreferences: PasswordSecurityPreferences,
) : ViewModel() {

    private val familyIdFlow = MutableStateFlow<String?>(null)
    private val filterFlow = MutableStateFlow<PasswordHomeFilter>(PasswordHomeFilter.All)
    private val searchFlow = MutableStateFlow("")
    private val selectingFlow = MutableStateFlow(false)
    private val selectedFlow = MutableStateFlow<Set<String>>(emptySet())
    private val collapsedSectionIdsFlow = MutableStateFlow<Set<String>>(emptySet())

    /** Evita get ripetuti su ogni riapertura della stessa famiglia nella sessione. */
    private var lastHydratedFamilyId: String? = null

    val uiState: StateFlow<PasswordsHomeUiState> = familyIdFlow
        .filterNotNull()
        .flatMapLatest { fid ->
            val entriesFlow = passwordsRepository.observeVisibleEntries(fid)
            val groupsFlow = passwordsRepository.observeVisibleGroups(fid)
            combine(
                combine(entriesFlow, groupsFlow, filterFlow) { entries, groups, filter ->
                    Triple(entries, groups, filter)
                },
                combine(
                    searchFlow,
                    selectingFlow,
                    selectedFlow,
                    collapsedSectionIdsFlow,
                ) { searchQuery, selecting, selected, collapsedSectionIds ->
                    UiSelectionState(searchQuery, selecting, selected, collapsedSectionIds)
                },
            ) { egf, sss ->
                buildUiState(
                    familyId = fid,
                    entries = egf.first,
                    groups = egf.second,
                    filter = egf.third,
                    searchQuery = sss.searchQuery,
                    selecting = sss.selecting,
                    selected = sss.selected,
                    collapsedSectionIds = sss.collapsedSectionIds,
                )
            }
        }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            PasswordsHomeUiState(),
        )

    private fun buildUiState(
        familyId: String,
        entries: List<PasswordEntryEntity>,
        groups: List<PasswordGroupEntity>,
        filter: PasswordHomeFilter,
        searchQuery: String,
        selecting: Boolean,
        selected: Set<String>,
        collapsedSectionIds: Set<String>,
    ): PasswordsHomeUiState {
        val uid = auth.currentUser?.uid.orEmpty()
        val unassignedId = PasswordGroupIds.id(familyId, PasswordGroupIds.UNASSIGNED_SLUG)
        val visibleGroups = groups.filter { it.deletedAtEpochMillis == null }
        val groupChips = buildSortedGroupChips(visibleGroups, unassignedId, uid)

        val active = entries.filter { it.deletedAtEpochMillis == null }
        val scoped = when (filter) {
            PasswordHomeFilter.All -> active
            PasswordHomeFilter.Favorites -> active.filter { it.isFavorite }
            PasswordHomeFilter.FamilyShared ->
                active.filter { KBVisibilityScope.normalizedPassword(it.visibility) == KBVisibilityScope.FAMILY }
            PasswordHomeFilter.OnlyMineVisibility ->
                active.filter {
                    KBVisibilityScope.normalizedPassword(it.visibility) == KBVisibilityScope.ONLY_CREATOR &&
                        it.createdBy == uid
                }
            is PasswordHomeFilter.ByGroup -> active.filter { (it.groupId ?: unassignedId) == filter.groupId }
        }

        val q = searchQuery.trim().lowercase()
        val filtered = if (q.isEmpty()) {
            scoped
        } else {
            scoped.filter { e ->
                val title = runCatching {
                    passwordCypher.decrypt(e.titleCipher, e.familyId, e.visibility, e.createdBy, uid)
                }.getOrElse { "" }.lowercase()
                val user = e.usernameCipher?.let { c ->
                    runCatching {
                        passwordCypher.decrypt(c, e.familyId, e.visibility, e.createdBy, uid)
                    }.getOrNull()
                }.orEmpty().lowercase()
                title.contains(q) || user.contains(q)
            }
        }

        val sections = buildSections(
            familyId = familyId,
            filteredEntries = filtered,
            visibleGroups = visibleGroups,
            unassignedId = unassignedId,
            uid = uid,
            collapsedSectionIds = collapsedSectionIds,
        )

        return PasswordsHomeUiState(
            sections = sections,
            filter = filter,
            groupChips = groupChips,
            searchQuery = searchQuery,
            isSelecting = selecting,
            selectedIds = selected,
            securityIssuesCount = active.count { (it.pwnedCount ?: 0) > 0 },
        )
    }

    private fun buildSections(
        familyId: String,
        filteredEntries: List<PasswordEntryEntity>,
        visibleGroups: List<PasswordGroupEntity>,
        unassignedId: String,
        uid: String,
        collapsedSectionIds: Set<String>,
    ): List<PasswordSectionUi> {
        val bucket = linkedMapOf<String, MutableList<PasswordEntryEntity>>()
        for (e in filteredEntries) {
            val gid = e.groupId.orEmpty()
            bucket.getOrPut(gid) { mutableListOf() }.add(e)
        }
        for (list in bucket.values) {
            list.sortByDescending { it.updatedAtEpochMillis }
        }

        val groupById = visibleGroups.associateBy { it.id }
        val sortedGroups = visibleGroups.sortedWith { a, b ->
            val aUn = a.id == unassignedId
            val bUn = b.id == unassignedId
            when {
                aUn && !bUn -> 1
                !aUn && bUn -> -1
                else -> decryptGroupName(a, uid).compareTo(decryptGroupName(b, uid), ignoreCase = true)
            }
        }

        val result = mutableListOf<PasswordSectionUi>()
        for (g in sortedGroups) {
            val list = bucket[g.id] ?: continue
            if (list.isEmpty()) continue
            result.add(
                PasswordSectionUi(
                    id = g.id,
                    title = decryptGroupName(g, uid).ifBlank { "Gruppo" },
                    headerIconRaw = g.icon.ifBlank { "folder" },
                    headerColorHex = g.color.ifBlank { "#5E5CE6" },
                    isExpanded = !collapsedSectionIds.contains(g.id),
                    rows = list.map { mapEntryToRow(it, uid) },
                ),
            )
            bucket.remove(g.id)
        }

        val loose = mutableListOf<PasswordEntryEntity>()
        for ((k, v) in bucket) {
            if (v.isEmpty()) continue
            if (k.isEmpty() || groupById[k] == null) {
                loose.addAll(v)
            }
        }
        if (loose.isNotEmpty()) {
            loose.sortByDescending { it.updatedAtEpochMillis }
            result.add(
                PasswordSectionUi(
                    id = unassignedId,
                    title = "Senza gruppo",
                    headerIconRaw = "tray",
                    headerColorHex = "#8E8E93",
                    isExpanded = !collapsedSectionIds.contains(unassignedId),
                    rows = loose.map { mapEntryToRow(it, uid) },
                ),
            )
        }

        return result
    }

    private fun mapEntryToRow(e: PasswordEntryEntity, uid: String): PasswordRowUi {
        val title = runCatching {
            passwordCypher.decrypt(e.titleCipher, e.familyId, e.visibility, e.createdBy, uid)
        }.getOrElse { "—" }
        val user = e.usernameCipher?.let { c ->
            runCatching {
                passwordCypher.decrypt(c, e.familyId, e.visibility, e.createdBy, uid)
            }.getOrNull()
        }.orEmpty().trim()
        val now = System.currentTimeMillis()
        val ex = e.expiresAtEpochMillis
        val isExpired = ex != null && ex < now
        val isCompromised = (e.pwnedCount ?: 0) > 0
        val hasWarning = isExpired || isCompromised
        return PasswordRowUi(
            id = e.id,
            title = title.ifBlank { "—" },
            subtitle = user.ifBlank { "—" },
            isFavorite = e.isFavorite,
            iconURL = e.iconURL,
            hasWarning = hasWarning,
        )
    }

    private fun buildSortedGroupChips(
        visibleGroups: List<PasswordGroupEntity>,
        unassignedId: String,
        uid: String,
    ): List<PasswordGroupChipUi> {
        return visibleGroups
            .sortedWith { a, b ->
                val aUn = a.id == unassignedId
                val bUn = b.id == unassignedId
                when {
                    aUn && !bUn -> 1
                    !aUn && bUn -> -1
                    else -> {
                        val na = decryptGroupName(a, uid).lowercase()
                        val nb = decryptGroupName(b, uid).lowercase()
                        na.compareTo(nb)
                    }
                }
            }
            .map { g ->
                PasswordGroupChipUi(
                    id = g.id,
                    label = decryptGroupName(g, uid).ifBlank { "Gruppo" },
                )
            }
    }

    private fun decryptGroupName(g: PasswordGroupEntity, uid: String): String =
        runCatching {
            passwordCypher.decrypt(g.nameCipher, g.familyId, g.visibility, g.createdBy, uid)
        }.getOrElse { "" }

    fun bind(familyId: String) {
        viewModelScope.launch {
            if (securityPreferences.weeklyScanEnabled.first()) {
                securityScanScheduler.enqueueWeekly()
            }
        }
        familyIdFlow.value = familyId
        if (lastHydratedFamilyId != familyId) {
            lastHydratedFamilyId = familyId
            viewModelScope.launch(Dispatchers.IO) {
                runCatching {
                    passwordsRepository.hydratePasswordRoomFromServer(familyId)
                }.onFailure { e ->
                    Log.w(TAG, "hydratePasswordRoomFromServer failed familyId=$familyId: ${e.message}")
                }
            }
        }
    }

    fun setFilter(f: PasswordHomeFilter) {
        filterFlow.value = f
        selectedFlow.value = emptySet()
    }

    fun setSearchQuery(value: String) {
        searchFlow.value = value
    }

    fun setSelecting(value: Boolean) {
        selectingFlow.value = value
        if (!value) selectedFlow.value = emptySet()
    }

    fun toggleSelected(id: String) {
        val cur = selectedFlow.value.toMutableSet()
        if (cur.contains(id)) cur.remove(id) else cur.add(id)
        selectedFlow.value = cur
    }

    fun toggleSectionExpanded(sectionId: String) {
        val current = collapsedSectionIdsFlow.value
        collapsedSectionIdsFlow.value = if (current.contains(sectionId)) {
            current - sectionId
        } else {
            current + sectionId
        }
    }

    fun toggleFavorite(entryId: String) {
        viewModelScope.launch {
            val e = passwordEntryDao.getById(entryId) ?: return@launch
            val updated = e.copy(
                isFavorite = !e.isFavorite,
                updatedAtEpochMillis = System.currentTimeMillis(),
                syncStateRaw = 1,
            )
            passwordEntryDao.upsert(updated)
            passwordsRepository.pushUpsertEntry(updated)
            passwordsRepository.scheduleAutofillSnapshotRebuild()
        }
    }

    fun softDeleteSelected(familyId: String) {
        viewModelScope.launch {
            val ids = selectedFlow.value
            if (ids.isEmpty()) return@launch
            val uid = auth.currentUser?.uid.orEmpty()
            val ownedIds = ids.filter { id ->
                passwordEntryDao.getById(id)?.createdBy == uid
            }
            if (ownedIds.isNotEmpty()) {
                passwordsRepository.softDeleteEntriesOptimistic(familyId, ownedIds)
            }
            selectingFlow.value = false
            selectedFlow.value = emptySet()
        }
    }
}

private data class UiSelectionState(
    val searchQuery: String,
    val selecting: Boolean,
    val selected: Set<String>,
    val collapsedSectionIds: Set<String>,
)
