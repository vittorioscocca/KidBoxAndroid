package it.vittorioscocca.kidbox.data.remote.chat

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.data.remote.DocumentCryptoManager
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class ChatMediaUploadResult(
    val storagePath: String,
    val downloadUrl: String,
    val bytes: Long,
)

@Singleton
class ChatStorageService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val cryptoManager: DocumentCryptoManager,
) {
    private val storage: FirebaseStorage
        get() = FirebaseStorage.getInstance()

    suspend fun upload(
        familyId: String,
        messageId: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): ChatMediaUploadResult {
        require(auth.currentUser != null) { "Not authenticated" }
        require(bytes.isNotEmpty()) { "Empty media payload" }

        val safeName = fileName.ifBlank { "file.bin" }
        val path = "families/$familyId/chat/$messageId/$safeName.kbenc"
        val ref = storage.reference.child(path)
        val encrypted = cryptoManager.encrypt(bytes, familyId)
        val metadata = StorageMetadata.Builder()
            .setContentType("application/octet-stream")
            .setCustomMetadata("kb_encrypted", "1")
            .setCustomMetadata("kb_alg", "AES-GCM")
            .setCustomMetadata("kb_orig_name", safeName)
            .setCustomMetadata("kb_orig_mime", mimeType)
            .build()

        ref.putBytes(encrypted, metadata).await()
        val downloadUrl = ref.downloadUrl.await().toString()
        return ChatMediaUploadResult(
            storagePath = path,
            downloadUrl = downloadUrl,
            bytes = bytes.size.toLong(), // logical/original size for UI
        )
    }

    suspend fun downloadDecrypted(
        storagePath: String,
        familyId: String,
    ): ByteArray {
        val encrypted = storage.reference.child(storagePath).getBytes(250L * 1024L * 1024L).await()
        return cryptoManager.decrypt(encrypted, familyId)
    }

    fun cachePlainMediaLocally(
        familyId: String,
        messageId: String,
        fileName: String,
        bytes: ByteArray,
    ): String {
        val baseDir = File(context.filesDir, "chat_media/$familyId")
        if (!baseDir.exists()) baseDir.mkdirs()
        val safeName = fileName.ifBlank { "file.bin" }.replace('/', '_')
        val output = File(baseDir, "${messageId}_$safeName")
        output.writeBytes(bytes)
        return output.absolutePath
    }

    fun cleanupCachedMedia(
        familyId: String,
        keepAbsolutePaths: Set<String>,
    ) {
        val baseDir = File(context.filesDir, "chat_media/$familyId")
        if (!baseDir.exists() || !baseDir.isDirectory) return
        baseDir.listFiles()
            ?.filter { it.isFile }
            ?.forEach { file ->
                if (!keepAbsolutePaths.contains(file.absolutePath)) {
                    runCatching { file.delete() }
                }
            }
    }

    suspend fun delete(storagePath: String) {
        if (storagePath.isBlank()) return
        runCatching { storage.reference.child(storagePath).delete().await() }
    }

    companion object {
        fun defaultFileInfo(type: ChatMessageType): Pair<String, String> = when (type) {
            ChatMessageType.PHOTO -> "photo.jpg" to "image/jpeg"
            ChatMessageType.VIDEO -> "video.mp4" to "video/mp4"
            ChatMessageType.AUDIO -> "audio.m4a" to "audio/x-m4a"
            ChatMessageType.DOCUMENT -> "document" to "application/octet-stream"
            ChatMessageType.MEDIA_GROUP -> "photo.jpg" to "image/jpeg"
            ChatMessageType.CONTACT -> "contact.json" to "application/json"
            ChatMessageType.LOCATION -> "location.json" to "application/json"
            ChatMessageType.TEXT -> "file.bin" to "application/octet-stream"
        }
    }
}
