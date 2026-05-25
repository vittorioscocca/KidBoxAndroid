package it.vittorioscocca.kidbox.data.remote.support

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Ticket letto da Firestore (`support_tickets`). */
data class SupportTicketDto(
    val id: String,
    val familyId: String,
    val uid: String,
    val userEmail: String,
    val type: String,
    val title: String,
    val summary: String,
    val status: String,
    val conversation: List<Map<String, Any?>>,
    val createdAtMillis: Long?,
)

@Singleton
class SupportTicketRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db get() = FirebaseFirestore.getInstance()

    private fun collection() = db.collection(COLLECTION)

    /**
     * Crea/aggiorna documento su `support_tickets/{id}` con merge.
     * @return id documento Firestore
     */
    suspend fun submit(ticket: SupportTicketSubmitDto): String {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        if (ticket.uid != uid) error("uid ticket non corrisponde all'utente autenticato")

        val docId = ticket.id.trim().ifEmpty {
            collection().document().id
        }

        val data = SupportTicketFirestorePayload.buildDocumentData(
            ticket = ticket.copy(id = docId),
            platform = PLATFORM,
            statusNew = STATUS_NEW,
        )

        collection().document(docId).set(data, SetOptions.merge()).await()
        KBLog.data.info("support ticket submitted id=$docId type=${ticket.type}", TAG)
        return docId
    }

    /**
     * Ascolta i ticket dell'utente corrente (query su `uid`).
     */
    fun listenMyTickets(
        uid: String,
        onChange: (List<SupportTicketDto>) -> Unit,
    ): ListenerRegistration =
        collection()
            .whereEqualTo("uid", uid)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    KBLog.data.warning("listenMyTickets failed uid=$uid: ${err.message}", TAG)
                    return@addSnapshotListener
                }
                if (snap == null) {
                    onChange(emptyList())
                    return@addSnapshotListener
                }
                val tickets = snap.documents.mapNotNull { doc ->
                    decode(doc.id, doc.data)
                }
                onChange(tickets)
            }

    private fun decode(documentId: String, data: Map<String, Any>?): SupportTicketDto? {
        if (data == null) return null
        val familyId = data["familyId"] as? String ?: return null
        val ticketUid = data["uid"] as? String ?: return null
        val type = data["type"] as? String ?: return null
        val title = (data["title"] as? String)?.trim().orEmpty()
        if (title.isEmpty()) return null
        val id = (data["id"] as? String)?.takeIf { it.isNotBlank() } ?: documentId
        return SupportTicketDto(
            id = id,
            familyId = familyId,
            uid = ticketUid,
            userEmail = data["userEmail"] as? String ?: "",
            type = type,
            title = title,
            summary = data["summary"] as? String ?: "",
            status = data["status"] as? String ?: STATUS_NEW,
            conversation = conversationList(data["conversation"]),
            createdAtMillis = epochMillisFromFirestore(data["createdAt"]),
        )
    }

    private fun conversationList(any: Any?): List<Map<String, Any?>> {
        if (any !is List<*>) return emptyList()
        return any.mapNotNull { item ->
            if (item !is Map<*, *>) return@mapNotNull null
            @Suppress("UNCHECKED_CAST")
            item as? Map<String, Any?>
        }
    }

    private fun epochMillisFromFirestore(value: Any?): Long? = when (value) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> null
    }

    private companion object {
        const val COLLECTION = "support_tickets"
        const val PLATFORM = "android"
        const val STATUS_NEW = "new"
        const val TAG = "SupportTicketRemoteStore"
    }
}
