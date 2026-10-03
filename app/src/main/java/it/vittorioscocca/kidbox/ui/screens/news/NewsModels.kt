package it.vittorioscocca.kidbox.ui.screens.news

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Euro
import androidx.compose.material.icons.filled.FamilyRestroom
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.School
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.KBLocale
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Notizie per la famiglia (Pro e Max): i dati di `getFamilyNews` e
 * `getNewsOffers` (functions/news) e le scelte dell'utente. Stessi modelli di
 * iOS (`Features/News/NewsModels.swift`); disegno in `internal/notizie.md`.
 */

/**
 * Gli argomenti fra cui l'utente sceglie. Gli id sono quelli del server
 * (`CATEGORIES` in functions/news/prompts.js) e di iOS: non si rinominano.
 */
enum class NewsCategory(
    val id: String,
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val icon: ImageVector,
    val tint: Color,
) {
    ECONOMY("economy", R.string.news_cat_economy, R.string.news_cat_economy_sub, Icons.Filled.Euro, Color(0xFF338C66)),
    BONUS("bonus", R.string.news_cat_bonus, R.string.news_cat_bonus_sub, Icons.Filled.CardGiftcard, Color(0xFFF27326)),
    SCHOOL("school", R.string.news_cat_school, R.string.news_cat_school_sub, Icons.Filled.School, Color(0xFF4073D9)),
    HEALTH("health", R.string.news_cat_health, R.string.news_cat_health_sub, Icons.Filled.MedicalServices, Color(0xFFD94D59)),
    GROWTH("growth", R.string.news_cat_growth, R.string.news_cat_growth_sub, Icons.Filled.FamilyRestroom, Color(0xFF8C66D9)),
    LEISURE("leisure", R.string.news_cat_leisure, R.string.news_cat_leisure_sub, Icons.Filled.ConfirmationNumber, Color(0xFF1A99B3)),
    SOCIETY("society", R.string.news_cat_society, R.string.news_cat_society_sub, Icons.Filled.Groups, Color(0xFF73738C)),
    ;

    companion object {
        fun fromId(id: String?): NewsCategory? = entries.firstOrNull { it.id == id }
    }
}

/** Dove vive l'utente: paese, regione, città. Senza città arriva solo l'edizione nazionale. */
data class NewsPlace(
    val countryCode: String,
    val country: String,
    val region: String = "",
    val province: String = "",
    val city: String = "",
) {
    val hasCity: Boolean get() = city.isNotBlank()

    /** «Benevento · Campania · Italia», dal più vicino al più lontano. */
    val label: String
        get() = listOf(city, if (region == city) "" else region, country)
            .map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" · ")

    fun toMap(): Map<String, Any> = mapOf(
        "countryCode" to countryCode, "country" to country, "region" to region,
        "province" to province, "city" to city,
    )

    companion object {
        fun fromMap(m: Map<*, *>?): NewsPlace? {
            val cc = m?.get("countryCode") as? String ?: return null
            if (cc.length != 2) return null
            return NewsPlace(
                countryCode = cc,
                country = m["country"] as? String ?: cc,
                region = m["region"] as? String ?: "",
                province = m["province"] as? String ?: "",
                city = m["city"] as? String ?: "",
            )
        }

        /** Il paese del telefono, finché l'utente non sceglie la sua città. */
        fun deviceDefault(): NewsPlace {
            val code = Locale.getDefault().country.takeIf { it.length == 2 } ?: "IT"
            val name = Locale("", code).getDisplayCountry(Locale.getDefault()).ifBlank { code }
            return NewsPlace(countryCode = code, country = name)
        }
    }
}

/**
 * Le scelte della famiglia, uguali per tutti i membri su
 * `families/{familyId}/news/settings`: le notizie che un membro accende le
 * vedono tutti, con lo stesso luogo e la stessa lingua, e la famiglia le paga
 * una volta (richiesta dell'utente del 03/10/2026). Il server le legge e,
 * quando ci sono, le preferisce a quello che manda il telefono. Stesso
 * documento e stesso formato di iOS (`NewsFamilyStore.swift`).
 */
data class NewsFamilySettings(
    /** Qualcuno della famiglia ha letto la presentazione e acceso le Notizie: prima non parte nessuna ricerca né si scala nulla. */
    val enabled: Boolean = false,
    val place: NewsPlace? = null,
    /** Lingua delle edizioni: quella di chi le ha accese. Un'altra lingua sarebbe un'altra ricerca, pagata di nuovo. */
    val lang: String? = null,
    val updatedAtMs: Long = 0L,
    val updatedBy: String? = null,
) {
    val effectivePlace: NewsPlace get() = place ?: NewsPlace.deviceDefault()
    val effectiveLang: String get() = lang ?: newsAppLanguage()
}

/**
 * Le scelte di ciascuno, su `users/{uid}.newsPrefs` (vince la modifica più
 * recente): cosa leggere delle stesse edizioni della famiglia. Non cambiano le
 * notizie degli altri e non costano niente.
 */
data class NewsPrefs(
    val categories: List<NewsCategory> = NewsCategory.entries,
    /** Mostrare le offerte su misura della famiglia (si cercano solo su richiesta). */
    val personalOffers: Boolean = true,
    val updatedAtMs: Long = 0L,
)

