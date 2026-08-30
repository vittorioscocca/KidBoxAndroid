package it.vittorioscocca.kidbox.data.remote

import it.vittorioscocca.kidbox.util.KBLog

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
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await
private const val TAG_DOC_SYNC = "KB_Doc_Sync"

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
    val extractedText: String?,
    val extractedTextUpdatedAtEpochMillis: Long?,
    val extractionStatusRaw: Int?,
    val extractionError: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long?,
    val updatedAtEpochMillis: Long?,
    val updatedBy: String?,
    val visibilityScope: String?,
    val visibilityMemberIds: List<String>?,
    val createdBy: String?,
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
    data class UpsertDocument(
        val dto: RemoteDocumentDto,
        val isFromCache: Boolean,
    ) : DocumentRemoteChange
    data class RemoveDocument(
        val id: String,
        val isFromCache: Boolean,
    ) : DocumentRemoteChange
    data class UpsertCategory(
        val dto: RemoteDocumentCategoryDto,
        val isFromCache: Boolean,
    ) : DocumentRemoteChange
    data class RemoveCategory(
        val id: String,
        val isFromCache: Boolean,
    ) : DocumentRemoteChange
}

@Singleton
class DocumentRemoteStore @Inject constructor(
    private val auth: FirebaseAuth,
) {
    private val db: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun listenDocuments(
        familyId: String,
        onChange: (DocumentRemoteChange) -> Unit,
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
                val isFromCache = snap?.metadata?.isFromCache == true
                var upsertCount = 0
                var removedCount = 0
                // Documenti da riversare: normalmente il delta, ma se il delta è
                // vuoto si ricade sul risultato completo della query. Con la
                // persistenza locale Firestore può riusare una snapshot in cache e
                // farsela confermare "invariata" dal server con un existence
                // filter, senza inviare alcun document_change: il delta risulta
                // vuoto anche con risultati reali e in Room non arriva più nulla.
                //
                // Il fallback invece della lettura sempre completa (come fa
                // TodoRemoteStore) è deliberato: qui i documenti di una famiglia
                // possono essere molti, e rimandarli tutti a ogni snapshot
                // costerebbe caro per l'unico caso in cui serve.
                val changed = snap?.documentChanges.orEmpty()
                val upsertDocs = changed
                    .filter { it.type != DocumentChange.Type.REMOVED }
                    .map { it.document }
                    .ifEmpty { if (changed.isEmpty()) snap?.documents.orEmpty() else emptyList() }
                upsertDocs.forEach { doc ->
                    val d = doc.data ?: return@forEach
                    val resolvedCategoryId = (d["categoryId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: (d["parentId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                        ?: (d["folderId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    if (isFromCache && doc.id.startsWith("exp-") && resolvedCategoryId.isNullOrBlank()) {
                        KBLog.data.debug("cache-guard skip document id=${doc.id} reason=fromCache_null_category", TAG_DOC_SYNC)
                        return@forEach
                    }
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
                        extractedText = (d["extractedText"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                        extractedTextUpdatedAtEpochMillis = (d["extractedTextUpdatedAt"] as? Timestamp)?.toDate()?.time,
                        extractionStatusRaw = (d["extractionStatusRaw"] as? Number)?.toInt(),
                        extractionError = (d["extractionError"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                        isDeleted = d["isDeleted"] as? Boolean ?: false,
                        createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                        updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                        updatedBy = (d["updatedBy"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                        visibilityScope = (d["visibilityScope"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                        visibilityMemberIds = visibilityMemberIdsFromFirestore(d["visibilityMemberIds"]),
                        createdBy = (d["createdBy"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                    )
                    onChange(DocumentRemoteChange.UpsertDocument(dto, isFromCache = isFromCache))
                    upsertCount += 1
                }
                changed.filter { it.type == DocumentChange.Type.REMOVED }.forEach { diff ->
                    if (isFromCache) {
                        KBLog.data.debug("cache-guard skip document removed id=${diff.document.id}", TAG_DOC_SYNC)
                        return@forEach
                    }
                    onChange(DocumentRemoteChange.RemoveDocument(diff.document.id, isFromCache = false))
                    removedCount += 1
                }
                if (upsertCount + removedCount > 0) {
                    KBLog.data.debug("Snapshot processed. Changes: $upsertCount added/modified, $removedCount removed. collection=documents isFromCache=$isFromCache", TAG_DOC_SYNC)
                }
            },
        )

    fun listenCategories(
        familyId: String,
        onChange: (DocumentRemoteChange) -> Unit,
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
                val isFromCache = snap?.metadata?.isFromCache == true
                var upsertCount = 0
                var removedCount = 0
                // Documenti da riversare: normalmente il delta, ma se il delta è
                // vuoto si ricade sul risultato completo della query. Con la
                // persistenza locale Firestore può riusare una snapshot in cache e
                // farsela confermare "invariata" dal server con un existence
                // filter, senza inviare alcun document_change: il delta risulta
                // vuoto anche con risultati reali e in Room non arriva più nulla.
                //
                // Il fallback invece della lettura sempre completa (come fa
                // TodoRemoteStore) è deliberato: qui le cartelle di una famiglia
                // possono essere molti, e rimandarli tutti a ogni snapshot
                // costerebbe caro per l'unico caso in cui serve.
                val changed = snap?.documentChanges.orEmpty()
                val upsertDocs = changed
                    .filter { it.type != DocumentChange.Type.REMOVED }
                    .map { it.document }
                    .ifEmpty { if (changed.isEmpty()) snap?.documents.orEmpty() else emptyList() }
                upsertDocs.forEach { doc ->
                    val d = doc.data ?: return@forEach
                    val resolvedParentId = (d["parentId"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                    if (
                        isFromCache &&
                        doc.id.startsWith("exp-") &&
                        !doc.id.startsWith("exp-root-") &&
                        resolvedParentId.isNullOrBlank()
                    ) {
                        KBLog.data.debug("cache-guard skip category id=${doc.id} reason=fromCache_null_parent", TAG_DOC_SYNC)
                        return@forEach
                    }
                    val dto = RemoteDocumentCategoryDto(
                        id = doc.id,
                        familyId = familyId,
                        title = (d["title"] as? String)?.trim().orEmpty(),
                        sortOrder = (d["sortOrder"] as? Number)?.toInt() ?: 0,
                        parentId = resolvedParentId,
                        isDeleted = d["isDeleted"] as? Boolean ?: false,
                        createdAtEpochMillis = (d["createdAt"] as? Timestamp)?.toDate()?.time,
                        updatedAtEpochMillis = (d["updatedAt"] as? Timestamp)?.toDate()?.time,
                        updatedBy = (d["updatedBy"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
                    )
                    onChange(DocumentRemoteChange.UpsertCategory(dto, isFromCache = isFromCache))
                    upsertCount += 1
                }
                changed.filter { it.type == DocumentChange.Type.REMOVED }.forEach { diff ->
                    if (isFromCache) {
                        KBLog.data.debug("cache-guard skip category removed id=${diff.document.id}", TAG_DOC_SYNC)
                        return@forEach
                    }
                    onChange(DocumentRemoteChange.RemoveCategory(diff.document.id, isFromCache = false))
                    removedCount += 1
                }
                if (upsertCount + removedCount > 0) {
                    KBLog.data.debug("Snapshot processed. Changes: $upsertCount added/modified, $removedCount removed. collection=documentCategories isFromCache=$isFromCache", TAG_DOC_SYNC)
                }
            },
        )

    /**
     * Esegue una `get()` one-shot dalla collezione `documentCategories` e ritorna i DTO
     * con la **stessa normalizzazione** usata dal listener realtime (filtro cache-guard escluso,
     * perché il get esplicito serve proprio a pre-caricare la gerarchia prima dei documenti).
     *
     * Usato all'avvio dell'app per garantire che, quando i documenti arrivano dal listener,
     * tutte le categorie siano già presenti in DB locale con il `parentId` corretto.
     * In questo modo l'UI non vede mai categorie "placeholder" agganciate al root per pochi ms.
     */
    suspend fun fetchCategoriesOnce(familyId: String): List<RemoteDocumentCategoryDto> {
        val snap = db.collection("families")
            .document(familyId)
            .collection("documentCategories")
            .get()
            .await()
        return snap.documents.mapNotNull { doc ->
            val d = doc.data ?: return@mapNotNull null
            RemoteDocumentCategoryDto(
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
        }
    }

    suspend fun upsertDocument(entity: KBDocumentEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val ref = db.collection("families")
            .document(entity.familyId)
            .collection("documents")
            .document(entity.id)
        val exists = ref.get().await().exists()
        val inferredExpenseCategoryId = entity.notes
            ?.takeIf { it.startsWith("expense:") }
            ?.substringAfter("expense:")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { expenseId -> "exp-cat-$expenseId" }
        val normalizedCategoryId = entity.categoryId
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?: inferredExpenseCategoryId
        if (entity.categoryId.isNullOrBlank() && !inferredExpenseCategoryId.isNullOrBlank()) {
            KBLog.data.debug("outbound document guard id=${entity.id} forcingCategory=$inferredExpenseCategoryId fromNotes=${entity.notes}", TAG_DOC_SYNC)
        }
        val normalizedVis = KBVisibilityScope.normalized(entity.visibilityScope)
        val memberIdsOutbound = decodeStringList(entity.visibilityMemberIdsJson)
        val visibilityMemberIdsFirestore = memberIdsOutbound
            .takeIf { normalizedVis == KBVisibilityScope.MEMBERS }
            .orEmpty()
        val payload = mutableMapOf<String, Any?>(
            "title" to entity.title,
            "fileName" to entity.fileName,
            "mimeType" to entity.mimeType,
            "fileSize" to entity.fileSize,
            "storagePath" to entity.storagePath,
            "downloadURL" to entity.downloadURL,
            "notes" to entity.notes,
            "childId" to entity.childId,
            "categoryId" to normalizedCategoryId,
            // compatibilità cross-client: alcuni path storici leggono parentId/folderId
            "parentId" to normalizedCategoryId,
            "folderId" to normalizedCategoryId,
            "visibilityScope" to normalizedVis,
            "visibilityMemberIds" to visibilityMemberIdsFirestore,
            "isDeleted" to entity.isDeleted,
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
            "createdAt" to timestampFromMillis(entity.createdAtEpochMillis),
        )
        if (!exists) {
            payload["createdBy"] = if (entity.createdBy.isBlank()) uid else entity.createdBy
        }
        // Aggiorna createdBy quando il doc passa a "solo io" (anche dopo la prima creazione).
        if (normalizedVis == KBVisibilityScope.ONLY_CREATOR) {
            payload["createdBy"] = entity.createdBy.takeIf { it.isNotBlank() } ?: uid
        }
        // OCR fields must be additive: never wipe remote OCR with null from stale/local-only updates.
        entity.extractedText
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { payload["extractedText"] = it }
        entity.extractedTextUpdatedAtEpochMillis?.let {
            payload["extractedTextUpdatedAt"] = timestampFromNullableMillis(it)
        }
        if (entity.extractionStatusRaw != 0 || !entity.extractionError.isNullOrBlank() || !entity.extractedText.isNullOrBlank()) {
            payload["extractionStatusRaw"] = entity.extractionStatusRaw
        }
        entity.extractionError
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { payload["extractionError"] = it }

        ref.set(payload, SetOptions.merge()).await()
    }

    private fun visibilityMemberIdsFromFirestore(raw: Any?): List<String> = when (raw) {
        is List<*> -> raw.mapNotNull { it as? String }.distinct()
        else -> emptyList()
    }

    suspend fun upsertCategory(entity: KBDocumentCategoryEntity) {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val enforcedParentId = when {
            entity.id.startsWith("exp-cat-") && entity.parentId.isNullOrBlank() -> "exp-root-${entity.familyId}"
            else -> entity.parentId
        }
        if (enforcedParentId != entity.parentId) {
            KBLog.data.debug("outbound category guard id=${entity.id} forcingParent=$enforcedParentId originalParent=${entity.parentId}", TAG_DOC_SYNC)
        }
        db.collection("families")
            .document(entity.familyId)
            .collection("documentCategories")
            .document(entity.id)
            .set(
                mapOf(
                    "title" to entity.title,
                    "sortOrder" to entity.sortOrder,
                    "parentId" to enforcedParentId,
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

    private fun timestampFromNullableMillis(epochMillis: Long?): Timestamp? =
        epochMillis?.let { Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt()) }
}
