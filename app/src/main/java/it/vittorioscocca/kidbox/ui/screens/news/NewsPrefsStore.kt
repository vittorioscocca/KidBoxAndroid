package it.vittorioscocca.kidbox.ui.screens.news

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le scelte delle Notizie: nel telefono per partire subito, su
 * `users/{uid}.newsPrefs` per gli altri dispositivi dello stesso utente. Vince
 * la modifica più recente (`updatedAtMs`). Stesso campo e stesso formato di iOS
 * (`NewsPrefsStore.swift`).
 */
@Singleton
class NewsPrefsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kb_news_prefs", Context.MODE_PRIVATE)
    private val db by lazy { FirebaseFirestore.getInstance() }

    private val _prefs = MutableStateFlow(loadLocal() ?: NewsPrefs())
    val state: StateFlow<NewsPrefs> = _prefs.asStateFlow()

    val current: NewsPrefs get() = _prefs.value

    /** Rilegge da Firestore e tiene la versione più recente fra le due. */
    suspend fun refreshFromRemote() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            val snap = db.collection("users").document(uid).get().await()
            val raw = snap.get("newsPrefs") as? Map<*, *> ?: return
            val remote = decode(raw)
            if (remote.updatedAtMs > _prefs.value.updatedAtMs) {
                _prefs.value = remote
                saveLocal(remote)
            }
        }.onFailure { KBLog.app.warning("NewsPrefs refresh failed: ${it.message}", "NewsPrefsStore") }
    }

    suspend fun update(change: (NewsPrefs) -> NewsPrefs) {
        var next = change(_prefs.value)
        if (next.categories.isEmpty()) next = next.copy(categories = NewsCategory.entries)
        next = next.copy(updatedAtMs = System.currentTimeMillis())
        if (next == _prefs.value) return
        _prefs.value = next
        saveLocal(next)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return
        runCatching {
            db.collection("users").document(uid).set(mapOf("newsPrefs" to encode(next)), SetOptions.merge()).await()
        }.onFailure { KBLog.app.warning("NewsPrefs push failed: ${it.message}", "NewsPrefsStore") }
    }

    /** All'uscita dall'account: le scelte sono dell'utente, non del telefono. */
    fun resetOnSignOut() {
        prefs.edit().clear().apply()
        _prefs.value = NewsPrefs()
    }

    // ── Formato (uguale su iOS) ─────────────────────────────────────────────

    private fun encode(p: NewsPrefs): Map<String, Any?> = mapOf(
        "enabled" to p.enabled,
        "categories" to p.categories.map { it.id },
        "personalOffers" to p.personalOffers,
        "updatedAtMs" to p.updatedAtMs,
        "place" to p.place?.toMap(),
    )

    private fun decode(d: Map<*, *>): NewsPrefs {
        val cats = (d["categories"] as? List<*>)?.mapNotNull { NewsCategory.fromId(it as? String) }.orEmpty()
        return NewsPrefs(
            enabled = d["enabled"] as? Boolean ?: false,
            categories = if (cats.isEmpty()) NewsCategory.entries else NewsCategory.entries.filter { it in cats },
            place = NewsPlace.fromMap(d["place"] as? Map<*, *>),
            personalOffers = d["personalOffers"] as? Boolean ?: true,
            updatedAtMs = (d["updatedAtMs"] as? Number)?.toLong() ?: 0L,
        )
    }

    private fun loadLocal(): NewsPrefs? = runCatching {
        val raw = prefs.getString(KEY, null) ?: return null
        val o = JSONObject(raw)
        val placeObj = o.optJSONObject("place")
        val cats = o.optJSONArray("categories") ?: JSONArray()
        decode(
            mapOf(
                "enabled" to o.optBoolean("enabled", false),
                "categories" to (0 until cats.length()).map { cats.getString(it) },
                "personalOffers" to o.optBoolean("personalOffers", true),
                "updatedAtMs" to o.optLong("updatedAtMs", 0L),
                "place" to placeObj?.let { p -> p.keys().asSequence().associateWith { p.optString(it) } },
            ),
        )
    }.getOrNull()

    private fun saveLocal(p: NewsPrefs) {
        val o = JSONObject()
            .put("enabled", p.enabled)
            .put("categories", JSONArray(p.categories.map { it.id }))
            .put("personalOffers", p.personalOffers)
            .put("updatedAtMs", p.updatedAtMs)
        p.place?.let { o.put("place", JSONObject(it.toMap())) }
        prefs.edit().putString(KEY, o.toString()).apply()
    }

    private companion object {
        const val KEY = "prefs"
    }
}
