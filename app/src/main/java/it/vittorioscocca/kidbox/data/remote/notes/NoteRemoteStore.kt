package it.vittorioscocca.kidbox.data.remote.notes

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
import it.vittorioscocca.kidbox.data.local.entity.KBNoteEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class NoteRemoteDto(
    val id: String,
    val familyId: String,
    val titleEnc: String?,
    val bodyEnc: String?,
    val titlePlain: String?,
    val bodyPlain: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val createdBy: String?,
    val createdByName: String?,
    val updatedBy: String?,
    val updatedByName: String?,
)

sealed interface NoteRemoteChange {
    data class Upsert(val dto: NoteRemoteDto) : NoteRemoteChange
    data class Remove(val id: String) : NoteRemoteChange
}

@Singleton
class NoteRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
    private val crypto: NoteCryptoManager,
) {
    private val db get() = FirebaseFirestore.getInstance()

    fun listen(
        familyId: String,
        onChange: (List<NoteRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration {
        return db.collection("families").document(familyId).collection("notes")
            .addSnapshotListener(
                MetadataChanges.INCLUDE,
                EventListener<QuerySnapshot> { snap, err ->
                    if (err != null) {
                        onError(err)
                    } else if (snap != null) {
                        val changes = snap.documentChanges.mapNotNull { diff ->
                            val doc = diff.document
                            val d = doc.data
                            val dto = NoteRemoteDto(
                                id = doc.id,
                                familyId = familyId,
                                titleEnc = d["titleEnc"] as? String,
                                bodyEnc = d["bodyEnc"] as? String,
                                titlePlain = d["title"] as? String,
                                bodyPlain = d["body"] as? String,
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
                                -> NoteRemoteChange.Upsert(dto)

                                DocumentChange.Type.REMOVED -> NoteRemoteChange.Remove(doc.id)
                            }
                        }
                        if (changes.isNotEmpty()) onChange(changes)
                    }
                },
            )
    }

    suspend fun upsert(note: KBNoteEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = db.collection("families").document(note.familyId).collection("notes").document(note.id)
        val exists = ref.get().await().exists()

        val titleEnc = crypto.encryptToBase64(note.title, note.familyId)
        val bodyEnc = crypto.encryptToBase64(note.body, note.familyId)

        val payload = mutableMapOf<String, Any?>(
            "schemaVersion" to 1,
            "titleEnc" to titleEnc,
            "bodyEnc" to bodyEnc,
            "title" to FieldValue.delete(),
            "body" to FieldValue.delete(),
            "isDeleted" to false,
            "updatedBy" to uid,
            "updatedByName" to note.updatedByName,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (!exists) {
            payload["createdBy"] = if (note.createdBy.isBlank()) uid else note.createdBy
            payload["createdByName"] = note.createdByName
            payload["createdAt"] = FieldValue.serverTimestamp()
        }
        ref.set(payload, SetOptions.merge()).await()
    }

    suspend fun softDelete(
        familyId: String,
        noteId: String,
    ) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families").document(familyId).collection("notes").document(noteId)
            .set(
                mapOf(
                    "isDeleted" to true,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    fun decryptOrFallback(
        dto: NoteRemoteDto,
    ): Pair<String, String> {
        if (!dto.titleEnc.isNullOrBlank() && !dto.bodyEnc.isNullOrBlank()) {
            return runCatching {
                val title = crypto.decryptFromBase64(dto.titleEnc, dto.familyId)
                val body = crypto.decryptFromBase64(dto.bodyEnc, dto.familyId)
                title to body
            }.getOrElse { (dto.titlePlain ?: "") to (dto.bodyPlain ?: "") }
        }
        return (dto.titlePlain ?: "") to (dto.bodyPlain ?: "")
    }
}
