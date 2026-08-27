package it.vittorioscocca.kidbox.ui.screens.passwords

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.PasswordEntryDao
import it.vittorioscocca.kidbox.data.local.dao.PasswordGroupDao
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.data.local.entity.PasswordGroupEntity
import it.vittorioscocca.kidbox.data.passwords.FaviconResolver
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.notifications.PasswordExpiryReminderScheduler
import java.util.UUID
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray

data class AddPasswordPickerMember(
    val userId: String,
    val label: String,
)

data class AddPasswordPickerGroup(
    val id: String,
    val label: String,
    /** Nome in stile SF Symbol, come lo scrive iOS. Vedi `passwordGroupIcon`. */
    val icon: String = "folder",
    val colorHex: String = "#7C6FDE",
)

/** Valori decifrati per precompilare il form in modifica. */
data class AddPasswordEditDraft(
    val entryId: String,
    val title: String,
    val username: String,
    val password: String,
    val website: String,
    val notes: String,
    val visibilityScope: String,
    val visibilityMemberIds: Set<String>,
    val selectedGroupId: String?,
    val hasExpiry: Boolean,
    val expiryMillis: Long,
)

@HiltViewModel
class AddPasswordViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val passwordCypher: PasswordCypher,
    private val passwordEntryDao: PasswordEntryDao,
    private val passwordGroupDao: PasswordGroupDao,
    private val passwordsRepository: PasswordsRepository,
    private val familyMemberDao: KBFamilyMemberDao,
    private val auth: FirebaseAuth,
    private val passwordExpiryReminderScheduler: PasswordExpiryReminderScheduler,
) : ViewModel() {

    private val familyIdFlow = MutableStateFlow<String?>(null)
    private var bindLoadJob: Job? = null

    private val _editDraft = MutableStateFlow<AddPasswordEditDraft?>(null)
    val editDraft: StateFlow<AddPasswordEditDraft?> = _editDraft

    val pickerMembers: StateFlow<List<AddPasswordPickerMember>> = familyIdFlow
        .filterNotNull()
        .flatMapLatest { fid ->
            familyMemberDao.observeActiveByFamilyId(fid).map { members ->
                val self = auth.currentUser?.uid?.trim().orEmpty()
                members
                    .asSequence()
                    .filter { !it.isDeleted && it.userId != self }
                    .sortedBy { it.displayName?.lowercase() ?: it.userId }
                    .map { m ->
                        val label = sequenceOf(m.displayName, m.email)
                            .mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }
                            .firstOrNull() ?: m.userId
                        AddPasswordPickerMember(m.userId, label)
                    }
                    .toList()
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val pickerGroups: StateFlow<List<AddPasswordPickerGroup>> = familyIdFlow
        .filterNotNull()
        .flatMapLatest { fid ->
            passwordsRepository.observeVisibleGroups(fid).map { groups ->
                val uid = auth.currentUser?.uid.orEmpty()
                val visible = groups.filter { it.deletedAtEpochMillis == null }
                val unassignedId = PasswordGroupIds.id(fid, PasswordGroupIds.UNASSIGNED_SLUG)
                val sorted = visible.sortedWith { a, b ->
                    val aUn = a.id == unassignedId
                    val bUn = b.id == unassignedId
                    when {
                        aUn && !bUn -> 1
                        !aUn && bUn -> -1
                        else -> decryptGroupName(a, uid).lowercase()
                            .compareTo(decryptGroupName(b, uid).lowercase())
                    }
                }
                sorted
                    // "Non assegnato" è già la prima voce fissa del selettore, che
                    // corrisponde a `groupId = null`. Lasciando qui anche il gruppo
                    // omonimo — che esiste davvero, seminato o arrivato da iOS — la
                    // voce compariva DUE volte.
                    .filterNot { it.id == unassignedId }
                    .map { g ->
                        AddPasswordPickerGroup(
                            id = g.id,
                            label = decryptGroupName(g, uid).ifBlank { "Gruppo" },
                            icon = g.icon,
                            colorHex = g.color,
                        )
                    }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Crea un gruppo mentre si sta compilando la password e lo restituisce già
     * selezionato.
     *
     * Prima, se il gruppo giusto non esisteva, bisognava abbandonare il form,
     * andare nella gestione gruppi e ricominciare. Stessa forma di
     * `PasswordsGroupsViewModel.createGroup`, con id casuale perché è un gruppo
     * dell'utente e non uno dei predefiniti a id deterministico.
     *
     * @return l'id del gruppo creato, o `null` se il nome era vuoto o manca la
     *     sessione.
     */
    suspend fun createGroup(name: String): String? {
        val familyId = familyIdFlow.value ?: return null
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        val cleanName = name.trim()
        if (uid.isEmpty() || cleanName.isEmpty()) return null

        return runCatching {
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
            entity.id
        }.getOrNull()
    }

    fun bind(familyId: String, editingEntryId: String? = null) {
        familyIdFlow.value = familyId
        bindLoadJob?.cancel()
        if (editingEntryId.isNullOrBlank()) {
            _editDraft.value = null
            return
        }
        val eid = editingEntryId
        bindLoadJob = viewModelScope.launch {
            val entry = passwordEntryDao.getById(eid)
            if (entry == null) {
                _editDraft.value = null
                return@launch
            }
            val uid = auth.currentUser?.uid.orEmpty()
            if (uid.isEmpty()) {
                _editDraft.value = null
                return@launch
            }
            _editDraft.value = entry.toAddPasswordEditDraft(uid, passwordCypher)
        }
    }

    fun save(
        familyId: String,
        editingEntryId: String?,
        title: String,
        username: String,
        password: String,
        website: String,
        notes: String,
        visibilityScope: String,
        visibilityMemberIds: List<String>,
        selectedGroupId: String?,
        hasExpiry: Boolean,
        expiresAtEpochMillis: Long?,
        onDone: () -> Unit,
        onError: (String) -> Unit,
    ) {
        val t = title.trim()
        val p = password
        if (t.isEmpty()) {
            onError("Il titolo è obbligatorio")
            return
        }
        if (p.isEmpty()) {
            onError("La password è obbligatoria")
            return
        }
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        if (uid.isEmpty()) {
            onError("Utente non autenticato")
            return
        }
        viewModelScope.launch {
            try {
                var vis = KBVisibilityScope.normalizedPassword(visibilityScope)
                var memberIds = visibilityMemberIds.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
                if (vis == KBVisibilityScope.MEMBERS && memberIds.isEmpty()) {
                    vis = KBVisibilityScope.FAMILY
                    memberIds = emptyList()
                }

                val unassignedId = PasswordGroupIds.id(familyId, PasswordGroupIds.UNASSIGNED_SLUG)
                val resolvedGroupId = selectedGroupId?.trim()?.takeIf { it.isNotEmpty() } ?: unassignedId

                if (vis == KBVisibilityScope.FAMILY) {
                    val g = passwordGroupDao.getById(resolvedGroupId)
                    if (g != null) {
                        val gv = KBVisibilityScope.normalizedPassword(g.visibility)
                        if (gv != KBVisibilityScope.FAMILY) {
                            onError("Una password condivisa con la famiglia può appartenere solo a un gruppo famiglia.")
                            return@launch
                        }
                    }
                }

                val now = System.currentTimeMillis()
                val membersJson = encodeVisibilityMemberIds(
                    if (vis == KBVisibilityScope.MEMBERS) memberIds else emptyList(),
                )

                val existing = editingEntryId?.trim()?.takeIf { it.isNotEmpty() }
                    ?.let { passwordEntryDao.getById(it) }

                val creatorForCipher = existing?.createdBy ?: uid

                val titleCipher = passwordCypher.encrypt(t, familyId, vis, creatorForCipher)
                val usernameCipher = username.trim().takeIf { it.isNotEmpty() }?.let { u ->
                    passwordCypher.encrypt(u, familyId, vis, creatorForCipher)
                }
                val passwordCipher = passwordCypher.encrypt(p, familyId, vis, creatorForCipher)
                val websiteCipher = website.trim().takeIf { it.isNotEmpty() }?.let { w ->
                    passwordCypher.encrypt(w, familyId, vis, creatorForCipher)
                }
                val notesCipher = notes.trim().takeIf { it.isNotEmpty() }?.let { n ->
                    passwordCypher.encrypt(n, familyId, vis, creatorForCipher)
                }

                if (existing != null) {
                    if (existing.createdBy != uid) {
                        onError("Non puoi modificare questa password")
                        return@launch
                    }
                    if (existing.familyId != familyId) {
                        onError("Famiglia non valida")
                        return@launch
                    }
                    val oldPassPlain = runCatching {
                        passwordCypher.decrypt(
                            existing.passwordCipher,
                            existing.familyId,
                            existing.visibility,
                            existing.createdBy,
                            uid,
                        )
                    }.getOrNull().orEmpty()
                    val passUpdatedMillis =
                        if (p != oldPassPlain) now else existing.passwordUpdatedAtEpochMillis

                    val iconUrl = if (website.trim().isNotEmpty()) {
                        FaviconResolver.resolve(website.trim())
                    } else {
                        existing.iconURL
                    }

                    val merged = existing.copy(
                        visibility = vis,
                        visibilityMemberIdsJson = membersJson,
                        groupId = resolvedGroupId,
                        titleCipher = titleCipher,
                        usernameCipher = usernameCipher,
                        passwordCipher = passwordCipher,
                        websiteCipher = websiteCipher,
                        notesCipher = notesCipher,
                        iconURL = iconUrl,
                        passwordUpdatedAtEpochMillis = passUpdatedMillis,
                        expiresAtEpochMillis = if (hasExpiry) expiresAtEpochMillis else null,
                        updatedAtEpochMillis = now,
                        syncStateRaw = 1,
                        lastSyncError = null,
                    )
                    passwordEntryDao.upsert(merged)
                    passwordsRepository.pushUpsertEntry(merged)
                    passwordsRepository.scheduleAutofillSnapshotRebuild()
                    passwordExpiryReminderScheduler.sync(merged.id, familyId, t, merged.expiresAtEpochMillis)
                    onDone()
                    return@launch
                }

                val id = UUID.randomUUID().toString()
                val iconUrl = FaviconResolver.resolve(website)

                val entity = PasswordEntryEntity(
                    id = id,
                    familyId = familyId,
                    createdBy = uid,
                    visibility = vis,
                    visibilityMemberIdsJson = membersJson,
                    groupId = resolvedGroupId,
                    titleCipher = titleCipher,
                    usernameCipher = usernameCipher,
                    passwordCipher = passwordCipher,
                    websiteCipher = websiteCipher,
                    notesCipher = notesCipher,
                    otpConfigCipher = null,
                    iconURL = iconUrl,
                    lastUsedAtEpochMillis = null,
                    passwordUpdatedAtEpochMillis = now,
                    expiresAtEpochMillis = if (hasExpiry) expiresAtEpochMillis else null,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    deletedAtEpochMillis = null,
                    isFavorite = false,
                    syncStateRaw = 1,
                    lastSyncError = null,
                )
                passwordEntryDao.upsert(entity)
                passwordsRepository.pushUpsertEntry(entity)
                passwordsRepository.scheduleAutofillSnapshotRebuild()
                passwordExpiryReminderScheduler.sync(entity.id, familyId, t, entity.expiresAtEpochMillis)
                onDone()
            } catch (e: Exception) {
                onError(e.message ?: "Errore durante il salvataggio")
            }
        }
    }

    private fun decryptGroupName(g: PasswordGroupEntity, uid: String): String {
        val stored = runCatching {
            passwordCypher.decrypt(g.nameCipher, g.familyId, g.visibility, g.createdBy, uid)
        }.getOrElse { "" }
        return PasswordDefaultGroups.displayName(appContext, g.id, g.familyId, stored)
    }

    private fun encodeVisibilityMemberIds(ids: List<String>): String {
        val cleaned = ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return JSONArray(cleaned).toString()
    }
}

private fun decodeVisibilityMemberIds(raw: String): Set<String> =
    runCatching {
        val arr = JSONArray(raw)
        buildSet {
            for (i in 0 until arr.length()) {
                val v = arr.optString(i).trim()
                if (v.isNotEmpty()) add(v)
            }
        }
    }.getOrElse { emptySet() }

private fun PasswordEntryEntity.toAddPasswordEditDraft(
    currentUid: String,
    cypher: PasswordCypher,
): AddPasswordEditDraft {
    val creator = createdBy
    val vis = KBVisibilityScope.normalizedPassword(visibility)
    val title = runCatching {
        cypher.decrypt(titleCipher, familyId, visibility, creator, currentUid)
    }.getOrDefault("")
    val username = usernameCipher?.let { data ->
        runCatching { cypher.decrypt(data, familyId, visibility, creator, currentUid) }.getOrNull()
    }.orEmpty()
    val password = runCatching {
        cypher.decrypt(passwordCipher, familyId, visibility, creator, currentUid)
    }.getOrDefault("")
    val website = websiteCipher?.let { data ->
        runCatching { cypher.decrypt(data, familyId, visibility, creator, currentUid) }.getOrNull()
    }.orEmpty()
    val notes = notesCipher?.let { data ->
        runCatching { cypher.decrypt(data, familyId, visibility, creator, currentUid) }.getOrNull()
    }.orEmpty()
    val unassignedId = PasswordGroupIds.id(familyId, PasswordGroupIds.UNASSIGNED_SLUG)
    val groupForForm = when {
        groupId.isNullOrBlank() -> null
        groupId == unassignedId -> null
        else -> groupId
    }
    val exp = expiresAtEpochMillis
    return AddPasswordEditDraft(
        entryId = id,
        title = title,
        username = username,
        password = password,
        website = website,
        notes = notes,
        visibilityScope = vis,
        visibilityMemberIds = decodeVisibilityMemberIds(visibilityMemberIdsJson),
        selectedGroupId = groupForForm,
        hasExpiry = exp != null,
        expiryMillis = exp ?: System.currentTimeMillis(),
    )
}
