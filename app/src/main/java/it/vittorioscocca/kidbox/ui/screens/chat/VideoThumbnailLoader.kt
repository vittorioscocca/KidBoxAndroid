package it.vittorioscocca.kidbox.ui.screens.chat

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object VideoThumbnailLoader {
    private val cache = LruCache<String, Bitmap>(80)

    suspend fun load(url: String): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext it }
        runCatching {
            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(url, hashMapOf())
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            retriever.release()
            frame
        }.getOrNull()?.also { cache.put(url, it) }
    }
}
