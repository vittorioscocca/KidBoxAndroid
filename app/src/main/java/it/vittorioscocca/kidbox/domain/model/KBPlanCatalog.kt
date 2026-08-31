package it.vittorioscocca.kidbox.domain.model

import android.content.Context
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.util.KBLog
import java.util.Locale
import kotlinx.coroutines.tasks.await
import org.json.JSONArray
import org.json.JSONObject

/**
 * Catalogo piani letto da Firestore (`config/plans`), con i valori compilati
 * come rete di sicurezza — stesso pattern di `config/nudges` / `NudgeCatalog`.
 *
 * FONTE DI VERITÀ: `functions/plans.json`. Da lì il backend calcola le quote che
 * applica davvero e da lì viene pubblicato `config/plans`, che questo oggetto
 * legge per MOSTRARE quote, prezzi e feature. Il documento remoto è materiale di
 * presentazione: quanto storage e quanti messaggi AI la famiglia ottenga davvero
 * lo decide il backend, non ciò che il client ha letto qui.
 *
 * Le `SharedPreferences` sono solo cache locale, come in `ChatAvailability`: a
 * freddo le schermate partono dall'ultimo listino visto invece che dai valori
 * compilati, potenzialmente più vecchi.
 *
 * Gemello di `KBPlanCatalog` su iOS, stesso documento Firestore.
 */
object KBPlanCatalog {

    private const val PREFS_FILE = "kidbox_prefs"
    private const val KEY_CATALOG = "kb_planCatalogJSON"
    private const val TAG = "Plans"

    @Volatile
    private var specs: Map<String, KBPlanSpec> = builtIn()

    /** Da `KidBoxApplication.onCreate()`: allinea il catalogo alla cache locale. */
    fun init(context: Context) {
        val cached = prefs(context).getString(KEY_CATALOG, null) ?: return
        runCatching { parse(JSONObject(cached)) }
            .onSuccess { if (it.isNotEmpty()) specs = it }
    }

    /**
     * Rilegge `config/plans`. Un errore (documento assente, permessi, campo
     * malformato) lascia in piedi il catalogo precedente: non si degrada mai.
     */
    suspend fun refresh(context: Context) {
        runCatching {
            val data = FirebaseFirestore.getInstance()
                .collection("config").document("plans")
                // Server-first: con la persistenza locale attiva un `get()` può
                // rispondere dalla cache, e il listino appena pubblicato dalla
                // console non arriverebbe mai. Se il server non risponde resta
                // valido l'ultimo catalogo già in memoria.
                .get(com.google.firebase.firestore.Source.SERVER).await().data
                ?: return
            // I timestamp di pubblicazione non servono al client e non sono
            // serializzabili: si scartano prima di salvare la cache.
            val payload = data.filterKeys { it != "publishedAt" && it != "publishedBy" }
            // NON `JSONObject(payload)`: quel costruttore è superficiale, le mappe
            // annidate resterebbero `LinkedHashMap` e `optJSONObject("plans")`
            // tornerebbe null — il catalogo remoto veniva scartato in silenzio.
            val json = toJsonObject(payload)
            val parsed = parse(json)
            require(parsed.containsKey(KBPlan.FREE.rawValue)) { "catalogo senza piano free" }
            specs = parsed
            prefs(context).edit().putString(KEY_CATALOG, json.toString()).apply()
            parsed.size
        }.onSuccess {
            KBLog.app.info("Catalogo piani aggiornato: $it piani", TAG)
        }.onFailure {
            KBLog.app.debug("Catalogo piani non disponibile, resto sull'ultimo valido: ${it.message}", TAG)
        }
    }

    /** Conversione ricorsiva Map/List → JSONObject/JSONArray. */
    private fun toJsonObject(map: Map<*, *>): JSONObject {
        val out = JSONObject()
        for ((k, v) in map) out.put(k.toString(), toJsonValue(v))
        return out
    }

