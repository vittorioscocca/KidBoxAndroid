package it.vittorioscocca.kidbox.data.remote

import android.util.Log
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

private const val TAG_PHOTO_STORAGE = "KB_Photo_Storage"

data class PhotoVideoUploadResult(
    val storagePath: String,
    val downloadUrl: String,
)

@Singleton
class PhotoVideoStorageManager @Inject constructor(
    private val cryptoManager: DocumentCryptoManager,
) {
    private val storage: FirebaseStorage
        get() = FirebaseStorage.getInstance()

    suspend fun uploadEncrypted(
        familyId: String,
        photoId: String,
        mimeType: String,
        plainBytes: ByteArray,
    ): PhotoVideoUploadResult {
        val path = "families/$familyId/photos/$photoId/original.enc"
        val encrypted = cryptoManager.encrypt(plainBytes, familyId)
        val metadata = StorageMetadata.Builder()
            .setContentType("application/octet-stream")
            .setCustomMetadata("kb_encrypted", "1")
            .setCustomMetadata("kb_alg", "AES-GCM")
            .setCustomMetadata("kb_orig_mime", mimeType)
            .build()
        Log.d(TAG_PHOTO_STORAGE, "uploadEncrypted start photoId=$photoId bytes=${plainBytes.size}")
        val ref = storage.reference.child(path)
        ref.putBytes(encrypted, metadata).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        Log.d(TAG_PHOTO_STORAGE, "uploadEncrypted ok photoId=$photoId path=$path")
        return PhotoVideoUploadResult(storagePath = path, downloadUrl = downloadUrl)
    }

    suspend fun downloadDecrypted(
        storagePath: String,
        familyId: String,
    ): ByteArray {
        val encrypted = storage.reference.child(storagePath).getBytes(200L * 1024L * 1024L).await()
        return cryptoManager.decrypt(encrypted, familyId)
    }

    suspend fun delete(storagePath: String) {
        if (storagePath.isBlank()) return
        runCatching { storage.reference.child(storagePath).delete().await() }
            .onFailure { Log.w(TAG_PHOTO_STORAGE, "delete failed path=$storagePath ${it.message}") }
    }
}
