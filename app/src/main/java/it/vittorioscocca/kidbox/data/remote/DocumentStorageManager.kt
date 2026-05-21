package it.vittorioscocca.kidbox.data.remote

import it.vittorioscocca.kidbox.util.KBLog

import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

private const val TAG_DOC_STORAGE = "KB_Doc_Storage"

data class DocumentUploadResult(
    val storagePath: String,
    val downloadUrl: String,
)

@Singleton
class DocumentStorageManager @Inject constructor(
    private val cryptoManager: DocumentCryptoManager,
) {
    private val storage: FirebaseStorage
        get() = FirebaseStorage.getInstance()

    suspend fun uploadEncrypted(
        familyId: String,
        docId: String,
        fileName: String,
        mimeType: String,
        plainBytes: ByteArray,
    ): DocumentUploadResult {
        KBLog.data.info("uploadEncrypted start docId=$docId bytes=${plainBytes.size} mime=$mimeType", TAG_DOC_STORAGE)
        val safeName = if (fileName.isBlank()) "file.bin" else fileName
        val path = "families/$familyId/documents/$docId/$safeName.kbenc"
        val ref = storage.reference.child(path)
        val encrypted = cryptoManager.encrypt(plainBytes, familyId)
        KBLog.data.debug("uploadEncrypted encrypted docId=$docId encryptedBytes=${encrypted.size}", TAG_DOC_STORAGE)
        val metadata = StorageMetadata.Builder()
            .setContentType("application/octet-stream")
            .setCustomMetadata("kb_encrypted", "1")
            .setCustomMetadata("kb_alg", "AES-GCM")
            .setCustomMetadata("kb_orig_name", safeName)
            .setCustomMetadata("kb_orig_mime", mimeType)
            .build()
        ref.putBytes(encrypted, metadata).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        KBLog.data.info("uploadEncrypted ok docId=$docId path=$path", TAG_DOC_STORAGE)
        return DocumentUploadResult(storagePath = path, downloadUrl = downloadUrl)
    }

    suspend fun downloadDecrypted(
        storagePath: String,
        familyId: String,
    ): ByteArray {
        KBLog.data.info("downloadDecrypted start path=$storagePath familyId=$familyId", TAG_DOC_STORAGE)
        val encrypted = storage.reference.child(storagePath).getBytes(15L * 1024L * 1024L).await()
        KBLog.data.debug("downloadDecrypted encrypted bytes=${encrypted.size}", TAG_DOC_STORAGE)
        val plain = cryptoManager.decrypt(encrypted, familyId)
        KBLog.data.info("downloadDecrypted ok path=$storagePath plainBytes=${plain.size} familyId=$familyId", TAG_DOC_STORAGE)
        return plain
    }

    /** Decripta byte letti dalla cache locale documenti (stesso formato del blob `.kbenc` su Storage). */
    fun decryptCachedDocumentBytes(combined: ByteArray, familyId: String): ByteArray {
        KBLog.data.debug("decryptCachedDocumentBytes bytes=${combined.size} familyId=$familyId", TAG_DOC_STORAGE)
        return cryptoManager.decrypt(combined, familyId)
    }

    /** Upload already-encrypted bytes (or plain bytes that will be encrypted) to an explicit [storagePath].
     *  Returns the download URL. Useful when the caller needs a non-standard storage path. */
    suspend fun uploadEncryptedToPath(
        storagePath: String,
        familyId: String,
        mimeType: String,
        fileName: String,
        plainBytes: ByteArray,
    ): String {
        KBLog.data.info("uploadEncryptedToPath start path=$storagePath bytes=${plainBytes.size}", TAG_DOC_STORAGE)
        val encrypted = cryptoManager.encrypt(plainBytes, familyId)
        val ref = storage.reference.child(storagePath)
        val metadata = StorageMetadata.Builder()
            .setContentType("application/octet-stream")
            .setCustomMetadata("kb_encrypted", "1")
            .setCustomMetadata("kb_alg", "AES-GCM")
            .setCustomMetadata("kb_orig_mime", mimeType)
            .setCustomMetadata("kb_orig_name", fileName)
            .build()
        ref.putBytes(encrypted, metadata).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        KBLog.data.info("uploadEncryptedToPath ok path=$storagePath", TAG_DOC_STORAGE)
        return downloadUrl
    }

    /** Upload bytes already encrypted with [DocumentCryptoManager] (e.g. wallet PDF). */
    suspend fun uploadEncryptedBytes(
        storagePath: String,
        encrypted: ByteArray,
        mimeType: String,
        fileName: String,
    ): String {
        KBLog.data.info("uploadEncryptedBytes start path=$storagePath bytes=${encrypted.size}", TAG_DOC_STORAGE)
        val ref = storage.reference.child(storagePath)
        val metadata = StorageMetadata.Builder()
            .setContentType("application/octet-stream")
            .setCustomMetadata("kb_encrypted", "1")
            .setCustomMetadata("kb_alg", "AES-GCM")
            .setCustomMetadata("kb_orig_mime", mimeType)
            .setCustomMetadata("kb_orig_name", fileName)
            .build()
        ref.putBytes(encrypted, metadata).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        KBLog.data.info("uploadEncryptedBytes ok path=$storagePath", TAG_DOC_STORAGE)
        return downloadUrl
    }

    suspend fun delete(storagePath: String) {
        if (storagePath.isBlank()) return
        KBLog.data.info("delete start path=$storagePath", TAG_DOC_STORAGE)
        runCatching { storage.reference.child(storagePath).delete().await() }
            .onSuccess { KBLog.data.info("delete ok path=$storagePath", TAG_DOC_STORAGE) }
            .onFailure { KBLog.data.error("delete failed path=$storagePath", TAG_DOC_STORAGE, it) }
    }
}
