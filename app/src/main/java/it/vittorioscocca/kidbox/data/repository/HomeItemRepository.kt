package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.HomeItemDao
import it.vittorioscocca.kidbox.data.local.dao.VehicleDao
import it.vittorioscocca.kidbox.data.local.entity.HomeItemEntity
import it.vittorioscocca.kidbox.data.remote.life.HomeItemRemoteChange
import it.vittorioscocca.kidbox.data.remote.life.HomeItemRemoteStore
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
class HomeItemRepository @Inject constructor(
    private val homeItemDao: HomeItemDao,
    private val remoteStore: HomeItemRemoteStore,
    private val auth: FirebaseAuth,
    private val vehicleDao: VehicleDao,
    @ApplicationContext private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var boundFamilyId: String? = null
    private var subscriberCount: Int = 0

    fun observeByFamily(familyId: String): Flow<List<HomeItemEntity>> =
        homeItemDao.observeByFamily(familyId)

    fun observeById(id: String): Flow<HomeItemEntity?> = homeItemDao.observeById(id)

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
        listener = remoteStore.listenHomeItems(
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

    suspend fun addHomeItem(
        familyId: String,
        name: String,
        category: String,
        brand: String?,
        model: String?,
        serialNumber: String?,
        purchaseDate: Long?,
        warrantyExpiryDate: Long?,
        nextServiceDate: Long?,
        servicePeriodMonths: Int?,
        notes: String?,
        reminderEnabled: Boolean,
        presetItemId: String? = null,
    ) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val isFirstUse = homeItemDao.countByFamilyId(familyId) == 0 && vehicleDao.countByFamilyId(familyId) == 0
        val id = presetItemId?.trim()?.takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()
        val entity = HomeItemEntity(
            id = id,
            familyId = familyId,
            name = name,
            category = category,
            brand = brand,
            model = model,
            serialNumber = serialNumber,
            purchaseDate = purchaseDate,
            warrantyExpiryDate = warrantyExpiryDate,
            nextServiceDate = nextServiceDate,
            servicePeriodMonths = servicePeriodMonths,
            notes = notes,
            reminderEnabled = reminderEnabled,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
            createdBy = uid,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        homeItemDao.upsert(entity)
        runCatching { remoteStore.upsertHomeItemToFirestore(entity) }
            .onSuccess { homeItemDao.upsert(entity.copy(syncState = KBSyncState.SYNCED.rawValue)) }
            .onFailure {
                homeItemDao.upsert(entity.copy(syncState = KBSyncState.ERROR.rawValue))
                throw it
            }
        AppAnalytics.contentCreated(appContext, "home_vehicles")
        if (isFirstUse) {
            AppAnalytics.featureFirstUse(appContext, feature = "home_vehicles")
        }
    }

    suspend fun updateHomeItem(entity: HomeItemEntity) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val local = entity.copy(
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        homeItemDao.upsert(local)
        runCatching { remoteStore.upsertHomeItemToFirestore(local) }
            .onSuccess { homeItemDao.upsert(local.copy(syncState = KBSyncState.SYNCED.rawValue)) }
            .onFailure {
                homeItemDao.upsert(local.copy(syncState = KBSyncState.ERROR.rawValue))
                throw it
            }
    }

    suspend fun deleteHomeItem(entity: HomeItemEntity) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val tombstone = entity.copy(
            isDeleted = true,
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_DELETE.rawValue,
        )
        homeItemDao.upsert(tombstone)
        runCatching { remoteStore.softDelete(entity.familyId, entity.id) }
            .onSuccess {
                homeItemDao.upsert(tombstone.copy(syncState = KBSyncState.SYNCED.rawValue))
            }
    }

    private suspend fun applyInbound(changes: List<HomeItemRemoteChange>) {
        changes.forEach { change ->
            when (change) {
                is HomeItemRemoteChange.Remove -> {
                    val local = homeItemDao.getById(change.id) ?: return@forEach
                    homeItemDao.upsert(local.copy(isDeleted = true, syncState = KBSyncState.SYNCED.rawValue))
                }
                is HomeItemRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) return@forEach
                    val local = homeItemDao.getById(dto.id)
                    val remoteTs = dto.updatedAtMillis ?: 0L
                    if (
                        local != null &&
                        KBSyncState.fromRaw(local.syncState) == KBSyncState.PENDING_DELETE
                    ) {
                        return@forEach
                    }
                    if (local != null && remoteTs < local.updatedAt) return@forEach
                    val now = System.currentTimeMillis()
                    homeItemDao.upsert(
                        HomeItemEntity(
                            id = dto.id,
                            familyId = dto.familyId,
                            name = dto.name,
                            category = dto.categoryRaw,
                            brand = dto.brand,
                            model = dto.model,
                            serialNumber = dto.serialNumber,
                            purchaseDate = dto.purchaseDateMillis,
                            warrantyExpiryDate = dto.warrantyExpiryDateMillis,
                            nextServiceDate = dto.nextServiceDateMillis,
                            servicePeriodMonths = dto.servicePeriodMonths,
                            notes = dto.notes,
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
