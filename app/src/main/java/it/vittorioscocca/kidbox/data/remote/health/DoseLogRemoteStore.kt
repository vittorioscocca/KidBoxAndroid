package it.vittorioscocca.kidbox.data.remote.health

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBDoseLogEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteDoseLogDto(
    val id: String,
    val familyId: String,
    val childId: String,
    val treatmentId: String,
    val dayNumber: Int,
    val slotIndex: Int,
    val scheduledTime: String,
    val takenAtEpochMillis: Long?,
    val taken: Boolean,
    val isDeleted: Boolean,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
    val createdAtEpochMillis: Long,
)

sealed class DoseLogRemoteChange {
    data class Upsert(val dto: RemoteDoseLogDto) : DoseLogRemoteChange()
    data class Remove(val doseLogId: String) : DoseLogRemoteChange()
}

@Singleton
class DoseLogRemoteStore @Inject constructor() {

    private val db get() = FirebaseFirestore.getInstance()

    /**
     * Solo documenti modificati (come iOS) + [MetadataChanges.INCLUDE] così quando si risolve
     * `serverTimestamp` / cache non si perdono aggiornamenti; evita di riscrivere tutta la tabella a ogni snapshot.
     */
    fun listenAll(
        familyId: String,
        onChange: (List<RemoteDoseLogDto>, removedIds: List<String>) -> Unit,
    ): ListenerRegistration =
        db.collection("families")
            .document(familyId)
            .collection("doseLogs")
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val removed = mutableListOf<String>()
                val dtos = buildList {
                    for (change in snap.documentChanges) {
                        when (change.type) {
                            DocumentChange.Type.REMOVED -> removed.add(change.document.id)
                            DocumentChange.Type.ADDED, DocumentChange.Type.MODIFIED ->
                                decode(change.document, familyId)?.let(::add)
                        }
                    }
                }
                if (dtos.isNotEmpty() || removed.isNotEmpty()) {
                    onChange(dtos, removed)
                }
            }

    suspend fun upsert(dto: RemoteDoseLogDto) {
        val ref = db.collection("families")
            .document(dto.familyId)
            .collection("doseLogs")
            .document(dto.id)

        fun Long.toTs() = Timestamp(this / 1000, ((this % 1000) * 1_000_000).toInt())

        val takenAtTs = dto.takenAtEpochMillis?.toTs()

        val payload = mapOf(
            "familyId" to dto.familyId,
            "childId" to dto.childId,
            "treatmentId" to dto.treatmentId,
            "dayNumber" to dto.dayNumber,
            "slotIndex" to dto.slotIndex,
            "scheduledTime" to dto.scheduledTime,
            "takenAt" to takenAtTs,
            "taken" to dto.taken,
            "isDeleted" to dto.isDeleted,
            "updatedAt" to Timestamp.now(),
            "updatedBy" to dto.updatedBy,
            "createdAt" to dto.createdAtEpochMillis.toTs(),
        )
        ref.set(payload, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, doseLogId: String, updatedBy: String) {
        db.collection("families")
            .document(familyId)
            .collection("doseLogs")
            .document(doseLogId)
            .update(
                mapOf(
                    "isDeleted" to true,
                    "updatedBy" to updatedBy,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
    }

    fun decode(
        doc: com.google.firebase.firestore.DocumentSnapshot,
        familyId: String,
    ): RemoteDoseLogDto? {
        val data = doc.data ?: return null
        val treatmentId = data["treatmentId"] as? String ?: return null
        val childId = data["childId"] as? String ?: return null

        return RemoteDoseLogDto(
            id = doc.id,
            familyId = familyId,
            childId = childId,
            treatmentId = treatmentId,
            dayNumber = (data["dayNumber"] as? Number)?.toInt() ?: 0,
            slotIndex = (data["slotIndex"] as? Number)?.toInt() ?: 0,
            scheduledTime = data["scheduledTime"] as? String ?: "",
            takenAtEpochMillis = (data["takenAt"] as? Timestamp)?.toDate()?.time,
            taken = data["taken"] as? Boolean ?: false,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
            updatedAtEpochMillis = (data["updatedAt"] as? Timestamp)?.toDate()?.time,
            updatedBy = data["updatedBy"] as? String,
            createdAtEpochMillis = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
        )
    }
}

fun RemoteDoseLogDto.toEntity(): KBDoseLogEntity = KBDoseLogEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    treatmentId = treatmentId,
    dayNumber = dayNumber,
    slotIndex = slotIndex,
    scheduledTime = scheduledTime,
    takenAtEpochMillis = takenAtEpochMillis,
    taken = taken,
    isDeleted = isDeleted,
    syncStatus = 0,
    lastSyncError = null,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis ?: 0L,
    updatedBy = updatedBy,
)
