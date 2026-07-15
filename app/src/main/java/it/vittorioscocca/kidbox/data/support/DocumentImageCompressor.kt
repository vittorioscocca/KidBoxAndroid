package it.vittorioscocca.kidbox.data.support

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import java.io.ByteArrayOutputStream
import kotlin.math.max

/**
 * Compressione conservativa delle immagini caricate come documenti/allegati.
 * Riduce foto/scan ad alta risoluzione prima di cifratura e upload, per
 * risparmiare storage e banda, preservando la leggibilità di referti e testo
 * (utile anche all'OCR). Non tocca PDF né altri formati.
 *
 * Speculare all'implementazione iOS (DocumentImageCompressor.swift).
 */
object DocumentImageCompressor {

    /** Lato lungo massimo (px). Sopra questa soglia l'immagine viene ridimensionata. */
    const val MAX_DIMENSION = 3000

    /** Qualità JPEG in uscita (0-100). 80 = compressione conservativa. */
    const val JPEG_QUALITY = 80

    /** Sotto questa dimensione non vale la pena comprimere. */
    const val MIN_BYTES_TO_CONSIDER = 1_200_000 // ~1.2 MB

    data class Output(
        val bytes: ByteArray,
        val fileName: String,
        val mimeType: String,
        val didCompress: Boolean,
    )

    fun isCompressibleImage(mimeType: String?, fileName: String): Boolean {
        if (mimeType != null && mimeType.startsWith("image/")) return true
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return ext in setOf("jpg", "jpeg", "png", "heic", "heif", "webp")
    }

    /**
     * Restituisce una versione compressa dell'immagine se conviene, altrimenti
     * i byte originali invariati. La compressione è lossy e produce sempre JPEG.
     */
    fun compressIfNeeded(bytes: ByteArray, fileName: String, mimeType: String?): Output {
        val mime = mimeType ?: ""
        val unchanged = Output(bytes, fileName, mime, false)

        if (!isCompressibleImage(mimeType, fileName)) return unchanged
        if (bytes.size < MIN_BYTES_TO_CONSIDER) return unchanged

        // Leggi solo le dimensioni (senza decodificare il bitmap completo).
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val srcW = boundsOpts.outWidth
        val srcH = boundsOpts.outHeight
        if (srcW <= 0 || srcH <= 0) return unchanged

        val longest = max(srcW, srcH)

        // inSampleSize: downscale a potenze di 2 in fase di decode per contenere la memoria.
        var sample = 1
        while (longest / (sample * 2) >= MAX_DIMENSION) sample *= 2

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts) ?: return unchanged

        // Scala fine se ancora sopra il limite dopo il sub-sampling.
        val decLongest = max(decoded.width, decoded.height)
        val scaled = if (decLongest > MAX_DIMENSION) {
            val scale = MAX_DIMENSION.toFloat() / decLongest
            val w = (decoded.width * scale).toInt().coerceAtLeast(1)
            val h = (decoded.height * scale).toInt().coerceAtLeast(1)
            Bitmap.createScaledBitmap(decoded, w, h, true).also {
                if (it !== decoded) decoded.recycle()
            }
        } else decoded

        // Appiattisci su sfondo bianco: JPEG non ha canale alpha e i PNG/WebP
        // trasparenti diventerebbero neri. Coerente con iOS (fill bianco).
        val flattened = Bitmap.createBitmap(scaled.width, scaled.height, Bitmap.Config.ARGB_8888)
        Canvas(flattened).apply {
            drawColor(Color.WHITE)
            drawBitmap(scaled, 0f, 0f, null)
        }
        if (flattened !== scaled) scaled.recycle()

        val out = ByteArrayOutputStream()
        flattened.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
        flattened.recycle()
        val jpeg = out.toByteArray()

        // Tieni la versione compressa solo se fa risparmiare spazio reale.
        if (jpeg.size >= bytes.size) return unchanged

        val base = fileName.substringBeforeLast('.', fileName)
        val newName = if (base.isBlank()) "image.jpg" else "$base.jpg"
        return Output(jpeg, newName, "image/jpeg", true)
    }
}
