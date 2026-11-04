package it.vittorioscocca.kidbox.ui.screens.news

import org.json.JSONArray
import org.json.JSONObject

/**
 * L'edizione completa del giorno, su disco: dura il giorno solare, così riaprire
 * la scheda o l'app non richiama il server. La mattina dopo la data non coincide
 * più e si scarica quella nuova. Il file porta uid, famiglia e scelte: dopo un
 * cambio di account o di argomenti non vale.
 */
internal object NewsFeedCache {

    /**
     * Definitiva per oggi: tutte le edizioni pronte e nessuna di ripiego (tetto
     * di spesa, generazione fallita), che alla prossima apertura va riprovata.
     */
    fun isComplete(data: Map<*, *>): Boolean {
        if (data["status"] == "preparing") return false
        if ((data["pending"] as? List<*>).orEmpty().isNotEmpty()) return false
        val editions = data["editions"] as? Map<*, *> ?: return false
        val country = editions["country"] as? Map<*, *> ?: return false
        return isFinal(country) && (editions["local"] as? Map<*, *>)?.let(::isFinal) != false
    }

    private fun isFinal(edition: Map<*, *>) = edition["status"] == "ready" && edition["stale"] != true

    fun encode(uid: String, familyId: String, key: String, dateKey: String, data: Map<*, *>): String =
        JSONObject()
            .put("uid", uid)
            .put("familyId", familyId)
            .put("key", key)
            .put("dateKey", dateKey)
            .put("feed", toJson(data))
            .toString()

    /** La risposta salvata, se è di questo utente, di queste scelte e di oggi. */
    fun decode(text: String, uid: String, familyId: String, key: String, today: String): Map<*, *>? {
        val stored = runCatching { JSONObject(text) }.getOrNull() ?: return null
        val matches = stored.optString("uid") == uid && stored.optString("familyId") == familyId &&
            stored.optString("key") == key && stored.optString("dateKey") == today
        if (!matches) return null
        return stored.optJSONObject("feed")?.let(::toMap)
    }

    private fun toJson(value: Any?): Any = when (value) {
        is Map<*, *> -> JSONObject().also { json ->
            value.forEach { (k, v) -> if (k is String && v != null) json.put(k, toJson(v)) }
        }
        is List<*> -> JSONArray().also { json -> value.filterNotNull().forEach { json.put(toJson(it)) } }
        else -> value ?: JSONObject.NULL
    }

    private fun toMap(json: JSONObject): Map<String, Any?> =
        json.keys().asSequence().associateWith { fromJson(json.get(it)) }

    private fun fromJson(value: Any?): Any? = when (value) {
        is JSONObject -> toMap(value)
        is JSONArray -> (0 until value.length()).map { fromJson(value.get(it)) }
        JSONObject.NULL -> null
        else -> value
    }
}