// ── Risposte del server ─────────────────────────────────────────────────────

data class NewsItem(
    val category: String,
    val level: String,
    val title: String,
    val summary: String,
    val action: String?,
    val keyDate: String?,
    val keyDateKind: String?,
    val publishedAt: String?,
    val source: String,
    val url: String,
) {
    val newsCategory: NewsCategory? get() = NewsCategory.fromId(category)
}

data class NewsEvent(
    val title: String,
    val summary: String?,
    val place: String?,
    val distanceKm: Int?,
    val startDate: String,
    val endDate: String?,
    val free: Boolean?,
    val source: String,
    val url: String,
)

data class NewsOffer(
    val kind: String,
    val title: String,
    val summary: String,
    val saving: String?,
    val source: String,
    val url: String,
)

data class NewsOffersPayload(
    val offers: List<NewsOffer>,
    val generatedAtMs: Long?,
    val units: Int?,
    val estimateUnits: Int?,
    val status: String?,
)

data class NewsFeed(
    val status: String,
    val pending: List<String>,
    val dateKey: String,
    val placeCity: String?,
    val placeRegion: String?,
    val placeCountry: String?,
    val items: List<NewsItem>,
    val events: List<NewsEvent>,
    val offers: NewsOffersPayload?,
    val chargedUnits: Int,
    val totalUnits: Int,
    val maxUnitsPerEdition: Int?,
) {
    val isPreparing: Boolean get() = status == "preparing"
}

// ── Lettura delle mappe che arrivano dalla callable ─────────────────────────

internal object NewsParser {
    private fun Map<*, *>.str(key: String): String? = (this[key] as? String)?.takeIf { it.isNotEmpty() }
    private fun Map<*, *>.int(key: String): Int? = (this[key] as? Number)?.toInt()
    private fun Map<*, *>.list(key: String): List<Map<*, *>> = (this[key] as? List<*>)?.filterIsInstance<Map<*, *>>().orEmpty()

    fun feed(m: Map<*, *>): NewsFeed {
        val place = m["place"] as? Map<*, *>
        val charge = m["charge"] as? Map<*, *>
        return NewsFeed(
            status = m.str("status") ?: "ready",
            pending = (m["pending"] as? List<*>)?.filterIsInstance<String>().orEmpty(),
            dateKey = m.str("dateKey") ?: "",
            placeCity = place?.str("city"),
            placeRegion = place?.str("region"),
            placeCountry = place?.str("country"),
            items = m.list("items").mapNotNull(::item),
            events = m.list("events").mapNotNull(::event),
            offers = (m["offers"] as? Map<*, *>)?.let(::offers),
            chargedUnits = charge?.int("units") ?: 0,
            totalUnits = charge?.int("totalUnits") ?: 0,
            maxUnitsPerEdition = m.int("maxUnitsPerEdition"),
        )
    }

    private fun item(m: Map<*, *>): NewsItem? {
        val title = m.str("title") ?: return null
        val url = m.str("url") ?: return null
        return NewsItem(
            category = m.str("category") ?: "",
            level = m.str("level") ?: "country",
            title = title,
            summary = m.str("summary") ?: "",
            action = m.str("action"),
            keyDate = m.str("keyDate"),
            keyDateKind = m.str("keyDateKind"),
            publishedAt = m.str("publishedAt"),
            source = m.str("source") ?: "",
            url = url,
        )
    }

    private fun event(m: Map<*, *>): NewsEvent? {
        val title = m.str("title") ?: return null
        val url = m.str("url") ?: return null
        val start = m.str("startDate") ?: return null
        return NewsEvent(
            title = title,
            summary = m.str("summary"),
            place = m.str("place"),
            distanceKm = m.int("distanceKm"),
            startDate = start,
            endDate = m.str("endDate"),
            free = m["free"] as? Boolean,
            source = m.str("source") ?: "",
            url = url,
        )
    }

    fun offers(m: Map<*, *>): NewsOffersPayload = NewsOffersPayload(
        offers = m.list("offers").mapNotNull { o ->
            val title = o.str("title") ?: return@mapNotNull null
            val url = o.str("url") ?: return@mapNotNull null
            NewsOffer(o.str("kind") ?: "grocery", title, o.str("summary") ?: "", o.str("saving"), o.str("source") ?: "", url)
        },
        generatedAtMs = (m["generatedAt"] as? Number)?.toLong(),
        units = m.int("units"),
        estimateUnits = m.int("estimateUnits"),
        status = m.str("status"),
    )
}

// ── Date ────────────────────────────────────────────────────────────────────

internal object NewsDates {
    private fun parser() = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)

    fun parse(key: String?): Date? = key?.takeIf { it.isNotBlank() }?.let { runCatching { parser().parse(it) }.getOrNull() }

    fun key(date: Date): String = parser().format(date)

    /** «3 ott», nella lingua dell'app. */
    fun dayMonth(key: String?): String? = parse(key)?.let { SimpleDateFormat("d MMM", KBLocale.current()).format(it) }

    /** «sab 3 ott». */
    fun short(key: String?): String? = parse(key)?.let { SimpleDateFormat("EEE d MMM", KBLocale.current()).format(it) }
}
