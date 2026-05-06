package it.vittorioscocca.kidbox.data.sync

import android.util.Log
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.remote.health.MedicalExamRemoteStore
import it.vittorioscocca.kidbox.data.remote.health.RemoteExamDto
import it.vittorioscocca.kidbox.data.remote.health.toEntity
import java.util.Collections
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TAG = "MedicalExamSync"

/** Room impone FK `prescribingVisitId` → `kb_medical_visits`; il listener esami può arrivare prima delle visite. */
private const val INBOUND_PRESCRIBING_VISIT_RETRY_ATTEMPTS = 8

@Singleton
class MedicalExamSyncCenter @Inject constructor(
    private val remote: MedicalExamRemoteStore,
    private val dao: KBMedicalExamDao,
    private val visitDao: KBMedicalVisitDao,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val listeners = mutableMapOf<String, ListenerRegistration>()
    private val lastInboundDtosByFamily =
        Collections.synchronizedMap<String, List<RemoteExamDto>>(mutableMapOf())

    fun start(familyId: String) {
        if (listeners.containsKey(familyId)) return
        Log.d(TAG, "start listener familyId=$familyId")
        listeners[familyId] = remote.listenAll(familyId) { dtos ->
            lastInboundDtosByFamily[familyId] = dtos
            scope.launch { applyInbound(familyId, dtos) }
        }
    }

    /**
     * Dopo che le visite sono state scritte in Room, riprova ad applicare gli esami
     * (stesso snapshot Firestore memorizzato) per soddisfare la FK.
     */
    fun retryAfterVisitSnapshotPersisted(familyId: String) {
        scope.launch {
            val dtos = lastInboundDtosByFamily[familyId] ?: return@launch
            applyInbound(familyId, dtos)
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

    private suspend fun applyInbound(familyId: String, dtos: List<RemoteExamDto>) {
        repeat(INBOUND_PRESCRIBING_VISIT_RETRY_ATTEMPTS) { attempt ->
            var anyDeferred = false
            for (dto in dtos) {
                val local = dao.getById(dto.id)
                val remoteStamp = dto.updatedAtEpochMillis ?: 0L
                val localStamp = local?.updatedAtEpochMillis ?: 0L
                val localSync = local?.syncStateRaw ?: 0

                if (local != null && localSync == 1 && localStamp > remoteStamp) {
                    Log.d(TAG, "skip anti-resurrect examId=${dto.id}")
                    continue
                }

                if (dto.isDeleted) {
                    local?.let { dao.delete(it) }
                    continue
                }

                if (remoteStamp >= localStamp) {
                    val pv = dto.prescribingVisitId
                    if (pv != null && visitDao.getById(pv) == null) {
                        if (attempt < INBOUND_PRESCRIBING_VISIT_RETRY_ATTEMPTS - 1) {
                            anyDeferred = true
                            Log.d(
                                TAG,
                                "defer exam id=${dto.id} prescribingVisitId=$pv " +
                                    "(visit not local yet) attempt=$attempt familyId=$familyId",
                            )
                            continue
                        }
                        Log.w(
                            TAG,
                            "exam id=${dto.id} prescribingVisitId=$pv visit still missing after retries — " +
                                "upsert with prescribingVisitId=null (FK) familyId=$familyId",
                        )
                        dao.upsert(dto.toEntity().copy(prescribingVisitId = null))
                        continue
                    }
                    dao.upsert(dto.toEntity())
                }
            }
            if (!anyDeferred) return
            if (attempt < INBOUND_PRESCRIBING_VISIT_RETRY_ATTEMPTS - 1) {
                delay(350L * (attempt + 1))
            }
        }
    }
}
