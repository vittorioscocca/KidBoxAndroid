package it.vittorioscocca.kidbox.ui.screens.news

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.SetOptions
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Le scelte della famiglia per una famiglia precisa. [loaded] è falso finché
 * non si sa com'è la famiglia (né cache né server): la scheda aspetta invece
 * di mostrare la presentazione a chi ha già le Notizie accese da un altro membro.
 */
data class NewsFamilyState(
    val familyId: String? = null,
    val settings: NewsFamilySettings = NewsFamilySettings(),
    val loaded: Boolean = false,
)

/**
 * Le scelte delle Notizie che valgono per tutta la famiglia — accese o no,
 * dove vive, lingua delle edizioni — su `families/{familyId}/news/settings`
 * (le rules lo aprono ai membri col wildcard delle sottocollezioni). Un ascolto
 * sul documento: quando un membro accende le Notizie o cambia città, gli altri
 * lo vedono subito e leggono le stesse edizioni che la famiglia ha già pagato.
 * Gemello di `NewsFamilyStore.swift`, stesso documento e stesso formato; il
 * server lo preferisce a quello che manda il telefono.
 */
@Singleton
class NewsFamilyStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val local = context.getSharedPreferences("kb_news_family", Context.MODE_PRIVATE)
    private val db by lazy { FirebaseFirestore.getInstance() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow(NewsFamilyState())
    val state: StateFlow<NewsFamilyState> = _state.asStateFlow()

    private var listener: ListenerRegistration? = null
    /** L'ultima scrittura: le chiamate al server la aspettano ([settled]). */
    private var lastWrite: Job? = null

    private fun ref(familyId: String): DocumentReference =
        db.collection("families").document(familyId).collection("news").document("settings")

    fun bind(familyId: String) {
        if (familyId.isBlank() || familyId == _state.value.familyId) return
        listener?.remove()
        val cached = loadLocal(familyId)
        _state.value = NewsFamilyState(familyId, cached ?: NewsFamilySettings(), loaded = cached != null)
        // Con i cambi di metadati: un documento che non c'è, letto da una cache
        // vuota, non dice che la famiglia ha le Notizie spente — lo dice solo la
        // conferma del server, che altrimenti non arriverebbe se la cache è identica.
        listener = ref(familyId).addSnapshotListener(MetadataChanges.INCLUDE) { snap, error ->
            if (_state.value.familyId != familyId) return@addSnapshotListener
            if (error != null) {
                KBLog.app.warning("NewsFamily listen failed: ${error.message}", "NewsFamilyStore")
                return@addSnapshotListener
            }
            snap ?: return@addSnapshotListener
            val data = snap.data
            if (snap.exists() && data != null) {
                val remote = decode(data)
                saveLocal(familyId, remote)
                _state.update { it.copy(settings = remote, loaded = true) }
            } else if (!snap.metadata.isFromCache) {
                local.edit().remove(key(familyId)).apply()
                _state.update { it.copy(settings = NewsFamilySettings(), loaded = true) }
            }
        }
    }

    /**
     * Cambia le scelte della famiglia: subito sul telefono, poi sul server.
     * La lingua delle edizioni la fissa chi le accende per primo.
     */
    fun update(change: (NewsFamilySettings) -> NewsFamilySettings) {
        val familyId = _state.value.familyId ?: return
        val current = _state.value.settings
        var next = change(current)
        if (next.enabled && next.place == null) next = next.copy(place = NewsPlace.deviceDefault())
        if (next.enabled && next.lang == null) next = next.copy(lang = newsAppLanguage())
        if (next == current) return
        next = next.copy(updatedAtMs = System.currentTimeMillis(), updatedBy = FirebaseAuth.getInstance().currentUser?.uid)
        _state.update { it.copy(settings = next, loaded = true) }
        saveLocal(familyId, next)
        val previous = lastWrite
        val data = encode(next)
        lastWrite = scope.launch {
            previous?.join()
            runCatching { ref(familyId).set(data, SetOptions.merge()).await() }
                .onFailure { KBLog.app.warning("NewsFamily push failed: ${it.message}", "NewsFamilyStore") }
        }
    }

    /** Aspetta che l'ultima scelta sia arrivata al server. */
    suspend fun settled() {
        lastWrite?.join()
    }

    // ── Formato (uguale su iOS) ─────────────────────────────────────────────

    private fun encode(s: NewsFamilySettings): Map<String, Any?> = mapOf(
        "enabled" to s.enabled,
        "place" to s.place?.toMap(),
        "lang" to s.lang,
        "updatedAtMs" to s.updatedAtMs,
        "updatedBy" to s.updatedBy,
    )

    private fun decode(d: Map<*, *>): NewsFamilySettings = NewsFamilySettings(
        enabled = d["enabled"] as? Boolean ?: false,
        place = NewsPlace.fromMap(d["place"] as? Map<*, *>),
        lang = (d["lang"] as? String)?.takeIf { it.isNotBlank() },
        updatedAtMs = (d["updatedAtMs"] as? Number)?.toLong() ?: 0L,
        updatedBy = d["updatedBy"] as? String,
    )

    private fun key(familyId: String) = "family_$familyId"

    private fun loadLocal(familyId: String): NewsFamilySettings? = runCatching {
        val o = JSONObject(local.getString(key(familyId), null) ?: return null)
        val place = o.optJSONObject("place")?.let { p -> p.keys().asSequence().associateWith { p.optString(it) } }
        decode(
            mapOf(
                "enabled" to o.optBoolean("enabled", false),
                "place" to place,
                "lang" to o.optString("lang").takeIf { it.isNotBlank() },
                "updatedAtMs" to o.optLong("updatedAtMs", 0L),
                "updatedBy" to o.optString("updatedBy").takeIf { it.isNotBlank() },
            ),
        )
    }.getOrNull()

    private fun saveLocal(familyId: String, s: NewsFamilySettings) {
        val o = JSONObject()
            .put("enabled", s.enabled)
            .put("updatedAtMs", s.updatedAtMs)
        s.place?.let { o.put("place", JSONObject(it.toMap())) }
        s.lang?.let { o.put("lang", it) }
        s.updatedBy?.let { o.put("updatedBy", it) }
        local.edit().putString(key(familyId), o.toString()).apply()
    }
}
