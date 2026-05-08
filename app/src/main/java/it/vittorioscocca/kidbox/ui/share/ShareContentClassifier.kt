package it.vittorioscocca.kidbox.ui.share

import android.content.Intent
import android.net.Uri
import java.util.Locale

sealed class ShareContentType {
    data class TextContent(val text: String) : ShareContentType()
    data class UrlContent(val url: String) : ShareContentType()
    data class ImageContent(val uri: Uri) : ShareContentType()
    data class VideoContent(val uri: Uri) : ShareContentType()
    data class PdfContent(val uri: Uri) : ShareContentType()
    data class FileContent(val uri: Uri, val mimeType: String) : ShareContentType()
    object Unknown : ShareContentType()
}

data class ShareIncomingPayload(
    val contentType: ShareContentType,
    val allUris: List<Uri>,
    val mimeType: String?,
)

object ShareContentClassifier {

    fun fromIntent(intent: Intent): ShareIncomingPayload {
        val action = intent.action
        val mime = intent.type
        @Suppress("DEPRECATION")
        val allUris = when (action) {
            Intent.ACTION_SEND_MULTIPLE ->
                intent.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)?.toList().orEmpty()
            Intent.ACTION_SEND ->
                listOfNotNull(intent.getParcelableExtra<Uri>(Intent.EXTRA_STREAM))
            else -> emptyList()
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
        if (text.isNotBlank() && allUris.isEmpty()) {
            return if (looksLikeUrl(text)) {
                ShareIncomingPayload(ShareContentType.UrlContent(text), emptyList(), mime)
            } else {
                ShareIncomingPayload(ShareContentType.TextContent(text), emptyList(), mime)
            }
        }

        val firstUri = allUris.firstOrNull()
        if (firstUri != null) {
            val resolvedMime = (mime ?: "").lowercase(Locale.ROOT)
            val ext = firstUri.toString().substringAfterLast('.', "").lowercase(Locale.ROOT)
            val type = when {
                resolvedMime.startsWith("image/") -> ShareContentType.ImageContent(firstUri)
                resolvedMime.startsWith("video/") -> ShareContentType.VideoContent(firstUri)
                resolvedMime == "application/pdf" || ext == "pdf" -> ShareContentType.PdfContent(firstUri)
                resolvedMime.startsWith("application/") -> ShareContentType.FileContent(firstUri, resolvedMime)
                resolvedMime.isNotBlank() -> ShareContentType.FileContent(firstUri, resolvedMime)
                else -> ShareContentType.FileContent(firstUri, "application/octet-stream")
            }
            return ShareIncomingPayload(type, allUris, mime)
        }

        return ShareIncomingPayload(ShareContentType.Unknown, emptyList(), mime)
    }

    private fun looksLikeUrl(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        return t.startsWith("http://") || t.startsWith("https://")
    }
}

