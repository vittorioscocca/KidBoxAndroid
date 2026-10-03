package it.vittorioscocca.kidbox.data.remote.wallet

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBPaymentCardEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.ui.screens.wallet.paymentcards.PaymentCardPalette
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Documento `paymentCards`: i campi `*Enc` viaggiano cifrati come sono. Mirror di `PaymentCardDTO` (iOS). */
data class PaymentCardRemoteDto(
    val id: String,
    val familyId: String,
    val labelEnc: String?,
    val cardNumberEnc: String?,
    val holderNameEnc: String?,
    val ibanEnc: String?,
    val expiryEnc: String?,
    val notesEnc: String?,
    val pinEnc: String?,
    val colorHex: String,
    val frontPhotoStorageURL: String?,
    val frontPhotoStoragePath: String?,
    val backPhotoStorageURL: String?,
    val backPhotoStoragePath: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val createdBy: String?,
    val createdByName: String?,
    val updatedBy: String?,
    val updatedByName: String?,
    val visibilityScope: String,
    val visibilityMemberIds: List<String>,
)

sealed interface PaymentCardRemoteChange {
    data class Upsert(val dto: PaymentCardRemoteDto) : PaymentCardRemoteChange
    data class Remove(val id: String) : PaymentCardRemoteChange
}

/**
 * Path Firestore: `families/{familyId}/paymentCards/{cardId}`, coperto dalla
 * regola generica delle sottocollezioni di famiglia. Nessuna Cloud Function
 * lo legge (niente push all'aggiunta).
 */
@Singleton
class PaymentCardRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun col(familyId: String) =
        db.collection("families").document(familyId).collection("paymentCards")

    fun listen(
        familyId: String,
        onChange: (List<PaymentCardRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration = col(familyId).addSnapshotListener(
        MetadataChanges.INCLUDE,
        EventListener<QuerySnapshot> { snap, err ->
            if (err != null) {
                onError(err)
            } else if (snap != null) {
                // Upsert dal risultato COMPLETO (`snap.documents`), rimozioni dal
                // delta: con la persistenza locale il delta può arrivare vuoto
                // anche con risultati reali (vedi LoyaltyCardRemoteStore).
                val upserts = snap.documents.map { PaymentCardRemoteChange.Upsert(dto(it, familyId)) }
                val removes = snap.documentChanges
                    .filter { it.type == DocumentChange.Type.REMOVED }
                    .map { PaymentCardRemoteChange.Remove(it.document.id) }
                val changes = upserts + removes
                if (changes.isNotEmpty()) onChange(changes)
            }
        },
    )

    suspend fun upsert(card: KBPaymentCardEntity, displayName: String, visibilityMemberIds: List<String>) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = col(card.familyId).document(card.id)
        val exists = ref.get().await().exists()

        val payload = mutableMapOf<String, Any?>(
            "schemaVersion" to 1,
            "labelEnc" to card.labelEnc,
            "cardNumberEnc" to card.cardNumberEnc,
            "holderNameEnc" to card.holderNameEnc,
            "ibanEnc" to card.ibanEnc,
            "expiryEnc" to card.expiryEnc,
            "notesEnc" to card.notesEnc,
            "pinEnc" to card.pinEnc,
            "colorHex" to card.colorHex,
            "frontPhotoStorageURL" to card.frontPhotoStorageURL,
            "frontPhotoStoragePath" to card.frontPhotoStoragePath,
            "backPhotoStorageURL" to card.backPhotoStorageURL,
            "backPhotoStoragePath" to card.backPhotoStoragePath,
            "visibilityScope" to KBVisibilityScope.normalizedWallet(card.visibilityScope),
            "visibilityMemberIds" to visibilityMemberIds,
            "isDeleted" to false,
            "updatedBy" to uid,
            "updatedByName" to displayName,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!exists) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["createdBy"] = card.createdBy.ifBlank { uid }
            payload["createdByName"] = card.createdByName
        }
        ref.set(payload, SetOptions.merge()).await()
    }

    /** Il tombstone svuota anche i campi cifrati, come su iOS. */
    suspend fun softDelete(cardId: String, familyId: String) {
        val uid = auth.currentUser?.uid ?: return
        val cleared = listOf(
            "labelEnc", "cardNumberEnc", "holderNameEnc", "ibanEnc", "expiryEnc", "notesEnc", "pinEnc",
            "frontPhotoStorageURL", "frontPhotoStoragePath", "backPhotoStorageURL", "backPhotoStoragePath",
        ).associateWith { FieldValue.delete() }
        col(familyId).document(cardId).set(
            cleared + mapOf(
                "isDeleted" to true,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
            SetOptions.merge(),
        ).await()
    }

    private fun dto(doc: DocumentSnapshot, familyId: String): PaymentCardRemoteDto {
        val d = doc.data ?: emptyMap()
        fun str(key: String): String? = (d[key] as? String)?.takeIf { it.isNotBlank() }
        return PaymentCardRemoteDto(
            id = doc.id,
            familyId = familyId,
            labelEnc = str("labelEnc"),
            cardNumberEnc = str("cardNumberEnc"),
            holderNameEnc = str("holderNameEnc"),
            ibanEnc = str("ibanEnc"),
            expiryEnc = str("expiryEnc"),
            notesEnc = str("notesEnc"),
            pinEnc = str("pinEnc"),
            colorHex = str("colorHex") ?: PaymentCardPalette.DEFAULT_HEX,
            frontPhotoStorageURL = str("frontPhotoStorageURL"),
            frontPhotoStoragePath = str("frontPhotoStoragePath"),
            backPhotoStorageURL = str("backPhotoStorageURL"),
            backPhotoStoragePath = str("backPhotoStoragePath"),
            isDeleted = d["isDeleted"] as? Boolean ?: false,
            createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
            updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
            createdBy = str("createdBy"),
            createdByName = str("createdByName"),
            updatedBy = str("updatedBy"),
            updatedByName = str("updatedByName"),
            visibilityScope = KBVisibilityScope.normalizedWallet(d["visibilityScope"] as? String),
            visibilityMemberIds = (d["visibilityMemberIds"] as? List<*>)?.mapNotNull { it as? String } ?: emptyList(),
        )
    }
}