    private fun toJsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> toJsonObject(value)
        is Iterable<*> -> JSONArray().also { arr -> value.forEach { arr.put(toJsonValue(it)) } }
        else -> value
    }

    /**
     * Spec del piano, con fallback sul valore compilato: un catalogo remoto
     * malformato non deve poter far sparire un piano dall'app.
     */
    fun spec(plan: KBPlan): KBPlanSpec =
        specs[plan.rawValue] ?: builtIn().getValue(plan.rawValue)

    // ── Parsing ──────────────────────────────────────────────────────────────

    private fun parse(json: JSONObject): Map<String, KBPlanSpec> {
        val plans = json.optJSONObject("plans") ?: return emptyMap()
        val out = mutableMapOf<String, KBPlanSpec>()
        for (key in plans.keys()) {
            val p = plans.optJSONObject(key) ?: continue
            out[key] = KBPlanSpec(
                id = p.optString("id", key),
                order = p.optInt("order", 0),
                displayName = p.optString("displayName", key.replaceFirstChar { it.uppercase() }),
                storageBytes = p.optLong("storageBytes", 0L),
                aiLimit = p.optInt("aiLimit", 0),
                aiPeriod = p.optString("aiPeriod", "daily"),
                productId = if (p.isNull("productId")) null else p.optString("productId").ifBlank { null },
                priceLabel = stringMap(p.optJSONObject("priceLabel")),
                tagline = stringMap(p.optJSONObject("tagline")),
                badge = stringMap(p.optJSONObject("badge")),
                features = featureMap(p.optJSONObject("features")),
            )
        }
        return out
    }

    private fun stringMap(obj: JSONObject?): Map<String, String> {
        if (obj == null) return emptyMap()
        return obj.keys().asSequence().associateWith { obj.optString(it, "") }
    }

    private fun featureMap(obj: JSONObject?): Map<String, List<KBPlanFeature>> {
        if (obj == null) return emptyMap()
        return obj.keys().asSequence().associateWith { lang ->
            val arr: JSONArray = obj.optJSONArray(lang) ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val f = arr.optJSONObject(i) ?: return@mapNotNull null
                KBPlanFeature(
                    icon = f.optString("icon", ""),
                    text = f.optString("text", ""),
                    strong = f.optBoolean("strong", false),
                )
            }
        }
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_FILE, Context.MODE_PRIVATE)

    // ── Rete di sicurezza compilata ──────────────────────────────────────────

    /**
     * Valori minimi garantiti se il catalogo remoto non è mai arrivato.
     * Volutamente scarni sulle feature: l'elenco lungo è materiale di listino e
     * vive in `functions/plans.json`, non qui.
     */
    private fun builtIn(): Map<String, KBPlanSpec> = mapOf(
        "free" to KBPlanSpec(
            id = "free", order = 0, displayName = "Free",
            storageBytes = 200L * 1024 * 1024, aiLimit = 5, aiPeriod = "lifetime",
            productId = null,
            priceLabel = mapOf("it" to "Gratis", "en" to "Free"),
            features = mapOf(
                "it" to listOf(
                    KBPlanFeature("✓", "{storage} di storage famiglia", true),
                    KBPlanFeature("💬", "{aiLimit} messaggi AI di prova, una tantum"),
                ),
                "en" to listOf(
                    KBPlanFeature("✓", "{storage} family storage", true),
                    KBPlanFeature("💬", "{aiLimit} trial AI messages, one time only"),
                ),
            ),
        ),
        "pro" to KBPlanSpec(
            id = "pro", order = 1, displayName = "Pro",
            storageBytes = 5L * 1024 * 1024 * 1024, aiLimit = 30, aiPeriod = "daily",
            productId = "it.vittorioscocca.kidbox.pro.monthly",
            priceLabel = mapOf("it" to "€4,99/mese", "en" to "€4.99/month"),
            badge = mapOf("it" to "Più popolare", "en" to "Most popular"),
            features = mapOf(
                "it" to listOf(
                    KBPlanFeature("☁️", "{storage} di storage famiglia", true),
                    KBPlanFeature("💬", "{aiLimit} messaggi AI al giorno", true),
                ),
                "en" to listOf(
                    KBPlanFeature("☁️", "{storage} family storage", true),
                    KBPlanFeature("💬", "{aiLimit} AI messages per day", true),
                ),
            ),
        ),
        "max" to KBPlanSpec(
            id = "max", order = 2, displayName = "Max",
            storageBytes = 20L * 1024 * 1024 * 1024, aiLimit = 100, aiPeriod = "daily",
            productId = "it.vittorioscocca.kidbox.max.monthly",
            priceLabel = mapOf("it" to "€9,99/mese", "en" to "€9.99/month"),
            badge = mapOf("it" to "Migliore", "en" to "Best value"),
            features = mapOf(
                "it" to listOf(
                    KBPlanFeature("☁️", "{storage} di storage famiglia", true),
                    KBPlanFeature("💬", "{aiLimit} messaggi AI al giorno", true),
                ),
                "en" to listOf(
                    KBPlanFeature("☁️", "{storage} family storage", true),
                    KBPlanFeature("💬", "{aiLimit} AI messages per day", true),
                ),
            ),
        ),
    )
}

