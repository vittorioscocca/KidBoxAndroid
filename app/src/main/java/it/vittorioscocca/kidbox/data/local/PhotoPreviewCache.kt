package it.vittorioscocca.kidbox.data.local

import android.content.Context
import android.graphics.Bitmap
import it.vittorioscocca.kidbox.util.decodeSampledFromFile
import java.io.File

/**
 * Cache su disco per le anteprime a risoluzione media generate on-demand
 * (porting di `PhotoPreviewCache` iOS). Chiave: `photoId` + bucket di risoluzione.
 * I file vivono nella cache dir dell'app, quindi possono essere rigenerati.
 */
object PhotoPreviewCache {
    private const val DIR_NAME = "KBPhotoPreviews"
    private const val DEFAULT_MAX_BYTES = 200L * 1024 * 1024 // 200 MB

    private fun dir(context: Context): File =
        File(context.cacheDir, DIR_NAME).apply { mkdirs() }

    private fun fileFor(context: Context, photoId: String, bucket: Int): File =
        File(dir(context), "${photoId}_$bucket.jpg")

    fun load(context: Context, photoId: String, bucket: Int): Bitmap? {
        val f = fileFor(context, photoId, bucket)
        if (!f.exists()) return null
        return decodeSampledFromFile(f, 1024)
    }

    fun store(context: Context, bytes: ByteArray, photoId: String, bucket: Int) {
        runCatching { fileFor(context, photoId, bucket).writeBytes(bytes) }
    }

    /** Rimuove l'intera cache delle anteprime (logout / uscita famiglia). */
    fun clearAll(context: Context) {
        runCatching { dir(context).deleteRecursively() }
    }

    /**
     * Sfratta le anteprime più vecchie quando la cache supera il budget.
     * È solo una cache: i file rimossi vengono rigenerati on-demand.
     */
    fun trim(context: Context, maxBytes: Long = DEFAULT_MAX_BYTES) {
        val files = dir(context).listFiles()?.filter { it.isFile } ?: return
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) return
        files.sortedBy { it.lastModified() }.forEach { f ->
            if (total <= maxBytes) return
            val len = f.length()
            if (f.delete()) total -= len
        }
    }
}
