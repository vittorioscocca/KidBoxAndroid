package it.vittorioscocca.kidbox.ui.share

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import it.vittorioscocca.kidbox.R
import java.util.Locale

enum class ShareDestination(val labelRes: Int, val icon: ImageVector) {
    CHAT(R.string.share_destination_chat, Icons.Default.Chat),
    NOTE(R.string.share_destination_note, Icons.Default.Note),
    TODO(R.string.share_destination_todo, Icons.Default.CheckCircle),
    DOCUMENTS(R.string.share_destination_documents, Icons.Default.Folder),
    SHOPPING(R.string.share_destination_shopping, Icons.Default.ShoppingCart),
    PHOTOS(R.string.share_destination_photos, Icons.Default.Photo),
    EVENT(R.string.share_destination_event, Icons.Default.CalendarMonth),
}

object ShareDestinationSuggester {
    fun suggest(content: ShareContentType): List<ShareDestination> {
        return when (content) {
            is ShareContentType.TextContent -> textSuggestions(content.text)
            is ShareContentType.UrlContent -> listOf(ShareDestination.CHAT, ShareDestination.NOTE, ShareDestination.TODO)
            is ShareContentType.ImageContent -> listOf(ShareDestination.CHAT, ShareDestination.PHOTOS)
            is ShareContentType.VideoContent -> listOf(ShareDestination.CHAT, ShareDestination.PHOTOS)
            is ShareContentType.PdfContent -> listOf(ShareDestination.CHAT, ShareDestination.DOCUMENTS)
            is ShareContentType.FileContent -> listOf(ShareDestination.CHAT, ShareDestination.DOCUMENTS)
            ShareContentType.Unknown -> listOf(ShareDestination.CHAT)
        }
    }

    private fun textSuggestions(text: String): List<ShareDestination> {
        val lines = text.lines().map { it.trim() }.filter { it.isNotBlank() }
        val isShort = lines.size <= 1 && text.length < 120
        val looksList = lines.size >= 2
        val hasDateHint = hasDateLikeHint(text)
        return when {
            looksList -> listOf(ShareDestination.CHAT, ShareDestination.NOTE, ShareDestination.SHOPPING)
            hasDateHint -> listOf(ShareDestination.CHAT, ShareDestination.NOTE, ShareDestination.EVENT)
            isShort -> listOf(ShareDestination.CHAT, ShareDestination.NOTE, ShareDestination.TODO)
            else -> listOf(ShareDestination.CHAT, ShareDestination.NOTE)
        }
    }

    private fun hasDateLikeHint(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        val ddmmyyyy = Regex("""\b\d{1,2}/\d{1,2}/\d{2,4}\b""").containsMatchIn(t)
        val words = listOf("domani", "dopodomani", "lunedì", "lunedi", "martedì", "martedi", "mercoledì", "mercoledi", "giovedì", "giovedi", "venerdì", "venerdi", "sabato", "domenica")
        return ddmmyyyy || words.any { t.contains(it) }
    }
}

