package it.vittorioscocca.kidbox.data.sync

import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBDoseLogDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.remote.health.DoseLogRemoteStore
import it.vittorioscocca.kidbox.data.remote.health.RemoteDoseLogDto
import it.vittorioscocca.kidbox.data.remote.health.toEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "DoseLogSync"
/** Dose log ha FK su `kb_treatments` e `kb_families`: il listener può arrivare prima del sync cure. */
private const val INBOUND_PARENT_RETRY_ATTEMPTS = 8

@Singleton
class DoseLogSyncCenter @Inject constructor(
    private val remote: DoseLogRemoteStore,
    private val dao: KBDoseLogDao,
    private val familyDao: KBFamilyDao,
    private val treatmentDao: KBTreatmentDao,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    fun start(familyId: String) {
        if (listeners.containsKey(familyId)) return
        Log.d(TAG, "start listener familyId=$familyId")
        listeners[familyId] = remote.listenAll(familyId) { dtos, removedIds ->
            scope.launch { applyInbound(familyId, dtos, removedIds) }
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

    private suspend fun applyInbound(
        familyId: String,
        dtos: List<RemoteDoseLogDto>,
        removedIds: List<String> = emptyList(),
    ) {
        repeat(INBOUND_PARENT_RETRY_ATTEMPTS) { attempt ->
            var anyDeferred = false
            for (rid in removedIds) {
                dao.getById(rid)?.let { dao.delete(it) }
            }
            for (dto in dtos) {
                val local = dao.getById(dto.id)
                val remoteStamp = dto.updatedAtEpochMillis ?: 0L
                val localStamp = local?.updatedAtEpochMillis ?: 0L
                val localPending = local?.syncStatus == 1

                if (local != null && localPending && localStamp > remoteStamp) {
                    Log.d(TAG, "skip anti-resurrect doseLogId=${dto.id}")
                    continue
                }

                if (dto.isDeleted) {
                    dao.deleteAllForTreatmentDaySlot(dto.treatmentId, dto.dayNumber, dto.slotIndex)
                    continue
                }

                if (remoteStamp >= localStamp) {
                    if (familyDao.getById(dto.familyId) == null || treatmentDao.getById(dto.treatmentId) == null) {
                        anyDeferred = true
                        Log.d(
                            TAG,
                            "defer doseLog id=${dto.id} treatmentId=${dto.treatmentId} " +
                                "(parent not local yet) attempt=$attempt familyId=$familyId",
                        )
                        continue
                    }
                    val unchanged =
                        local != null &&
                            local.taken == dto.taken &&
                            local.takenAtEpochMillis == dto.takenAtEpochMillis &&
                            local.scheduledTime == dto.scheduledTime &&
                            local.isDeleted == dto.isDeleted &&
                            local.updatedAtEpochMillis == remoteStamp
                    if (unchanged) {
                        continue
                    }
                    // Un solo record per slot: rimuove id UUID legacy prima di applicare il documento remoto.
                    dao.deleteAllForTreatmentDaySlot(dto.treatmentId, dto.dayNumber, dto.slotIndex)
                    dao.upsert(dto.toEntity())
                }
            }
            if (!anyDeferred) return
            if (attempt < INBOUND_PARENT_RETRY_ATTEMPTS - 1) {
                delay(350L * (attempt + 1))
            } else {
                Log.w(TAG, "orphan dose logs remain after retries familyId=$familyId (missing family/treatment rows)")
            }
        }
    }
}
