package it.vittorioscocca.kidbox.util

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Android Download / document provider spesso espone segmenti tipo `msf:188` (non è un nome file).
 * Usare [queryDisplayName] + [finalFileNameForUpload] all'invio, e [cachePreviewFileName] + [mimeFromBytes] all'apertura.
 */
object ChatDocumentFileNaming {

    private val MSF_PLACEHOLDER = Regex("(?i)^msf:\\d+$")

    fun isMsfPlaceholder(name: String): Boolean = MSF_PLACEHOLDER.matches(name.trim())

    fun queryDisplayName(resolver: ContentResolver, uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (i < 0) null else c.getString(i)
        }?.trim()?.takeIf { it.isNotEmpty() }
    }.getOrNull()

    fun sniffExtensionFromBytes(bytes: ByteArray): String {
        if (bytes.isEmpty()) return "bin"
        val n = minOf(16, bytes.size)
        val b0 = bytes[0].toInt() and 0xFF
        val b1 = if (n > 1) bytes[1].toInt() and 0xFF else 0
        val b2 = if (n > 2) bytes[2].toInt() and 0xFF else 0
        val b3 = if (n > 3) bytes[3].toInt() and 0xFF else 0
        if (n >= 4 && b0 == 0x25 && b1 == 0x50 && b2 == 0x44 && b3 == 0x46) return "pdf"
        if (n >= 3 && b0 == 0xFF && b1 == 0xD8 && b2 == 0xFF) return "jpg"
        if (n >= 8 && b0 == 0x89 && b1 == 0x50 && b2 == 0x4E && b3 == 0x47) return "png"
        if (n >= 6 && b0 == 0x47 && b1 == 0x49 && b2 == 0x46 && b3 == 0x38) return "gif"
        if (n >= 12 && b0 == 0x52 && b1 == 0x49 && b2 == 0x46 && b3 == 0x46) {
            val b8 = bytes[8].toInt() and 0xFF
            val b9 = bytes[9].toInt() and 0xFF
            val b10 = bytes[10].toInt() and 0xFF
            val b11 = bytes[11].toInt() and 0xFF
            if (b8 == 0x57 && b9 == 0x45 && b10 == 0x42 && b11 == 0x50) return "webp"
        }
        if (n >= 2 && b0 == 0x50 && b1 == 0x4B) return "zip"
        return "bin"
    }

    fun mimeFromBytes(bytes: ByteArray): String = when (sniffExtensionFromBytes(bytes)) {
        "pdf" -> "application/pdf"
        "jpg" -> "image/jpeg"
        "png" -> "image/png"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "zip" -> "application/zip"
        else -> "application/octet-stream"
    }

    /**
     * Nome file per upload Storage + campo [text] del messaggio (come iOS: nome leggibile + estensione).
     */
    fun finalFileNameForUpload(displayName: String?, declaredMime: String, bytes: ByteArray): String {
        val mime = declaredMime.ifBlank { "application/octet-stream" }
        val extFromMime = MimeTypeMap.getSingleton().getExtensionFromMimeType(mime)?.lowercase()?.trim()
        val sniffed = sniffExtensionFromBytes(bytes)
        val ext = extFromMime?.takeIf { it.isNotBlank() && it != "bin" } ?: sniffed

        val raw = displayName?.trim().orEmpty()
        val usable = when {
            raw.isEmpty() -> null
            isMsfPlaceholder(raw) -> null
            else -> raw
        }

        fun stripUnsafe(s: String) = s.replace('/', '_').take(180)

        if (usable != null) {
            val s = stripUnsafe(usable)
            return if (s.contains('.')) s else "$s.$ext"
        }
        return "documento.$ext"
    }

    /**
     * Nome file in cache per [FileProvider] + intent VIEW (evita `msf:188` senza estensione).
     */
    fun cachePreviewFileName(messageId: String, displayText: String?, bytes: ByteArray): String {
        val raw = displayText?.trim().orEmpty()
        val ext = sniffExtensionFromBytes(bytes)
        if (raw.isEmpty() || isMsfPlaceholder(raw) || !raw.contains('.')) {
            return "kb_doc_${messageId}.$ext"
        }
        return raw.replace('/', '_').take(180)
    }

    fun decodeStoragePathSegment(encoded: String): String = runCatching {
        URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
    }.getOrDefault(encoded)

    fun displayNameForSaveToDownloads(url: String, bytes: ByteArray, fallbackTs: Long): String {
        val raw = runCatching {
            val path = Uri.parse(url).path ?: return@runCatching ""
            path.substringAfterLast('/').substringBefore('?')
        }.getOrDefault("")
        val decoded = decodeStoragePathSegment(raw).trim()
        val ext = sniffExtensionFromBytes(bytes)
        return when {
            decoded.isNotBlank() && !isMsfPlaceholder(decoded) && decoded.contains('.') ->
                decoded.replace('/', '_').take(200)
            decoded.isNotBlank() && !isMsfPlaceholder(decoded) ->
                "${decoded.replace('/', '_').take(160)}.$ext"
            else -> "KidBox_$fallbackTs.$ext"
        }
    }
}
