package it.vittorioscocca.kidbox.data.remote

import android.util.Log
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
        Log.i(TAG_DOC_STORAGE, "uploadEncrypted start docId=$docId bytes=${plainBytes.size} mime=$mimeType")
        val safeName = if (fileName.isBlank()) "file.bin" else fileName
        val path = "families/$familyId/documents/$docId/$safeName.kbenc"
        val ref = storage.reference.child(path)
        val encrypted = cryptoManager.encrypt(plainBytes, familyId)
        Log.d(TAG_DOC_STORAGE, "uploadEncrypted encrypted docId=$docId encryptedBytes=${encrypted.size}")
        val metadata = StorageMetadata.Builder()
            .setContentType("application/octet-stream")
            .setCustomMetadata("kb_encrypted", "1")
            .setCustomMetadata("kb_alg", "AES-GCM")
            .setCustomMetadata("kb_orig_name", safeName)
            .setCustomMetadata("kb_orig_mime", mimeType)
            .build()
        ref.putBytes(encrypted, metadata).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Log.i(TAG_DOC_STORAGE, "uploadEncrypted ok docId=$docId path=$path")
        return DocumentUploadResult(storagePath = path, downloadUrl = downloadUrl)
    }

    suspend fun downloadDecrypted(
        storagePath: String,
        familyId: String,
    ): ByteArray {
        Log.i(TAG_DOC_STORAGE, "downloadDecrypted start path=$storagePath familyId=$familyId")
        val encrypted = storage.reference.child(storagePath).getBytes(15L * 1024L * 1024L).await()
        Log.d(TAG_DOC_STORAGE, "downloadDecrypted encrypted bytes=${encrypted.size}")
        val plain = cryptoManager.decrypt(encrypted, familyId)
        Log.i(TAG_DOC_STORAGE, "downloadDecrypted ok path=$storagePath plainBytes=${plain.size} familyId=$familyId")
        return plain
    }

    suspend fun delete(storagePath: String) {
        if (storagePath.isBlank()) return
        Log.i(TAG_DOC_STORAGE, "delete start path=$storagePath")
        runCatching { storage.reference.child(storagePath).delete().await() }
            .onSuccess { Log.i(TAG_DOC_STORAGE, "delete ok path=$storagePath") }
            .onFailure { Log.e(TAG_DOC_STORAGE, "delete failed path=$storagePath", it) }
    }
}
