package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.VehicleEventDao
import it.vittorioscocca.kidbox.data.local.entity.VehicleEventEntity
import it.vittorioscocca.kidbox.data.remote.life.VehicleEventRemoteChange
import it.vittorioscocca.kidbox.data.remote.life.VehicleEventRemoteStore
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
class VehicleEventRepository @Inject constructor(
    private val vehicleEventDao: VehicleEventDao,
    private val remoteStore: VehicleEventRemoteStore,
    private val expenseRepository: ExpenseRepository,
    private val auth: FirebaseAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var boundFamilyId: String? = null
    private var subscriberCount: Int = 0

    fun observeByVehicle(familyId: String, vehicleId: String): Flow<List<VehicleEventEntity>> =
        vehicleEventDao.observeByVehicle(familyId, vehicleId)

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
                listener = remoteStore.listenVehicleEvents(
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

    suspend fun listActiveByVehicle(familyId: String, vehicleId: String): List<VehicleEventEntity> =
        vehicleEventDao.listActiveByVehicle(familyId, vehicleId)

    suspend fun addVehicleEvent(
        familyId: String,
        vehicleId: String,
        title: String,
        eventType: String,
        dateMillis: Long,
        km: Int?,
        cost: Double?,
        garageName: String?,
        notes: String?,
        presetEventId: String? = null,
        // Serve solo al titolo della spesa: "Tagliando — Panda" invece del solo
        // titolo dell'intervento.
        vehicleName: String? = null,
        expenseFallbackTitle: String? = null,
    ) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val id = presetEventId?.trim()?.takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()

        // Un intervento che è costato qualcosa è anche una spesa di famiglia:
        // il costo si scrive una volta sola, qui, e la voce in Spese nasce da sé.
        val linkedExpenseId = if ((cost ?: 0.0) > 0.0) {
            runCatching {
                // La categoria "Automobile" deve esistere prima di agganciarcisi.
                expenseRepository.seedDefaultCategories(familyId)
                expenseRepository.createExpenseLocal(
                    familyId = familyId,
                    title = expenseTitle(title, vehicleName, expenseFallbackTitle),
                    amount = cost ?: 0.0,
                    dateEpochMillis = dateMillis,
                    categoryId = ExpenseRepository.defaultCategoryId(familyId, "automobile"),
                    notes = expenseNotes(garageName, km, notes),
                ).id
            }.getOrNull()
        } else {
            null
        }

        val entity = VehicleEventEntity(
            id = id,
            familyId = familyId,
            vehicleId = vehicleId,
            title = title,
            eventType = eventType,
            date = dateMillis,
            km = km,
            cost = cost,
            garageName = garageName,
            notes = notes,
            linkedExpenseId = linkedExpenseId,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
            createdBy = uid,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        vehicleEventDao.upsert(entity)
        if (linkedExpenseId != null) {
            runCatching { expenseRepository.flushPending(familyId) }
        }
        runCatching { remoteStore.upsertVehicleEventToFirestore(entity) }
            .onSuccess { vehicleEventDao.upsert(entity.copy(syncState = KBSyncState.SYNCED.rawValue)) }
            .onFailure {
                vehicleEventDao.upsert(entity.copy(syncState = KBSyncState.ERROR.rawValue))
                throw it
            }
    }

    /** "Tagliando — Panda": in Spese si legge cosa e su quale auto. */
    private fun expenseTitle(title: String, vehicleName: String?, fallback: String?): String {
        val base = title.trim().ifEmpty { fallback?.trim().orEmpty().ifEmpty { "Intervento auto" } }
        val vehicle = vehicleName?.trim().orEmpty()
        return if (vehicle.isEmpty()) base else "$base — $vehicle"
    }

    private fun expenseNotes(garageName: String?, km: Int?, notes: String?): String? {
        val parts = buildList {
            garageName?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
            km?.let { add("$it km") }
            notes?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        }
        return parts.joinToString(" · ").takeIf { it.isNotEmpty() }
    }

    suspend fun deleteVehicleEvent(entity: VehicleEventEntity) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val tombstone = entity.copy(
            isDeleted = true,
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_DELETE.rawValue,
        )
        vehicleEventDao.upsert(tombstone)
        runCatching { remoteStore.softDelete(entity.familyId, entity.id) }
            .onSuccess {
                vehicleEventDao.upsert(tombstone.copy(syncState = KBSyncState.SYNCED.rawValue))
            }
    }

    private suspend fun applyInbound(changes: List<VehicleEventRemoteChange>) {
        changes.forEach { change ->
            when (change) {
                is VehicleEventRemoteChange.Remove -> {
                    val local = vehicleEventDao.getById(change.id) ?: return@forEach
                    vehicleEventDao.upsert(local.copy(isDeleted = true, syncState = KBSyncState.SYNCED.rawValue))
                }
                is VehicleEventRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) return@forEach
                    val local = vehicleEventDao.getById(dto.id)
                    val remoteTs = dto.updatedAtMillis ?: 0L
                    if (
                        local != null &&
                        KBSyncState.fromRaw(local.syncState) == KBSyncState.PENDING_DELETE
                    ) {
                        return@forEach
                    }
                    if (local != null && remoteTs < local.updatedAt) return@forEach
                    val now = System.currentTimeMillis()
                    vehicleEventDao.upsert(
                        VehicleEventEntity(
                            id = dto.id,
                            familyId = dto.familyId,
                            vehicleId = dto.vehicleId,
                            title = dto.title,
                            eventType = dto.eventTypeRaw,
                            date = dto.dateMillis,
                            km = dto.km,
                            cost = dto.cost,
                            garageName = dto.garageName,
                            notes = dto.notes,
                            linkedExpenseId = dto.linkedExpenseId,
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
