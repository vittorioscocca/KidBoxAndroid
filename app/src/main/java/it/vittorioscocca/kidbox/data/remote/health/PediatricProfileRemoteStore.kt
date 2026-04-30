package it.vittorioscocca.kidbox.data.remote.health

import com.google.firebase.Timestamp
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemotePediatricProfileDto(
    val id: String,
    val familyId: String,
    val childId: String,
    val bloodGroup: String?,
    val allergies: String?,
    val medicalNotes: String?,
    val doctorName: String?,
    val doctorPhone: String?,
    val emergencyContactsJson: String?,
    val isDeleted: Boolean,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
)

/**
 * Firestore remote store for pediatric profiles.
 * Document path: `families/{familyId}/pediatricProfiles/{childId}`.
 * Mirrors iOS `PediatricProfileRemoteStore`.
 */
@Singleton
class PediatricProfileRemoteStore @Inject constructor() {

    private val db get() = FirebaseFirestore.getInstance()

    fun listen(
        familyId: String,
        childId: String,
        onChange: (RemotePediatricProfileDto?) -> Unit,
    ): ListenerRegistration {
        return db.collection("families")
            .document(familyId)
            .collection("pediatricProfiles")
            .document(childId)
            .addSnapshotListener { snap, _ ->
                if (snap == null || !snap.exists()) {
                    onChange(null)
                    return@addSnapshotListener
                }
                val data = snap.data ?: return@addSnapshotListener
                onChange(
                    RemotePediatricProfileDto(
                        id = snap.id,
                        familyId = data["familyId"] as? String ?: familyId,
                        childId = data["childId"] as? String ?: childId,
                        bloodGroup = data["bloodGroup"] as? String,
                        allergies = data["allergies"] as? String,
                        medicalNotes = data["medicalNotes"] as? String,
                        doctorName = data["doctorName"] as? String,
                        doctorPhone = data["doctorPhone"] as? String,
                        emergencyContactsJson = data["emergencyContactsJSON"] as? String,
                        isDeleted = data["isDeleted"] as? Boolean ?: false,
                        updatedAtEpochMillis = (data["updatedAt"] as? Timestamp)?.toDate()?.time,
                        updatedBy = data["updatedBy"] as? String,
                    )
                )
            }
    }

    suspend fun upsert(dto: RemotePediatricProfileDto) {
        val ref = db.collection("families")
            .document(dto.familyId)
            .collection("pediatricProfiles")
            .document(dto.childId)

        val payload = mapOf(
            "familyId" to dto.familyId,
            "childId" to dto.childId,
            "bloodGroup" to dto.bloodGroup,
            "allergies" to dto.allergies,
            "medicalNotes" to dto.medicalNotes,
            "doctorName" to dto.doctorName,
            "doctorPhone" to dto.doctorPhone,
            "emergencyContactsJSON" to dto.emergencyContactsJson,
            "isDeleted" to dto.isDeleted,
            "updatedBy" to dto.updatedBy,
            "updatedAt" to Timestamp.now(),
        )
        ref.set(payload, SetOptions.merge()).await()
    }
}
