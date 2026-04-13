package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.remote.grocery.GroceryRemoteChange
import it.vittorioscocca.kidbox.data.remote.grocery.GroceryRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBSyncState
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
                    onChange = { changes -> scope.launch { applyInboundChanges(changes) } },
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
    ) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val item = KBGroceryItemEntity(
            id = java.util.UUID.randomUUID().toString(),
            familyId = familyId,
            name = name,
            category = category,
            notes = notes,
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
    ) {
        val existing = groceryDao.getById(itemId) ?: return
        val uid = auth.currentUser?.uid ?: "local"
        val local = existing.copy(
            name = name,
            category = category,
            notes = notes,
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

    private suspend fun applyInboundChanges(changes: List<GroceryRemoteChange>) {
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
    }
}
