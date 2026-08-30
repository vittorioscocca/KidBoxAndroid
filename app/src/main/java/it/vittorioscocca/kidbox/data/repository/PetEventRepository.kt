package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.PetEventDao
import it.vittorioscocca.kidbox.data.local.entity.PetEventEntity
import it.vittorioscocca.kidbox.data.remote.life.PetEventRemoteChange
import it.vittorioscocca.kidbox.data.remote.life.PetEventRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Singleton
class PetEventRepository @Inject constructor(
    private val petEventDao: PetEventDao,
    private val remoteStore: PetEventRemoteStore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var boundFamilyId: String? = null
    private var subscriberCount: Int = 0

    fun observeByPet(familyId: String, petId: String): Flow<List<PetEventEntity>> =
        petEventDao.observeByPet(familyId, petId)

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (familyId.isBlank()) return@withLock
                if (boundFamilyId != null && boundFamilyId != familyId) {
                    listener?.remove()
                    listener = null
                    subscriberCount = 0
                }
                boundFamilyId = familyId
                subscriberCount++
                if (listener != null) return@withLock
                attachListenerLocked(familyId, onPermissionDenied)
            }
        }
    }

    /** Aggancio del listener: chiamare solo con [realtimeMutex] già preso. */
    private fun attachListenerLocked(
        familyId: String,
        onPermissionDenied: (() -> Unit)?,
    ) {
        listener = remoteStore.listenPetEvents(
            familyId = familyId,
            onChange = { changes -> scope.launch { applyInbound(changes) } },
            onError = { err ->
                if (
                    err is FirebaseFirestoreException &&
                    err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED
                ) {
                    onPermissionDenied?.invoke()
                }
            },
        )
    }

    /**
     * Pull-to-refresh: stacca e riaggancia il listener realtime.
     *
     * [startRealtime] da solo non basta — con il listener già attivo esce dal
     * guard senza fare nulla. Qui il listener viene rimosso e riagganciato
     * dentro lo stesso lock, ma `subscriberCount` NON si tocca: le schermate
     * agganciate non sono cambiate, e alterarlo farebbe cadere il listener al
     * primo [stopRealtime]. Stesso idioma di
     * [PasswordsRepository.awaitForceRestartRealtime].
     */
    suspend fun awaitForceRestartRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        if (familyId.isBlank()) return
        realtimeMutex.withLock {
            listener?.remove()
            listener = null
            boundFamilyId = familyId
            attachListenerLocked(familyId, onPermissionDenied)
        }
    }

    fun stopRealtime() {
        scope.launch {
            realtimeMutex.withLock {
                subscriberCount = (subscriberCount - 1).coerceAtLeast(0)
                if (subscriberCount == 0) {
                    listener?.remove()
                    listener = null
                    boundFamilyId = null
                }
            }
        }
    }

    suspend fun addPetEvent(
        familyId: String,
        petId: String,
        title: String,
        eventType: String,
        dateMillis: Long,
        nextDueDate: Long?,
        vetName: String?,
        cost: Double?,
        notes: String?,
        reminderEnabled: Boolean,
        // L'id arriva da fuori quando l'evento ha già degli allegati addosso:
        // sono stati caricati durante la compilazione, con questo id nel tag.
        presetEventId: String? = null,
    ) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val id = presetEventId?.trim()?.takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()
        val entity = PetEventEntity(
            id = id,
            familyId = familyId,
            petId = petId,
            title = title,
            eventType = eventType,
            date = dateMillis,
            nextDueDate = nextDueDate,
            notes = notes,
            vetName = vetName,
            cost = cost,
            reminderEnabled = reminderEnabled,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
            createdBy = uid,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        petEventDao.upsert(entity)
        runCatching { remoteStore.upsertPetEventToFirestore(entity) }
            .onSuccess { petEventDao.upsert(entity.copy(syncState = KBSyncState.SYNCED.rawValue)) }
            .onFailure {
                petEventDao.upsert(entity.copy(syncState = KBSyncState.ERROR.rawValue))
                throw it
            }
        AppAnalytics.contentCreated(appContext, "pets")
    }

    suspend fun updatePetEvent(entity: PetEventEntity) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val local = entity.copy(
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        petEventDao.upsert(local)
        runCatching { remoteStore.upsertPetEventToFirestore(local) }
            .onSuccess { petEventDao.upsert(local.copy(syncState = KBSyncState.SYNCED.rawValue)) }
            .onFailure {
                petEventDao.upsert(local.copy(syncState = KBSyncState.ERROR.rawValue))
                throw it
            }
    }

    suspend fun deletePetEvent(entity: PetEventEntity) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val tombstone = entity.copy(
            isDeleted = true,
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_DELETE.rawValue,
        )
        petEventDao.upsert(tombstone)
        runCatching { remoteStore.softDelete(entity.familyId, entity.id) }
            .onSuccess {
                petEventDao.upsert(tombstone.copy(syncState = KBSyncState.SYNCED.rawValue))
            }
    }

    private suspend fun applyInbound(changes: List<PetEventRemoteChange>) {
        changes.forEach { change ->
            when (change) {
                is PetEventRemoteChange.Remove -> {
                    val local = petEventDao.getById(change.id) ?: return@forEach
                    petEventDao.upsert(local.copy(isDeleted = true, syncState = KBSyncState.SYNCED.rawValue))
                }
                is PetEventRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) return@forEach
                    val local = petEventDao.getById(dto.id)
                    val remoteTs = dto.updatedAtMillis ?: 0L
                    if (
                        local != null &&
                        KBSyncState.fromRaw(local.syncState) == KBSyncState.PENDING_DELETE
                    ) {
                        return@forEach
                    }
                    if (local != null && remoteTs < local.updatedAt) return@forEach
                    val now = System.currentTimeMillis()
                    petEventDao.upsert(
                        PetEventEntity(
                            id = dto.id,
                            familyId = dto.familyId,
                            petId = dto.petId,
                            title = dto.title,
                            eventType = dto.eventTypeRaw,
                            date = dto.dateMillis,
                            nextDueDate = dto.nextDueDateMillis,
                            notes = dto.notes,
                            vetName = dto.vetName,
                            cost = dto.cost,
                            reminderEnabled = dto.reminderEnabled,
                            isDeleted = false,
                            createdAt = local?.createdAt ?: (dto.createdAtMillis ?: now),
                            updatedAt = dto.updatedAtMillis ?: now,
                            createdBy = local?.createdBy ?: (dto.createdBy ?: ""),
                            updatedBy = dto.updatedBy ?: "",
                            syncState = KBSyncState.SYNCED.rawValue,
                        ),
                    )
                }
            }
        }
    }
}
