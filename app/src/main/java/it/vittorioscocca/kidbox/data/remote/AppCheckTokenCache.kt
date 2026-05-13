package it.vittorioscocca.kidbox.data.remote

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

private const val TAG = "AppCheckTokenCache"

/**
 * Singleton cache: un solo flusso di refresh verso Firebase App Check (~1 richiesta per TTL).
 * Prima ogni Storage op chiamava [FirebaseAppCheck.getAppCheckToken] tramite prefetch → “Too many attempts”.
 *
 * - [warmUp] dopo install del provider (`Application.onCreate`)
 * - Operazioni Storage chiamano [getToken] (no-op se ancora valido)
 */
object AppCheckTokenCache {

    private val mutex = Mutex()

    @Volatile
    private var cachedToken: String? = null

    @Volatile
    private var tokenExpiryEpochMs: Long = 0L

    /** Dopo errori App Check (403 / rate limit) evita raffiche verso Firebase fino a questo istante. */
    @Volatile
    private var fetchCooldownUntilEpochMs: Long = 0L

    private const val TTL_MS = 55L * 60 * 1000
    private const val COOLDOWN_AFTER_HARD_FAIL_MS = 30L * 60 * 1000

    private fun Throwable.messagesChain(): String {
        val sb = StringBuilder()
        var t: Throwable? = this
        while (t != null) {
            sb.append(t.message).append(' ').append(t.localizedMessage).append(' ')
            t = t.cause
        }
        return sb.toString()
    }

    private fun Throwable.isTooManyAttempts(): Boolean =
        messagesChain().contains("Too many attempts", ignoreCase = true)

    private fun Throwable.isAppCheckHardFailure(): Boolean {
        val m = messagesChain()
        if (m.contains("App attestation failed", ignoreCase = true)) return true
        if (m.contains("attestation failed", ignoreCase = true)) return true
        if (m.contains("403", ignoreCase = false) && m.contains("app check", ignoreCase = true)) return true
        if (m.contains("PERMISSION_DENIED", ignoreCase = true) && m.contains("app check", ignoreCase = true)) {
            return true
        }
        return false
    }

    private fun Throwable.shouldBackoffForRateLimit(): Boolean =
        isTooManyAttempts()

    suspend fun warmUp() {
        getToken(forceRefresh = false)
    }

    /**
     * Restituisce il token JWT cached o null se il fetch fallisce.
     * Con [forceRefresh] true si ignora cache (solo se in futuito servisse esplicitamente).
     */
    suspend fun getToken(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        val cacheValid = cachedToken != null && now < tokenExpiryEpochMs
        if (!forceRefresh && cacheValid) {
            return cachedToken
        }
        if (!forceRefresh && now < fetchCooldownUntilEpochMs) {
            return null
        }
        return mutex.withLock {
            val now2 = System.currentTimeMillis()
            val cacheValid2 = cachedToken != null && now2 < tokenExpiryEpochMs
            if (!forceRefresh && cacheValid2) {
                return@withLock cachedToken
            }
            if (!forceRefresh && now2 < fetchCooldownUntilEpochMs) {
                return@withLock null
            }
            refreshLocked(forceRefresh)
        }
    }

    private suspend fun refreshLocked(forceRefresh: Boolean): String? {
        repeat(4) { attempt ->
            try {
                val result = FirebaseAppCheck.getInstance()
                    .getAppCheckToken(forceRefresh)
                    .await()
                cachedToken = result.token
                tokenExpiryEpochMs = System.currentTimeMillis() + TTL_MS
                fetchCooldownUntilEpochMs = 0L
                return cachedToken
            } catch (e: Throwable) {
                if (e.isAppCheckHardFailure()) {
                    Log.w(TAG, "App Check attestation / policy failure (no retry): ${e.message}")
                    fetchCooldownUntilEpochMs = System.currentTimeMillis() + COOLDOWN_AFTER_HARD_FAIL_MS
                    return null
                }
                if (e.shouldBackoffForRateLimit() && attempt < 3) {
                    delay(1_000L shl attempt)
                } else {
                    Log.w(TAG, "Failed to get token: ${e.message}")
                    if (e.isTooManyAttempts()) {
                        fetchCooldownUntilEpochMs = System.currentTimeMillis() + COOLDOWN_AFTER_HARD_FAIL_MS
                    }
                    return null
                }
            }
        }
        return null
    }
}