/** Voce dell'elenco feature: `text` può contenere `{storage}` e `{aiLimit}`. */
data class KBPlanFeature(
    val icon: String,
    val text: String,
    val strong: Boolean = false,
)

/** Specifica di un piano così com'è pubblicata su `config/plans`. */
data class KBPlanSpec(
    val id: String,
    val order: Int = 0,
    val displayName: String,
    val storageBytes: Long,
    val aiLimit: Int,
    val aiPeriod: String,
    val productId: String?,
    val priceLabel: Map<String, String> = emptyMap(),
    val tagline: Map<String, String> = emptyMap(),
    val badge: Map<String, String> = emptyMap(),
    val features: Map<String, List<KBPlanFeature>> = emptyMap(),
) {
    private val language: String get() = Locale.getDefault().language.lowercase()

    private fun localized(map: Map<String, String>): String =
        map[language] ?: map["it"] ?: map["en"] ?: ""

    val localizedPriceLabel: String get() = localized(priceLabel)
    val localizedTagline: String get() = localized(tagline)
    val localizedBadge: String get() = localized(badge)

    /**
     * Quota storage in forma compatta da listino — "200 MB", "5 GB". Non si usa
     * `Formatter.formatFileSize`: da API 26 conta in unità da 1000 e mostrerebbe
     * "5,37 GB" per i 5 GB binari del piano.
     */
    val storageLabel: String
        get() {
            val mbUnit = if (language == "fr") "Mo" else "MB"
            val gbUnit = if (language == "fr") "Go" else "GB"
            val gb = storageBytes.toDouble() / (1024 * 1024 * 1024)
            if (gb >= 1) {
                return if (gb == Math.floor(gb)) "${gb.toInt()} $gbUnit"
                else String.format(Locale.getDefault(), "%.1f %s", gb, gbUnit)
            }
            val mb = storageBytes.toDouble() / (1024 * 1024)
            return if (mb == Math.floor(mb)) "${mb.toInt()} $mbUnit"
            else String.format(Locale.getDefault(), "%.1f %s", mb, mbUnit)
        }

    /** Feature nella lingua del device, con i segnaposto già risolti. */
    val renderedFeatures: List<KBPlanFeature>
        get() {
            val list = features[language] ?: features["it"] ?: features["en"] ?: emptyList()
            return list.map {
                it.copy(
                    text = it.text
                        .replace("{storage}", storageLabel)
                        .replace("{aiLimit}", aiLimit.toString()),
                )
            }
        }
}
