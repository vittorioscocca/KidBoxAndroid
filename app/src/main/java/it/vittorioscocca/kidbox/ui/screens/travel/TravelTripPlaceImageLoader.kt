package it.vittorioscocca.kidbox.ui.screens.travel

import android.net.Uri
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import it.vittorioscocca.kidbox.network.KidBoxHttpHeaders
import org.json.JSONArray
import org.json.JSONObject

object TravelTripPlaceImageLoader {
    private const val USER_AGENT = KidBoxHttpHeaders.USER_AGENT
    private val cache = ConcurrentHashMap<String, String>()

    /** Nomi inglesi per migliorare foto città su Wikipedia (es. Lisbona → Lisbon). */
    private val englishSearchAliases = mapOf(
        "lisbona" to "Lisbon",
        "lisboa" to "Lisbon",
        "londra" to "London",
        "parigi" to "Paris",
        "madrid" to "Madrid",
        "barcellona" to "Barcelona",
        "amsterdam" to "Amsterdam",
        "berlino" to "Berlin",
        "vienna" to "Vienna",
        "praga" to "Prague",
        "atene" to "Athens",
        "istanbul" to "Istanbul",
        "dublino" to "Dublin",
        "copenaghen" to "Copenhagen",
        "stoccolma" to "Stockholm",
        "oslo" to "Oslo",
        "helsinki" to "Helsinki",
        "bruxelles" to "Brussels",
        "zurigo" to "Zurich",
        "ginevra" to "Geneva",
        "new york" to "New York City",
        "los angeles" to "Los Angeles",
        "tokyo" to "Tokyo",
        "kyoto" to "Kyoto",
        "marrakech" to "Marrakesh",
        "cairo" to "Cairo",
        "cape town" to "Cape Town",
    )

    suspend fun imageUrl(
        destination: String,
        locationContext: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        searchQueries(destination, locationContext).firstNotNullOfOrNull { query ->
            val key = query.lowercase(Locale.getDefault())
            cache[key] ?: fetchWikipediaThumbnail(query)?.also { cache[key] = it }
        }
    }

    private fun searchQueries(destination: String, locationContext: String?): List<String> {
        val primary = destination.trim().substringBefore(',').trim()
        if (primary.isBlank()) return emptyList()

        val context = locationContext?.trim().orEmpty()
        val region = context.substringBefore(',').trim()
        val alias = englishSearchAliases[primary.lowercase(Locale.getDefault())]
        return buildList {
            add(primary)
            if (region.isNotBlank() && !region.equals(primary, ignoreCase = true)) {
                add("$primary $region")
            }
            alias?.let { add(it) }
        }.distinct()
    }

    private fun fetchWikipediaThumbnail(query: String): String? {
        val candidates = mutableListOf<String>()
        fetchWikipediaThumbnail(query, language = "it")?.let { candidates += it }
        fetchWikipediaThumbnail(query, language = "en")?.let { candidates += it }
        return candidates.firstOrNull { !isWeakThumbnail(it) }
            ?: candidates.firstOrNull()
    }

    private fun fetchWikipediaThumbnail(query: String, language: String): String? {
        val title = wikipediaTitle(query, language) ?: return null
        return pageImageThumbnail(title, language)
            ?: wikipediaSummaryThumbnail(title, language)
    }

    private fun isWeakThumbnail(url: String): Boolean {
        val lower = url.lowercase(Locale.getDefault())
        return lower.contains("flag") ||
            lower.contains("bandeira") ||
            lower.contains("bandiera") ||
            lower.contains("coat_of_arms") ||
            lower.contains("stemma")
    }

    private fun pageImageThumbnail(title: String, language: String): String? {
        val wikiTitle = title.trim().replace(' ', '_')
        val encodedTitle = URLEncoder.encode(wikiTitle, Charsets.UTF_8.name())
        val apiUrl =
            "https://$language.wikipedia.org/w/api.php?action=query&format=json&prop=pageimages" +
                "&piprop=thumbnail&pithumbsize=1200&titles=$encodedTitle"
        val json = JSONObject(readText(apiUrl) ?: return null)
        val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null
        val pageKey = pages.keys().asSequence().firstOrNull() ?: return null
        return pages.optJSONObject(pageKey)
            ?.optJSONObject("thumbnail")
            ?.optString("source")
            ?.takeIf { it.isNotBlank() }
    }

    private fun wikipediaTitle(query: String, language: String): String? {
        val encodedQuery = URLEncoder.encode(query, Charsets.UTF_8.name())
        val apiUrl =
            "https://$language.wikipedia.org/w/api.php?action=opensearch&search=$encodedQuery&limit=1&namespace=0&format=json"
        val json = JSONArray(readText(apiUrl) ?: return null)
        if (json.length() < 2) return null
        val titles = json.optJSONArray(1) ?: return null
        return titles.optString(0).takeIf { it.isNotBlank() }
    }

    private fun wikipediaSummaryThumbnail(title: String, language: String): String? {
        val wikiTitle = title.trim().replace(' ', '_')
        val encodedTitle = Uri.encode(wikiTitle)
        val summaryUrl = "https://$language.wikipedia.org/api/rest_v1/page/summary/$encodedTitle"
        val json = JSONObject(readText(summaryUrl) ?: return null)
        json.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() }?.let { return it }
        return json.optJSONObject("originalimage")?.optString("source")?.takeIf { it.isNotBlank() }
    }

    private fun readText(urlString: String): String? {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
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
