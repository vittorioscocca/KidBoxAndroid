package it.vittorioscocca.kidbox.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream

/**
 * Returns a copy of [bitmap] rotated/flipped to match the EXIF orientation stored
 * in [exifOrientation] (one of the [ExifInterface.ORIENTATION_*] constants).
 * Returns the original bitmap unchanged when no transformation is needed.
 */
fun applyExifOrientation(bitmap: Bitmap, exifOrientation: Int): Bitmap {
    val matrix = Matrix()
    when (exifOrientation) {
        ExifInterface.ORIENTATION_ROTATE_90    -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180   -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270   -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL   -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE    -> { matrix.postRotate(90f); matrix.preScale(-1f, 1f) }
        ExifInterface.ORIENTATION_TRANSVERSE   -> { matrix.postRotate(-90f); matrix.preScale(-1f, 1f) }
        else -> return bitmap                          // ORIENTATION_NORMAL or unknown
    }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
}

/**
 * Reads EXIF orientation from a JPEG/PNG file at [filePath] and applies it to [bitmap].
 */
fun fixBitmapOrientationFromFile(bitmap: Bitmap, filePath: String): Bitmap =
    runCatching {
        val orientation = ExifInterface(filePath)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        applyExifOrientation(bitmap, orientation)
    }.getOrDefault(bitmap)

/**
 * Reads EXIF orientation from a [ByteArray] and applies it to [bitmap].
 * Useful for images decoded with [BitmapFactory.decodeByteArray] where the raw
 * bytes still carry the original EXIF metadata.
 */
fun fixBitmapOrientationFromBytes(bitmap: Bitmap, bytes: ByteArray): Bitmap =
    runCatching {
        val orientation = ExifInterface(bytes.inputStream())
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        applyExifOrientation(bitmap, orientation)
    }.getOrDefault(bitmap)

/**
 * Reads EXIF orientation from an [InputStream] and applies it to [bitmap].
 */
fun fixBitmapOrientationFromStream(bitmap: Bitmap, stream: InputStream): Bitmap =
    runCatching {
        val orientation = ExifInterface(stream)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        applyExifOrientation(bitmap, orientation)
    }.getOrDefault(bitmap)

/**
 * Decodes a JPEG/PNG file downsampled so that neither dimension exceeds [maxPx].
 * Uses a two-pass approach (inJustDecodeBounds + inSampleSize) to avoid loading
 * the full-resolution bitmap into memory unnecessarily.
 */
fun decodeSampledFromFile(file: File, maxPx: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, opts)
    opts.inSampleSize = calcInSampleSize(opts, maxPx)
    opts.inJustDecodeBounds = false
    return runCatching { BitmapFactory.decodeFile(file.absolutePath, opts) }.getOrNull()
}

/**
 * Decodes a JPEG/PNG byte array downsampled so that neither dimension exceeds [maxPx].
 */
fun decodeSampledFromBytes(bytes: ByteArray, maxPx: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    opts.inSampleSize = calcInSampleSize(opts, maxPx)
    opts.inJustDecodeBounds = false
    return runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) }.getOrNull()
}

private fun calcInSampleSize(opts: BitmapFactory.Options, maxPx: Int): Int {
    val h = opts.outHeight
    val w = opts.outWidth
    var sample = 1
    while ((h / (sample * 2)) >= maxPx || (w / (sample * 2)) >= maxPx) {
        sample *= 2
    }
    return sample
}

/**
 * Raddrizza il fotogramma estratto da un video usando `METADATA_KEY_VIDEO_ROTATION`.
 *
 * Il punto delicato è che **non tutte le versioni di Android si comportano allo
 * stesso modo**: [MediaMetadataRetriever.getFrameAtTime] su alcune restituisce i
 * pixel grezzi non ruotati, su altre applica già la rotazione del contenitore.
 * Ruotare a scatola chiusa, come si faceva prima, raddrizzava il fotogramma nel
 * primo caso e lo storceva nel secondo — ed è per questo che le anteprime dei
 * video giravano storte.
 *
 * Qui non si indovina: si confrontano le dimensioni del bitmap con quelle
 * **codificate** nel video (che sono sempre pre-rotazione). Se il bitmap risulta
 * già trasposto, la piattaforma ha fatto il lavoro e non si tocca nulla.
 *
 * Per una rotazione di 180° le dimensioni non cambiano e il confronto non può
 * dire nulla: lì si applica comunque la rotazione, come prima. È il caso raro —
 * i video da telefono ruotano quasi sempre di 90° o 270°.
 */
fun fixVideoFrameOrientation(bitmap: Bitmap, retriever: MediaMetadataRetriever): Bitmap {
    val degrees = ((retriever
        .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)
        ?.toIntOrNull() ?: 0) % 360 + 360) % 360
    if (degrees == 0) return bitmap

    val isQuarterTurn = degrees % 180 != 0
    if (isQuarterTurn) {
        val encodedWidth = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull()
        val encodedHeight = retriever
            .extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull()
        // Il confronto è utile solo su video non quadrati: con lati uguali la
        // trasposizione è invisibile.
        if (encodedWidth != null && encodedHeight != null && encodedWidth != encodedHeight) {
            val alreadyRotated = bitmap.width == encodedHeight && bitmap.height == encodedWidth
            if (alreadyRotated) return bitmap
        }
    }

    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    return runCatching {
        Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }.getOrDefault(bitmap)
}
