package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageException
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.tasks.await

/**
 * Cache avatar mittente (parità iOS [SenderAvatarCache]): memoria, disco, poi Storage
 * `users/{uid}/avatar.jpg` e `families/{familyId}/avatars/{uid}.jpg`.
 */
internal object SenderAvatarCache {
    private sealed class Entry {
        data class Image(val bitmap: Bitmap) : Entry()
        data object Failed : Entry()
    }

    private val memory = ConcurrentHashMap<String, Entry>()
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    fun peek(uid: String): Bitmap? {
        val entry = memory[uid] ?: return null
        return when (entry) {
            is Entry.Image -> entry.bitmap
            Entry.Failed -> null
        }
    }

    suspend fun load(
        context: Context,
        uid: String,
        familyId: String,
    ): Bitmap? {
        if (uid.isBlank()) return null
        when (val cached = memory[uid]) {
            is Entry.Image -> return cached.bitmap
            Entry.Failed -> return null
            null -> Unit
        }

        val disk = diskFile(context, uid)
        if (disk.exists()) {
            runCatching {
                BitmapFactory.decodeFile(disk.absolutePath)
            }.getOrNull()?.let { bitmap ->
                memory[uid] = Entry.Image(bitmap)
                return bitmap
            }
        }

        if (!inFlight.add(uid)) {
            return peek(uid)
        }
        try {
            val storage = FirebaseStorage.getInstance()
            val paths = buildList {
                add("users/$uid/avatar.jpg")
                if (familyId.isNotBlank()) add("families/$familyId/avatars/$uid.jpg")
            }
            for (path in paths) {
                val bitmap = runCatching {
                    val bytes = storage.reference.child(path).getBytes(5L * 1024L * 1024L).await()
                    BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.also { bmp ->
                        disk.parentFile?.mkdirs()
                        runCatching { disk.writeBytes(bytes) }
                        memory[uid] = Entry.Image(bmp)
                    }
                }.getOrNull()
                if (bitmap != null) return bitmap
            }
            memory[uid] = Entry.Failed
            return null
        } catch (e: StorageException) {
            if (e.errorCode == StorageException.ERROR_OBJECT_NOT_FOUND) {
                memory[uid] = Entry.Failed
            }
            return null
        } catch (_: Exception) {
            return null
        } finally {
            inFlight.remove(uid)
        }
    }

    private fun diskFile(context: Context, uid: String): File =
        File(context.cacheDir, "sender_avatars/$uid.jpg")
}
