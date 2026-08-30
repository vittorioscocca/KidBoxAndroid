package it.vittorioscocca.kidbox.data.remote.life

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.HomeItemEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

private fun millisToTimestamp(millis: Long): Timestamp =
    Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())

data class HomeItemRemoteDto(
    val id: String,
    val familyId: String,
    val name: String,
    val categoryRaw: String,
    val brand: String?,
    val model: String?,
    val serialNumber: String?,
    val purchaseDateMillis: Long?,
    val warrantyExpiryDateMillis: Long?,
    val nextServiceDateMillis: Long?,
    val servicePeriodMonths: Int?,
    val notes: String?,
    val reminderEnabled: Boolean,
    val isDeleted: Boolean,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val createdBy: String?,
    val updatedBy: String?,
)

sealed interface HomeItemRemoteChange {
    data class Upsert(val dto: HomeItemRemoteDto) : HomeItemRemoteChange
    data class Remove(val id: String) : HomeItemRemoteChange
}

@Singleton
class HomeItemRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun ref(familyId: String, itemId: String) =
        db.collection("families").document(familyId).collection("homeItems").document(itemId)

    fun listenHomeItems(
        familyId: String,
        onChange: (List<HomeItemRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("homeItems")
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                // Gli upsert vengono dal risultato COMPLETO della query
                // (`snap.documents`), non dal delta (`snap.documentChanges`): con la
                // persistenza locale attiva Firestore può riusare una snapshot in cache e
                // farsela confermare "invariata" dal server con un existence filter, senza
                // inviare alcun document_change. In quel caso il delta è vuoto anche se la
                // query ha risultati reali, e in Room non arrivava più nulla. Le rimozioni
                // restano sul delta, dove sono affidabili.
                val upserts = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val name = (d["name"] as? String)?.trim().orEmpty()
                    if (name.isEmpty()) return@mapNotNull null
                    val categoryRaw = (d["categoryRaw"] as? String) ?: "other"
                    val servicePeriodMonths: Int? = when (val v = d["servicePeriodMonths"]) {
                        is Int -> v
                        is Number -> v.toInt()
                        else -> null
                    }
                    val dto = HomeItemRemoteDto(
                        id = doc.id,
                        familyId = familyId,
                        name = name,
                        categoryRaw = categoryRaw,
                        brand = d["brand"] as? String,
                        model = d["model"] as? String,
                        serialNumber = d["serialNumber"] as? String,
                        purchaseDateMillis = (d["purchaseDate"] as? Timestamp)?.toDate()?.time,
                        warrantyExpiryDateMillis = (d["warrantyExpiryDate"] as? Timestamp)?.toDate()?.time,
                        nextServiceDateMillis = (d["nextServiceDate"] as? Timestamp)?.toDate()?.time,
                        servicePeriodMonths = servicePeriodMonths,
                        notes = d["notes"] as? String,
                        reminderEnabled = d["reminderEnabled"] as? Boolean ?: false,
                        isDeleted = d["isDeleted"] as? Boolean ?: false,
                        createdAtMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                        updatedAtMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                        createdBy = d["createdBy"] as? String,
                        updatedBy = d["updatedBy"] as? String,
                    )
                    HomeItemRemoteChange.Upsert(dto)
                }
                val removes = snap.documentChanges
                    .filter { it.type == DocumentChange.Type.REMOVED }
                    .map { HomeItemRemoteChange.Remove(it.document.id) }
                val changes = upserts + removes
                if (changes.isNotEmpty()) onChange(changes)
            }

    suspend fun upsertHomeItemToFirestore(entity: HomeItemEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val snap = ref(entity.familyId, entity.id).get().await()
        val isNew = !snap.exists()
        val data = mutableMapOf<String, Any?>(
            "name" to entity.name,
            "categoryRaw" to entity.category,
            "isDeleted" to false,
            "reminderEnabled" to entity.reminderEnabled,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) data["createdAt"] = FieldValue.serverTimestamp()
        data["brand"] = entity.brand
        data["model"] = entity.model
        data["serialNumber"] = entity.serialNumber
        data["purchaseDate"] = entity.purchaseDate?.let { millisToTimestamp(it) }
        data["warrantyExpiryDate"] = entity.warrantyExpiryDate?.let { millisToTimestamp(it) }
        data["nextServiceDate"] = entity.nextServiceDate?.let { millisToTimestamp(it) }
        data["servicePeriodMonths"] = entity.servicePeriodMonths
        data["notes"] = entity.notes
        if (isNew) data["createdBy"] = entity.createdBy.ifEmpty { uid }
        ref(entity.familyId, entity.id).set(data, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, itemId: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        ref(familyId, itemId).set(
            mapOf(
                "isDeleted" to true,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }
}
