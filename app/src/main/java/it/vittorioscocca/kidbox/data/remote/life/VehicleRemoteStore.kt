package it.vittorioscocca.kidbox.data.remote.life

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.VehicleEntity
import it.vittorioscocca.kidbox.data.vehicles.VehicleReminderOffsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

private fun millisToTimestamp(millis: Long): Timestamp =
    Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())

data class VehicleRemoteDto(
    val id: String,
    val familyId: String,
    val name: String,
    val licensePlate: String?,
    val brand: String?,
    val model: String?,
    val year: Int?,
    val fuelTypeRaw: String?,
    val color: String?,
    val vin: String?,
    val insuranceExpiryDateMillis: Long?,
    val revisionExpiryDateMillis: Long?,
    val taxExpiryDateMillis: Long?,
    val lastServiceDateMillis: Long?,
    val nextServiceDateMillis: Long?,
    val currentKm: Int?,
    val notes: String?,
    val photoURL: String?,
    val reminderEnabled: Boolean,
    val reminderOffsetsJson: String?,
    val isDeleted: Boolean,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val createdBy: String?,
    val updatedBy: String?,
)

sealed interface VehicleRemoteChange {
    data class Upsert(val dto: VehicleRemoteDto) : VehicleRemoteChange
    data class Remove(val id: String) : VehicleRemoteChange
}

@Singleton
class VehicleRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun ref(familyId: String, vehicleId: String) =
        db.collection("families").document(familyId).collection("vehicles").document(vehicleId)

    fun listenVehicles(
        familyId: String,
        onChange: (List<VehicleRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("vehicles")
            .whereEqualTo("isDeleted", false)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    onError(err)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                // Delta vuoto con risultati reali: con la persistenza locale
                // Firestore può riusare una snapshot in cache e farsela confermare
                // "invariata" dal server con un existence filter, senza inviare
                // alcun document_change — e in Room non arriva più nulla. Qui si
                // ricade sul risultato completo della query.
                //
                // Il fallback invece della lettura sempre completa (come fa
                // TodoRemoteStore) è deliberato: `applyInbound` riprogramma la
                // sveglia di ogni scadenza che riceve, e rimandare tutti i
                // documenti a ogni snapshot significherebbe rifare gli allarmi di
                // tutta la lista ogni volta.
                val changed = snap.documentChanges
                val upsertDocs = changed
                    .filter { it.type != DocumentChange.Type.REMOVED }
                    .map { it.document }
                    .ifEmpty { if (changed.isEmpty()) snap.documents else emptyList() }
                val upserts = upsertDocs.mapNotNull { doc ->
                    val d = doc.data ?: return@mapNotNull null
                    val name = (d["name"] as? String)?.trim().orEmpty()
                    if (name.isEmpty()) return@mapNotNull null
                    val year: Int? = when (val y = d["year"]) {
                        is Int -> y
                        is Number -> y.toInt()
                        else -> null
                    }
                    val currentKm: Int? = when (val k = d["currentKm"]) {
                        is Int -> k
                        is Number -> k.toInt()
                        else -> null
                    }
                    val dto = VehicleRemoteDto(
                        id = doc.id,
                        familyId = familyId,
                        name = name,
                        licensePlate = d["licensePlate"] as? String,
                        brand = d["brand"] as? String,
                        model = d["model"] as? String,
                        year = year,
                        fuelTypeRaw = d["fuelTypeRaw"] as? String,
                        color = d["color"] as? String,
                        vin = d["vin"] as? String,
                        insuranceExpiryDateMillis = (d["insuranceExpiryDate"] as? Timestamp)?.toDate()?.time,
                        revisionExpiryDateMillis = (d["revisionExpiryDate"] as? Timestamp)?.toDate()?.time,
                        taxExpiryDateMillis = (d["taxExpiryDate"] as? Timestamp)?.toDate()?.time,
                        lastServiceDateMillis = (d["lastServiceDate"] as? Timestamp)?.toDate()?.time,
                        nextServiceDateMillis = (d["nextServiceDate"] as? Timestamp)?.toDate()?.time,
                        currentKm = currentKm,
                        notes = d["notes"] as? String,
                        photoURL = d["photoURL"] as? String,
                        reminderEnabled = d["reminderEnabled"] as? Boolean ?: false,
                        reminderOffsetsJson = VehicleReminderOffsets.fromFirestoreMap(d["reminderOffsets"] as? Map<*, *>).encode(),
                        isDeleted = d["isDeleted"] as? Boolean ?: false,
                        createdAtMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                        updatedAtMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                        createdBy = d["createdBy"] as? String,
                        updatedBy = d["updatedBy"] as? String,
                    )
                    VehicleRemoteChange.Upsert(dto)
                }
                val removes = changed
                    .filter { it.type == DocumentChange.Type.REMOVED }
                    .map { VehicleRemoteChange.Remove(it.document.id) }
                val changes = upserts + removes
                if (changes.isNotEmpty()) onChange(changes)
            }

    suspend fun upsertVehicleToFirestore(entity: VehicleEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val snap = ref(entity.familyId, entity.id).get().await()
        val isNew = !snap.exists()
        val data = mutableMapOf<String, Any?>(
            "name" to entity.name,
            "isDeleted" to false,
            "reminderEnabled" to entity.reminderEnabled,
            "reminderOffsets" to VehicleReminderOffsets.decode(entity.reminderOffsetsJson).toFirestoreMap(),
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) data["createdAt"] = FieldValue.serverTimestamp()
        data["licensePlate"] = entity.licensePlate
        data["brand"] = entity.brand
        data["model"] = entity.model
        data["year"] = entity.year
        data["fuelTypeRaw"] = entity.fuelType
        data["color"] = entity.color
        data["vin"] = entity.vin
        data["insuranceExpiryDate"] = entity.insuranceExpiryDate?.let { millisToTimestamp(it) }
        data["revisionExpiryDate"] = entity.revisionExpiryDate?.let { millisToTimestamp(it) }
        data["taxExpiryDate"] = entity.taxExpiryDate?.let { millisToTimestamp(it) }
        data["lastServiceDate"] = entity.lastServiceDate?.let { millisToTimestamp(it) }
        data["nextServiceDate"] = entity.nextServiceDate?.let { millisToTimestamp(it) }
        data["currentKm"] = entity.currentKm
        data["notes"] = entity.notes
        data["photoURL"] = entity.photoURL
        if (isNew) data["createdBy"] = entity.createdBy.ifEmpty { uid }
        ref(entity.familyId, entity.id).set(data, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, vehicleId: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        ref(familyId, vehicleId).set(
            mapOf(
                "isDeleted" to true,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }
}
