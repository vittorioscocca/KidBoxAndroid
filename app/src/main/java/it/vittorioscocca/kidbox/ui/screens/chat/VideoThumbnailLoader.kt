package it.vittorioscocca.kidbox.ui.screens.chat

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.collection.LruCache
import it.vittorioscocca.kidbox.util.fixVideoFrameOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal object VideoThumbnailLoader {
    private val cache = LruCache<String, Bitmap>(80)

    suspend fun load(url: String, cacheKey: String = url): Bitmap? = withContext(Dispatchers.IO) {
        cache.get(cacheKey)?.let { return@withContext it }
        runCatching {
            val retriever = MediaMetadataRetriever()
            // setDataSource(url, headers) works for HTTP/HTTPS but throws on file:// URIs.
            // Detect local files by scheme and use the path overload instead.
            val scheme = Uri.parse(url).scheme?.lowercase()
            if (scheme == "file" || scheme == null || url.startsWith("/")) {
                val path = if (url.startsWith("file://")) Uri.parse(url).path!! else url
                retriever.setDataSource(path)
            } else {
                retriever.setDataSource(url, hashMapOf())
            }
            val frame = retriever.getFrameAtTime(0, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?.let { fixVideoFrameOrientation(it, retriever) }
            retriever.release()
            frame
        }.getOrNull()?.also { cache.put(cacheKey, it) }
    }
}
