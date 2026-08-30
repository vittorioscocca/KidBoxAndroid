package it.vittorioscocca.kidbox.data.remote.life

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.HousePaymentEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

private fun millisToTimestamp(millis: Long): Timestamp =
    Timestamp(millis / 1000, ((millis % 1000) * 1_000_000).toInt())

data class HousePaymentRemoteDto(
    val id: String,
    val familyId: String,
    val name: String,
    val typeRaw: String,
    val subtypeRaw: String?,
    val importo: Double?,
    val linkedExpenseId: String?,
    val giornoDiScadenzaMensile: Int?,
    val dataScadenzaMillis: Long?,
    val dataScadenzaContrattoMillis: Long?,
    val fornitore: String?,
    val note: String?,
    val reminderOn: Boolean,
    val isDeleted: Boolean,
    val createdAtMillis: Long?,
    val updatedAtMillis: Long?,
    val createdBy: String?,
    val updatedBy: String?,
)

sealed interface HousePaymentRemoteChange {
    data class Upsert(val dto: HousePaymentRemoteDto) : HousePaymentRemoteChange
    data class Remove(val id: String) : HousePaymentRemoteChange
}

@Singleton
class HousePaymentRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun ref(familyId: String, paymentId: String) =
        db.collection("families").document(familyId).collection("housePayments").document(paymentId)

    fun listenHousePayments(
        familyId: String,
        onChange: (List<HousePaymentRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration =
        db.collection("families").document(familyId).collection("housePayments")
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
                    val typeRaw = (d["typeRaw"] as? String) ?: "altro"
                    val giorno: Int? = when (val v = d["giornoDiScadenzaMensile"]) {
                        is Int -> v
                        is Number -> v.toInt()
                        else -> null
                    }
                    val importo: Double? = when (val v = d["importo"]) {
                        is Double -> v
                        is Number -> v.toDouble()
                        else -> null
                    }
                    val dto = HousePaymentRemoteDto(
                        id = doc.id,
                        familyId = familyId,
                        name = name,
                        typeRaw = typeRaw,
                        subtypeRaw = d["subtypeRaw"] as? String,
                        importo = importo,
                        linkedExpenseId = d["linkedExpenseId"] as? String,
                        giornoDiScadenzaMensile = giorno,
                        dataScadenzaMillis = (d["dataScadenza"] as? Timestamp)?.toDate()?.time,
                        dataScadenzaContrattoMillis = (d["dataScadenzaContratto"] as? Timestamp)?.toDate()?.time,
                        fornitore = d["fornitore"] as? String,
                        note = d["note"] as? String,
                        reminderOn = d["reminderOn"] as? Boolean ?: true,
                        isDeleted = d["isDeleted"] as? Boolean ?: false,
                        createdAtMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                        updatedAtMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                        createdBy = d["createdBy"] as? String,
                        updatedBy = d["updatedBy"] as? String,
                    )
                    HousePaymentRemoteChange.Upsert(dto)
                }
                val removes = changed
                    .filter { it.type == DocumentChange.Type.REMOVED }
                    .map { HousePaymentRemoteChange.Remove(it.document.id) }
                val changes = upserts + removes
                if (changes.isNotEmpty()) onChange(changes)
            }

    suspend fun upsertHousePaymentToFirestore(entity: HousePaymentEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val snap = ref(entity.familyId, entity.id).get().await()
        val isNew = !snap.exists()
        val data = mutableMapOf<String, Any?>(
            "name" to entity.name,
            "typeRaw" to entity.typeRaw,
            "isDeleted" to false,
            "reminderOn" to entity.reminderOn,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (isNew) data["createdAt"] = FieldValue.serverTimestamp()
        data["subtypeRaw"] = entity.subtypeRaw
        data["importo"] = entity.importo
        data["linkedExpenseId"] = entity.linkedExpenseId
        data["giornoDiScadenzaMensile"] = entity.giornoDiScadenzaMensile
        data["dataScadenza"] = entity.dataScadenza?.let { millisToTimestamp(it) }
        data["dataScadenzaContratto"] = entity.dataScadenzaContratto?.let { millisToTimestamp(it) }
        data["fornitore"] = entity.fornitore
        data["note"] = entity.note
        if (isNew) data["createdBy"] = entity.createdBy.ifEmpty { uid }
        ref(entity.familyId, entity.id).set(data, SetOptions.merge()).await()
    }

    suspend fun softDelete(familyId: String, paymentId: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        ref(familyId, paymentId).set(
            mapOf(
                "isDeleted" to true,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }
}
