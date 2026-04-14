package it.vittorioscocca.kidbox.data.remote

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
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class RemoteDocumentDto(
    val id: String,
    val familyId: String,
    val childId: String?,
    val categoryId: String?,
    val title: String,
    val fileName: String,
    val mimeType: String,
    val fileSize: Long,
    val storagePath: String,
    val downloadURL: String?,
    val notes: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
)

data class RemoteDocumentCategoryDto(
    val id: String,
    val familyId: String,
    val title: String,
    val sortOrder: Int,
    val parentId: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
)

sealed interface DocumentRemoteChange {
    data class UpsertDocument(val dto: RemoteDocumentDto) : DocumentRemoteChange
    data class RemoveDocument(val id: String) : DocumentRemoteChange
    data class UpsertCategory(val dto: RemoteDocumentCategoryDto) : DocumentRemoteChange
    data class RemoveCategory(val id: String) : DocumentRemoteChange
}

@Singleton
class DocumentRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun listenDocuments(
        familyId: String,
        onChange: (List<DocumentRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration = db.collection("families")
        .document(familyId)
        .collection("documents")
        .addSnapshotListener(
            MetadataChanges.EXCLUDE,
            EventListener<QuerySnapshot> { snap, err ->
                if (err != null) {
                    onError(err)
                    return@EventListener
                }
                val changes = snap?.documentChanges?.mapNotNull { diff ->
                    val doc = diff.document
                    val d = doc.data
                    val resolvedCategoryId = (d["categoryId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: (d["parentId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: (d["folderId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    when (diff.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED,
                        -> {
                            val dto = RemoteDocumentDto(
                                id = doc.id,
                                familyId = familyId,
                                childId = (d["childId"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                categoryId = resolvedCategoryId,
                                title = (d["title"] as? String)?.trim().orEmpty(),
                                fileName = (d["fileName"] as? String)?.trim().orEmpty(),
                                mimeType = (d["mimeType"] as? String)?.trim().orEmpty().ifBlank { "application/octet-stream" },
                                fileSize = (d["fileSize"] as? Number)?.toLong() ?: 0L,
                                storagePath = (d["storagePath"] as? String)?.trim().orEmpty(),
                                downloadURL = (d["downloadURL"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                notes = (d["notes"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                isDeleted = d["isDeleted"] as? Boolean ?: false,
                                createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                                updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                                updatedBy = (d["updatedBy"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                            )
                            DocumentRemoteChange.UpsertDocument(dto)
                        }

                        DocumentChange.Type.REMOVED -> DocumentRemoteChange.RemoveDocument(doc.id)
                    }
                }.orEmpty()
                if (changes.isNotEmpty()) onChange(changes)
            },
        )

    fun listenCategories(
        familyId: String,
        onChange: (List<DocumentRemoteChange>) -> Unit,
        onError: (Exception) -> Unit,
    ): ListenerRegistration = db.collection("families")
        .document(familyId)
        .collection("documentCategories")
        .addSnapshotListener(
            MetadataChanges.EXCLUDE,
            EventListener<QuerySnapshot> { snap, err ->
                if (err != null) {
                    onError(err)
                    return@EventListener
                }
                val changes = snap?.documentChanges?.mapNotNull { diff ->
                    val doc = diff.document
                    val d = doc.data
                    when (diff.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED,
                        -> {
                            val dto = RemoteDocumentCategoryDto(
                                id = doc.id,
                                familyId = familyId,
                                title = (d["title"] as? String)?.trim().orEmpty(),
                                sortOrder = (d["sortOrder"] as? Number)?.toInt() ?: 0,
                                parentId = (d["parentId"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                                isDeleted = d["isDeleted"] as? Boolean ?: false,
                                createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                                updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                                updatedBy = (d["updatedBy"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                            )
                            DocumentRemoteChange.UpsertCategory(dto)
                        }

                        DocumentChange.Type.REMOVED -> DocumentRemoteChange.RemoveCategory(doc.id)
                    }
                }.orEmpty()
                if (changes.isNotEmpty()) onChange(changes)
            },
        )

    suspend fun upsertDocument(entity: KBDocumentEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families")
            .document(entity.familyId)
            .collection("documents")
            .document(entity.id)
            .set(
                mapOf(
                    "title" to entity.title,
                    "fileName" to entity.fileName,
                    "mimeType" to entity.mimeType,
                    "fileSize" to entity.fileSize,
                    "storagePath" to entity.storagePath,
                    "downloadURL" to entity.downloadURL,
                    "notes" to entity.notes,
                    "childId" to entity.childId,
                    "categoryId" to entity.categoryId,
                    // compatibilità cross-client: alcuni path storici leggono parentId/folderId
                    "parentId" to entity.categoryId,
                    "folderId" to entity.categoryId,
                    "isDeleted" to entity.isDeleted,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to timestampFromMillis(entity.createdAtEpochMillis),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    suspend fun upsertCategory(entity: KBDocumentCategoryEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families")
            .document(entity.familyId)
            .collection("documentCategories")
            .document(entity.id)
            .set(
                mapOf(
                    "title" to entity.title,
                    "sortOrder" to entity.sortOrder,
                    "parentId" to entity.parentId,
                    "isDeleted" to entity.isDeleted,
                    "updatedBy" to uid,
                    "updatedAt" to FieldValue.serverTimestamp(),
                    "createdAt" to timestampFromMillis(entity.createdAtEpochMillis),
                ),
                SetOptions.merge(),
            )
            .await()
    }

    suspend fun softDeleteDocument(
        familyId: String,
        documentId: String,
    ) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families")
            .document(familyId)
            .collection("documents")
            .document(documentId)
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

    suspend fun softDeleteCategory(
        familyId: String,
        categoryId: String,
    ) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        db.collection("families")
            .document(familyId)
            .collection("documentCategories")
            .document(categoryId)
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

    private fun timestampFromMillis(epochMillis: Long): Timestamp =
        Timestamp(epochMillis / 1000, ((epochMillis % 1000) * 1_000_000).toInt())
}
