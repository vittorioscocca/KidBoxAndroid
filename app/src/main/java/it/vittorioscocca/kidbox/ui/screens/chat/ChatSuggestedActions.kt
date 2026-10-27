package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Context
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Note
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.chat.model.ChatMessageType
import it.vittorioscocca.kidbox.domain.save.AddressDetector
import it.vittorioscocca.kidbox.domain.save.SaveAction
import it.vittorioscocca.kidbox.domain.save.SaveClassifier
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * «Azioni suggerite»: la voce del foglio a pressione lunga (MessageActionDialog)
 * che propone le azioni per un messaggio (testo, foto, video, documento); un
 * tocco le esegue subito. Gemello di `ChatSuggestedActions.swift`.
 * Ogni tipo si accende/spegne da Impostazioni > Messaggi > Azioni suggerite; un
 * tipo acceso ma mai toccato per 30 giorni si spegne da solo (l'orologio parte la
 * prima volta che compare e riparte a ogni tocco e a ogni riaccensione).
 */
enum class SuggestedActionKind(
    val key: String,
    val labelRes: Int,
    val icon: ImageVector,
    val tint: Color,
) {
    CALENDAR("calendar", R.string.chat_suggested_calendar, Icons.Default.CalendarMonth, Color(0xFFFF3B30)),
    GROCERY("grocery", R.string.chat_suggested_grocery, Icons.Default.ShoppingCart, Color(0xFF34C759)),
    TODO("todo", R.string.chat_suggested_todo, Icons.Default.CheckCircle, Color(0xFFFF9500)),
    NOTE("note", R.string.chat_suggested_note, Icons.Default.Note, Color(0xFFFFCC00)),
    MAP("map", R.string.chat_suggested_map, Icons.Default.Map, Color(0xFF007AFF)),
    PHOTOS("photos", R.string.chat_suggested_photos, Icons.Default.PhotoLibrary, Color(0xFF32ADE6)),
    DOCUMENTS("documents", R.string.chat_suggested_documents, Icons.Default.Folder, Color(0xFF9973D9)),
}

sealed interface SuggestedChatAction {
    val kind: SuggestedActionKind

    data class Save(val action: SaveAction) : SuggestedChatAction {
        override val kind
            get() = when (action) {
                is SaveAction.Event -> SuggestedActionKind.CALENDAR
                is SaveAction.Grocery -> SuggestedActionKind.GROCERY
                is SaveAction.Todo -> SuggestedActionKind.TODO
                is SaveAction.Note -> SuggestedActionKind.NOTE
            }
    }

    data class ShowMap(val address: String) : SuggestedChatAction {
        override val kind get() = SuggestedActionKind.MAP
    }

    /** Foto o video → «Foto e video»; documento → «Documenti». */
    data class SaveMedia(override val kind: SuggestedActionKind, val message: UiChatMessage) : SuggestedChatAction
}

/** Preferenze per dispositivo, in `kidbox_prefs` come il resto delle impostazioni messaggi. */
object SuggestedActionsPrefs {
    const val STALE_AFTER_MS = 30L * 24 * 3600 * 1000

    private val state = MutableStateFlow<Set<SuggestedActionKind>?>(null)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)

    private fun enabledKey(kind: SuggestedActionKind) = "kb_chatSuggested_${kind.key}"
    private fun lastUsedKey(kind: SuggestedActionKind) = "kb_chatSuggested_${kind.key}_lastUsed"

    /** Tipi accesi, osservabili: i banner spariscono appena si spegne un tipo. */
    fun enabled(context: Context): StateFlow<Set<SuggestedActionKind>?> {
        if (state.value == null) {
            val p = prefs(context)
            state.value = SuggestedActionKind.entries.filter { p.getBoolean(enabledKey(it), true) }.toSet()
        }
        return state
    }

    fun setEnabled(context: Context, kind: SuggestedActionKind, on: Boolean) {
        prefs(context).edit().putBoolean(enabledKey(kind), on).apply()
        if (on) markUsed(context, kind) // riaccendere fa ripartire i 30 giorni
        val current = enabled(context).value.orEmpty()
        state.value = if (on) current + kind else current - kind
    }

    /** Tocco del banner: riparte l'orologio dei 30 giorni. */
    fun markUsed(context: Context, kind: SuggestedActionKind, now: Long = System.currentTimeMillis()) {
        prefs(context).edit().putLong(lastUsedKey(kind), now).apply()
    }

    /**
     * Il tipo è comparso: se l'orologio non è mai partito parte ora; se sono
     * passati 30 giorni senza un tocco il tipo si spegne. true = resta visibile.
     */
    fun noteShown(context: Context, kind: SuggestedActionKind, now: Long = System.currentTimeMillis()): Boolean {
        if (kind !in enabled(context).value.orEmpty()) return false
        val last = prefs(context).getLong(lastUsedKey(kind), 0L)
        if (last == 0L) {
            markUsed(context, kind, now)
            return true
        }
        if (isStale(last, now)) {
            setEnabled(context, kind, false)
            return false
        }
        return true
    }

    fun isStale(lastUsedMillis: Long, now: Long): Boolean = now - lastUsedMillis > STALE_AFTER_MS
}

/** Banner per un testo: mappa se c'è un indirizzo, poi le regole di «Salva in». Al massimo tre. */
object SuggestedActionsFinder {
    /** Banner per un media: uno solo, secondo il tipo. */
    fun forMedia(message: UiChatMessage): List<SuggestedChatAction> = when (message.type) {
        ChatMessageType.PHOTO, ChatMessageType.VIDEO ->
            listOf(SuggestedChatAction.SaveMedia(SuggestedActionKind.PHOTOS, message))
        ChatMessageType.DOCUMENT -> listOf(SuggestedChatAction.SaveMedia(SuggestedActionKind.DOCUMENTS, message))
        else -> emptyList()
    }

    private val cache = object : LinkedHashMap<String, List<SuggestedChatAction>>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<SuggestedChatAction>>?) = size > 300
    }

    @Synchronized
    fun find(messageId: String, text: String): List<SuggestedChatAction> {
        val key = "$messageId|${text.hashCode()}"
        cache[key]?.let { return it }
        val trimmed = text.trim()
        val out = ArrayList<SuggestedChatAction>()
        if (trimmed.isNotEmpty()) {
            AddressDetector.firstAddress(trimmed)?.let { out += SuggestedChatAction.ShowMap(it) }
            SaveClassifier.classify(trimmed, forChips = true).actions.forEach { out += SuggestedChatAction.Save(it) }
        }
        return out.take(3).also { cache[key] = it }
    }
}
