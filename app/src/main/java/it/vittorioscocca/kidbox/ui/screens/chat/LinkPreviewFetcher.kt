package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.collection.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Metadata extracted from the Open Graph / HTML <title> of a URL.
 */
data class LinkPreviewData(
    val url: String,
    val displayUrl: String,       // e.g. "example.com"
    val title: String?,
    val description: String?,
    val imageUrl: String?,        // og:image, may be null
)

/**
 * Fetches OG-metadata for URLs and caches the results in memory.
 * All network work happens on [Dispatchers.IO].
 */
internal object LinkPreviewFetcher {

    // Cache up to 100 previews (~tiny, just strings)
    private val cache = LruCache<String, LinkPreviewData>(100)

    // Sentinel for URLs that returned no useful data (avoids repeated fetches)
    private val empty = LinkPreviewData("", "", null, null, null)

    suspend fun fetch(url: String): LinkPreviewData? = withContext(Dispatchers.IO) {
        cache.get(url)?.let { return@withContext if (it === empty) null else it }

        val result = runCatching {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.apply {
                connectTimeout = 6_000
                readTimeout = 6_000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "Mozilla/5.0 (compatible; KidBoxBot/1.0)")
                setRequestProperty("Accept", "text/html")
            }
            try {
                val html = conn.inputStream.bufferedReader(
                    charset = conn.contentEncoding
                        ?.let { runCatching { charset(it) }.getOrNull() }
                        ?: Charsets.UTF_8,
                ).use { reader ->
                    val sb = StringBuilder()
                    val buf = CharArray(2048)
                    var n: Int
                    // Read at most 40 KB — enough to capture the <head>
                    while (reader.read(buf).also { n = it } != -1 && sb.length < 40_960) {
                        sb.append(buf, 0, n)
                        if (sb.contains("</head>", ignoreCase = true)) break
                    }
                    sb.toString()
                }
                parsePreview(html, url)
            } finally {
                conn.disconnect()
            }
        }.getOrNull()

        if (result == null || (result.title == null && result.imageUrl == null)) {
            cache.put(url, empty)
            null
        } else {
            cache.put(url, result)
            result
        }
    }

    private fun parsePreview(html: String, sourceUrl: String): LinkPreviewData {
        val title = ogMeta(html, "og:title")
            ?: ogMeta(html, "twitter:title")
            ?: htmlTitle(html)
        val description = ogMeta(html, "og:description")
            ?: ogMeta(html, "twitter:description")
            ?: metaName(html, "description")
        val imageUrl = resolveUrl(ogMeta(html, "og:image") ?: ogMeta(html, "twitter:image"), sourceUrl)
        val displayUrl = runCatching { URL(sourceUrl).host.removePrefix("www.") }.getOrDefault(sourceUrl)
        return LinkPreviewData(
            url = sourceUrl,
            displayUrl = displayUrl,
            title = title?.trim()?.unescapeHtml()?.takeIf { it.isNotBlank() },
            description = description?.trim()?.unescapeHtml()?.takeIf { it.isNotBlank() },
            imageUrl = imageUrl?.trim()?.takeIf { it.startsWith("http") },
        )
    }

    // <meta property="og:title" content="..." />  or reversed attribute order
    private fun ogMeta(html: String, property: String): String? {
        val escaped = Regex.escape(property)
        val pat1 = Regex(
            """<meta[^>]+property=["']$escaped["'][^>]+content=["']([^"']{1,300})["']""",
            RegexOption.IGNORE_CASE,
        )
        val pat2 = Regex(
            """<meta[^>]+content=["']([^"']{1,300})["'][^>]+property=["']$escaped["']""",
            RegexOption.IGNORE_CASE,
        )
        return (pat1.find(html) ?: pat2.find(html))?.groupValues?.get(1)
    }

    // <meta name="description" content="..." />
    private fun metaName(html: String, name: String): String? {
        val escaped = Regex.escape(name)
        val pat1 = Regex(
            """<meta[^>]+name=["']$escaped["'][^>]+content=["']([^"']{1,300})["']""",
            RegexOption.IGNORE_CASE,
        )
        val pat2 = Regex(
            """<meta[^>]+content=["']([^"']{1,300})["'][^>]+name=["']$escaped["']""",
            RegexOption.IGNORE_CASE,
        )
        return (pat1.find(html) ?: pat2.find(html))?.groupValues?.get(1)
    }

    private fun htmlTitle(html: String): String? =
        Regex("<title[^>]*>([^<]{1,200})</title>", RegexOption.IGNORE_CASE)
            .find(html)?.groupValues?.get(1)

    /** Resolve relative og:image paths against the source URL. */
    private fun resolveUrl(path: String?, base: String): String? {
        if (path == null) return null
        if (path.startsWith("http")) return path
        return runCatching {
            val baseUrl = URL(base)
            URL(baseUrl, path).toString()
        }.getOrNull()
    }

    private fun String.unescapeHtml(): String = this
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&nbsp;", " ")
        .replace(Regex("&#(\\d+);")) { it.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: "" }
}

/** Returns all http/https URLs found in [text], preserving order. */
internal fun extractUrls(text: String): List<String> {
    val matcher = android.util.Patterns.WEB_URL.matcher(text)
    val result = mutableListOf<String>()
    while (matcher.find()) {
        val raw = matcher.group() ?: continue
        val url = when {
            raw.startsWith("http://") || raw.startsWith("https://") -> raw
            else -> "https://$raw"
        }
        result.add(url)
    }
    return result.distinct()
}
