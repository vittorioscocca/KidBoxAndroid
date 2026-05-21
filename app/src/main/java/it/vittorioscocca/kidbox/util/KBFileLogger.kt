package it.vittorioscocca.kidbox.util

import android.content.Context
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Persistenza su file dei log KidBox (Logcat + file, anche in debug per report crash).
 */
object KBFileLogger {

    private const val LOG_DIR = "kidbox_logs"
    private const val LOG_FILE = "kidbox_log.txt"
    private const val MAX_FILE_BYTES = 500 * 1024
    private const val RETENTION_DAYS = 3

    private val lock = Any()
    private val lineDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }
    private val parseDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
        timeZone = TimeZone.getDefault()
    }

    @Volatile
    private var logFile: File? = null

    fun init(context: Context) {
        synchronized(lock) {
            val dir = File(context.filesDir, LOG_DIR)
            if (!dir.exists()) dir.mkdirs()
            logFile = File(dir, LOG_FILE)
            if (!logFile!!.exists()) logFile!!.writeText("")
            rotateAndTrimLocked()
        }
    }

    fun appendSync(line: String) {
        val file = logFile ?: return
        val sanitized = sanitizeMessage(line)
        if (sanitized.isEmpty()) return
        synchronized(lock) {
            file.appendText("$sanitized\n")
            enforceMaxSizeLocked(file)
        }
    }

    fun readLogs(): String = synchronized(lock) {
        val file = logFile ?: return ""
        if (!file.exists()) return ""
        runCatching { file.readText() }.getOrDefault("")
    }

    fun clearLogs() = synchronized(lock) {
        logFile?.writeText("")
    }

    fun fileSize(): Long = synchronized(lock) {
        logFile?.length() ?: 0L
    }

    private fun rotateAndTrimLocked() {
        val file = logFile ?: return
        if (!file.exists()) return
        val cutoff = System.currentTimeMillis() - RETENTION_DAYS * 24L * 60L * 60L * 1000L
        val lines = runCatching { file.readLines() }.getOrDefault(emptyList())
        val kept = lines.filter { line ->
            val timestamp = parseTimestamp(line) ?: return@filter true
            timestamp.time >= cutoff
        }
        file.writeText(if (kept.isEmpty()) "" else kept.joinToString("\n", postfix = if (kept.isNotEmpty()) "\n" else ""))
        enforceMaxSizeLocked(file)
    }

    private fun enforceMaxSizeLocked(file: File) {
        if (!file.exists() || file.length() <= MAX_FILE_BYTES) return
        var lines = runCatching { file.readLines() }.getOrDefault(emptyList()).toMutableList()
        while (lines.isNotEmpty()) {
            val candidate = lines.joinToString("\n")
            if (candidate.toByteArray(Charsets.UTF_8).size <= MAX_FILE_BYTES) {
                file.writeText(if (candidate.isEmpty()) "" else "$candidate\n")
                return
            }
            lines.removeAt(0)
        }
        file.writeText("")
    }

    private fun parseTimestamp(line: String): Date? {
        if (!line.startsWith("[")) return null
        val end = line.indexOf(']', 1)
        if (end <= 1) return null
        val token = line.substring(1, end)
        return runCatching { parseDateFormat.parse(token) }.getOrNull()
    }

    private fun sanitizeMessage(message: String): String {
        return message
            .replace('\n', ' ')
            .replace('\r', ' ')
            .trim()
    }
}
