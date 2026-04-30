package it.vittorioscocca.kidbox.data.sync

import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBPediatricProfileDao
import it.vittorioscocca.kidbox.data.local.entity.KBPediatricProfileEntity
import it.vittorioscocca.kidbox.data.remote.health.PediatricProfileRemoteStore
import it.vittorioscocca.kidbox.data.remote.health.RemotePediatricProfileDto
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "PediatricProfileSync"

/**
 * Maintains a Firestore real-time listener for a pediatric profile and writes
 * incoming snapshots to Room. Mirrors iOS `SyncCenter+PediatricProfile`.
 *
 * Anti-resurrect: if the local record has a pending upsert with a timestamp
 * newer than the remote snapshot, the remote update is skipped.
 */
@Singleton
class PediatricProfileSyncCenter @Inject constructor(
    private val remote: PediatricProfileRemoteStore,
    private val dao: KBPediatricProfileDao,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    fun start(familyId: String, childId: String) {
        val key = "$familyId:$childId"
        if (listeners.containsKey(key)) return
        Log.d(TAG, "start listener familyId=$familyId childId=$childId")
        listeners[key] = remote.listen(familyId, childId) { dto ->
            scope.launch { applyInbound(familyId, childId, dto) }
        }
    }

    fun stop(familyId: String, childId: String) {
        val key = "$familyId:$childId"
        listeners.remove(key)?.remove()
        Log.d(TAG, "stopped listener familyId=$familyId childId=$childId")
    }

    fun stopAll() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
    }

    private suspend fun applyInbound(
        familyId: String,
        childId: String,
        dto: RemotePediatricProfileDto?,
    ) {
        if (dto == null) return

        val local = dao.getByChildId(childId)
        val remoteStamp = dto.updatedAtEpochMillis ?: 0L
        val localStamp = local?.updatedAtEpochMillis ?: 0L
        val localSync = local?.syncStateRaw ?: 0

        // Anti-resurrect: a local pending write that is newer than the remote snapshot wins.
        if (local != null && localSync == 1 && localStamp > remoteStamp) {
            Log.d(TAG, "skip anti-resurrect childId=$childId localStamp=$localStamp remoteStamp=$remoteStamp")
            return
        }

        if (dto.isDeleted) {
            local?.let { dao.delete(it) }
            return
        }

        if (remoteStamp >= localStamp) {
            dao.upsert(
                KBPediatricProfileEntity(
                    id = childId,
                    familyId = familyId,
                    childId = childId,
                    emergencyContactsJson = dto.emergencyContactsJson,
                    bloodGroup = dto.bloodGroup,
                    allergies = dto.allergies,
                    medicalNotes = dto.medicalNotes,
                    doctorName = dto.doctorName,
                    doctorPhone = dto.doctorPhone,
                    updatedAtEpochMillis = remoteStamp,
                    updatedBy = dto.updatedBy,
                    syncStateRaw = 0, // SYNCED
                    lastSyncError = null,
                )
            )
        }
    }
}
