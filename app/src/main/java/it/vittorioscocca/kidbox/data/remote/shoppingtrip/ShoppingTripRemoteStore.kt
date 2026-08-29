package it.vittorioscocca.kidbox.data.remote.shoppingtrip

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBShoppingTripEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class ShoppingTripRemoteDto(
    val id: String,
    val familyId: String,
    val storeName: String?,
    val total: Double,
    val dateEpochMillis: Long,
    val linesJson: String?,
    val notes: String?,
    val linkedExpenseId: String?,
    val isDeleted: Boolean,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
    val createdBy: String?,
)

sealed interface ShoppingTripRemoteChange {
    data class Upsert(val dto: ShoppingTripRemoteDto) : ShoppingTripRemoteChange
    data class Remove(val id: String) : ShoppingTripRemoteChange
}

/**
 * Le spese fatte su Firestore: `families/{familyId}/shoppingTrips/{id}`.
 * Stessa forma del negozio remoto della lista spesa, da cui questi record nascono.
 */
@Singleton
class ShoppingTripRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun col(familyId: String) = db.collection("families")
        .document(familyId)
        .collection("shoppingTrips")

    private fun ref(familyId: String, tripId: String) = col(familyId).document(tripId)

    /**
     * @param onChange riceve le modifiche e, quando la snapshot arriva dal server,
     *   l'insieme completo degli id remoti — serve al chiamante per riconciliare
     *   le righe locali sparite altrove.
     */
    fun listenShoppingTrips(
        familyId: String,
        onChange: (List<ShoppingTripRemoteChange>, Set<String>?) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        return col(familyId)
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                // Upsert dal risultato COMPLETO e non dal delta: con la persistenza
                // locale una query può risolversi da cache e farsi confermare
                // "invariata" dal server, lasciando `documentChanges` vuoto pur
                // avendo risultati reali. Stesso motivo di GroceryRemoteStore.
                val upserts = snap.documents.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    ShoppingTripRemoteChange.Upsert(
                        ShoppingTripRemoteDto(
                            id = doc.id,
                            familyId = familyId,
                            storeName = (d["storeName"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                            total = (d["total"] as? Number)?.toDouble() ?: 0.0,
                            dateEpochMillis = (d["date"] as? Timestamp)?.toDate()?.time
                                ?: System.currentTimeMillis(),
                            linesJson = d["linesJson"] as? String,
                            notes = (d["notes"] as? String)?.takeIf { it.isNotBlank() },
                            linkedExpenseId = d["linkedExpenseId"] as? String,
                            isDeleted = d["isDeleted"] as? Boolean ?: false,
                            updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                            updatedBy = d["updatedBy"] as? String,
                            createdBy = d["createdBy"] as? String,
                        ),
                    )
                }
                val removes = snap.documentChanges
                    .filter { it.type == DocumentChange.Type.REMOVED }
                    .map { ShoppingTripRemoteChange.Remove(it.document.id) }
                val changes = upserts + removes
                // `null` da cache: non si riconcilia su una snapshot che potrebbe
                // essere parziale, si cancellerebbero righe ancora valide.
                val snapshotIds =
                    if (snap.metadata.isFromCache) null
                    else snap.documents.map { it.id }.toSet()
                if (changes.isNotEmpty() || snapshotIds != null) {
                    onChange(changes, snapshotIds)
                }
            }
    }

    suspend fun upsert(trip: KBShoppingTripEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val payload = mutableMapOf<String, Any?>(
            "storeName" to trip.storeName,
            "total" to trip.total,
            "date" to trip.dateEpochMillis.toTimestamp(),
            "linesJson" to trip.linesJson,
            "notes" to trip.notes,
            "linkedExpenseId" to trip.linkedExpenseId,
            "isDeleted" to false,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (trip.createdBy != null) payload["createdBy"] = trip.createdBy
        ref(trip.familyId, trip.id).set(payload, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, tripId: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        ref(familyId, tripId).set(
            mapOf(
                "isDeleted" to true,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    private fun Long.toTimestamp() =
        Timestamp(this / 1000, ((this % 1000) * 1_000_000).toInt())
}
