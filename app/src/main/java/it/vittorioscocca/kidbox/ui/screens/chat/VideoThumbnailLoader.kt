package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.collection.LruCache
import it.vittorioscocca.kidbox.util.fixVideoFrameOrientation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Two-level (memory + disk) cache for video thumbnails.
 *
 * Level 1 — in-process [LruCache] capped at 80 bitmaps.
 * Level 2 — JPEG files under `<cacheDir>/video_thumbs/`. Thumbnails survive
 *            app restarts; the OS evicts them when storage is low.
 *
 * The [cacheKey] parameter lets callers supply a stable identifier
 * (e.g. `"vid_<messageId>"`) that stays constant even if the Firebase
 * Storage signed URL rotates.
 */
internal object VideoThumbnailLoader {

    private val memCache = LruCache<String, Bitmap>(80)

    suspend fun load(url: String, context: Context, cacheKey: String = url): Bitmap? =
        withContext(Dispatchers.IO) {
            // ── Level 1: memory cache ─────────────────────────────────────────
            memCache.get(cacheKey)?.let { return@withContext it }

            // ── Level 2: disk cache ───────────────────────────────────────────
            val thumbDir = File(context.cacheDir, "video_thumbs").also { it.mkdirs() }
            // Sanitise the key so it is safe to use as a filename
            val safeKey = cacheKey.replace(Regex("[^a-zA-Z0-9_\\-]"), "_")
            val cacheFile = File(thumbDir, "$safeKey.jpg")
            if (cacheFile.exists()) {
                val bmp = BitmapFactory.decodeFile(cacheFile.absolutePath)
                if (bmp != null) {
                    memCache.put(cacheKey, bmp)
                    return@withContext bmp
                }
            }

            // ── Level 3: decode from source ───────────────────────────────────
            val bmp = runCatching {
                val retriever = MediaMetadataRetriever()
                // setDataSource(url, headers) works for HTTP/HTTPS but throws on
                // file:// URIs — detect the scheme and pick the right overload.
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
            }.getOrNull() ?: return@withContext null

            // Persist to disk so future app sessions skip the network round-trip
            runCatching {
                cacheFile.outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            }
            memCache.put(cacheKey, bmp)
            bmp
        }
}
