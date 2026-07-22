package it.vittorioscocca.kidbox.util.analytics

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import it.vittorioscocca.kidbox.util.KidBoxApplicationHolder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Logger analytics lato client. Gemello Android di `KBAnalytics.swift`.
 *
 * Registra i due soli eventi che il server non può vedere: le **letture**
 * (`content_retrieved`) e le aperture (`session_start`). Un trigger Firestore
 * scatta su una scrittura; aprire il wallet, copiare una password o guardare
 * dov'è un familiare non producono scritture. Senza questo logger il DAU
 * misurerebbe solo chi crea contenuti — l'onboarding, non il prodotto.
 *
 * I 15 trigger di scrittura lato server sono **già attivi e comuni alle due
 * piattaforme**: scattano sulle scritture Firestore da iOS come da Android.
 * Qui serve solo la metà che il server non vede.
 *
 * **Privacy — vincolo non negoziabile.** Si registra la FORMA della lettura, mai
 * l'OGGETTO: niente id, titoli, nomi file, testo. Un registro di *quale*
 * documento ha aperto *chi* sarebbe sorveglianza interna alla famiglia, per di
 * più consultabile dalla console admin. `daysSinceUpload` è a bucket proprio
 * perché il valore esatto è quasi un identificatore del documento.
 *
 * **Costo.** Le letture sono l'evento più frequente dell'app: una scrittura
 * Firestore ciascuna costerebbe più del valore che produce. Gli eventi si
 * accumulano in memoria e partono in un unico batch quando l'app va in
 * background.
 *
 * Design: internal/analytics-active-users.md
 */
object KBAnalytics {

    /** Allineato a RETENTION_DAYS in functions/analytics.js. */
    private const val RETENTION_DAYS = 90

    /**
     * Finestra di inattività oltre la quale un ritorno in foreground è una nuova
     * sessione. Senza, ogni cambio di app conterebbe come apertura.
     */
    private val SESSION_GAP_MS = TimeUnit.MINUTES.toMillis(30)

    /**
     * Oltre questa soglia si scarica subito, per non perdere tutto se il processo
     * viene ucciso senza passare da background.
     */
    private const val FLUSH_THRESHOLD = 25

    private const val EVENTS_COLLECTION = "analyticsEvents"

    // Duplicati da FamilySessionPreferences: quella richiede Hilt, e KBAnalytics
    // è chiamato da Composable e da callback di lifecycle dove l'injection non
    // c'è. Tenere allineati.
    private const val PREFS_NAME = "kidbox_prefs"
    private const val KEY_ACTIVE_FAMILY_ID = "active_family_id"

    private val mutex = Mutex()
    private val buffer = mutableMapOf<RetrievalKey, Int>()
    private var lastSessionStartMs: Long? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── API ──────────────────────────────────────────────────────────────────

    /**
     * Registra una lettura intenzionale. Da chiamare quando l'utente **recupera**
     * davvero qualcosa (apre il dettaglio, copia una password), non quando scorre
     * una lista.
     *
     * @param uploaderUid `createdBy` del contenuto. Serve solo a calcolare
     *   `uploaderIsSelf`: non viene mai scritto.
     * @param createdAtEpochMillis data di caricamento, usata solo per il bucket.
     */
    fun logRetrieval(
        feature: KBAnalyticsFeature,
        uploaderUid: String?,
        createdAtEpochMillis: Long?,
        entryPoint: KBAnalyticsEntryPoint,
    ) {
        val uid = currentUid() ?: return
        // `uploaderUid` nullo o vuoto → si assume propria. Meglio sottostimare le
        // cross-member read che gonfiare la metrica su cui poggia la tesi.
        val isSelf = uploaderUid?.takeIf { it.isNotBlank() }?.let { it == uid } ?: true
        logRetrieval(feature, isSelf, createdAtEpochMillis, entryPoint)
    }

