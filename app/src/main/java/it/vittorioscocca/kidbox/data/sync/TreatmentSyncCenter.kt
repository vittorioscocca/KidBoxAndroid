package it.vittorioscocca.kidbox.data.sync

import it.vittorioscocca.kidbox.util.KBLog

import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.dao.PetDao
import it.vittorioscocca.kidbox.data.remote.health.RemoteTreatmentDto
import it.vittorioscocca.kidbox.data.remote.health.TreatmentRemoteStore
import it.vittorioscocca.kidbox.data.remote.health.toEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

private const val TAG = "TreatmentSync"

@Singleton
class TreatmentSyncCenter @Inject constructor(
    private val remote: TreatmentRemoteStore,
    private val dao: KBTreatmentDao,
    private val childDao: KBChildDao,
    private val petDao: PetDao,
    private val doseLogSyncCenter: DoseLogSyncCenter,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val listeners = mutableMapOf<String, ListenerRegistration>()

    fun start(familyId: String) {
        // Registra prima le cure: i dose log hanno FK su `kb_treatments` e il listener dose può arrivare subito.
        if (!listeners.containsKey(familyId)) {
            KBLog.sync.debug("start listener familyId=$familyId", TAG)
            listeners[familyId] = remote.listenAll(familyId) { dtos ->
                scope.launch { applyInbound(dtos) }
            }
        }
        doseLogSyncCenter.start(familyId)
    }

    fun stop(familyId: String) {
        listeners.remove(familyId)?.remove()
        doseLogSyncCenter.stop(familyId)
        KBLog.sync.debug("stopped listener familyId=$familyId", TAG)
    }

    /**
     * Pull-to-refresh: stacca e riaggancia il listener della famiglia.
     *
     * [start] da solo esce subito se il listener è già in mappa, quindi per
     * rileggere davvero da Firestore serve passare dallo stop.
     */
    fun restart(familyId: String) {
        stop(familyId)
        start(familyId)
    }

    fun stopAll() {
        listeners.values.forEach { it.remove() }
        listeners.clear()
        doseLogSyncCenter.stopAll()
    }

    private suspend fun isPediatricHealthSubject(familyId: String, childId: String): Boolean {
        val row = childDao.getById(childId) ?: return false
        return row.familyId == familyId
    }

    private suspend fun isPetInFamily(familyId: String, petId: String): Boolean {
        if (petId.isBlank()) return false
        val pet = petDao.getById(petId) ?: return false
        return pet.familyId == familyId && !pet.isDeleted
    }

    private suspend fun applyInbound(dtos: List<RemoteTreatmentDto>) {
        for (dto in dtos) {
            val local = dao.getById(dto.id)
            val remoteStamp = dto.updatedAtEpochMillis ?: 0L
            val localStamp = local?.updatedAtEpochMillis ?: 0L
            val localSync = local?.syncStateRaw ?: 0

            if (local != null && localSync == 1 && localStamp > remoteStamp) {
                KBLog.sync.debug("skip anti-resurrect treatmentId=${dto.id}", TAG)
                continue
            }

            if (dto.isDeleted) {
                local?.let { dao.delete(it) }
                continue
            }

            if (remoteStamp >= localStamp) {
                val pediatric = isPediatricHealthSubject(dto.familyId, dto.childId)
                val petSubject = isPetInFamily(dto.familyId, dto.petId)
                val merged = if (pediatric || petSubject) {
                    dto.toEntity()
                } else {
                    val fromRemote = dto.toEntity()
                    if (local != null) {
                        fromRemote.copy(reminderEnabled = local.reminderEnabled)
                    } else {
                        fromRemote.copy(reminderEnabled = false)
                    }
                }
                dao.upsert(merged)
            }
        }
    }
}
