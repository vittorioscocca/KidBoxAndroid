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
import java.util.Locale

enum class ShareDestination(val labelIt: String, val icon: ImageVector) {
    CHAT("Chat famiglia", Icons.Default.Chat),
    NOTE("Note", Icons.Default.Note),
    TODO("To-Do", Icons.Default.CheckCircle),
    DOCUMENTS("Documenti", Icons.Default.Folder),
    SHOPPING("Lista spesa", Icons.Default.ShoppingCart),
    PHOTOS("Foto e video", Icons.Default.Photo),
    EVENT("Evento", Icons.Default.CalendarMonth),
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

