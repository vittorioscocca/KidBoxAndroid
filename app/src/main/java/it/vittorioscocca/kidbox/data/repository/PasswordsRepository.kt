package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.PasswordEntryDao
import it.vittorioscocca.kidbox.data.local.dao.PasswordGroupDao
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.data.local.entity.PasswordGroupEntity
import it.vittorioscocca.kidbox.data.remote.passwords.PasswordEntryRemoteDto
import it.vittorioscocca.kidbox.data.remote.passwords.PasswordGroupRemoteDto
import it.vittorioscocca.kidbox.data.remote.passwords.PasswordRemoteChange
import it.vittorioscocca.kidbox.data.passwords.AutoFillSnapshotScheduler
import it.vittorioscocca.kidbox.data.remote.passwords.PasswordRemoteStore
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyEscrow
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.notifications.PasswordExpiryReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

private const val TAG = "PasswordsRepository"

@Singleton
class PasswordsRepository @Inject constructor(
    @ApplicationContext private val appContext: Context,
    private val entryDao: PasswordEntryDao,
    private val groupDao: PasswordGroupDao,
    private val remoteStore: PasswordRemoteStore,
    private val auth: FirebaseAuth,
    private val autoFillSnapshotScheduler: AutoFillSnapshotScheduler,
    private val passwordCypher: PasswordCypher,
    private val passwordExpiryReminderScheduler: PasswordExpiryReminderScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inboundMutex = Mutex()
    private val realtimeMutex = Mutex()
    private var entryListener: ListenerRegistration? = null
    private var groupListener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observeVisibleEntries(familyId: String) =
        entryDao.observeVisibleByFamily(familyId, auth.currentUser?.uid.orEmpty())

    fun observeVisibleGroups(familyId: String) =
        groupDao.observeVisibleByFamily(familyId, auth.currentUser?.uid.orEmpty())

    private suspend fun ensureFamilyKeyBeforeListen(familyId: String) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (familyId.isBlank() || uid.isEmpty()) return
        val ok = FamilyKeyEscrow.ensureFamilyKeyAvailable(appContext, familyId, uid)
        if (!ok) {
            KBLog.data.warning("family key still missing after escrow familyId=$familyId", TAG)
        }
    }

    private fun attachListenersLocked(familyId: String, onPermissionDenied: (() -> Unit)?) {
        val onErr: (Exception) -> Unit = { err ->
            if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                onPermissionDenied?.invoke()
            } else {
                KBLog.data.warning("passwords listen: ${err.message}", TAG)
            }
        }
        entryListener = remoteStore.listenPasswordEntries(
            familyId = familyId,
            onChange = { ch -> scope.launch { applyInbound(familyId, ch) } },
            onError = onErr,
        )
        groupListener = remoteStore.listenPasswordGroups(
            familyId = familyId,
            onChange = { ch -> scope.launch { applyInbound(familyId, ch) } },
            onError = onErr,
        )
    }

    fun startRealtime(familyId: String, onPermissionDenied: (() -> Unit)? = null) {
        scope.launch {
            ensureFamilyKeyBeforeListen(familyId)
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && entryListener != null) return@withLock
                stopRealtimeLocked()
                listeningFamilyId = familyId
                attachListenersLocked(familyId, onPermissionDenied)
            }
        }
    }

    /**
     * Scarica password + gruppi da Firestore e applica a Room (una tantum).
     * Utile dopo join e al primo bind della schermata, così la lista non dipende solo dal listener.
     */
    suspend fun hydratePasswordRoomFromServer(familyId: String) = withContext(Dispatchers.IO) {
        ensureFamilyKeyBeforeListen(familyId)
        val batch = remoteStore.fetchAllPasswordRemoteChanges(familyId)
        if (batch.isEmpty()) {
            autoFillSnapshotScheduler.enqueue()
            return@withContext
        }
        applyInbound(familyId, batch)
    }

    /**
     * Riattacca i listener in modo sincrono (da coroutine), evitando race con la navigazione post-join.
     */
    suspend fun awaitForceRestartRealtime(familyId: String, onPermissionDenied: (() -> Unit)? = null) {
        ensureFamilyKeyBeforeListen(familyId)
        realtimeMutex.withLock {
            stopRealtimeLocked()
            listeningFamilyId = familyId
            attachListenersLocked(familyId, onPermissionDenied)
        }
    }

    /**
     * Dopo join famiglia: la chiave può comparire in [FamilyKeyStore] dopo i listener Home;
     * forza detach/reattach Firestore così [applyInbound] riesegue e la UI ridestra i decrypt.
     */
    fun forceRestartRealtime(familyId: String, onPermissionDenied: (() -> Unit)? = null) {
        scope.launch {
            try {
                awaitForceRestartRealtime(familyId, onPermissionDenied)
            } catch (e: Exception) {
                KBLog.data.error("forceRestartRealtime: ${e.message}", TAG, e)
            }
        }
    }

    fun stopRealtime() {
        scope.launch { realtimeMutex.withLock { stopRealtimeLocked() } }
    }

    private fun stopRealtimeLocked() {
        entryListener?.remove()
        entryListener = null
        groupListener?.remove()
        groupListener = null
        listeningFamilyId = null
    }

    private fun decodeB64(s: String?): ByteArray? {
        if (s.isNullOrBlank()) return null
        return Base64.decode(s, Base64.NO_WRAP)
    }

    private fun encodeStringList(values: List<String>): String {
        val cleaned = values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        return JSONArray(cleaned).toString()
    }

    private fun decodeStringList(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val value = arr.optString(i).trim()
                    if (value.isNotEmpty()) add(value)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun applyInbound(familyId: String, changes: List<PasswordRemoteChange>) {
        inboundMutex.withLock {
            val uid = auth.currentUser?.uid.orEmpty()
            for (change in changes) {
                when (change) {
                    is PasswordRemoteChange.RemoveEntry -> entryDao.deleteById(change.id)
                    is PasswordRemoteChange.RemoveGroup -> groupDao.deleteById(change.id)
                    is PasswordRemoteChange.UpsertEntry -> applyEntryDto(change.dto, familyId, uid)
                    is PasswordRemoteChange.UpsertGroup -> applyGroupDto(change.dto, familyId, uid)
                }
            }
        }
        autoFillSnapshotScheduler.enqueue()
    }

    fun scheduleAutofillSnapshotRebuild() {
        autoFillSnapshotScheduler.enqueue()
    }

    private suspend fun applyEntryDto(dto: PasswordEntryRemoteDto, familyId: String, uid: String) {
        val vis = KBVisibilityScope.normalizedPassword(dto.visibility)
        if (!KBVisibilityScope.isVisible(vis, dto.visibilityMemberIds, dto.createdBy, uid)) return

        if (dto.deletedAtEpochMillis != null) {
            val existing = entryDao.getById(dto.id) ?: return
            val remoteTs = dto.updatedAtEpochMillis ?: 0L
            if (remoteTs >= existing.updatedAtEpochMillis) {
                entryDao.deleteById(dto.id)
                passwordExpiryReminderScheduler.cancel(dto.id)
            }
            return
        }

        val title = decodeB64(dto.titleCipherB64) ?: return
        val pass = decodeB64(dto.passwordCipherB64) ?: return
        val remoteTs = dto.updatedAtEpochMillis ?: dto.createdAtEpochMillis ?: System.currentTimeMillis()
        val existing = entryDao.getById(dto.id)
        if (existing != null) {
            if (remoteTs < existing.updatedAtEpochMillis) return
            entryDao.upsert(
                existing.copy(
                    familyId = familyId,
                    createdBy = dto.createdBy,
                    visibility = vis,
                    visibilityMemberIdsJson = encodeStringList(dto.visibilityMemberIds),
                    groupId = dto.groupId,
                    titleCipher = title,
                    usernameCipher = decodeB64(dto.usernameCipherB64),
                    passwordCipher = pass,
                    websiteCipher = decodeB64(dto.websiteCipherB64),
                    notesCipher = decodeB64(dto.notesCipherB64),
                    otpConfigCipher = decodeB64(dto.otpConfigCipherB64),
                    iconURL = dto.iconURL,
                    lastUsedAtEpochMillis = dto.lastUsedAtEpochMillis,
                    passwordUpdatedAtEpochMillis = dto.passwordUpdatedAtEpochMillis ?: remoteTs,
                    expiresAtEpochMillis = dto.expiresAtEpochMillis,
                    createdAtEpochMillis = dto.createdAtEpochMillis ?: existing.createdAtEpochMillis,
                    updatedAtEpochMillis = remoteTs,
                    deletedAtEpochMillis = null,
                    isFavorite = dto.isFavorite,
                    pwnedCount = dto.pwnedCount,
                    pwnedCheckedAt = dto.pwnedCheckedAt,
                    syncStateRaw = 0,
                    lastSyncError = null,
                ),
            )
            resyncPasswordExpiryReminder(dto.id, familyId, vis, dto.createdBy, uid, title, dto.expiresAtEpochMillis)
        } else {
            val created = dto.createdAtEpochMillis ?: remoteTs
            entryDao.upsert(
                PasswordEntryEntity(
                    id = dto.id,
                    familyId = familyId,
                    createdBy = dto.createdBy,
                    visibility = vis,
                    visibilityMemberIdsJson = encodeStringList(dto.visibilityMemberIds),
                    groupId = dto.groupId,
                    titleCipher = title,
                    usernameCipher = decodeB64(dto.usernameCipherB64),
                    passwordCipher = pass,
                    websiteCipher = decodeB64(dto.websiteCipherB64),
                    notesCipher = decodeB64(dto.notesCipherB64),
                    otpConfigCipher = decodeB64(dto.otpConfigCipherB64),
                    iconURL = dto.iconURL,
                    lastUsedAtEpochMillis = dto.lastUsedAtEpochMillis,
                    passwordUpdatedAtEpochMillis = dto.passwordUpdatedAtEpochMillis ?: remoteTs,
                    expiresAtEpochMillis = dto.expiresAtEpochMillis,
                    createdAtEpochMillis = created,
                    updatedAtEpochMillis = remoteTs,
                    deletedAtEpochMillis = null,
                    isFavorite = dto.isFavorite,
                    pwnedCount = dto.pwnedCount,
                    pwnedCheckedAt = dto.pwnedCheckedAt,
                    syncStateRaw = 0,
                    lastSyncError = null,
                ),
            )
            resyncPasswordExpiryReminder(dto.id, familyId, vis, dto.createdBy, uid, title, dto.expiresAtEpochMillis)
        }
    }

    /** Ridecifra il titolo (serve in chiaro per il corpo della notifica) e riallinea i promemoria scadenza. */
    private fun resyncPasswordExpiryReminder(
        entryId: String,
        familyId: String,
        visibility: String,
        createdBy: String,
        decryptingUid: String,
        titleCipher: ByteArray,
        expiresAtEpochMillis: Long?,
    ) {
        val title = runCatching {
            passwordCypher.decrypt(titleCipher, familyId, visibility, createdBy, familyKeyUserId = decryptingUid)
        }.getOrNull().orEmpty()
        passwordExpiryReminderScheduler.sync(entryId, familyId, title, expiresAtEpochMillis)
    }

    private suspend fun applyGroupDto(dto: PasswordGroupRemoteDto, familyId: String, uid: String) {
        val vis = KBVisibilityScope.normalizedPassword(dto.visibility)
        if (!KBVisibilityScope.isVisible(vis, emptyList(), dto.createdBy, uid)) return

        if (dto.deletedAtEpochMillis != null) {
            val existing = groupDao.getById(dto.id) ?: return
            val remoteTs = dto.updatedAtEpochMillis ?: 0L
            if (remoteTs >= existing.updatedAtEpochMillis) groupDao.deleteById(dto.id)
            return
        }

        val name = decodeB64(dto.nameCipherB64) ?: return
        val remoteTs = dto.updatedAtEpochMillis ?: dto.createdAtEpochMillis ?: System.currentTimeMillis()
        val existing = groupDao.getById(dto.id)
        if (existing != null) {
            if (remoteTs < existing.updatedAtEpochMillis) return
            groupDao.upsert(
                existing.copy(
                    familyId = familyId,
                    createdBy = dto.createdBy,
                    visibility = vis,
                    nameCipher = name,
                    icon = dto.icon ?: existing.icon,
                    color = dto.color ?: existing.color,
                    createdAtEpochMillis = dto.createdAtEpochMillis ?: existing.createdAtEpochMillis,
                    updatedAtEpochMillis = remoteTs,
                    deletedAtEpochMillis = null,
                    syncStateRaw = 0,
                    lastSyncError = null,
                ),
            )
        } else {
            val created = dto.createdAtEpochMillis ?: remoteTs
            groupDao.upsert(
                PasswordGroupEntity(
                    id = dto.id,
                    familyId = familyId,
                    nameCipher = name,
                    icon = dto.icon ?: "folder",
                    color = dto.color ?: "#7C6FDE",
                    visibility = vis,
                    createdBy = dto.createdBy,
                    createdAtEpochMillis = created,
                    updatedAtEpochMillis = remoteTs,
                    deletedAtEpochMillis = null,
                    syncStateRaw = 0,
                    lastSyncError = null,
                ),
            )
        }
    }

    suspend fun pushUpsertEntry(entity: PasswordEntryEntity) = withContext(Dispatchers.IO) {
        remoteStore.upsertEntry(entity)
    }

    suspend fun pushUpsertGroup(entity: PasswordGroupEntity) = withContext(Dispatchers.IO) {
        remoteStore.upsertGroup(entity)
    }

    suspend fun pushSoftDeleteEntry(familyId: String, entryId: String) = withContext(Dispatchers.IO) {
        remoteStore.softDeleteEntry(familyId, entryId)
    }

    suspend fun pushSoftDeleteGroup(familyId: String, groupId: String) = withContext(Dispatchers.IO) {
        remoteStore.softDeleteGroup(familyId, groupId)
    }

    /** Elimina in locale e invia tombstone su Firestore (ottimistico). */
    suspend fun softDeleteEntriesOptimistic(familyId: String, entryIds: Collection<String>) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid.orEmpty()
        entryIds.forEach { id ->
            val entry = entryDao.getById(id) ?: return@forEach
            if (entry.createdBy != uid) return@forEach
            remoteStore.softDeleteEntry(familyId, id)
            entryDao.deleteById(id)
            passwordExpiryReminderScheduler.cancel(id)
        }
        autoFillSnapshotScheduler.enqueue()
    }

    suspend fun listVisibleEntriesNow(familyId: String): List<PasswordEntryEntity> = withContext(Dispatchers.IO) {
        entryDao.listVisibleForAutofill(familyId, auth.currentUser?.uid.orEmpty())
    }

    suspend fun updatePwnedVerdict(
        entryId: String,
        pwnedCount: Int?,
        checkedAtEpochMillis: Long,
    ) = withContext(Dispatchers.IO) {
        val existing = entryDao.getById(entryId) ?: return@withContext
        // `updatedAtEpochMillis` NON si tocca: significa "quando l'utente ha
        // modificato questa password", e l'esito di uno scan di sicurezza non è
        // una modifica sua. La data del controllo ha già il suo campo,
        // `pwnedCheckedAt`, aggiornato qui sopra.
        //
        // Non è cosmesi: la lista è ordinata per data di modifica decrescente
        // (PasswordsHomeViewModel), quindi ogni verdetto faceva saltare quella
        // voce in cima al gruppo e la lista si riordinava sotto gli occhi
        // dell'utente per tutta la durata dello scan. Stessa correzione fatta su
        // iOS in PasswordsSecurityScanner.
        val updated = existing.copy(
            pwnedCount = pwnedCount,
            pwnedCheckedAt = checkedAtEpochMillis,
            syncStateRaw = 1,
        )
        entryDao.upsert(updated)
        remoteStore.upsertEntry(updated)
    }
}
