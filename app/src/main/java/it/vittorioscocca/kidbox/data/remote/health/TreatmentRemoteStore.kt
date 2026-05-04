package it.vittorioscocca.kidbox.data.remote.health

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteTreatmentDto(
    val id: String,
    val familyId: String,
    val childId: String,
    val drugName: String,
    val activeIngredient: String?,
    val dosageValue: Double,
    val dosageUnit: String,
    val isLongTerm: Boolean,
    val durationDays: Int,
    val startDateEpochMillis: Long,
    val endDateEpochMillis: Long?,
    val dailyFrequency: Int,
    val scheduleTimes: List<String>,
    val isActive: Boolean,
    val notes: String?,
    val reminderEnabled: Boolean,
    val isDeleted: Boolean,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
    val createdAtEpochMillis: Long,
    val createdBy: String?,
)

sealed class TreatmentRemoteChange {
    data class Upsert(val dto: RemoteTreatmentDto) : TreatmentRemoteChange()
    data class Remove(val treatmentId: String) : TreatmentRemoteChange()
}

@Singleton
class TreatmentRemoteStore @Inject constructor() {

    private val db get() = FirebaseFirestore.getInstance()

    fun listenAll(
        familyId: String,
        onChange: (List<RemoteTreatmentDto>) -> Unit,
    ): ListenerRegistration =
        db.collection("families")
            .document(familyId)
            .collection("treatments")
            .addSnapshotListener { snap, _ ->
                if (snap == null) return@addSnapshotListener
                val dtos = snap.documents.mapNotNull { decode(it, familyId) }
                onChange(dtos)
            }

    /** Promemoria in remoto solo per cure del bambino ([syncReminderEnabledToRemote] = true). Per adulti il campo viene rimosso da Firestore. */
    suspend fun upsert(dto: RemoteTreatmentDto, syncReminderEnabledToRemote: Boolean) {
        val ref = db.collection("families")
            .document(dto.familyId)
            .collection("treatments")
            .document(dto.id)

        fun Long.toTs() = Timestamp(this / 1000, ((this % 1000) * 1_000_000).toInt())

        val payload = mutableMapOf<String, Any>(
            "familyId" to dto.familyId,
            "childId" to dto.childId,
            "drugName" to dto.drugName,
            "dosageValue" to dto.dosageValue,
            "dosageUnit" to dto.dosageUnit,
            "isLongTerm" to dto.isLongTerm,
            "durationDays" to dto.durationDays,
            "startDate" to dto.startDateEpochMillis.toTs(),
            "dailyFrequency" to dto.dailyFrequency,
            "scheduleTimes" to dto.scheduleTimes,
            "isActive" to dto.isActive,
            "isDeleted" to dto.isDeleted,
            "updatedAt" to Timestamp.now(),
            "updatedBy" to (dto.updatedBy ?: "local"),
            "createdAt" to dto.createdAtEpochMillis.toTs(),
        )
        dto.activeIngredient?.let { payload["activeIngredient"] = it }
        dto.endDateEpochMillis?.let { payload["endDate"] = it.toTs() }
        dto.notes?.let { payload["notes"] = it }
        dto.createdBy?.let { payload["createdBy"] = it }
        if (syncReminderEnabledToRemote) {
            payload["reminderEnabled"] = dto.reminderEnabled
        } else {
            payload["reminderEnabled"] = FieldValue.delete()
        }
        ref.set(payload, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, treatmentId: String, updatedBy: String) {
        db.collection("families")
            .document(familyId)
            .collection("treatments")
            .document(treatmentId)
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
    ): RemoteTreatmentDto? {
        val data = doc.data ?: return null
        val childId = data["childId"] as? String ?: return null
        val drugName = data["drugName"] as? String ?: return null
        val startDate = data["startDate"] as? Timestamp ?: return null

        @Suppress("UNCHECKED_CAST")
        val scheduleTimes = (data["scheduleTimes"] as? List<*>)?.filterIsInstance<String>() ?: emptyList()

        return RemoteTreatmentDto(
            id = doc.id,
            familyId = familyId,
            childId = childId,
            drugName = drugName,
            activeIngredient = data["activeIngredient"] as? String,
            dosageValue = (data["dosageValue"] as? Number)?.toDouble() ?: 0.0,
            dosageUnit = data["dosageUnit"] as? String ?: "ml",
            isLongTerm = data["isLongTerm"] as? Boolean ?: false,
            durationDays = (data["durationDays"] as? Number)?.toInt() ?: 1,
            startDateEpochMillis = startDate.toDate().time,
            endDateEpochMillis = (data["endDate"] as? Timestamp)?.toDate()?.time,
            dailyFrequency = (data["dailyFrequency"] as? Number)?.toInt() ?: 1,
            scheduleTimes = scheduleTimes,
            isActive = data["isActive"] as? Boolean ?: true,
            notes = data["notes"] as? String,
            reminderEnabled = data["reminderEnabled"] as? Boolean ?: false,
            isDeleted = data["isDeleted"] as? Boolean ?: false,
            updatedAtEpochMillis = (data["updatedAt"] as? Timestamp)?.toDate()?.time,
            updatedBy = data["updatedBy"] as? String,
            createdAtEpochMillis = (data["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L,
            createdBy = data["createdBy"] as? String,
        )
    }
}

fun RemoteTreatmentDto.toEntity(): KBTreatmentEntity = KBTreatmentEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    drugName = drugName,
    activeIngredient = activeIngredient,
    dosageValue = dosageValue,
    dosageUnit = dosageUnit,
    isLongTerm = isLongTerm,
    durationDays = durationDays,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    dailyFrequency = dailyFrequency,
    scheduleTimesData = scheduleTimes.joinToString(","),
    isActive = isActive,
    notes = notes,
    reminderEnabled = reminderEnabled,
    isDeleted = isDeleted,
    syncStateRaw = 0,
    lastSyncError = null,
    syncStatus = 0,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis ?: 0L,
    updatedBy = updatedBy,
    createdBy = createdBy,
)
