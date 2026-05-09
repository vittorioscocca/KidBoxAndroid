package it.vittorioscocca.kidbox.data.remote

import android.util.Log
import com.google.firebase.appcheck.FirebaseAppCheck
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

private const val TAG = "AppCheckStorage"

private val prefetchMutex = Mutex()

private fun Throwable.isTooManyAttempts(): Boolean {
    val hay = message ?: ""
    if (hay.contains("Too many attempts", ignoreCase = true)) return true
    val c = cause
    if (c != null && c.message?.contains("Too many attempts", ignoreCase = true) == true) return true
    return false
}

/**
 * Single-flight + backoff: evita burst paralleli verso App Check (log "Too many attempts") e dà
 * a Firebase Storage un token valido prima di putBytes quando l'enforcement è attivo.
 */
suspend fun prefetchAppCheckTokenForStorage() {
    prefetchMutex.withLock {
        repeat(4) { attempt ->
            try {
                FirebaseAppCheck.getInstance().getAppCheckToken(false).await()
                return
            } catch (e: Throwable) {
                if (e.isTooManyAttempts() && attempt < 3) {
                    delay(400L shl attempt)
                } else {
                    Log.w(TAG, "prefetch failed; Storage potrebbe fallire con enforcement: ${e.message}")
                    return
                }
            }
        }
    }
}
