package it.vittorioscocca.kidbox.data.sync

import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBVaccineDao
import it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity
import it.vittorioscocca.kidbox.data.remote.health.RemoteVaccineDto
import it.vittorioscocca.kidbox.data.remote.health.VaccineRemoteStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "VaccineSync"

/**
 * Firestore real-time listener for vaccines. LWW with anti-resurrect guard.
 * Mirrors iOS `SyncCenter+Vaccines`.
 */
@Singleton
class VaccineSyncCenter @Inject constructor(
    private val remote: VaccineRemoteStore,
    private val dao: KBVaccineDao,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    fun start(familyId: String) {
        if (listeners.containsKey(familyId)) return
        Log.d(TAG, "start listener familyId=$familyId")
        listeners[familyId] = remote.listenAll(familyId) { dtos ->
            scope.launch { applyInbound(familyId, dtos) }
        }
    }

    fun stop(familyId: String) {
        listeners.remove(familyId)?.remove()
        Log.d(TAG, "stopped listener familyId=$familyId")
    }

    fun stopAll() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
    }

    private suspend fun applyInbound(familyId: String, dtos: List<RemoteVaccineDto>) {
        for (dto in dtos) {
            val local = dao.getById(dto.id)
            val remoteStamp = dto.updatedAtEpochMillis ?: 0L
            val localStamp = local?.updatedAtEpochMillis ?: 0L
            val localSync = local?.syncStateRaw ?: 0

            // Anti-resurrect: local pending write newer than remote wins.
            if (local != null && localSync == 1 && localStamp > remoteStamp) {
                Log.d(TAG, "skip anti-resurrect vaccineId=${dto.id}")
                continue
            }

            if (dto.isDeleted) {
                local?.let { dao.delete(it) }
                continue
            }

            if (remoteStamp >= localStamp) {
                val reminderOn = local?.reminderOn ?: dto.reminderOn
                dao.upsert(dto.toEntity(familyId, reminderOn))
            }
        }
    }
}

private fun RemoteVaccineDto.toEntity(familyId: String, reminderOn: Boolean) = KBVaccineEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    name = name,
    vaccineTypeRaw = vaccineTypeRaw,
    statusRaw = statusRaw,
    commercialName = commercialName,
    doseNumber = doseNumber,
    totalDoses = totalDoses,
    administeredDateEpochMillis = administeredDateEpochMillis,
    scheduledDateEpochMillis = scheduledDateEpochMillis,
    lotNumber = lotNumber,
    doctorName = doctorName,
    location = location,
    administeredBy = administeredBy,
    administrationSiteRaw = administrationSiteRaw,
    notes = notes,
    reminderOn = reminderOn,
    nextDoseDateEpochMillis = nextDoseDateEpochMillis,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis ?: 0L,
    updatedBy = updatedBy,
    createdBy = createdBy,
    syncStateRaw = 0,
    lastSyncError = null,
)
