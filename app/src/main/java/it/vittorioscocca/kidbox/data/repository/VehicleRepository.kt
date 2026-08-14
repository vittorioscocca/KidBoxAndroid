package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.VehicleDao
import it.vittorioscocca.kidbox.data.local.entity.VehicleEntity
import it.vittorioscocca.kidbox.data.remote.life.VehicleRemoteChange
import it.vittorioscocca.kidbox.data.remote.life.VehicleRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.notifications.VehicleDeadlineReminderScheduler
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
class VehicleRepository @Inject constructor(
    private val vehicleDao: VehicleDao,
    private val remoteStore: VehicleRemoteStore,
    private val auth: FirebaseAuth,
    private val vehicleDeadlineReminderScheduler: VehicleDeadlineReminderScheduler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var listener: ListenerRegistration? = null
    private var boundFamilyId: String? = null
    private var subscriberCount: Int = 0

    fun observeByFamily(familyId: String): Flow<List<VehicleEntity>> =
        vehicleDao.observeByFamily(familyId)

    fun observeById(id: String): Flow<VehicleEntity?> = vehicleDao.observeById(id)

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
                listener = remoteStore.listenVehicles(
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

    suspend fun addVehicle(
        familyId: String,
        name: String,
        licensePlate: String?,
        brand: String?,
        model: String?,
        year: Int?,
        fuelType: String?,
        color: String?,
        vin: String?,
        insuranceExpiryDate: Long?,
        revisionExpiryDate: Long?,
        taxExpiryDate: Long?,
        lastServiceDate: Long?,
        nextServiceDate: Long?,
        currentKm: Int?,
        notes: String?,
        reminderEnabled: Boolean,
        reminderOffsetsJson: String? = null,
        presetVehicleId: String? = null,
    ) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val id = presetVehicleId?.trim()?.takeIf { it.isNotEmpty() } ?: java.util.UUID.randomUUID().toString()
        val entity = VehicleEntity(
            id = id,
            familyId = familyId,
            name = name,
            licensePlate = licensePlate,
            brand = brand,
            model = model,
            year = year,
            fuelType = fuelType,
            color = color,
            vin = vin,
            insuranceExpiryDate = insuranceExpiryDate,
            revisionExpiryDate = revisionExpiryDate,
            taxExpiryDate = taxExpiryDate,
            lastServiceDate = lastServiceDate,
            nextServiceDate = nextServiceDate,
            currentKm = currentKm,
            notes = notes,
            photoURL = null,
            reminderEnabled = reminderEnabled,
            reminderOffsetsJson = reminderOffsetsJson,
            isDeleted = false,
            createdAt = now,
            updatedAt = now,
            createdBy = uid,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        vehicleDao.upsert(entity)
        vehicleDeadlineReminderScheduler.syncVehicle(entity)
        runCatching { remoteStore.upsertVehicleToFirestore(entity) }
            .onSuccess {
                val synced = entity.copy(syncState = KBSyncState.SYNCED.rawValue)
                vehicleDao.upsert(synced)
                vehicleDeadlineReminderScheduler.syncVehicle(synced)
            }
            .onFailure {
                val err = entity.copy(syncState = KBSyncState.ERROR.rawValue)
                vehicleDao.upsert(err)
                vehicleDeadlineReminderScheduler.syncVehicle(err)
                throw it
            }
    }

    suspend fun updateVehicle(entity: VehicleEntity) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val local = entity.copy(
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_UPSERT.rawValue,
        )
        vehicleDao.upsert(local)
        vehicleDeadlineReminderScheduler.syncVehicle(local)
        runCatching { remoteStore.upsertVehicleToFirestore(local) }
            .onSuccess {
                val synced = local.copy(syncState = KBSyncState.SYNCED.rawValue)
                vehicleDao.upsert(synced)
                vehicleDeadlineReminderScheduler.syncVehicle(synced)
            }
            .onFailure {
                val err = local.copy(syncState = KBSyncState.ERROR.rawValue)
                vehicleDao.upsert(err)
                vehicleDeadlineReminderScheduler.syncVehicle(err)
                throw it
            }
    }

    suspend fun deleteVehicle(entity: VehicleEntity) {
        vehicleDeadlineReminderScheduler.cancelForVehicle(entity.id)
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val tombstone = entity.copy(
            isDeleted = true,
            updatedAt = now,
            updatedBy = uid,
            syncState = KBSyncState.PENDING_DELETE.rawValue,
        )
        vehicleDao.upsert(tombstone)
        vehicleDeadlineReminderScheduler.syncVehicle(tombstone)
        runCatching { remoteStore.softDelete(entity.familyId, entity.id) }
            .onSuccess {
                val synced = tombstone.copy(syncState = KBSyncState.SYNCED.rawValue)
                vehicleDao.upsert(synced)
                vehicleDeadlineReminderScheduler.syncVehicle(synced)
            }
    }

    private suspend fun applyInbound(changes: List<VehicleRemoteChange>) {
        changes.forEach { change ->
            when (change) {
                is VehicleRemoteChange.Remove -> {
                    vehicleDeadlineReminderScheduler.cancelForVehicle(change.id)
                    val local = vehicleDao.getById(change.id) ?: return@forEach
                    val tomb = local.copy(isDeleted = true, syncState = KBSyncState.SYNCED.rawValue)
                    vehicleDao.upsert(tomb)
                    vehicleDeadlineReminderScheduler.syncVehicle(tomb)
                }
                is VehicleRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) {
                        vehicleDeadlineReminderScheduler.cancelForVehicle(dto.id)
                        return@forEach
                    }
                    val local = vehicleDao.getById(dto.id)
                    val remoteTs = dto.updatedAtMillis ?: 0L
                    if (
                        local != null &&
                        KBSyncState.fromRaw(local.syncState) == KBSyncState.PENDING_DELETE
                    ) {
                        return@forEach
                    }
                    if (local != null && remoteTs < local.updatedAt) return@forEach
                    val now = System.currentTimeMillis()
                    vehicleDao.upsert(
                        VehicleEntity(
                            id = dto.id,
                            familyId = dto.familyId,
                            name = dto.name,
                            licensePlate = dto.licensePlate,
                            brand = dto.brand,
                            model = dto.model,
                            year = dto.year,
                            fuelType = dto.fuelTypeRaw,
                            color = dto.color,
                            vin = dto.vin,
                            insuranceExpiryDate = dto.insuranceExpiryDateMillis,
                            revisionExpiryDate = dto.revisionExpiryDateMillis,
                            taxExpiryDate = dto.taxExpiryDateMillis,
                            lastServiceDate = dto.lastServiceDateMillis,
                            nextServiceDate = dto.nextServiceDateMillis,
                            currentKm = dto.currentKm,
                            notes = dto.notes,
                            photoURL = dto.photoURL,
                            reminderEnabled = dto.reminderEnabled,
                            reminderOffsetsJson = dto.reminderOffsetsJson,
                            isDeleted = false,
                            createdAt = local?.createdAt ?: (dto.createdAtMillis ?: now),
                            updatedAt = dto.updatedAtMillis ?: now,
                            createdBy = local?.createdBy ?: (dto.createdBy ?: ""),
                            updatedBy = dto.updatedBy ?: "",
                            syncState = KBSyncState.SYNCED.rawValue,
                        ),
                    )
                    vehicleDao.getById(dto.id)?.let { vehicleDeadlineReminderScheduler.syncVehicle(it) }
                }
            }
        }
    }
}
