package it.vittorioscocca.kidbox.data.repository

import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.WorkDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBWorkProfileEntity
import it.vittorioscocca.kidbox.data.local.entity.KBWorkRecordEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.remote.DocumentCryptoManager
import it.vittorioscocca.kidbox.data.remote.work.WorkProfileRemoteChange
import it.vittorioscocca.kidbox.data.remote.work.WorkRecordRemoteChange
import it.vittorioscocca.kidbox.data.remote.work.WorkRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.work.WorkFields
import it.vittorioscocca.kidbox.util.KBLog
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val TAG = "WorkRepository"

/**
 * Scheda Lavoro: Room + listener Firestore + LWW. Mirror di `SyncCenter+Work`
 * (iOS). Tre ascolti, tutti vincolati (le rules non filtrano una LIST): la
 * propria scheda, le schede che altri adulti hanno condiviso con me
 * (`array-contains` sul mio uid) e i documenti di ciascun proprietario
 * visibile (`ownerUid ==`).
 *
 * Un errore di permessi dei listener **non** passa da `FamilyAccessGuard`:
 * quella strada espelle dalla famiglia, e un listener di Lavoro che fallisce è
 * un problema della scheda, non della famiglia.
 */
@Singleton
class WorkRepository @Inject constructor(
    private val dao: WorkDao,
    private val familyDao: KBFamilyDao,
    private val remoteStore: WorkRemoteStore,
    private val documentCrypto: DocumentCryptoManager,
    private val auth: FirebaseAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inboundMutex = Mutex()
    private val realtimeMutex = Mutex()
    private var ownProfileListener: ListenerRegistration? = null
    private var sharedProfilesListener: ListenerRegistration? = null
    private val recordListeners = mutableMapOf<String, ListenerRegistration>()
    private var listeningFamilyId: String? = null

    fun observeProfiles(familyId: String): Flow<List<KBWorkProfileEntity>> = dao.observeProfiles(familyId)
    fun observeRecords(familyId: String): Flow<List<KBWorkRecordEntity>> = dao.observeRecords(familyId)

    // ── Realtime ────────────────────────────────────────────────────────

    fun startRealtime(familyId: String) {
        val uid = auth.currentUser?.uid ?: return
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && ownProfileListener != null) return@withLock
                stopLocked()
                listeningFamilyId = familyId
                ownProfileListener = remoteStore.listenOwnProfile(
                    familyId, uid,
                    onChange = { scope.launch { applyProfiles(it, uid) } },
                    onError = { KBLog.data.warning("work own profile listen error: ${it.message}", TAG) },
                )
                sharedProfilesListener = remoteStore.listenSharedProfiles(
                    familyId, uid,
                    onChange = { scope.launch { applyProfiles(it, uid) } },
                    onError = { KBLog.data.warning("work shared profiles listen error: ${it.message}", TAG) },
                )
                attachRecordsLocked(familyId, uid)
            }
        }
    }

    fun stopRealtime() {
        scope.launch { realtimeMutex.withLock { stopLocked() } }
    }

    private fun attachRecordsLocked(familyId: String, ownerUid: String) {
        if (recordListeners.containsKey(ownerUid)) return
        recordListeners[ownerUid] = remoteStore.listenRecords(
            familyId, ownerUid,
            onChange = { scope.launch { applyRecords(it) } },
            onError = { KBLog.data.warning("work records listen error owner=$ownerUid: ${it.message}", TAG) },
        )
    }

    private fun stopLocked() {
        ownProfileListener?.remove(); ownProfileListener = null
        sharedProfilesListener?.remove(); sharedProfilesListener = null
        recordListeners.values.forEach { it.remove() }
        recordListeners.clear()
        listeningFamilyId = null
    }

    // ── Cifratura ───────────────────────────────────────────────────────

    /** I valori di un documento; `null` = chiave di famiglia assente o blob che non si apre. */
    fun open(record: KBWorkRecordEntity): WorkFields? = runCatching {
        val b64 = record.fieldsEnc?.takeIf { it.isNotBlank() } ?: return null
        val json = String(documentCrypto.decrypt(Base64.decode(b64, Base64.DEFAULT), record.familyId), Charsets.UTF_8)
        WorkFields.fromJson(json)
    }.getOrElse {
        KBLog.data.warning("work decrypt failed id=${record.id}", TAG)
        null
    }

    fun employer(profile: KBWorkProfileEntity): String = runCatching {
        val b64 = profile.employerEnc?.takeIf { it.isNotBlank() } ?: return ""
        String(documentCrypto.decrypt(Base64.decode(b64, Base64.DEFAULT), profile.familyId), Charsets.UTF_8)
    }.getOrDefault("")

    private fun seal(plain: String, familyId: String): String? {
        if (plain.isEmpty()) return null
        return Base64.encodeToString(documentCrypto.encrypt(plain.toByteArray(Charsets.UTF_8), familyId), Base64.NO_WRAP)
    }

    // ── Scritture ───────────────────────────────────────────────────────

    private fun displayName(): String =
        auth.currentUser?.displayName?.trim().orEmpty().ifBlank { "Tu" }

    /** La mia scheda: la crea (privata) se non c'è. */
    suspend fun ensureProfile(familyId: String, name: String = displayName()): KBWorkProfileEntity =
        withContext(Dispatchers.IO) {
            val uid = auth.currentUser?.uid ?: error("Non autenticato")
            ensureFamilyExists(familyId)
            dao.getProfile(familyId, uid)?.let { return@withContext it }
            val now = System.currentTimeMillis()
            val p = KBWorkProfileEntity(
                familyId = familyId, ownerUid = uid,
                createdBy = uid, createdByName = name, updatedBy = uid, updatedByName = name,
                createdAtEpochMillis = now, updatedAtEpochMillis = now, syncStateRaw = 1,
            )
            dao.upsertProfile(p)
            runCatching { remoteStore.upsert(p, emptyList()) }
                .onSuccess { dao.upsertProfile(p.copy(syncStateRaw = 0)) }
            p
        }

    suspend fun setEmployerIfMissing(familyId: String, employer: String, name: String) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: return@withContext
        val p = dao.getProfile(familyId, uid) ?: return@withContext
        if (p.employerEnc != null || employer.isBlank()) return@withContext
        pushProfile(p.copy(employerEnc = seal(employer, familyId)), name)
    }

    /** Condivisione: `memberIds` vuoto = solo io. */
    suspend fun setSharing(familyId: String, memberIds: List<String>, name: String = displayName()) =
        withContext(Dispatchers.IO) {
            val uid = auth.currentUser?.uid ?: error("Non autenticato")
            val p = ensureProfile(familyId, name)
            val ids = memberIds.filter { it != uid }.distinct().sorted()
            val sharing = ids.isNotEmpty()
            pushProfile(
                p.copy(
                    visibilityScope = if (sharing) KBVisibilityScope.MEMBERS else KBVisibilityScope.ONLY_CREATOR,
                    visibilityMemberIdsJson = encodeStringList(if (sharing) ids else emptyList()),
                ),
                name,
            )
        }

    private suspend fun pushProfile(profile: KBWorkProfileEntity, name: String) {
        val updated = profile.copy(
            updatedBy = auth.currentUser?.uid.orEmpty(), updatedByName = name,
            updatedAtEpochMillis = System.currentTimeMillis(), syncStateRaw = 1,
        )
        dao.upsertProfile(updated)
        remoteStore.upsert(updated, decodeStringList(updated.visibilityMemberIdsJson))
        dao.upsertProfile(updated.copy(syncStateRaw = 0))
    }

    /** Crea (`recordId == null`) o aggiorna un documento. Cifra prima di toccare Room. */
    suspend fun saveRecord(
        familyId: String,
        recordId: String?,
        kind: String,
        referenceDateEpochMillis: Long,
        fields: WorkFields,
        attachmentDocumentId: String?,
        name: String = displayName(),
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = auth.currentUser?.uid ?: error("Non autenticato")
            ensureFamilyExists(familyId)
            val existing = recordId?.let { dao.getRecord(it) }
            if (existing != null && existing.ownerUid != uid) error("Non è un tuo documento")
            val sealed = seal(fields.toJson(), familyId)
            val now = System.currentTimeMillis()
            val base = existing ?: KBWorkRecordEntity(
                id = UUID.randomUUID().toString(), familyId = familyId, ownerUid = uid, kind = kind,
                referenceDateEpochMillis = referenceDateEpochMillis,
                createdBy = uid, createdByName = name, updatedBy = uid, updatedByName = name,
                createdAtEpochMillis = now, updatedAtEpochMillis = now,
            )
            val updated = base.copy(
                referenceDateEpochMillis = referenceDateEpochMillis,
                fieldsEnc = sealed,
                attachmentDocumentId = attachmentDocumentId ?: base.attachmentDocumentId,
                updatedBy = uid, updatedByName = name, updatedAtEpochMillis = now, syncStateRaw = 1,
            )
            dao.upsertRecord(updated)
            remoteStore.upsert(updated)
            dao.upsertRecord(updated.copy(syncStateRaw = 0))
            updated.id
        }
    }

    suspend fun deleteRecord(record: KBWorkRecordEntity) = withContext(Dispatchers.IO) {
        dao.deleteRecord(record.id)
        runCatching { remoteStore.softDelete(record.id, record.familyId) }
            .onFailure { KBLog.data.warning("work remote softDelete failed: ${it.message}", TAG) }
    }

    // ── Inbound (LWW) ───────────────────────────────────────────────────

    private suspend fun applyProfiles(changes: List<WorkProfileRemoteChange>, me: String) {
        inboundMutex.withLock {
            changes.forEach { change ->
                when (change) {
                    is WorkProfileRemoteChange.Remove -> {
                        // Il proprietario ha smesso di condividere: via scheda e documenti in cache.
                        if (change.ownerUid != me) purgeOwner(change.ownerUid)
                    }
                    is WorkProfileRemoteChange.Upsert -> {
                        val dto = change.dto
                        if (dto.isDeleted) { dao.deleteProfile(dto.familyId, dto.ownerUid); return@forEach }
                        ensureFamilyExists(dto.familyId)
                        val local = dao.getProfile(dto.familyId, dto.ownerUid)
                        if (local?.syncStateRaw == 1) return@forEach
                        val remoteUpdated = dto.updatedAtEpochMillis ?: 0L
                        if (local != null && remoteUpdated < local.updatedAtEpochMillis) return@forEach
                        val now = System.currentTimeMillis()
                        dao.upsertProfile(
                            KBWorkProfileEntity(
                                familyId = dto.familyId, ownerUid = dto.ownerUid,
                                employerEnc = dto.employerEnc,
                                visibilityScope = dto.visibilityScope,
                                visibilityMemberIdsJson = encodeStringList(dto.visibilityMemberIds),
                                createdBy = dto.createdBy ?: local?.createdBy ?: dto.ownerUid,
                                createdByName = dto.createdByName ?: local?.createdByName.orEmpty(),
                                updatedBy = dto.updatedBy ?: local?.updatedBy.orEmpty(),
                                updatedByName = dto.updatedByName ?: local?.updatedByName.orEmpty(),
                                createdAtEpochMillis = dto.createdAtEpochMillis ?: local?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                            ),
                        )
                        // Una scheda condivisa con me porta con sé i suoi documenti.
                        if (dto.ownerUid != me) {
                            realtimeMutex.withLock { attachRecordsLocked(dto.familyId, dto.ownerUid) }
                        }
                    }
                }
            }
        }
    }

    private suspend fun purgeOwner(ownerUid: String) {
        val familyId = listeningFamilyId ?: return
        realtimeMutex.withLock { recordListeners.remove(ownerUid)?.remove() }
        dao.deleteRecordsByOwner(familyId, ownerUid)
        dao.deleteProfile(familyId, ownerUid)
    }

    private suspend fun applyRecords(changes: List<WorkRecordRemoteChange>) {
        inboundMutex.withLock {
            changes.forEach { change ->
                when (change) {
                    is WorkRecordRemoteChange.Remove -> dao.deleteRecord(change.id)
                    is WorkRecordRemoteChange.Upsert -> {
                        val dto = change.dto
                        if (dto.isDeleted) { dao.deleteRecord(dto.id); return@forEach }
                        ensureFamilyExists(dto.familyId)
                        val local = dao.getRecord(dto.id)
                        if (local?.syncStateRaw == 1) return@forEach
                        val remoteUpdated = dto.updatedAtEpochMillis ?: 0L
                        if (local != null && remoteUpdated < local.updatedAtEpochMillis) return@forEach
                        val now = System.currentTimeMillis()
                        dao.upsertRecord(
                            KBWorkRecordEntity(
                                id = dto.id, familyId = dto.familyId, ownerUid = dto.ownerUid, kind = dto.kind,
                                referenceDateEpochMillis = dto.referenceDateEpochMillis ?: dto.createdAtEpochMillis ?: now,
                                fieldsEnc = dto.fieldsEnc, attachmentDocumentId = dto.attachmentDocumentId,
                                createdBy = dto.createdBy ?: local?.createdBy ?: dto.ownerUid,
                                createdByName = dto.createdByName ?: local?.createdByName.orEmpty(),
                                updatedBy = dto.updatedBy ?: local?.updatedBy.orEmpty(),
                                updatedByName = dto.updatedByName ?: local?.updatedByName.orEmpty(),
                                createdAtEpochMillis = dto.createdAtEpochMillis ?: local?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                            ),
                        )
                    }
                }
            }
        }
    }

    private suspend fun ensureFamilyExists(familyId: String) {
        if (familyId.isBlank() || familyDao.getById(familyId) != null) return
        val uid = auth.currentUser?.uid ?: "local"
        // Riga segnaposto con updatedAt = 0: il dato remoto vince il LWW (vedi porting-android).
        familyDao.upsert(
            KBFamilyEntity(
                id = familyId, name = "Famiglia", heroPhotoURL = null, heroPhotoLocalPath = null,
                heroPhotoUpdatedAtEpochMillis = null, heroPhotoScale = null, heroPhotoOffsetX = null,
                heroPhotoOffsetY = null, createdBy = uid, updatedBy = uid,
                createdAtEpochMillis = 0, updatedAtEpochMillis = 0,
                lastSyncAtEpochMillis = null, lastSyncError = null,
            ),
        )
    }
}
