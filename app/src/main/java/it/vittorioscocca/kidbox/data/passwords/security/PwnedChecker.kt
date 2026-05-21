package it.vittorioscocca.kidbox.data.passwords.security

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import android.util.LruCache
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.PwnedPrefixCacheDao
import java.io.IOException
import java.net.SocketTimeoutException
import java.security.MessageDigest
import java.time.Clock
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * HIBP checker con modello k-anonymity identico all'iOS:
 * la password in chiaro non lascia mai il device; viene inviato solo il prefisso SHA-1 (5 char, 20 bit).
 */
@Singleton
class PwnedChecker @Inject constructor(
    private val http: OkHttpClient,
    @ApplicationContext private val context: Context,
    private val networkMonitor: NetworkMonitor,
    private val clock: Clock,
    private val prefixCacheDao: PwnedPrefixCacheDao,
) {
    private var hibpBaseUrl: String = DEFAULT_HIBP_BASE_URL

    internal constructor(
        http: OkHttpClient,
        context: Context,
        networkMonitor: NetworkMonitor,
        clock: Clock,
        prefixCacheDao: PwnedPrefixCacheDao,
        hibpBaseUrl: String,
    ) : this(http, context, networkMonitor, clock, prefixCacheDao) {
        this.hibpBaseUrl = hibpBaseUrl
    }
    sealed interface Result {
        data class Pwned(val count: Int) : Result
        data object Safe : Result
        data object Unknown : Result
    }

    private val requestMutex = Mutex()
    private var lastRequestAt = 0L
    private val memoryCache = LruCache<String, Map<String, Int>>(256)
    private val cacheTtlMs = 24L * 60 * 60 * 1000

    suspend fun check(password: String): Result {
        if (password.isEmpty()) return Result.Safe
        if (!networkMonitor.isOnline.value) return Result.Unknown

        val sha1 = sha1UpperHex(password)
        val prefix = sha1.take(5)
        val suffix = sha1.drop(5)
        val now = clock.millis()

        val cached = resolveCachedSuffixMap(prefix, now)
        if (cached != null) {
            val count = cached.entries.firstOrNull { it.key.equals(suffix, ignoreCase = true) }?.value ?: 0
            return if (count > 0) Result.Pwned(count) else Result.Safe
        }

        return requestMutex.withLock {
            if (!networkMonitor.isOnline.value) return@withLock Result.Unknown
            val now2 = clock.millis()
            val elapsed = now2 - lastRequestAt
            if (elapsed < 200) delay(200 - elapsed)
            lastRequestAt = clock.millis()

            runCatching {
                val url = "${hibpBaseUrl.trimEnd('/')}/range/$prefix"
                val request = Request.Builder()
                    .url(url)
                    .header("Add-Padding", "true")
                    .header("User-Agent", "KidBox-Android/${appVersionName()}")
                    .get()
                    .build()
                val body = http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HIBP HTTP ${response.code}")
                    response.body?.string().orEmpty()
                }
                val parsed = parseResponseMap(body)
                memoryCache.put(prefix, parsed)
                prefixCacheDao.upsert(
                    it.vittorioscocca.kidbox.data.local.entity.PwnedPrefixCacheEntity(
                        prefix = prefix,
                        body = body,
                        fetchedAt = clock.millis(),
                    ),
                )
                val count = parsed.entries.firstOrNull { it.key.equals(suffix, ignoreCase = true) }?.value ?: 0
                if (count > 0) Result.Pwned(count) else Result.Safe
            }.getOrElse { err ->
                when (err) {
                    is IOException, is SocketTimeoutException -> {
                        KBLog.security.warning("HIBP network error prefix=$prefix", "PwnedChecker")
                        Result.Unknown
                    }
                    else -> {
                        KBLog.security.warning("HIBP unknown error prefix=$prefix", "PwnedChecker")
                        Result.Unknown
                    }
                }
            }
        }
    }

    fun invalidatePrefixCache() {
        memoryCache.evictAll()
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { prefixCacheDao.clearAll() }
        }
    }

    private suspend fun resolveCachedSuffixMap(prefix: String, now: Long): Map<String, Int>? {
        memoryCache.get(prefix)?.let { return it }
        val persisted = prefixCacheDao.getByPrefix(prefix) ?: return null
        if (now - persisted.fetchedAt > cacheTtlMs) return null
        return parseResponseMap(persisted.body).also { memoryCache.put(prefix, it) }
    }

    private fun parseResponseMap(body: String): Map<String, Int> {
        if (body.isBlank()) return emptyMap()
        return body
            .lineSequence()
            .mapNotNull { line ->
                val idx = line.indexOf(':')
                if (idx <= 0) return@mapNotNull null
                val suffix = line.substring(0, idx).trim().uppercase()
                val count = line.substring(idx + 1).trim().toIntOrNull() ?: 0
                suffix to count
            }
            .toMap()
    }

    private fun sha1UpperHex(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-1").digest(value.toByteArray(Charsets.UTF_8))
        val out = StringBuilder(bytes.size * 2)
        bytes.forEach { b -> out.append(String.format("%02X", b)) }
        return out.toString()
    }

    private fun appVersionName(): String {
        return runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "unknown"
        }.getOrDefault("unknown")
    }

    private companion object {
        const val DEFAULT_HIBP_BASE_URL = "https://api.pwnedpasswords.com"
    }
}
