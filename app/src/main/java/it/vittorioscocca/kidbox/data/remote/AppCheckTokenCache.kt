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

    private const val TTL_MS = 55L * 60 * 1000

    private fun Throwable.isTooManyAttempts(): Boolean {
        val m = message ?: ""
        if (m.contains("Too many attempts", ignoreCase = true)) return true
        return cause?.message?.contains("Too many attempts", ignoreCase = true) == true
    }

    suspend fun warmUp() {
        getToken(forceRefresh = false)
    }

    /**
     * Restituisce il token JWT cached o null se il fetch fallisce.
     * Con [forceRefresh] true si ignora cache (solo se in futuito servisse esplicitamente).
     */
    suspend fun getToken(forceRefresh: Boolean = false): String? {
        val now = System.currentTimeMillis()
        if (!forceRefresh && cachedToken != null && now < tokenExpiryEpochMs) {
            return cachedToken
        }
        return mutex.withLock {
            val now2 = System.currentTimeMillis()
            if (!forceRefresh && cachedToken != null && now2 < tokenExpiryEpochMs) {
                return@withLock cachedToken
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
                return cachedToken
            } catch (e: Throwable) {
                if (e.isTooManyAttempts() && attempt < 3) {
                    delay(500L shl attempt)
                } else {
                    Log.w(TAG, "Failed to get token: ${e.message}")
                    return null
                }
            }
        }
        return null
    }
}
