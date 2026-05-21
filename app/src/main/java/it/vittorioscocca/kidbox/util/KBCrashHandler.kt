package it.vittorioscocca.kidbox.util

import android.os.Build
import it.vittorioscocca.kidbox.BuildConfig

/**
 * Intercetta crash non gestiti e li registra su Logcat + file (release) via [KBLog].
 * Dopo il logging delega al handler di sistema/precedente.
 */
object KBCrashHandler {

    @Volatile
    private var installed = false

    fun install() {
        if (installed) return
        synchronized(this) {
            if (installed) return
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                logUncaught(thread.name, throwable)
                previous?.uncaughtException(thread, throwable)
            }
            installed = true
        }
    }

    private fun logUncaught(threadName: String, throwable: Throwable) {
        try {
            val summary = buildString {
                append("Uncaught on thread ")
                append(threadName)
                append(" | ")
                append(throwable.javaClass.simpleName)
                throwable.message?.takeIf { it.isNotBlank() }?.let { append(": ").append(it) }
                append(" | v")
                append(BuildConfig.VERSION_NAME)
                append(" (")
                append(BuildConfig.VERSION_CODE)
                append(") ")
                append(Build.MANUFACTURER)
                append(' ')
                append(Build.MODEL)
                append(" API ")
                append(Build.VERSION.SDK_INT)
            }
            KBLog.app.crash(summary, throwable)
        } catch (_: Throwable) {
            // Best effort: non bloccare la terminazione del processo.
        }
    }
}
