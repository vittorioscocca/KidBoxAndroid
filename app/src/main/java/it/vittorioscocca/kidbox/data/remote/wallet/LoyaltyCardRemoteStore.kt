package it.vittorioscocca.kidbox.data.remote.wallet

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.EventListener
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.entity.KBLoyaltyCardEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/**
 * DTO di un documento `loyaltyCards` su Firestore. A differenza di
 * `WalletTicketRemoteDto`, tutti i campi sono in chiaro: nessuna cifratura
 * (stesso trattamento del campo `emitter` sui biglietti). Mirror di
 * `LoyaltyCardDTO` (iOS).
 */
data class LoyaltyCardRemoteDto(
    val id: String,
    val familyId: String,
    val brandId: String?,
    val brandName: String,
    val cardNumber: String,
    val barcodeFormat: String,
    val note: String?,
    val primaryColorHex: String,
    val secondaryColorHex: String,
    /** URL del logo ufficiale del brand, in chiaro come gli altri campi. `null` se ignoto. */
    val logoURL: String?,
    /**
     * Foto fronte/retro della tessera fisica: su Storage il blob è CIFRATO
     * (`DocumentCryptoManager`), su Firestore viaggiano solo URL e path, in
     * chiaro come gli altri campi. Nomi speculari a iOS.
     */
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
    val visibilityScope: String = KBVisibilityScope.FAMILY,
    val visibilityMemberIds: List<String> = emptyList(),
)

sealed interface LoyaltyCardRemoteChange {
    data class Upsert(val dto: LoyaltyCardRemoteDto) : LoyaltyCardRemoteChange
    data class Remove(val id: String) : LoyaltyCardRemoteChange
}

/**
 * Remote store per le carte fedeltà del Wallet.
 *
 * Path Firestore: `families/{familyId}/loyaltyCards/{cardId}`
 *
 * Pattern speculare a [WalletRemoteStore], ma senza cifratura né PDF/Storage
 * (le carte fedeltà sono solo testo).
 */
@Singleton
class LoyaltyCardRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun stringListField(any: Any?): List<String> = when (any) {
        is List<*> -> any.mapNotNull { it as? String }
        else -> emptyList()
    }

    fun listen(
        familyId: String,
        onChange: (List<LoyaltyCardRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        return db.collection("families").document(familyId).collection("loyaltyCards")
            .addSnapshotListener(
                MetadataChanges.INCLUDE,
                EventListener<QuerySnapshot> { snap, err ->
                    if (err != null) {
                        onError(err)
                    } else if (snap != null) {
                        // Gli upsert vengono dal risultato COMPLETO della query
                        // (`snap.documents`), non dal delta (`snap.documentChanges`): con la
                        // persistenza locale attiva Firestore può riusare una snapshot in cache e
                        // farsela confermare "invariata" dal server con un existence filter, senza
                        // inviare alcun document_change. In quel caso il delta è vuoto anche se la
                        // query ha risultati reali, e in Room non arrivava più nulla. Le rimozioni
                        // restano sul delta, dove sono affidabili.
                        val upserts = snap.documents.mapNotNull { doc ->
                            val d = doc.data ?: emptyMap()
                            val dto = LoyaltyCardRemoteDto(
                                id = doc.id,
                                familyId = familyId,
                                brandId = d["brandId"] as? String,
                                brandName = d["brandName"] as? String ?: "",
                                cardNumber = d["cardNumber"] as? String ?: "",
                                barcodeFormat = d["barcodeFormat"] as? String ?: "code128",
                                note = d["note"] as? String,
                                primaryColorHex = d["primaryColorHex"] as? String ?: "#5856D6",
                                secondaryColorHex = d["secondaryColorHex"] as? String ?: "#3634A3",
                                logoURL = (d["logoURL"] as? String)?.takeIf { it.isNotBlank() },
                                frontPhotoStorageURL = (d["frontPhotoStorageURL"] as? String)?.takeIf { it.isNotBlank() },
                                frontPhotoStoragePath = (d["frontPhotoStoragePath"] as? String)?.takeIf { it.isNotBlank() },
                                backPhotoStorageURL = (d["backPhotoStorageURL"] as? String)?.takeIf { it.isNotBlank() },
                                backPhotoStoragePath = (d["backPhotoStoragePath"] as? String)?.takeIf { it.isNotBlank() },
                                isDeleted =d["isDeleted"] as? Boolean ?: false,
                                createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                                updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                                createdBy = d["createdBy"] as? String,
                                createdByName = d["createdByName"] as? String,
                                updatedBy = d["updatedBy"] as? String,
                                updatedByName = d["updatedByName"] as? String,
                                visibilityScope = KBVisibilityScope.normalized(d["visibilityScope"] as? String),
                                visibilityMemberIds = stringListField(d["visibilityMemberIds"]),
                            )
                            LoyaltyCardRemoteChange.Upsert(dto)
                        }
                        val removes = snap.documentChanges
                            .filter { it.type == DocumentChange.Type.REMOVED }
                            .map { LoyaltyCardRemoteChange.Remove(it.document.id) }
                        val changes = upserts + removes
                        if (changes.isNotEmpty()) onChange(changes)
                    }
                },
            )
    }

    suspend fun upsert(card: KBLoyaltyCardEntity, displayName: String, visibilityMemberIds: List<String>) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = db.collection("families").document(card.familyId)
            .collection("loyaltyCards").document(card.id)
        val exists = ref.get().await().exists()

        val payload = mutableMapOf<String, Any?>(
            "schemaVersion" to 1,
            "brandId" to card.brandId,
            "brandName" to card.brandName,
            "cardNumber" to card.cardNumber,
            "barcodeFormat" to card.barcodeFormat,
            "note" to card.note,
            "primaryColorHex" to card.primaryColorHex,
            "secondaryColorHex" to card.secondaryColorHex,
            "logoURL" to card.logoURL,
            "frontPhotoStorageURL" to card.frontPhotoStorageURL,
            "frontPhotoStoragePath" to card.frontPhotoStoragePath,
            "backPhotoStorageURL" to card.backPhotoStorageURL,
            "backPhotoStoragePath" to card.backPhotoStoragePath,
            "visibilityScope" to KBVisibilityScope.normalized(card.visibilityScope),
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

    suspend fun softDelete(cardId: String, familyId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("families").document(familyId)
            .collection("loyaltyCards").document(cardId)
            .update(
                mapOf(
                    "isDeleted" to true,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
    }
}
