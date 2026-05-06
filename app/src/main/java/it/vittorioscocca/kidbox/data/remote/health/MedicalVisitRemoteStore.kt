package it.vittorioscocca.kidbox.data.remote.health

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import kotlinx.coroutines.tasks.await

data class RemoteMedicalVisitDto(
    val id: String,
    val familyId: String,
    val childId: String,
    val dateEpochMillis: Long,
    val doctorName: String?,
    val doctorSpecializationRaw: String?,
    val reason: String,
    val diagnosis: String?,
    val recommendations: String?,
    val notes: String?,
    val visitStatusRaw: String?,
    val nextVisitDateEpochMillis: Long?,
    val nextVisitReason: String?,
    val reminderOn: Boolean,
    val nextVisitReminderOn: Boolean,
    val linkedTreatmentIdsJson: String,
    val linkedExamIdsJson: String,
    val asNeededDrugsJson: String,
    val therapyTypesJson: String,
    val photoUrlsJson: String,
    val isDeleted: Boolean,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
    val createdAtEpochMillis: Long,
    val createdBy: String?,
)

/**
 * Firestore remote store for medical visits.
 * Document path: `families/{familyId}/medicalVisits/{visitId}`.
 * Mirrors iOS `SyncCenter+Visits`.
 */
@Singleton
class MedicalVisitRemoteStore @Inject constructor() {

    private val db get() = FirebaseFirestore.getInstance()

    /**
     * Listen to all (non-deleted) visits for a family. The callback is fired once per
     * snapshot with the full list of changed/added documents.
     */
    fun listenByFamily(
        familyId: String,
        onChange: (List<RemoteMedicalVisitDto>) -> Unit,
    ): ListenerRegistration {
        return db.collection("families")
            .document(familyId)
            .collection("medicalVisits")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val dtos = snap.documents.mapNotNull { doc ->
                    val data = doc.data ?: return@mapNotNull null
                    val linkedTreatmentIdsJson = mergedStringListJsonField(
                        data = data,
                        jsonKey = "linkedTreatmentIdsJson",
                        arrayKey = "linkedTreatmentIds",
                    )
                    val linkedExamIdsJson = mergedStringListJsonField(
                        data = data,
                        jsonKey = "linkedExamIdsJson",
                        arrayKey = "linkedExamIds",
                    )
                    val therapyTypesJson = mergedStringListJsonField(
                        data = data,
                        jsonKey = "therapyTypesJson",
                        arrayKey = "therapyTypesRaw",
                    )
                    val photoUrlsJson = mergedStringListJsonField(
                        data = data,
                        jsonKey = "photoUrlsJson",
                        arrayKey = "photoURLs",
                    )
                    RemoteMedicalVisitDto(
                        id = doc.id,
                        familyId = data["familyId"] as? String ?: familyId,
                        childId = data["childId"] as? String ?: "",
                        dateEpochMillis = (data["date"] as? Timestamp)?.toDate()?.time
                            ?: (data["dateEpochMillis"] as? Long ?: 0L),
                        doctorName = data["doctorName"] as? String,
                        doctorSpecializationRaw = (data["doctorSpecialization"] as? String)
                            ?: (data["doctorSpecializationRaw"] as? String),
                        reason = data["reason"] as? String ?: "",
                        diagnosis = data["diagnosis"] as? String,
                        recommendations = data["recommendations"] as? String,
                        notes = data["notes"] as? String,
                        visitStatusRaw = data["visitStatus"] as? String,
                        nextVisitDateEpochMillis = (data["nextVisitDate"] as? Timestamp)
                            ?.toDate()?.time,
                        nextVisitReason = data["nextVisitReason"] as? String,
                        reminderOn = data["reminderOn"] as? Boolean ?: false,
                        nextVisitReminderOn = data["nextVisitReminderOn"] as? Boolean ?: false,
                        linkedTreatmentIdsJson = linkedTreatmentIdsJson,
                        linkedExamIdsJson = linkedExamIdsJson,
                        asNeededDrugsJson = data["asNeededDrugsJson"] as? String ?: "[]",
                        therapyTypesJson = therapyTypesJson,
                        photoUrlsJson = photoUrlsJson,
                        isDeleted = data["isDeleted"] as? Boolean ?: false,
                        updatedAtEpochMillis = (data["updatedAt"] as? Timestamp)?.toDate()?.time,
                        updatedBy = data["updatedBy"] as? String,
                        createdAtEpochMillis = (data["createdAt"] as? Timestamp)?.toDate()?.time
                            ?: 0L,
                        createdBy = data["createdBy"] as? String,
                    )
                }
                onChange(dtos)
            }
    }

    suspend fun upsert(dto: RemoteMedicalVisitDto) {
        val ref = db.collection("families")
            .document(dto.familyId)
            .collection("medicalVisits")
            .document(dto.id)

        val dateTs = if (dto.dateEpochMillis > 0) {
            Timestamp(dto.dateEpochMillis / 1000, ((dto.dateEpochMillis % 1000) * 1_000_000).toInt())
        } else null
        val nextVisitTs = dto.nextVisitDateEpochMillis?.let { ms ->
            Timestamp(ms / 1000, ((ms % 1000) * 1_000_000).toInt())
        }

        val payload = mapOf(
            "familyId" to dto.familyId,
            "childId" to dto.childId,
            "date" to dateTs,
            "doctorName" to dto.doctorName,
            "doctorSpecialization" to dto.doctorSpecializationRaw,
            "reason" to dto.reason,
            "diagnosis" to dto.diagnosis,
            "recommendations" to dto.recommendations,
            "notes" to dto.notes,
            "visitStatus" to dto.visitStatusRaw,
            "nextVisitDate" to nextVisitTs,
            "nextVisitReason" to dto.nextVisitReason,
            "reminderOn" to dto.reminderOn,
            "nextVisitReminderOn" to dto.nextVisitReminderOn,
            "linkedTreatmentIdsJson" to dto.linkedTreatmentIdsJson,
            "linkedExamIdsJson" to dto.linkedExamIdsJson,
            "asNeededDrugsJson" to dto.asNeededDrugsJson,
            "therapyTypesJson" to dto.therapyTypesJson,
            "photoUrlsJson" to dto.photoUrlsJson,
            "isDeleted" to dto.isDeleted,
            "updatedAt" to Timestamp.now(),
            "updatedBy" to dto.updatedBy,
        )
        ref.set(payload, SetOptions.merge()).await()
    }
}

/**
 * Preferisce gli array nativi Firestore (scritti da iOS); altrimenti usa la stringa JSON (Android).
 */
private fun mergedStringListJsonField(
    data: Map<String, Any>,
    jsonKey: String,
    arrayKey: String,
): String {
    val fromArray = (data[arrayKey] as? List<*>)
        ?.mapNotNull { it as? String }
        .orEmpty()
    if (fromArray.isNotEmpty()) {
        return encodeStringList(fromArray)
    }
    val jsonPart = data[jsonKey] as? String
    if (!jsonPart.isNullOrBlank() && decodeStringList(jsonPart).isNotEmpty()) {
        return jsonPart
    }
    return jsonPart?.takeIf { it.isNotBlank() } ?: "[]"
}
