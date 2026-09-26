package it.vittorioscocca.kidbox.data.calendarfeed

import android.graphics.Color as AndroidColor
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import it.vittorioscocca.kidbox.data.devicecalendar.DeviceCalendarEvent
import it.vittorioscocca.kidbox.util.KBLog
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/** Un calendario iscritto da link (feed ICS), com'è in `families/{id}/calendarFeeds`. */
data class CalendarFeed(
    val id: String,
    val name: String,
    val url: String,
    val colorHex: String,
    val eventCount: Int,
    /** Codice d'errore dell'ultimo aggiornamento (`calendarFeeds.js` → `ERR`), o null. */
    val lastError: String?,
    val lastFetchAtMillis: Long?,
    val truncated: Boolean,
    val createdBy: String,
)

data class CalendarFeedState(
    val familyId: String = "",
    val feeds: List<CalendarFeed> = emptyList(),
    val events: List<DeviceCalendarEvent> = emptyList(),
)

/** Esito di un'iscrizione: il codice è quello che il server mette in `details.reason`. */
sealed interface CalendarFeedResult {
    data class Ok(val eventCount: Int) : CalendarFeedResult
    data class Failed(val reason: String) : CalendarFeedResult
}

/**
 * I calendari iscritti da link: scuola, squadra, festività. Li scarica e li
 * rilegge il server (`functions/calendarFeeds.js`), che salva le occorrenze
 * già espanse dentro il documento del feed; qui si ascoltano e basta.
 * Scrivere il documento dal client non si può (rules): passa tutto da
 * `saveCalendarFeed` / `deleteCalendarFeed`.
 *
 * Gemello di `CalendarFeedStore` su iOS e dell'hook `useCalendarFeeds` sul web.
 */
