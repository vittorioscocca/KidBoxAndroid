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
 * Three-level (memory + disk + decode) cache for video thumbnails.
 *
 * Level 1 — in-process [LruCache] capped at 80 bitmaps.
 * Level 2 — JPEG files under `<cacheDir>/video_thumbs/`. Thumbnails survive
 *            app restarts; the OS evicts them when storage is low.
 * Level 3 — [MediaMetadataRetriever] decode from [source].
 *
 * [source] may be an absolute file path, a `file://` URI, or an `https://` URL.
 * [cacheKey] should be a stable identifier (e.g. `"vid_<messageId>"`) so the same
 * bitmap is reused even if [source] rotates (signed URL refresh, local→remote switch).
 */
internal object VideoThumbnailLoader {

    private val memCache = LruCache<String, Bitmap>(80)

    suspend fun load(source: String, context: Context, cacheKey: String = source): Bitmap? =
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
                // setDataSource(String, headers) works for HTTP/HTTPS but throws on
                // plain file paths — detect the scheme and pick the right overload.
                val scheme = Uri.parse(source).scheme?.lowercase()
                if (scheme == "file" || scheme == null || source.startsWith("/")) {
                    val path = if (source.startsWith("file://")) Uri.parse(source).path!! else source
                    retriever.setDataSource(path)
                } else {
                    retriever.setDataSource(source, hashMapOf())
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
