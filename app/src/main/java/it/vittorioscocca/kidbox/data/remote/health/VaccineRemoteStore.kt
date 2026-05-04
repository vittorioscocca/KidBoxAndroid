package it.vittorioscocca.kidbox.data.remote.health

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineType
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteVaccineDto(
    val id: String,
    val familyId: String,
    val childId: String,
    /** Campo legacy Android; iOS usa tipo + nome commerciale. */
    val name: String,
    val vaccineTypeRaw: String,
    val statusRaw: String,
    val commercialName: String?,
    val doseNumber: Int,
    val totalDoses: Int,
    val scheduledDateEpochMillis: Long?,
    val administeredDateEpochMillis: Long?,
    val doctorName: String?,
    val location: String?,
    val lotNumber: String?,
    val administeredBy: String?,
    val administrationSiteRaw: String?,
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

        val payload = mutableMapOf<String, Any>(
            "familyId" to dto.familyId,
            "childId" to dto.childId,
            "name" to dto.name,
            "vaccineTypeRaw" to dto.vaccineTypeRaw,
            "statusRaw" to dto.statusRaw,
            "doseNumber" to dto.doseNumber,
            "totalDoses" to dto.totalDoses,
            "isDeleted" to dto.isDeleted,
            "reminderOn" to dto.reminderOn,
            "updatedAt" to Timestamp.now(),
            "createdAt" to dto.createdAtEpochMillis.toTs(),
        )
        dto.updatedBy?.let { payload["updatedBy"] = it }
            ?: run { payload["updatedBy"] = FieldValue.delete() }
        dto.createdBy?.let { payload["createdBy"] = it } ?: run { payload["createdBy"] = FieldValue.delete() }
        dto.commercialName?.takeIf { it.isNotBlank() }?.let { payload["commercialName"] = it }
            ?: run { payload["commercialName"] = FieldValue.delete() }
        dto.lotNumber?.takeIf { it.isNotBlank() }?.let { payload["lotNumber"] = it }
            ?: run { payload["lotNumber"] = FieldValue.delete() }
        dto.administeredBy?.takeIf { it.isNotBlank() }?.let { payload["administeredBy"] = it }
            ?: run { payload["administeredBy"] = FieldValue.delete() }
        dto.administrationSiteRaw?.takeIf { it.isNotBlank() }?.let { payload["administrationSiteRaw"] = it }
            ?: run { payload["administrationSiteRaw"] = FieldValue.delete() }
        dto.notes?.takeIf { it.isNotBlank() }?.let { payload["notes"] = it }
            ?: run { payload["notes"] = FieldValue.delete() }
        dto.doctorName?.takeIf { it.isNotBlank() }?.let { payload["doctorName"] = it }
            ?: run { payload["doctorName"] = FieldValue.delete() }
        dto.location?.takeIf { it.isNotBlank() }?.let { payload["location"] = it }
            ?: run { payload["location"] = FieldValue.delete() }

        if (dto.scheduledDateEpochMillis != null) {
            payload["scheduledDate"] = dto.scheduledDateEpochMillis.toTs()
        } else {
            payload["scheduledDate"] = FieldValue.delete()
        }
        if (dto.administeredDateEpochMillis != null) {
            payload["administeredDate"] = dto.administeredDateEpochMillis.toTs()
        } else {
            payload["administeredDate"] = FieldValue.delete()
        }
        if (dto.nextDoseDateEpochMillis != null) {
            payload["nextDoseDate"] = dto.nextDoseDateEpochMillis.toTs()
        } else {
            payload["nextDoseDate"] = FieldValue.delete()
        }

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
        val childId = data["childId"] as? String ?: return null
        val vaccineTypeRaw = data["vaccineTypeRaw"] as? String ?: "altro"
        val commercialName = data["commercialName"] as? String
        val nameFromDoc = data["name"] as? String
        val name = nameFromDoc?.takeIf { it.isNotBlank() }
            ?: decodeDisplayName(vaccineTypeRaw, commercialName)

        return RemoteVaccineDto(
            id = doc.id,
            familyId = familyId,
            childId = childId,
            name = name,
            vaccineTypeRaw = vaccineTypeRaw,
            statusRaw = data["statusRaw"] as? String ?: "planned",
            commercialName = commercialName,
            doseNumber = (data["doseNumber"] as? Number)?.toInt() ?: 1,
            totalDoses = (data["totalDoses"] as? Number)?.toInt() ?: 1,
            scheduledDateEpochMillis = (data["scheduledDate"] as? Timestamp)?.toDate()?.time,
            administeredDateEpochMillis = (data["administeredDate"] as? Timestamp)?.toDate()?.time,
            doctorName = data["doctorName"] as? String,
            location = data["location"] as? String,
            lotNumber = data["lotNumber"] as? String,
            administeredBy = data["administeredBy"] as? String,
            administrationSiteRaw = data["administrationSiteRaw"] as? String,
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

private fun decodeDisplayName(vaccineTypeRaw: String, commercialName: String?): String {
    val c = commercialName?.trim().orEmpty()
    if (c.isNotEmpty()) return c
    return KBVaccineType.fromRaw(vaccineTypeRaw).displayName
}
