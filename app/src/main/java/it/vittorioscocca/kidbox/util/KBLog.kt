package it.vittorioscocca.kidbox.util

import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Logging centralizzato KidBox (speculare a iOS KBLog).
 */
object KBLog {

    val app = CategoryLogger("app")
    val navigation = CategoryLogger("navigation")
    val data = CategoryLogger("data")
    val sync = CategoryLogger("sync")
    val auth = CategoryLogger("auth")
    val storage = CategoryLogger("storage")
    val ui = CategoryLogger("ui")
    val ai = CategoryLogger("ai")
    val security = CategoryLogger("security")
    val crypto = CategoryLogger("crypto")
    val persistence = CategoryLogger("persistence")

    class CategoryLogger internal constructor(private val category: String) {
        private val baseTag = "KidBox/$category"
        private val lineDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).apply {
            timeZone = TimeZone.getDefault()
        }

        fun debug(message: String, tag: String = "") {
            log(Log.DEBUG, "DEBUG", message, tag, null)
        }

        fun info(message: String, tag: String = "") {
            log(Log.INFO, "INFO", message, tag, null)
        }

        fun warning(message: String, tag: String = "") {
            log(Log.WARN, "WARNING", message, tag, null)
        }

        fun error(message: String, tag: String = "") {
            log(Log.ERROR, "ERROR", message, tag, null)
        }

        fun error(message: String, throwable: Throwable) {
            log(Log.ERROR, "ERROR", message, "", throwable)
        }

        fun error(message: String, tag: String, throwable: Throwable) {
            log(Log.ERROR, "ERROR", message, tag, throwable)
        }

        fun crash(message: String, tag: String = "") {
            log(Log.ERROR, "CRASH", message, tag, null)
        }

        fun crash(message: String, throwable: Throwable) {
            log(Log.ERROR, "CRASH", message, "", throwable)
        }

        fun crash(message: String, tag: String, throwable: Throwable) {
            log(Log.ERROR, "CRASH", message, tag, throwable)
        }

        private fun log(
            priority: Int,
            level: String,
            message: String,
            extraTag: String,
            throwable: Throwable?,
        ) {
            val logcatTag = if (extraTag.isNotBlank()) "$baseTag/$extraTag" else baseTag
            val location = callerLocation()
            val prefix = if (location.isNotEmpty()) "$location " else ""
            when (priority) {
                Log.DEBUG -> Log.d(logcatTag, "$prefix$message", throwable)
                Log.INFO -> Log.i(logcatTag, "$prefix$message", throwable)
                Log.WARN -> Log.w(logcatTag, "$prefix$message", throwable)
                Log.ERROR -> Log.e(logcatTag, "$prefix$message", throwable)
                else -> Log.println(priority, logcatTag, "$prefix$message")
            }
            val timestamp = lineDateFormat.format(Date())
            val fileLine = "[$timestamp] [$level] [$category] $location $message"
            KBFileLogger.appendSync(fileLine)
        }

        private fun callerLocation(): String {
            val stack = Thread.currentThread().stackTrace
            val element = stack.drop(5).firstOrNull { frame ->
                !frame.className.startsWith("dalvik.") &&
                    !frame.className.startsWith("java.lang.Thread") &&
                    !frame.className.contains("KBLog")
            } ?: return ""
            val file = element.fileName ?: "unknown"
            return "[$file:${element.methodName}():${element.lineNumber}]"
        }
    }
}