    /**
     * Variante per i contenuti senza un "uploader" singolo — la posizione dei
     * familiari, per esempio: guardare dov'è un altro membro è cross-member per
     * definizione, e non c'è un `createdBy` da confrontare.
     */
    fun logRetrieval(
        feature: KBAnalyticsFeature,
        uploaderIsSelf: Boolean,
        createdAtEpochMillis: Long?,
        entryPoint: KBAnalyticsEntryPoint,
    ) {
        if (currentUid() == null) return

        val key = RetrievalKey(
            feature = feature,
            uploaderIsSelf = uploaderIsSelf,
            entryPoint = entryPoint,
            daysBucket = bucketFor(createdAtEpochMillis),
        )

        scope.launch {
            val shouldFlush = mutex.withLock {
                buffer[key] = (buffer[key] ?: 0) + 1
                buffer.size >= FLUSH_THRESHOLD
            }
            if (shouldFlush) flush()
        }
    }

    /**
     * Registra un'apertura dell'app. Non conta come utente attivo: serve solo come
     * denominatore, per sapere quanti aprono senza fare nulla.
     */
    fun logSessionStart(entryPoint: KBAnalyticsEntryPoint) {
        val now = System.currentTimeMillis()
        val uid = currentUid() ?: return
        val familyId = currentFamilyId() ?: return

        scope.launch {
            val skip = mutex.withLock {
                val last = lastSessionStartMs
                if (last != null && now - last < SESSION_GAP_MS) {
                    true
                } else {
                    lastSessionStartMs = now
                    false
                }
            }
            if (skip) return@launch

            write(
                listOf(
                    event(
                        name = "session_start",
                        uid = uid,
                        familyId = familyId,
                        feature = "app",
                        props = mapOf("entryPoint" to entryPoint.raw),
                    )
                )
            )
        }
    }

    /**
     * Registra un evento del motore di nudge.
     *
     * `campaignId` è l'id di una campagna del catalogo, non un dato dell'utente:
     * dice *quale messaggio* è stato mostrato, non cosa quella persona ha o non
     * ha in casa. Senza questi eventi i nudge si spingono al buio, e l'unico
     * segnale di ritorno sarebbe la disattivazione dei permessi — che è
     * irreversibile e arriva quando è troppo tardi.
     *
     * @param name `nudge_scheduled`, `nudge_opened` o `nudge_dismissed`.
     */
    fun logNudge(name: String, campaignId: String) {
        val uid = currentUid() ?: return
        val familyId = currentFamilyId() ?: return
        scope.launch {
            write(
                listOf(
                    event(
                        name = name,
                        uid = uid,
                        familyId = familyId,
                        feature = "nudge",
                        props = mapOf("campaignId" to campaignId),
                    )
                )
            )
        }
    }

    /**
     * Scarica il buffer in un unico batch. Svuota prima di scrivere, così un
     * fallimento non duplica gli eventi al flush successivo.
     */
    fun flush() {
        scope.launch {
            val pending = mutex.withLock {
                if (buffer.isEmpty()) return@withLock null
                val copy = buffer.toMap()
                buffer.clear()
                copy
            } ?: return@launch

            val uid = currentUid() ?: return@launch
            val familyId = currentFamilyId() ?: return@launch

            val events = pending.map { (key, count) ->
                event(
                    name = "content_retrieved",
                    uid = uid,
                    familyId = familyId,
                    feature = key.feature.raw,
                    props = mapOf(
                        "uploaderIsSelf" to key.uploaderIsSelf,
                        "entryPoint" to key.entryPoint.raw,
                        "daysSinceUpload" to key.daysBucket.raw,
                        "count" to count.toLong(),
                    ),
                )
            }
            write(events)
        }
    }

    // ── Scrittura ────────────────────────────────────────────────────────────

    private fun event(
        name: String,
        uid: String,
        familyId: String,
        feature: String,
        props: Map<String, Any>,
    ): Map<String, Any> {
        // Le chiavi devono restare allineate alla whitelist in firestore.rules:
        // una chiave in più fa fallire la create.
        val expiry = Date(
            System.currentTimeMillis() + TimeUnit.DAYS.toMillis(RETENTION_DAYS.toLong())
        )
        return mapOf(
            "name" to name,
            "uid" to uid,
            "familyId" to familyId,
            "feature" to feature,
            "ts" to FieldValue.serverTimestamp(),
            // Campo della TTL policy: è la data di MORTE, non di nascita.
            "expiresAt" to Timestamp(expiry),
            "props" to props,
        )
    }

