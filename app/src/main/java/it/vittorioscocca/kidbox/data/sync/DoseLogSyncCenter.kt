package it.vittorioscocca.kidbox.data.sync

import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBDoseLogDao
import it.vittorioscocca.kidbox.data.remote.health.DoseLogRemoteStore
import it.vittorioscocca.kidbox.data.remote.health.RemoteDoseLogDto
import it.vittorioscocca.kidbox.data.remote.health.toEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "DoseLogSync"

@Singleton
class DoseLogSyncCenter @Inject constructor(
    private val remote: DoseLogRemoteStore,
    private val dao: KBDoseLogDao,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    fun start(familyId: String) {
        if (listeners.containsKey(familyId)) return
        Log.d(TAG, "start listener familyId=$familyId")
        listeners[familyId] = remote.listenAll(familyId) { dtos ->
            scope.launch { applyInbound(dtos) }
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

    private suspend fun applyInbound(dtos: List<RemoteDoseLogDto>) {
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
                local?.let { dao.delete(it) }
                continue
            }

            if (remoteStamp >= localStamp) {
                dao.upsert(dto.toEntity())
            }
        }
    }
}
