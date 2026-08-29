package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.dao.OnboardingSignalsDao
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.remote.grocery.GroceryRemoteChange
import it.vittorioscocca.kidbox.data.remote.grocery.GroceryRemoteStore
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
class GroceryRepository @Inject constructor(
    private val groceryDao: KBGroceryItemDao,
    private val remoteStore: GroceryRemoteStore,
    private val auth: FirebaseAuth,
    private val onboardingSignalsDao: OnboardingSignalsDao,
    @ApplicationContext private val appContext: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null

    fun observeByFamilyId(familyId: String): Flow<List<KBGroceryItemEntity>> =
        groceryDao.observeByFamilyId(familyId)

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                listener?.remove()
                listener = remoteStore.listenGroceries(
                    familyId = familyId,
                    onChange = { changes, snapshotIds ->
                        scope.launch { applyInboundChanges(familyId, changes, snapshotIds) }
                    },
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
        }
    }

    fun stopRealtime() {
        scope.launch {
            realtimeMutex.withLock {
                listener?.remove()
                listener = null
            }
        }
    }

    suspend fun addItem(
        familyId: String,
        name: String,
        category: String?,
        notes: String?,
        quantity: Int? = null,
    ) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val isFirstItem = onboardingSignalsDao.groceryItemCount(familyId) == 0
        val item = KBGroceryItemEntity(
            id = java.util.UUID.randomUUID().toString(),
            familyId = familyId,
            name = name,
            category = category,
            notes = notes,
            quantity = quantity,
            isPurchased = false,
            purchasedAtEpochMillis = null,
            purchasedBy = null,
            isDeleted = false,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            createdBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        groceryDao.upsert(item)
        AppAnalytics.contentCreated(appContext, "grocery")
        if (isFirstItem) {
            AppAnalytics.featureFirstUse(appContext, feature = "grocery")
        }
        runCatching { remoteStore.upsert(item) }
            .onSuccess {
                groceryDao.upsert(
                    item.copy(
                        syncStateRaw = KBSyncState.SYNCED.rawValue,
                        lastSyncError = null,
                    ),
                )
            }
            .onFailure { err ->
                groceryDao.upsert(
                    item.copy(
                        syncStateRaw = KBSyncState.ERROR.rawValue,
                        lastSyncError = err.message,
                    ),
                )
                throw err
            }
    }

    suspend fun updateItem(
        itemId: String,
        name: String,
        category: String?,
        notes: String?,
        quantity: Int? = null,
    ) {
        val existing = groceryDao.getById(itemId) ?: return
        val uid = auth.currentUser?.uid ?: "local"
        val local = existing.copy(
            name = name,
            category = category,
            notes = notes,
            quantity = quantity,
            updatedAtEpochMillis = System.currentTimeMillis(),
            updatedBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        groceryDao.upsert(local)
        runCatching { remoteStore.upsert(local) }
            .onSuccess {
                groceryDao.upsert(local.copy(syncStateRaw = KBSyncState.SYNCED.rawValue))
            }
            .onFailure { err ->
                groceryDao.upsert(
                    local.copy(
                        syncStateRaw = KBSyncState.ERROR.rawValue,
                        lastSyncError = err.message,
                    ),
                )
                throw err
            }
    }

    suspend fun togglePurchased(itemId: String) {
        val existing = groceryDao.getById(itemId) ?: return
        val uid = auth.currentUser?.uid ?: "local"
        val isPurchased = !existing.isPurchased
        val now = System.currentTimeMillis()
        val local = existing.copy(
            isPurchased = isPurchased,
            purchasedAtEpochMillis = if (isPurchased) now else null,
            purchasedBy = if (isPurchased) uid else null,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        groceryDao.upsert(local)
        runCatching { remoteStore.upsert(local) }
            .onSuccess {
                groceryDao.upsert(local.copy(syncStateRaw = KBSyncState.SYNCED.rawValue))
            }
            .onFailure { err ->
                groceryDao.upsert(
                    local.copy(
                        syncStateRaw = KBSyncState.ERROR.rawValue,
                        lastSyncError = err.message,
                    ),
                )
                throw err
            }
    }

    suspend fun deleteItem(itemId: String) {
        val existing = groceryDao.getById(itemId) ?: return
        remoteStore.softDelete(existing.familyId, itemId)
        groceryDao.deleteById(itemId)
    }

    private suspend fun applyInboundChanges(
        familyId: String,
        changes: List<GroceryRemoteChange>,
        snapshotIds: Set<String>?,
    ) {
        changes.forEach { change ->
            when (change) {
                is GroceryRemoteChange.Remove -> groceryDao.deleteById(change.id)
                is GroceryRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) {
                        groceryDao.deleteById(dto.id)
                        return@forEach
                    }
                    val local = groceryDao.getById(dto.id)
                    val remoteTs = dto.updatedAtEpochMillis ?: 0L
                    if (
                        local != null &&
                        (local.isDeleted || KBSyncState.fromRaw(local.syncStateRaw) == KBSyncState.PENDING_DELETE)
                    ) {
                        return@forEach
                    }
                    if (local != null && remoteTs < local.updatedAtEpochMillis) {
                        return@forEach
                    }
                    val now = System.currentTimeMillis()
                    groceryDao.upsert(
                        KBGroceryItemEntity(
                            id = dto.id,
                            familyId = dto.familyId,
                            name = dto.name,
                            category = dto.category,
                            notes = dto.notes,
                            quantity = dto.quantity,
                            isPurchased = dto.isPurchased,
                            purchasedAtEpochMillis = dto.purchasedAtEpochMillis,
                            purchasedBy = dto.purchasedBy,
                            isDeleted = false,
                            createdAtEpochMillis = local?.createdAtEpochMillis ?: (dto.updatedAtEpochMillis ?: now),
                            updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                            updatedBy = dto.updatedBy,
                            createdBy = local?.createdBy ?: dto.createdBy,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        ),
                    )
                }
            }
        }

        // Riconciliazione: un articolo cancellato da un altro dispositivo può non
        // arrivare mai come REMOVED sul delta (vedi il commento in
        // GroceryRemoteStore). Se la snapshot viene dal server e non lo contiene
        // più, la riga locale va tolta. Si saltano le scritture locali non ancora
        // inviate: non sono nel result set remoto perché non ci sono ancora
        // arrivate, non perché siano state cancellate.
        if (snapshotIds != null) {
            runCatching {
                groceryDao.listByFamilyId(familyId).forEach { local ->
                    if (local.id in snapshotIds) return@forEach
                    val sync = KBSyncState.fromRaw(local.syncStateRaw)
                    if (sync == KBSyncState.PENDING_UPSERT || sync == KBSyncState.ERROR) return@forEach
                    groceryDao.deleteById(local.id)
                }
            }
        }
    }
}
