package it.vittorioscocca.kidbox.data.remote.wallet

import android.util.Base64
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
import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import it.vittorioscocca.kidbox.data.remote.DocumentCryptoManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class WalletTicketRemoteDto(
    val id: String,
    val familyId: String,
    val titleEnc: String?,
    val locationEnc: String?,
    val seatEnc: String?,
    val bookingCodeEnc: String?,
    val notesEnc: String?,
    val barcodeTextEnc: String?,
    val fileNameEnc: String?,
    val kindRaw: String?,
    val emitter: String?,
    val eventDateEpochMillis: Long?,
    val eventEndDateEpochMillis: Long?,
    val pdfStorageURL: String?,
    val pdfStorageBytes: Long,
    val addToAppleWalletURL: String?,
    val barcodeFormat: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val createdBy: String?,
    val createdByName: String?,
    val updatedBy: String?,
    val updatedByName: String?,
)

sealed interface WalletRemoteChange {
    data class Upsert(val dto: WalletTicketRemoteDto) : WalletRemoteChange
    data class Remove(val id: String) : WalletRemoteChange
}

@Singleton
class WalletRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
    private val documentCrypto: DocumentCryptoManager,
) {
    private val db get() = FirebaseFirestore.getInstance()

    fun listen(
        familyId: String,
        onChange: (List<WalletRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        return db.collection("families").document(familyId).collection("walletTickets")
            .addSnapshotListener(
                MetadataChanges.INCLUDE,
                EventListener<QuerySnapshot> { snap, err ->
                    if (err != null) {
                        onError(err)
                    } else if (snap != null) {
                        val changes = snap.documentChanges.mapNotNull { diff ->
                            val doc = diff.document
                            val d = doc.data ?: emptyMap()
                            fun longField(key: String): Long = when (val v = d[key]) {
                                is Long -> v
                                is Int -> v.toLong()
                                is Double -> v.toLong()
                                else -> 0L
                            }
                            val dto = WalletTicketRemoteDto(
                                id = doc.id,
                                familyId = familyId,
                                titleEnc = d["titleEnc"] as? String,
                                locationEnc = d["locationEnc"] as? String,
                                seatEnc = d["seatEnc"] as? String,
                                bookingCodeEnc = d["bookingCodeEnc"] as? String,
                                notesEnc = d["notesEnc"] as? String,
                                barcodeTextEnc = d["barcodeTextEnc"] as? String,
                                fileNameEnc = d["fileNameEnc"] as? String,
                                kindRaw = d["kind"] as? String,
                                emitter = d["emitter"] as? String,
                                eventDateEpochMillis = (d["eventDate"] as? Timestamp)?.toDate()?.time,
                                eventEndDateEpochMillis = (d["eventEndDate"] as? Timestamp)?.toDate()?.time,
                                pdfStorageURL = d["pdfStorageURL"] as? String,
                                pdfStorageBytes = longField("pdfStorageBytes"),
                                addToAppleWalletURL = d["addToAppleWalletURL"] as? String,
                                barcodeFormat = d["barcodeFormat"] as? String,
                                isDeleted = d["isDeleted"] as? Boolean ?: false,
                                createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                                updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                                createdBy = d["createdBy"] as? String,
                                createdByName = d["createdByName"] as? String,
                                updatedBy = d["updatedBy"] as? String,
                                updatedByName = d["updatedByName"] as? String,
                            )
                            when (diff.type) {
                                DocumentChange.Type.ADDED,
                                DocumentChange.Type.MODIFIED,
                                -> WalletRemoteChange.Upsert(dto)

                                DocumentChange.Type.REMOVED -> WalletRemoteChange.Remove(doc.id)
                            }
                        }
                        if (changes.isNotEmpty()) onChange(changes)
                    }
                },
            )
    }

    suspend fun upsert(ticket: KBWalletTicketEntity, displayName: String) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = db.collection("families").document(ticket.familyId)
            .collection("walletTickets").document(ticket.id)
        val exists = ref.get().await().exists()

        val payload = mutableMapOf<String, Any?>(
            "schemaVersion" to 1,
            "titleEnc" to encryptField(ticket.title, ticket.familyId),
            "fileNameEnc" to ticket.pdfFileName?.let { encryptField(it, ticket.familyId) },
            "locationEnc" to ticket.location?.let { encryptField(it, ticket.familyId) },
            "seatEnc" to ticket.seat?.let { encryptField(it, ticket.familyId) },
            "bookingCodeEnc" to ticket.bookingCode?.let { encryptField(it, ticket.familyId) },
            "notesEnc" to ticket.notes?.let { encryptField(it, ticket.familyId) },
            "barcodeTextEnc" to ticket.barcodeText?.let { encryptField(it, ticket.familyId) },
            "kind" to ticket.kindRaw,
            "emitter" to ticket.emitter,
            "eventDate" to ticket.eventDateEpochMillis?.let { Timestamp(it / 1000, 0) },
            "eventEndDate" to ticket.eventEndDateEpochMillis?.let { Timestamp(it / 1000, 0) },
            "pdfStorageURL" to ticket.pdfStorageURL,
            "pdfStorageBytes" to ticket.pdfStorageBytes,
            "barcodeFormat" to ticket.barcodeFormat,
            "isDeleted" to false,
            "updatedBy" to uid,
            "updatedByName" to displayName,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!exists) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["createdBy"] = uid
            payload["createdByName"] = displayName
        }
        ref.set(payload, SetOptions.merge()).await()
    }

    suspend fun softDelete(ticketId: String, familyId: String) {
        val uid = auth.currentUser?.uid ?: return
        db.collection("families").document(familyId)
            .collection("walletTickets").document(ticketId)
            .update(
                mapOf(
                    "isDeleted" to true,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
            ).await()
    }

    suspend fun upsertImportedTicket(
        familyId: String,
        ticketId: String,
        titleEnc: String,
        fileNameEnc: String?,
        pdfStorageURL: String,
        pdfStorageBytes: Long,
        displayName: String,
    ) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = db.collection("families").document(familyId).collection("walletTickets").document(ticketId)
        val exists = ref.get().await().exists()
        val payload = mutableMapOf<String, Any?>(
            "schemaVersion" to 1,
            "titleEnc" to titleEnc,
            "fileNameEnc" to fileNameEnc,
            "kind" to "other",
            "pdfStorageURL" to pdfStorageURL,
            "pdfStorageBytes" to pdfStorageBytes,
            "isDeleted" to false,
            "updatedBy" to uid,
            "updatedByName" to displayName,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!exists) {
            payload["createdAt"] = FieldValue.serverTimestamp()
            payload["createdBy"] = uid
            payload["createdByName"] = displayName
        }
        ref.set(payload, SetOptions.merge()).await()
    }

    private fun encryptField(plain: String, familyId: String): String {
        val combined = documentCrypto.encrypt(plain.toByteArray(Charsets.UTF_8), familyId)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }
}