@Singleton
class CalendarFeedRepository @Inject constructor() {
    private val db get() = FirebaseFirestore.getInstance()
    private val functions = FirebaseFunctions.getInstance("europe-west1")
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val http by lazy {
        OkHttpClient.Builder()
            .callTimeout(20, TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }
    /** Feed già rinfrescati dal telefono in questo avvio: niente giri a vuoto. */
    private val refreshedThisRun = mutableSetOf<String>()

    private val _state = MutableStateFlow(CalendarFeedState())
    val state: StateFlow<CalendarFeedState> = _state.asStateFlow()

    private var listener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun start(familyId: String) {
        if (familyId.isBlank() || familyId == listeningFamilyId) return
        stop()
        listeningFamilyId = familyId
        _state.value = CalendarFeedState(familyId = familyId)
        listener = db.collection("families").document(familyId).collection("calendarFeeds")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    KBLog.app.error("calendari iscritti: ascolto fallito", TAG, err)
                    return@addSnapshotListener
                }
                // Il risultato completo, non il delta: con la cache locale il
                // delta può arrivare vuoto anche con documenti veri.
                val docs = snap?.documents.orEmpty()
                val feeds = mutableListOf<CalendarFeed>()
                val events = mutableListOf<DeviceCalendarEvent>()
                val zone = ZoneId.systemDefault()
                for (d in docs) {
                    val name = d.getString("name").orEmpty()
                    val colorHex = d.getString("colorHex") ?: DEFAULT_COLOR
                    val color = runCatching { AndroidColor.parseColor(colorHex) }
                        .getOrDefault(AndroidColor.parseColor(DEFAULT_COLOR))
                    feeds += CalendarFeed(
                        id = d.id,
                        name = name,
                        url = d.getString("url").orEmpty(),
                        colorHex = colorHex,
                        eventCount = (d.getLong("eventCount") ?: 0L).toInt(),
                        lastError = d.getString("lastError"),
                        lastFetchAtMillis = d.getTimestamp("lastFetchAt")?.toDate()?.time,
                        truncated = d.getBoolean("truncated") == true,
                        createdBy = d.getString("createdBy").orEmpty(),
                    )
                    @Suppress("UNCHECKED_CAST")
                    val raw = d.get("events") as? List<Map<String, Any?>> ?: emptyList()
                    for (e in raw) {
                        parseEvent(e, d.id, name, color, zone)?.let { events += it }
                    }
                }
                _state.value = CalendarFeedState(
                    familyId = familyId,
                    feeds = feeds.sortedBy { it.name.lowercase() },
                    events = events.sortedBy { it.startMillis },
                )
                refreshStaleFromPhone(familyId, feeds)
            }
    }

    fun stop() {
        listener?.remove()
        listener = null
        listeningFamilyId = null
    }

    /**
     * Un evento come lo salva il server. Tutto il giorno: date "YYYY-MM-DD"
     * (fine esclusa), riportate alla mezzanotte LOCALE: così Natale resta il
     * 25 in ogni fuso. A orario: millisecondi epoch.
     */
    private fun parseEvent(
        e: Map<String, Any?>,
        feedId: String,
        feedName: String,
        color: Int,
        zone: ZoneId,
    ): DeviceCalendarEvent? = runCatching {
        val allDay = e["a"] == true
        val start: Long
        val end: Long
        if (allDay) {
            start = LocalDate.parse(e["s"] as String).atStartOfDay(zone).toInstant().toEpochMilli()
            end = LocalDate.parse(e["e"] as String).atStartOfDay(zone).toInstant().toEpochMilli()
        } else {
            start = (e["s"] as Number).toLong()
            end = (e["e"] as? Number)?.toLong() ?: start
        }
        val id = e["id"] as? String ?: "$start"
        DeviceCalendarEvent(
            id = "feed:$feedId|$id",
            eventId = -1,
            title = (e["t"] as? String).orEmpty(),
            location = (e["l"] as? String)?.takeIf { it.isNotBlank() },
            notes = (e["n"] as? String)?.takeIf { it.isNotBlank() },
            startMillis = start,
            endMillis = maxOf(end, start),
            isAllDay = allDay,
            calendarId = -1,
            calendarTitle = feedName,
            color = color,
            feedId = feedId,
        )
    }.getOrNull()

    /**
     * Prima prova il server; se il sito lo respinge (Google Calendar risponde
     * 429 agli indirizzi delle Cloud Functions) scarica il calendario da qui,
     * dove Google risponde, e manda al server il contenuto da leggere.
     */
    suspend fun subscribe(familyId: String, name: String, url: String, colorHex: String): CalendarFeedResult {
        val base = hashMapOf<String, Any>("familyId" to familyId, "name" to name, "url" to url, "colorHex" to colorHex)
        val first = call("saveCalendarFeed", base)
        if (first !is CalendarFeedResult.Failed || first.reason != "unreachable") return first
        val text = downloadFromPhone(url) ?: return first
        return call("saveCalendarFeed", HashMap(base).apply { put("icsText", text) })
    }

    /**
     * I feed rimasti indietro (ultimo aggiornamento fallito o più vecchio di
     * 12 ore) li rinfresca il telefono. Una volta per feed a ogni avvio.
     */
    private fun refreshStaleFromPhone(familyId: String, feeds: List<CalendarFeed>) {
        val now = System.currentTimeMillis()
        val stale = feeds.filter { feed ->
            feed.id !in refreshedThisRun &&
                (feed.lastError != null || (feed.lastFetchAtMillis ?: 0L) < now - STALE_AFTER_MS)
        }
        if (stale.isEmpty()) return
        refreshedThisRun += stale.map { it.id }
        scope.launch {
            for (feed in stale) {
                val text = downloadFromPhone(feed.url) ?: continue
                val result = call(
                    "uploadCalendarFeedContent",
                    hashMapOf("familyId" to familyId, "feedId" to feed.id, "icsText" to text),
                )
                KBLog.app.info("calendari iscritti: rinfrescato dal telefono ${feed.id} → $result", TAG)
            }
        }
    }

    /** Scarica il feed dal telefono, con lo stesso tetto di 5 MB del server. */
    private suspend fun downloadFromPhone(rawUrl: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            var url = rawUrl.trim().replace(Regex("^webcals?://", RegexOption.IGNORE_CASE), "https://")
            if (!url.contains("://")) url = "https://$url"
            if (!url.startsWith("https://", true) && !url.startsWith("http://", true)) return@runCatching null
            val request = Request.Builder().url(url).header("Accept", "text/calendar, */*;q=0.5").build()
            http.newCall(request).execute().use { res ->
                if (!res.isSuccessful) return@use null
                val body = res.body ?: return@use null
                val bytes = body.byteStream().use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    var total = 0
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        total += n
                        if (total > MAX_BYTES) return@use null
                        out.write(buf, 0, n)
                    }
                    out.toByteArray()
                } ?: return@use null
                String(bytes, Charsets.UTF_8).takeIf { it.contains("BEGIN:VCALENDAR", ignoreCase = true) }
            }
        }.onFailure { KBLog.app.error("calendari iscritti: download dal telefono fallito", TAG, it) }
            .getOrNull()
    }

    suspend fun delete(familyId: String, feedId: String): CalendarFeedResult =
        call("deleteCalendarFeed", hashMapOf("familyId" to familyId, "feedId" to feedId))

    private suspend fun call(name: String, data: HashMap<String, out Any>): CalendarFeedResult = try {
        val res = functions.getHttpsCallable(name).call(data).await()
        val count = ((res.getData() as? Map<*, *>)?.get("eventCount") as? Number)?.toInt() ?: 0
        CalendarFeedResult.Ok(count)
    } catch (e: FirebaseFunctionsException) {
        val reason = (e.details as? Map<*, *>)?.get("reason") as? String
        KBLog.app.error("calendari iscritti: $name fallita reason=$reason", TAG, e)
        CalendarFeedResult.Failed(reason ?: "unreachable")
    } catch (e: Exception) {
        KBLog.app.error("calendari iscritti: $name fallita", TAG, e)
        CalendarFeedResult.Failed("unreachable")
    }

    companion object {
        private const val TAG = "CalendarFeed"
        const val DEFAULT_COLOR = "#5B8DEF"
        private const val MAX_BYTES = 5 * 1024 * 1024
        private const val STALE_AFTER_MS = 12 * 60 * 60 * 1000L
        /** Colori proposti nell'iscrizione: gli stessi su iOS e web. */
        val PALETTE = listOf("#5B8DEF", "#E67E22", "#27AE60", "#8E44AD", "#E74C3C", "#16A085")
    }
}
