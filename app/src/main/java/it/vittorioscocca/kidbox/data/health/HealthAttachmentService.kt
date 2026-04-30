package it.vittorioscocca.kidbox.data.health

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.remote.DocumentStorageManager
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import java.io.File
import java.io.IOException
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "HealthAttachmentSvc"
private const val MAX_BYTES = 30L * 1024L * 1024L

class FileTooLargeException(message: String) : IOException(message)

@Singleton
class HealthAttachmentService @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val folderResolver: HealthFolderResolver,
    private val storageManager: DocumentStorageManager,
    private val documentDao: KBDocumentDao,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {

    suspend fun uploadVisitAttachment(
        uri: Uri,
        visitId: String,
        familyId: String,
        childId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = childId,
        tag = VisitAttachmentTag.make(visitId),
        storageScopeSegment = "visit-attachments/$visitId",
    )

    suspend fun uploadExamAttachment(
        uri: Uri,
        examId: String,
        familyId: String,
        childId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = childId,
        tag = ExamAttachmentTag.make(examId),
        storageScopeSegment = "exam-attachments/$examId",
    )

    suspend fun uploadTreatmentAttachment(
        uri: Uri,
        treatmentId: String,
        familyId: String,
        childId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = childId,
        tag = TreatmentAttachmentTag.make(treatmentId),
        storageScopeSegment = "treatment-attachments/$treatmentId",
    )

    /** Downloads (or returns the cached plaintext) for a document. */
    suspend fun downloadAttachment(doc: KBDocumentEntity): Result<File> = withContext(Dispatchers.IO) {
        runCatching { documentRepository.preparePreviewFile(doc) }
    }

    suspend fun deleteAttachment(doc: KBDocumentEntity) = withContext(Dispatchers.IO) {
        val localFile = doc.localPath?.let { File(it) }
        if (localFile != null && localFile.exists()) {
            runCatching { localFile.delete() }
                .onFailure { Log.w(TAG, "Failed to delete local file ${localFile.absolutePath}", it) }
        }
        documentRepository.deleteDocumentLocal(doc)
        runCatching { storageManager.delete(doc.storagePath) }
            .onFailure { Log.w(TAG, "Failed to delete storage blob ${doc.storagePath}", it) }
        // Flush the soft-delete to Firestore
        runCatching { documentRepository.flushPending(doc.familyId) }
            .onFailure { Log.w(TAG, "flushPending after delete failed", it) }
    }

    // ── Private ──────────────────────────────────────────────────────────────────

    private suspend fun upload(
        uri: Uri,
        familyId: String,
        childId: String,
        tag: String,
        storageScopeSegment: String,
    ): Result<KBDocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver

            // 1. Check size before reading bytes
            val fileSize = querySizeFromUri(uri)
            if (fileSize > MAX_BYTES) throw FileTooLargeException("File troppo grande (max 30 MB)")

            // 2. Read bytes
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Impossibile leggere il file")
            if (bytes.isEmpty()) throw IOException("Il file è vuoto")

            // 3. Resolve metadata
            val mimeType = cr.getType(uri)?.takeIf { it.isNotBlank() }
                ?: mimeFromExtension(fileNameFromUri(uri))
            val fileName = fileNameFromUri(uri).ifBlank { "attachment_${System.currentTimeMillis()}" }
            val title = titleFromFileName(fileName)

            // 4. docId + paths
            val docId = UUID.randomUUID().toString()
            val safeFile = safeFileName(fileName)
            val storagePath = "families/$familyId/$storageScopeSegment/$docId/$safeFile.kbenc"

            // 5. Ensure Salute/Referti folder hierarchy
            val (_, referti) = folderResolver.ensureHealthFolders(familyId)

            // 6. Write plaintext to local cache
            val localPath = writePendingFile(docId, fileName, bytes)

            val uid = auth.currentUser?.uid ?: "local"
            val now = System.currentTimeMillis()

            // 7. Persist entity with PENDING_UPSERT so a crash before step 9 is retryable
            val entity = KBDocumentEntity(
                id = docId,
                familyId = familyId,
                childId = childId,
                categoryId = referti.id,
                localPath = localPath,
                title = title,
                fileName = fileName,
                mimeType = mimeType,
                fileSize = bytes.size.toLong(),
                storagePath = storagePath,
                downloadURL = null,
                notes = tag,
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
            Log.d(TAG, "Persisted pending entity docId=$docId tag=$tag")

            // 8. Encrypt + upload to health-specific path
            val downloadUrl = try {
                storageManager.uploadEncryptedToPath(
                    storagePath = storagePath,
                    familyId = familyId,
                    mimeType = mimeType,
                    fileName = fileName,
                    plainBytes = bytes,
                )
            } catch (e: Exception) {
                documentDao.upsert(entity.copy(lastSyncError = e.message))
                throw e
            }

            // 9. Update with downloadURL and flush to Firestore
            val uploaded = entity.copy(
                downloadURL = downloadUrl,
                updatedAtEpochMillis = System.currentTimeMillis(),
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
            documentDao.upsert(uploaded)
            Log.d(TAG, "Uploaded docId=$docId path=$storagePath")

            // 10. Push metadata to Firestore (skips re-upload since downloadURL is set)
            runCatching { documentRepository.flushPending(familyId) }
                .onFailure { Log.w(TAG, "flushPending failed, will retry on next sync", it) }

            uploaded
        }
    }

    private fun writePendingFile(docId: String, fileName: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, "kb_documents_pending").apply { mkdirs() }
        val file = File(dir, "${docId}_${safeFileName(fileName)}")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun querySizeFromUri(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (col >= 0 && cursor.moveToFirst()) cursor.getLong(col) else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun fileNameFromUri(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
            } ?: uri.lastPathSegment ?: "file"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "file"
        }
    }

    private fun titleFromFileName(fileName: String): String =
        fileName.substringBeforeLast('.').ifBlank { fileName }

    private fun safeFileName(fileName: String): String =
        fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")

    private fun mimeFromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic" -> "image/heic"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "application/octet-stream"
        }
    }
}
