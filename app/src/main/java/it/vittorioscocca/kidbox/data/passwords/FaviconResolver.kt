package it.vittorioscocca.kidbox.data.passwords

import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object FaviconResolver {
    private const val USER_AGENT = "KidBox/1.0"
    private const val CONNECT_TIMEOUT_MS = 2_500
    private const val READ_TIMEOUT_MS = 2_500
    private val iconHrefRegex = Regex(
        pattern = """<link\b[^>]*\brel\s*=\s*["'][^"']*icon[^"']*["'][^>]*\bhref\s*=\s*["']([^"']+)["'][^>]*>""",
        option = RegexOption.IGNORE_CASE,
    )

    suspend fun resolve(websiteRaw: String): String? = withContext(Dispatchers.IO) {
        val normalized = normalizeWebsite(websiteRaw) ?: return@withContext null
        val host = normalized.host?.trim()?.takeIf { it.isNotEmpty() } ?: return@withContext null
        val root = "${normalized.scheme}://$host"

        val htmlIcon = fetchHtml(normalized.toString())?.let { html ->
            parseBestIconHref(html, normalized)
        }
        if (!htmlIcon.isNullOrBlank()) return@withContext htmlIcon

        if (canReach("$root/favicon.ico")) return@withContext "$root/favicon.ico"
        if (canReach("$root/apple-touch-icon.png")) return@withContext "$root/apple-touch-icon.png"

        "https://www.google.com/s2/favicons?domain=$host&sz=64"
    }

    private fun normalizeWebsite(raw: String): URI? {
        var value = raw.trim()
        if (value.isEmpty()) return null
        if (!value.contains("://")) value = "https://$value"
        return try {
            URI(value)
        } catch (_: Exception) {
            null
        }
    }

    private fun fetchHtml(url: String): String? {
        val connection = openConnection(url) ?: return null
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } catch (_: Exception) {
            null
        }
    }

    private fun openConnection(url: String): HttpURLConnection? {
        return try {
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun canReach(url: String): Boolean {
        val connection = openConnection(url) ?: return false
        return try {
            connection.requestMethod = "HEAD"
            val code = connection.responseCode
            code in 200..399
        } catch (_: Exception) {
            false
        }
    }

    private fun parseBestIconHref(html: String, baseUri: URI): String? {
        val matches = mutableListOf<String>()
        for (match in iconHrefRegex.findAll(html)) {
            val value = match.groups[1]?.value
            if (!value.isNullOrBlank()) {
                matches.add(value)
            }
        }
        if (matches.isEmpty()) return null
        val prioritized = matches.sortedBy { href: String ->
            val lowered = href.lowercase(Locale.ROOT)
            when {
                lowered.contains("apple-touch-icon") -> 0
                lowered.contains("favicon") -> 1
                else -> 2
            }
        }
        for (href in prioritized) {
            val resolved = try {
                baseUri.resolve(href).toString()
            } catch (_: Exception) {
                null
            }
            if (!resolved.isNullOrBlank()) return resolved
        }
        return null
    }
}
