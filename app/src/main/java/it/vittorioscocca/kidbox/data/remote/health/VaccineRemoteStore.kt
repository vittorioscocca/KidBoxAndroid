package it.vittorioscocca.kidbox.data.remote.health

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteVaccineDto(
    val id: String,
    val familyId: String,
    val childId: String,
    val name: String,
    val vaccineTypeRaw: String,
    val statusRaw: String,
    val scheduledDateEpochMillis: Long?,
    val administeredDateEpochMillis: Long?,
    val doctorName: String?,
    val location: String?,
    val lotNumber: String?,
    val notes: String?,
    val reminderOn: Boolean,
    val nextDoseDateEpochMillis: Long?,
    val isDeleted: Boolean,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
    val createdAtEpochMillis: Long,
    val createdBy: String?,
)

sealed class VaccineRemoteChange {
    data class Upsert(val dto: RemoteVaccineDto) : VaccineRemoteChange()
    data class Remove(val vaccineId: String) : VaccineRemoteChange()
}

@Singleton
class VaccineRemoteStore @Inject constructor() {

    private val db get() = FirebaseFirestore.getInstance()

    fun listenAll(
        familyId: String,
        onChange: (List<RemoteVaccineDto>) -> Unit,
    ): ListenerRegistration =
        db.collection("families")
            .document(familyId)
            .collection("vaccines")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val dtos = snap.documents.mapNotNull { decode(it, familyId) }
                onChange(dtos)
            }

    suspend fun upsert(dto: RemoteVaccineDto) {
        val ref = db.collection("families")
            .document(dto.familyId)
            .collection("vaccines")
            .document(dto.id)

        fun Long.toTs() = Timestamp(this / 1000, ((this % 1000) * 1_000_000).toInt())

        val payload = mapOf(
            "familyId" to dto.familyId,
            "childId" to dto.childId,
            "name" to dto.name,
            "vaccineTypeRaw" to dto.vaccineTypeRaw,
            "statusRaw" to dto.statusRaw,
            "scheduledDate" to dto.scheduledDateEpochMillis?.toTs(),
            "administeredDate" to dto.administeredDateEpochMillis?.toTs(),
            "doctorName" to dto.doctorName,
            "location" to dto.location,
            "lotNumber" to dto.lotNumber,
            "notes" to dto.notes,
            "reminderOn" to dto.reminderOn,
            "nextDoseDate" to dto.nextDoseDateEpochMillis?.toTs(),
            "isDeleted" to dto.isDeleted,
            "updatedAt" to Timestamp.now(),
            "updatedBy" to dto.updatedBy,
            "createdAt" to dto.createdAtEpochMillis.toTs(),
            "createdBy" to dto.createdBy,
        )
        ref.set(payload, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, vaccineId: String, updatedBy: String) {
        db.collection("families")
            .document(familyId)
            .collection("vaccines")
            .document(vaccineId)
            .update(
                mapOf(
                    "isDeleted" to true,
                    "updatedBy" to updatedBy,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
    }

    fun decode(doc: DocumentSnapshot, familyId: String): RemoteVaccineDto? {
        val data = doc.data ?: return null
        val name = data["name"] as? String ?: return null
        val childId = data["childId"] as? String ?: return null
        return RemoteVaccineDto(
            id = doc.id,
            familyId = familyId,
            childId = childId,
            name = name,
            vaccineTypeRaw = data["vaccineTypeRaw"] as? String ?: "Obbligatorio",
            statusRaw = data["statusRaw"] as? String ?: "Programmato",
            scheduledDateEpochMillis = (data["scheduledDate"] as? Timestamp)?.toDate()?.time,
            administeredDateEpochMillis = (data["administeredDate"] as? Timestamp)?.toDate()?.time,
            doctorName = data["doctorName"] as? String,
            location = data["location"] as? String,
            lotNumber = data["lotNumber"] as? String,
            notes = data["notes"] as? String,
            reminderOn = data["reminderOn"] as? Boolean ?: false,
            nextDoseDateEpochMillis = (data["nextDoseDate"] as? Timestamp)?.toDate()?.time,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
            updatedAtEpochMillis = (data["updatedAt"] as? Timestamp)?.toDate()?.time,
            updatedBy = data["updatedBy"] as? String,
            createdAtEpochMillis = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
            createdBy = data["createdBy"] as? String,
        )
    }
}
