package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.remote.DocumentRemoteChange
import it.vittorioscocca.kidbox.data.remote.DocumentRemoteStore
import it.vittorioscocca.kidbox.data.remote.DocumentStorageManager
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG_DOC_REPO = "KB_Doc_Repo"

data class DocumentBrowserData(
    val folders: List<KBDocumentCategoryEntity>,
    val documents: List<KBDocumentEntity>,
)

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: KBDocumentDao,
    private val categoryDao: KBDocumentCategoryDao,
    private val remoteStore: DocumentRemoteStore,
    private val storageManager: DocumentStorageManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var docsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observeBrowser(
        familyId: String,
        parentFolderId: String?,
    ): Flow<DocumentBrowserData> = combine(
        categoryDao.observeByFamilyId(familyId),
        documentDao.observeByFamilyId(familyId),
    ) { categories, documents ->
        DocumentBrowserData(
            folders = categories
                .filter { it.parentId == parentFolderId && !it.isDeleted }
                .sortedWith(compareBy<KBDocumentCategoryEntity> { it.sortOrder }.thenBy { it.title.lowercase() }),
            documents = documents
                .filter { it.categoryId == parentFolderId && !it.isDeleted }
                .sortedByDescending { it.updatedAtEpochMillis },
        )
    }

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && docsListener != null && categoriesListener != null) return@withLock
                stopRealtimeLocked()
                listeningFamilyId = familyId
                docsListener = remoteStore.listenDocuments(
                    familyId = familyId,
                    onChange = { changes -> scope.launch { applyInbound(familyId, changes) } },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )
                categoriesListener = remoteStore.listenCategories(
                    familyId = familyId,
                    onChange = { changes -> scope.launch { applyInbound(familyId, changes) } },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )
            }
        }
    }

    fun stopRealtime() {
        scope.launch {
            realtimeMutex.withLock { stopRealtimeLocked() }
        }
    }

    suspend fun createFolderLocal(
        familyId: String,
        title: String,
        parentId: String?,
        forcedId: String? = null,
        sortOrder: Int = 0,
    ): KBDocumentCategoryEntity {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        val id = forcedId ?: UUID.randomUUID().toString()
        val existing = categoryDao.getById(id)
        val folder = if (existing == null) {
            KBDocumentCategoryEntity(
                id = id,
                familyId = familyId,
                title = title.trim(),
                sortOrder = sortOrder,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                parentId = parentId,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
        } else {
            existing.copy(
                title = title.trim(),
                parentId = parentId,
                sortOrder = sortOrder,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
        }
        categoryDao.upsert(folder)
        return folder
    }

    suspend fun ensureExpenseFolders(
        familyId: String,
        expenseId: String,
        expenseTitle: String,
    ): KBDocumentCategoryEntity {
        val root = createFolderLocal(
            familyId = familyId,
            title = "Spese",
            parentId = null,
            forcedId = expensesRootFolderId(familyId),
            sortOrder = 99,
        )
        if (expenseId.isBlank()) return root
        return createFolderLocal(
            familyId = familyId,
            title = expenseTitle.trim().ifBlank { "Spesa" },
            parentId = root.id,
            forcedId = expenseCategoryFolderId(expenseId),
            sortOrder = 0,
        )
    }

    suspend fun uploadDocumentLocal(
        familyId: String,
        parentFolderId: String?,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        notes: String? = null,
        forcedId: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        val id = forcedId ?: UUID.randomUUID().toString()
        val localPath = persistPendingPlainFile(id, fileName, bytes).absolutePath
        val placeholderStoragePath = "families/$familyId/documents/$id/${safeFileName(fileName)}.kbenc"
        val entity = KBDocumentEntity(
            id = id,
            familyId = familyId,
            childId = null,
            categoryId = parentFolderId,
            localPath = localPath,
            title = titleFromFileName(fileName),
            fileName = fileName,
            mimeType = mimeType,
            fileSize = bytes.size.toLong(),
            storagePath = placeholderStoragePath,
            downloadURL = null,
            notes = notes,
            extractedText = null,
            extractedTextUpdatedAtEpochMillis = null,
            extractionStatusRaw = 0,
            extractionError = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            isDeleted = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        documentDao.upsert(entity)
    }

    suspend fun deleteDocumentLocal(document: KBDocumentEntity) {
        documentDao.upsert(
            document.copy(
                isDeleted = true,
                syncStateRaw = KBSyncState.PENDING_DELETE.rawValue,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: document.updatedBy,
                lastSyncError = null,
            ),
        )
    }

    suspend fun deleteFolderLocal(folder: KBDocumentCategoryEntity) {
        categoryDao.upsert(
            folder.copy(
                isDeleted = true,
                syncStateRaw = KBSyncState.PENDING_DELETE.rawValue,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: folder.updatedBy,
                lastSyncError = null,
            ),
        )
    }

    suspend fun moveDocumentLocal(
        document: KBDocumentEntity,
        destinationFolderId: String?,
    ) {
        documentDao.upsert(
            document.copy(
                categoryId = destinationFolderId,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: document.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun moveFolderLocal(
        folder: KBDocumentCategoryEntity,
        destinationFolderId: String?,
    ) {
        if (folder.id == destinationFolderId) return
        categoryDao.upsert(
            folder.copy(
                parentId = destinationFolderId,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: folder.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun renameDocumentLocal(
        document: KBDocumentEntity,
        newTitle: String,
    ) {
        documentDao.upsert(
            document.copy(
                title = newTitle.trim(),
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: document.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun renameFolderLocal(
        folder: KBDocumentCategoryEntity,
        newTitle: String,
    ) {
        categoryDao.upsert(
            folder.copy(
                title = newTitle.trim(),
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: folder.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun duplicateDocumentLocal(
        source: KBDocumentEntity,
        destinationFolderId: String? = source.categoryId,
    ): KBDocumentEntity {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: source.updatedBy
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            categoryId = destinationFolderId,
            title = "${source.title} copia",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        documentDao.upsert(copy)
        return copy
    }

    suspend fun duplicateFolderLocal(
        source: KBDocumentCategoryEntity,
        destinationParentId: String? = source.parentId,
    ): KBDocumentCategoryEntity {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: source.updatedBy
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            parentId = destinationParentId,
            title = "${source.title} copia",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        categoryDao.upsert(copy)
        return copy
    }

    suspend fun flushPending(familyId: String) {
        categoryDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue)
            .forEach { folder ->
                runCatching { remoteStore.upsertCategory(folder) }
                    .onSuccess {
                        categoryDao.upsert(
                            folder.copy(
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                            ),
                        )
                    }
                    .onFailure { err ->
                        categoryDao.upsert(folder.copy(lastSyncError = err.localizedMessage))
                    }
            }

        documentDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue)
            .forEach { doc ->
                val withUploadedBlob = ensureStorageUploaded(doc)
                runCatching { remoteStore.upsertDocument(withUploadedBlob) }
                    .onSuccess {
                        documentDao.upsert(
                            withUploadedBlob.copy(
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                                updatedAtEpochMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                    .onFailure { err ->
                        documentDao.upsert(withUploadedBlob.copy(lastSyncError = err.localizedMessage))
                    }
            }

        documentDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue)
            .forEach { doc ->
                runCatching {
                    remoteStore.softDeleteDocument(familyId, doc.id)
                    storageManager.delete(doc.storagePath)
                    documentDao.deleteById(doc.id)
                }.onFailure { err ->
                    documentDao.upsert(doc.copy(lastSyncError = err.localizedMessage))
                }
            }

        categoryDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue)
            .forEach { folder ->
                runCatching {
                    remoteStore.softDeleteCategory(familyId, folder.id)
                    categoryDao.deleteById(folder.id)
                }.onFailure { err ->
                    categoryDao.upsert(folder.copy(lastSyncError = err.localizedMessage))
                }
            }
    }

    suspend fun preparePreviewFile(document: KBDocumentEntity): File {
        Log.d(
            TAG_DOC_REPO,
            "preparePreviewFile docId=${document.id} hasLocal=${!document.localPath.isNullOrBlank()} storagePath=${document.storagePath}",
        )
        val previewDir = File(context.cacheDir, "kb_documents_preview").apply { mkdirs() }
        val output = File(previewDir, "${document.id}_${safeFileName(document.fileName)}")
        val local = document.localPath?.let { File(it) }
        if (local != null && local.exists()) {
            Log.d(TAG_DOC_REPO, "preparePreviewFile using local file docId=${document.id} localPath=${local.absolutePath}")
            local.copyTo(output, overwrite = true)
            Log.d(TAG_DOC_REPO, "preparePreviewFile local copy done docId=${document.id} out=${output.absolutePath}")
            return output
        }
        Log.d(TAG_DOC_REPO, "preparePreviewFile downloading+decrypting docId=${document.id}")
        val decrypted = storageManager.downloadDecrypted(
            storagePath = document.storagePath,
            familyId = document.familyId,
        )
        output.writeBytes(decrypted)
        Log.d(TAG_DOC_REPO, "preparePreviewFile decrypt done docId=${document.id} outSize=${output.length()}")
        return output
    }

    fun expensesRootFolderId(familyId: String): String = "exp-root-$familyId"

    fun expenseCategoryFolderId(expenseId: String): String = "exp-cat-$expenseId"

    private suspend fun ensureStorageUploaded(doc: KBDocumentEntity): KBDocumentEntity {
        if (!doc.downloadURL.isNullOrBlank()) return doc
        val local = doc.localPath?.let { File(it) }
        if (local == null || !local.exists()) return doc
        val upload = storageManager.uploadEncrypted(
            familyId = doc.familyId,
            docId = doc.id,
            fileName = doc.fileName,
            mimeType = doc.mimeType,
            plainBytes = local.readBytes(),
        )
        return doc.copy(storagePath = upload.storagePath, downloadURL = upload.downloadUrl)
    }

    private suspend fun applyInbound(
        familyId: String,
        changes: List<DocumentRemoteChange>,
    ) {
        val now = System.currentTimeMillis()
        changes.forEach { change ->
            when (change) {
                is DocumentRemoteChange.RemoveDocument -> documentDao.deleteById(change.id)
                is DocumentRemoteChange.RemoveCategory -> categoryDao.deleteById(change.id)
                is DocumentRemoteChange.UpsertCategory -> {
                    val dto = change.dto
                    if (dto.isDeleted) {
                        categoryDao.deleteById(dto.id)
                        return@forEach
                    }
                    val local = categoryDao.getById(dto.id)
                    if (
                        local != null &&
                        local.isDeleted &&
                        KBSyncState.fromRaw(local.syncStateRaw) == KBSyncState.PENDING_DELETE
                    ) return@forEach
                    if (local != null && (dto.updatedAtEpochMillis ?: 0L) < local.updatedAtEpochMillis) return@forEach
                    categoryDao.upsert(
                        KBDocumentCategoryEntity(
                            id = dto.id,
                            familyId = familyId,
                            title = dto.title.ifBlank { local?.title.orEmpty() },
                            sortOrder = dto.sortOrder,
                            createdAtEpochMillis = local?.createdAtEpochMillis ?: dto.createdAtEpochMillis ?: now,
                            updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                            updatedBy = dto.updatedBy ?: local?.updatedBy ?: "",
                            isDeleted = false,
                            parentId = dto.parentId,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        ),
                    )
                }

                is DocumentRemoteChange.UpsertDocument -> {
                    val dto = change.dto
                    if (dto.isDeleted) {
                        documentDao.deleteById(dto.id)
                        return@forEach
                    }
                    val local = documentDao.getById(dto.id)
                    if (
                        local != null &&
                        local.isDeleted &&
                        KBSyncState.fromRaw(local.syncStateRaw) == KBSyncState.PENDING_DELETE
                    ) return@forEach
                    if (local != null && (dto.updatedAtEpochMillis ?: 0L) < local.updatedAtEpochMillis) return@forEach
                    val resolvedCategoryId = dto.categoryId ?: local?.categoryId
                    documentDao.upsert(
                        KBDocumentEntity(
                            id = dto.id,
                            familyId = familyId,
                            childId = dto.childId,
                            categoryId = resolvedCategoryId,
                            localPath = local?.localPath,
                            title = dto.title.ifBlank { titleFromFileName(dto.fileName) },
                            fileName = dto.fileName,
                            mimeType = dto.mimeType,
                            fileSize = dto.fileSize,
                            storagePath = dto.storagePath,
                            downloadURL = dto.downloadURL,
                            notes = dto.notes,
                            extractedText = local?.extractedText,
                            extractedTextUpdatedAtEpochMillis = local?.extractedTextUpdatedAtEpochMillis,
                            extractionStatusRaw = local?.extractionStatusRaw ?: 0,
                            extractionError = local?.extractionError,
                            createdAtEpochMillis = local?.createdAtEpochMillis ?: dto.createdAtEpochMillis ?: now,
                            updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                            updatedBy = dto.updatedBy ?: local?.updatedBy ?: "",
                            isDeleted = false,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        ),
                    )
                }
            }
        }
    }

    private fun persistPendingPlainFile(
        docId: String,
        fileName: String,
        bytes: ByteArray,
    ): File {
        val dir = File(context.filesDir, "kb_documents_pending").apply { mkdirs() }
        return File(dir, "${docId}_${safeFileName(fileName)}").apply {
            writeBytes(bytes)
        }
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file.bin" }

    private fun titleFromFileName(name: String): String {
        val idx = name.lastIndexOf('.')
        return if (idx > 0) name.substring(0, idx) else name
    }

    private fun stopRealtimeLocked() {
        docsListener?.remove()
        categoriesListener?.remove()
        docsListener = null
        categoriesListener = null
        listeningFamilyId = null
    }
}
