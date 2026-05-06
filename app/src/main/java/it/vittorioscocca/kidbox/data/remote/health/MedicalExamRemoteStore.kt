package it.vittorioscocca.kidbox.data.remote.health

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteExamDto(
    val id: String,
    val familyId: String,
    val childId: String,
    val name: String,
    val isUrgent: Boolean,
    val deadlineEpochMillis: Long?,
    val preparation: String?,
    val notes: String?,
    val location: String?,
    val statusRaw: String,
    val resultText: String?,
    val resultDateEpochMillis: Long?,
    val prescribingVisitId: String?,
    val reminderOn: Boolean,
    val isDeleted: Boolean,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
    val createdAtEpochMillis: Long,
    val createdBy: String?,
)

sealed class ExamRemoteChange {
    data class Upsert(val dto: RemoteExamDto) : ExamRemoteChange()
    data class Remove(val examId: String) : ExamRemoteChange()
}

@Singleton
class MedicalExamRemoteStore @Inject constructor() {

    private val db get() = FirebaseFirestore.getInstance()

    fun listenAll(
        familyId: String,
        onChange: (List<RemoteExamDto>) -> Unit,
    ): ListenerRegistration =
        db.collection("families")
            .document(familyId)
            .collection("medicalExams")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val dtos = snap.documents.mapNotNull { decode(it, familyId) }
                onChange(dtos)
            }

    suspend fun upsert(dto: RemoteExamDto) {
        val ref = db.collection("families")
            .document(dto.familyId)
            .collection("medicalExams")
            .document(dto.id)

        val deadlineTs = dto.deadlineEpochMillis?.let { ms ->
            Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
        }
        val resultDateTs = dto.resultDateEpochMillis?.let { ms ->
            Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
        }

        val payload = mapOf(
            // Obbligatorio per iOS: parseDTO richiede d["id"] (oltre a documentId).
            "id" to dto.id,
            "familyId" to dto.familyId,
            "childId" to dto.childId,
            "name" to dto.name,
            "isUrgent" to dto.isUrgent,
            "deadline" to deadlineTs,
            "preparation" to dto.preparation,
            "notes" to dto.notes,
            "location" to dto.location,
            "statusRaw" to dto.statusRaw,
            "resultText" to dto.resultText,
            "resultDate" to resultDateTs,
            "prescribingVisitId" to dto.prescribingVisitId,
            "reminderOn" to dto.reminderOn,
            "isDeleted" to dto.isDeleted,
            "updatedAt" to Timestamp.now(),
            "updatedBy" to dto.updatedBy,
            "createdAt" to (dto.createdAtEpochMillis.let { ms ->
                Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
            }),
            "createdBy" to dto.createdBy,
        )
        ref.set(payload, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, examId: String, updatedBy: String) {
        db.collection("families")
            .document(familyId)
            .collection("medicalExams")
            .document(examId)
            .update(
                mapOf(
                    "isDeleted" to true,
                    "updatedBy" to updatedBy,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
    }

    fun decode(doc: com.google.firebase.firestore.DocumentSnapshot, familyId: String): RemoteExamDto? {
        val data = doc.data ?: return null
        val name = data["name"] as? String ?: return null
        val childId = data["childId"] as? String ?: return null
        return RemoteExamDto(
            id = doc.id,
            familyId = familyId,
            childId = childId,
            name = name,
            isUrgent = data["isUrgent"] as? Boolean ?: false,
            deadlineEpochMillis = (data["deadline"] as? Timestamp)?.toDate()?.time,
            preparation = data["preparation"] as? String,
            notes = data["notes"] as? String,
            location = data["location"] as? String,
            statusRaw = data["statusRaw"] as? String ?: "In attesa",
            resultText = data["resultText"] as? String,
            resultDateEpochMillis = (data["resultDate"] as? Timestamp)?.toDate()?.time,
            prescribingVisitId = data["prescribingVisitId"] as? String,
            reminderOn = data["reminderOn"] as? Boolean ?: false,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
            updatedAtEpochMillis = (data["updatedAt"] as? Timestamp)?.toDate()?.time,
            updatedBy = data["updatedBy"] as? String,
            createdAtEpochMillis = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
            createdBy = data["createdBy"] as? String,
        )
    }
}

fun RemoteExamDto.toEntity(): KBMedicalExamEntity = KBMedicalExamEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    name = name,
    isUrgent = isUrgent,
    deadlineEpochMillis = deadlineEpochMillis,
    preparation = preparation,
    notes = notes,
    location = location,
    statusRaw = statusRaw,
    resultText = resultText,
    resultDateEpochMillis = resultDateEpochMillis,
    prescribingVisitId = prescribingVisitId,
    reminderOn = reminderOn,
    isDeleted = isDeleted,
    syncStateRaw = 0,
    lastSyncError = null,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis ?: 0L,
    updatedBy = updatedBy ?: "",
    createdBy = createdBy ?: "",
)
