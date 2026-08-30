package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.PetDao
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import it.vittorioscocca.kidbox.data.remote.life.PetRemoteChange
import it.vittorioscocca.kidbox.data.remote.life.PetRemoteStore
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
class PetRepository @Inject constructor(
    private val petDao: PetDao,
    private val remoteStore: PetRemoteStore,
    private val auth: FirebaseAuth,
    @ApplicationContext private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var boundFamilyId: String? = null
    private var subscriberCount: Int = 0

    fun observeByFamily(familyId: String): Flow<List<PetEntity>> = petDao.observeByFamily(familyId)

    fun observeById(id: String): Flow<PetEntity?> = petDao.observeById(id)

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
        listener = remoteStore.listenPets(
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

    suspend fun addPet(
        familyId: String,
        name: String,
        species: String,
        breed: String?,
        birthDate: Long?,
        color: String?,
        chipCode: String?,
        notes: String?,
    ) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val isFirstUse = petDao.countByFamilyId(familyId) == 0
        val entity = PetEntity(
            id = java.util.UUID.randomUUID().toString(),
            familyId = familyId,
            name = name,
            species = species,
            breed = breed,
            birthDate = birthDate,
            color = color,
            chipCode = chipCode,
            notes = notes,
            photoURL = null,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
            createdBy = uid,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        petDao.upsert(entity)
        runCatching { remoteStore.upsertPetToFirestore(entity) }
            .onSuccess {
                petDao.upsert(entity.copy(syncState = KBSyncState.SYNCED.rawValue))
            }
            .onFailure {
                petDao.upsert(entity.copy(syncState = KBSyncState.ERROR.rawValue))
                throw it
            }
        AppAnalytics.contentCreated(appContext, "pets")
        if (isFirstUse) {
            AppAnalytics.featureFirstUse(appContext, feature = "pets")
        }
    }

    suspend fun updatePet(entity: PetEntity) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val local = entity.copy(
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        petDao.upsert(local)
        runCatching { remoteStore.upsertPetToFirestore(local) }
            .onSuccess { petDao.upsert(local.copy(syncState = KBSyncState.SYNCED.rawValue)) }
            .onFailure {
                petDao.upsert(local.copy(syncState = KBSyncState.ERROR.rawValue))
                throw it
            }
    }

    suspend fun deletePet(entity: PetEntity) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val tombstone = entity.copy(
            isDeleted = true,
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_DELETE.rawValue,
        )
        petDao.upsert(tombstone)
        runCatching { remoteStore.softDelete(entity.familyId, entity.id) }
            .onSuccess {
                petDao.upsert(tombstone.copy(syncState = KBSyncState.SYNCED.rawValue))
            }
    }

    private suspend fun applyInbound(changes: List<PetRemoteChange>) {
        changes.forEach { change ->
            when (change) {
                is PetRemoteChange.Remove -> {
                    val local = petDao.getById(change.id) ?: return@forEach
                    petDao.upsert(local.copy(isDeleted = true, syncState = KBSyncState.SYNCED.rawValue))
                }
                is PetRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) return@forEach
                    val local = petDao.getById(dto.id)
                    val remoteTs = dto.updatedAtMillis ?: 0L
                    if (
                        local != null &&
                        KBSyncState.fromRaw(local.syncState) == KBSyncState.PENDING_DELETE
                    ) {
                        return@forEach
                    }
                    if (local != null && remoteTs < local.updatedAt) return@forEach
                    val now = System.currentTimeMillis()
                    petDao.upsert(
                        PetEntity(
                            id = dto.id,
                            familyId = dto.familyId,
                            name = dto.name,
                            species = dto.species,
                            breed = dto.breed,
                            birthDate = dto.birthDateMillis,
                            color = dto.color,
                            chipCode = dto.chipCode,
                            notes = dto.notes,
                            photoURL = dto.photoURL,
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