    private suspend fun write(events: List<Map<String, Any>>) {
        if (events.isEmpty()) return
        runCatching {
            val db = FirebaseFirestore.getInstance()
            val batch = db.batch()
            events.forEach { batch.set(db.collection(EVENTS_COLLECTION).document(), it) }
            batch.commit().await()
        }
        // Esito ignorato di proposito: l'analytics non deve mai disturbare
        // l'utente né propagare errori. Gli eventi persi sono un costo accettabile.
    }

    // ── Contesto ─────────────────────────────────────────────────────────────

    private fun currentUid(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun currentFamilyId(): String? {
        val ctx = KidBoxApplicationHolder.applicationContext ?: return null
        val prefs = ctx.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_ACTIVE_FAMILY_ID, null)?.takeIf { it.isNotBlank() }
    }

    private fun bucketFor(createdAtEpochMillis: Long?): KBAnalyticsAgeBucket {
        if (createdAtEpochMillis == null || createdAtEpochMillis <= 0L) {
            return KBAnalyticsAgeBucket.UNKNOWN
        }
        val days = TimeUnit.MILLISECONDS.toDays(
            System.currentTimeMillis() - createdAtEpochMillis
        )
        return when {
            days < 1 -> KBAnalyticsAgeBucket.TODAY
            days <= 7 -> KBAnalyticsAgeBucket.WEEK
            days <= 30 -> KBAnalyticsAgeBucket.MONTH
            days <= 180 -> KBAnalyticsAgeBucket.HALF_YEAR
            else -> KBAnalyticsAgeBucket.OLDER
        }
    }

    private data class RetrievalKey(
        val feature: KBAnalyticsFeature,
        val uploaderIsSelf: Boolean,
        val entryPoint: KBAnalyticsEntryPoint,
        val daysBucket: KBAnalyticsAgeBucket,
    )
}

// ── Tassonomia ───────────────────────────────────────────────────────────────
// I valori `raw` devono restare identici a quelli iOS e a `TRACKED` in
// functions/analytics.js: finiscono nello stesso rollup.

/** Allineata a `TRACKED` in functions/analytics.js. */
enum class KBAnalyticsFeature(val raw: String) {
    DOCUMENTS("documents"),
    WALLET("wallet"),
    PASSWORDS("passwords"),
    HEALTH("health"),
    TRAVEL("travel"),
    VEHICLES("vehicles"),
    PETS("pets"),
    NOTE("note"),
    EXPENSES("expenses"),
    CALENDAR("calendar"),
    PHOTO_VIDEO("photoVideo"),
    HOME_ITEMS("homeItems"),
    TODO("todo"),
    GROCERY("grocery"),
    CHAT("chat"),
    FAMILY_LOCATION("familyLocation"),
}

/**
 * Come l'utente è arrivato al contenuto. Distingue "a portata di click" dal
 * cercare: è la proprietà che dice se la promessa del prodotto è mantenuta.
 */
enum class KBAnalyticsEntryPoint(val raw: String) {
    SEARCH("search"),
    WIDGET("widget"),
    HOME("home"),
    LIST("list"),
    NOTIFICATION("notification"),
    DEEP_LINK("deepLink"),
    ICON("icon"),
    DYNAMIC_ISLAND("dynamicIsland"),
    SHARE_EXT("shareExt"),
}

/**
 * Bucket, mai il valore esatto: `daysSinceUpload` preciso è quasi un
 * identificatore del documento.
 */
enum class KBAnalyticsAgeBucket(val raw: String) {
    TODAY("0"),
    WEEK("1-7"),
    MONTH("8-30"),
    HALF_YEAR("31-180"),
    OLDER("180+"),
    UNKNOWN("unknown"),
}
