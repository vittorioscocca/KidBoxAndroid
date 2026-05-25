package it.vittorioscocca.kidbox.data.chat.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Mirror Android del tipo iOS `ChatMention`.
 *
 * Rappresenta un membro citato all'interno del testo di un messaggio chat.
 * - [uid] punta al `userId` del membro citato (Firebase Auth uid).
 * - [displayName] è lo snapshot del nome al momento dell'invio; viene usato sia
 *   per localizzare il token `@<displayName>` nel testo (per evidenziarlo) sia
 *   come fallback quando il membro non fa più parte della famiglia.
 */
data class ChatMention(
    val uid: String,
    val displayName: String,
)

/** Serializza una lista di mention nel JSON `[{"uid":"...","displayName":"..."}]`. */
fun List<ChatMention>.toMentionsJsonOrNull(): String? {
    if (isEmpty()) return null
    val arr = JSONArray()
    forEach { mention ->
        val obj = JSONObject()
        obj.put("uid", mention.uid)
        obj.put("displayName", mention.displayName)
        arr.put(obj)
    }
    return arr.toString()
}

/** Deserializza il JSON `[{"uid":"...","displayName":"..."}]` in una lista. */
fun String?.toChatMentions(): List<ChatMention> {
    if (this.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(this)
        buildList {
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val uid = obj.optString("uid")
                if (uid.isBlank()) continue
                val displayName = obj.optString("displayName")
                add(ChatMention(uid = uid, displayName = displayName))
            }
        }
    }.getOrDefault(emptyList())
}

/**
 * Converte una lista di `Map<String, Any?>` ricevuta da Firestore (campo
 * `mentions`) nel JSON compatto persistito localmente. Restituisce `null` se
 * nessuna voce è valida così da non scrivere `"[]"` nel DB.
 */
fun parseMentionsFromFirestore(raw: List<*>?): String? {
    if (raw.isNullOrEmpty()) return null
    val mentions = raw.mapNotNull { entry ->
        @Suppress("UNCHECKED_CAST")
        val map = (entry as? Map<String, Any?>) ?: return@mapNotNull null
        val uid = (map["uid"] as? String).orEmpty()
        if (uid.isBlank()) return@mapNotNull null
        val displayName = (map["displayName"] as? String).orEmpty()
        ChatMention(uid = uid, displayName = displayName)
    }
    return mentions.toMentionsJsonOrNull()
}
