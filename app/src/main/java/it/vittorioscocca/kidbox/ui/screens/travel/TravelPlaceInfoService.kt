package it.vittorioscocca.kidbox.ui.screens.travel

import it.vittorioscocca.kidbox.data.remote.travel.TravelPlacesService
import it.vittorioscocca.kidbox.network.KidBoxHttpHeaders
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Storia e paesaggio della destinazione, da Wikipedia.
 *
 * Perché Wikipedia e non l'AI: il contenuto è fattuale e verificabile, ha già
 * testo e immagini, non costa nulla per chiamata e non consuma il budget AI
 * della famiglia. Un modello che "racconta" la storia di un paese piccolo è
 * esattamente il caso in cui inventa date e monumenti.
 */
data class TravelPlaceInfo(
    val title: String,
    /** Riga breve sotto il titolo (es. "comune italiano"). */
    val subtitle: String,
    /** Testo storico/descrittivo completo, diviso in paragrafi. */
    val paragraphs: List<String>,
    val imageUrls: List<String>,
    val wikipediaUrl: String?,
    /** Dati Google del luogo: voto, indirizzo, foto, recensioni. */
    val googleDetails: TravelPlaceDetails? = null,
)

@Singleton
class TravelPlaceInfoService @Inject constructor(
    private val placesService: TravelPlacesService,
) {
    private val cache = ConcurrentHashMap<String, TravelPlaceInfo>()

    suspend fun info(place: String, familyId: String?): TravelPlaceInfo? {
        val key = place.trim().lowercase()
        if (key.isEmpty()) return null
        cache[key]?.let { cached ->
            if (cached.googleDetails != null || familyId.isNullOrBlank()) return cached
        }

        // Italiano prima: per una località italiana l'articolo it.wikipedia è
        // quasi sempre più ricco di quello inglese.
        var base: TravelPlaceInfo? = null
        for (language in listOf("it", "en")) {
            if (base != null) break
            base = fetchInfo(place, language)
        }
        var info = base ?: return null

        // Google è complementare, non alternativo: Wikipedia dà la storia,
        // Places dà voto, indirizzo, foto attuali e recensioni. Se fallisce,
        // la scheda resta valida con la sola parte enciclopedica.
        if (!familyId.isNullOrBlank()) {
            val google = placesService.fetchDetails(place, place, familyId).getOrNull()
            info = info.copy(googleDetails = google)
        }

        cache[key] = info
        return info
    }

    // MARK: - Wikipedia

    private suspend fun fetchInfo(place: String, language: String): TravelPlaceInfo? {
        val title = geoDisambiguatedTitle(place, language) ?: return null
        val base = summary(title, language) ?: return null

        // Il riassunto è un paragrafo solo: per una scheda che deve
        // raccontare il posto serve il testo dell'articolo.
        val fullText = articleParagraphs(title, language)
        val extraImages = articleImages(title, language)

        val images = base.imageUrls.toMutableList()
        extraImages.forEach { if (it !in images) images += it }

        return base.copy(
            paragraphs = fullText.ifEmpty { base.paragraphs },
            imageUrls = images.take(12),
        )
    }

    /**
     * Testo dell'articolo in paragrafi.
     *
     * `explaintext` restituisce prosa senza wiki-markup, quindi è mostrabile
     * così com'è. Si scartano i paragrafi troppo corti: sono quasi sempre
     * titoli di sezione rimasti isolati, non contenuto.
     */
    private suspend fun articleParagraphs(title: String, language: String): List<String> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(title, Charsets.UTF_8.name())
        val url = "https://$language.wikipedia.org/w/api.php?action=query&prop=extracts" +
            "&explaintext=1&titles=$encoded&format=json&formatversion=2"
        val body = readText(url) ?: return@withContext emptyList()
        runCatching {
            val json = JSONObject(body)
            val pages = json.optJSONObject("query")?.optJSONArray("pages") ?: return@runCatching emptyList()
            val text = pages.optJSONObject(0)?.optString("extract").orEmpty()
            text.split("\n")
                .map { it.trim() }
                .filter { it.length > 40 }
                // Un tetto c'è: certi comuni hanno articoli lunghissimi con
                // elenchi di frazioni e gemellaggi che qui non servono.
                .take(14)
        }.getOrElse { emptyList() }
    }

    /** Immagini dell'articolo, per la galleria. */
    private suspend fun articleImages(title: String, language: String): List<String> = withContext(Dispatchers.IO) {
        val wikiTitle = title.replace(' ', '_')
        val encoded = URLEncoder.encode(wikiTitle, Charsets.UTF_8.name())
        val url = "https://$language.wikipedia.org/api/rest_v1/page/media-list/$encoded"
        val body = readText(url) ?: return@withContext emptyList()
        runCatching {
            val json = JSONObject(body)
            val items = json.optJSONArray("items") ?: return@runCatching emptyList()
            val urls = mutableListOf<String>()
            for (i in 0 until items.length()) {
                val item = items.optJSONObject(i) ?: continue
                // Solo immagini: la media-list contiene anche audio e video.
                if (item.optString("type") != "image") continue
                val srcset = item.optJSONArray("srcset") ?: continue
                if (srcset.length() == 0) continue
                // L'ultima entry è la risoluzione più alta.
                val src = srcset.optJSONObject(srcset.length() - 1)?.optString("src") ?: continue
                if (src.isBlank()) continue
                val normalized = if (src.startsWith("//")) "https:$src" else src
                if (normalized !in urls) urls += normalized
            }
            urls
        }.getOrElse { emptyList() }
    }

    /**
     * Titolo dell'articolo che descrive un LUOGO.
     *
     * Il criterio di disambiguazione è la presenza di coordinate geografiche:
     * l'articolo di un comune le ha, quello di una persona no. Serve perché
     * diverse località italiane sono omonime di persone — cercando
     * "Margherita di Savoia" Wikipedia restituisce la regina, non il paese in
     * provincia di Barletta, e la scheda mostrerebbe una biografia.
     */
    private suspend fun geoDisambiguatedTitle(place: String, language: String): String? = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(place, Charsets.UTF_8.name())
        val url = "https://$language.wikipedia.org/w/api.php?action=query&generator=search" +
            "&gsrsearch=$encoded&gsrlimit=5&prop=coordinates%7Cpageprops&format=json&formatversion=2"
        val body = readText(url) ?: return@withContext null
        runCatching {
            val json = JSONObject(body)
            val pages = json.optJSONObject("query")?.optJSONArray("pages") ?: return@runCatching null
            // I risultati arrivano senza ordine garantito: `index` conserva
            // il ranking di rilevanza della ricerca.
            val ranked = (0 until pages.length())
                .map { pages.optJSONObject(it) }
                .filterNotNull()
                .sortedBy { it.optInt("index", Int.MAX_VALUE) }
            for (page in ranked) {
                val isDisambiguation = page.optJSONObject("pageprops")?.has("disambiguation") == true
                if (isDisambiguation) continue
                if (!page.has("coordinates")) continue
                val title = page.optString("title")
                if (title.isNotBlank()) return@runCatching title
            }
            null
        }.getOrNull()
    }

    private suspend fun summary(title: String, language: String): TravelPlaceInfo? = withContext(Dispatchers.IO) {
        val wikiTitle = title.replace(' ', '_')
        val encoded = URLEncoder.encode(wikiTitle, Charsets.UTF_8.name())
        val url = "https://$language.wikipedia.org/api/rest_v1/page/summary/$encoded"
        val body = readText(url) ?: return@withContext null
        runCatching {
            val json = JSONObject(body)
            val extract = json.optString("extract").trim()
            if (extract.isEmpty()) return@runCatching null

            val images = mutableListOf<String>()
            json.optJSONObject("originalimage")?.optString("source")?.takeIf { it.isNotBlank() }?.let { images += it }
            json.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() && it !in images }?.let { images += it }

            val pageUrl = json.optJSONObject("content_urls")
                ?.optJSONObject("desktop")
                ?.optString("page")
                ?.takeIf { it.isNotBlank() }

            TravelPlaceInfo(
                title = json.optString("title").takeIf { it.isNotBlank() } ?: title,
                subtitle = json.optString("description"),
                paragraphs = listOf(extract),
                imageUrls = images,
                wikipediaUrl = pageUrl,
            )
        }.getOrNull()
    }

    private fun readText(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", KidBoxHttpHeaders.USER_AGENT)
            setRequestProperty("Accept", "application/json")
        }
        return try {
            if (connection.responseCode !in 200..299) return null
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }
}
