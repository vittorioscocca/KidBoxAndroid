package it.vittorioscocca.kidbox.data.remote.life

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

private fun millisToTimestamp(millis: Long): Timestamp =
    Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())

data class PetRemoteDto(
    val id: String,
    val familyId: String,
    val name: String,
    val species: String,
    val breed: String?,
    val birthDateMillis: Long?,
    val color: String?,
    val chipCode: String?,
    val notes: String?,
    val photoURL: String?,
    val isDeleted: Boolean,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val createdBy: String?,
    val updatedBy: String?,
)

sealed interface PetRemoteChange {
    data class Upsert(val dto: PetRemoteDto) : PetRemoteChange
    data class Remove(val id: String) : PetRemoteChange
}

@Singleton
class PetRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun ref(familyId: String, petId: String) =
        db.collection("families").document(familyId).collection("pets").document(petId)

    fun listenPets(
        familyId: String,
        onChange: (List<PetRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("pets")
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                val changes = snap.documentChanges.mapNotNull { diff ->
                    val doc = diff.document
                    val d = doc.data ?: return@mapNotNull null
                    val name = (d["name"] as? String)?.trim().orEmpty()
                    if (name.isEmpty()) return@mapNotNull null
                    val species = (d["species"] as? String)?.trim().orEmpty()
                    if (species.isEmpty()) return@mapNotNull null
                    val dto = PetRemoteDto(
                        id = doc.id,
                        familyId = familyId,
                        name = name,
                        species = species,
                        breed = d["breed"] as? String,
                        birthDateMillis = (d["birthDate"] as? Timestamp)?.toDate()?.time,
                        color = d["color"] as? String,
                        chipCode = d["chipCode"] as? String,
                        notes = d["notes"] as? String,
                        photoURL = d["photoURL"] as? String,
                        isDeleted = d["isDeleted"] as? Boolean ?: false,
                        createdAtMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                        updatedAtMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                        createdBy = d["createdBy"] as? String,
                        updatedBy = d["updatedBy"] as? String,
                    )
                    when (diff.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED,
                        -> PetRemoteChange.Upsert(dto)
                        DocumentChange.Type.REMOVED -> PetRemoteChange.Remove(doc.id)
                    }
                }
                if (changes.isNotEmpty()) onChange(changes)
            }

    suspend fun upsertPetToFirestore(entity: PetEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val snap = ref(entity.familyId, entity.id).get().await()
        val isNew = !snap.exists()
        val data = mutableMapOf<String, Any?>(
            "name" to entity.name,
            "species" to entity.species,
            "isDeleted" to false,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) data["createdAt"] = FieldValue.serverTimestamp()
        data["breed"] = entity.breed
        data["birthDate"] = entity.birthDate?.let { millisToTimestamp(it) }
        data["color"] = entity.color
        data["chipCode"] = entity.chipCode
        data["notes"] = entity.notes
        data["photoURL"] = entity.photoURL
        if (isNew) {
            data["createdBy"] = entity.createdBy.ifEmpty { uid }
        }
        ref(entity.familyId, entity.id).set(data, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, petId: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        ref(familyId, petId).set(
            mapOf(
                "isDeleted" to true,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }
}
