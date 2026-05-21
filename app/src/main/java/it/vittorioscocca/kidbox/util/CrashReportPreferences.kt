package it.vittorioscocca.kidbox.util

import android.content.Context

/**
 * Preferenze consenso report crash (SharedPreferences "kidbox_crash_prefs").
 */
object CrashReportPreferences {

    private const val PREFS_NAME = "kidbox_crash_prefs"
    private const val KEY_REPORTING_ENABLED = "reporting_enabled"
    private const val KEY_REPORTING_ASKED = "reporting_asked"
    const val KEY_ANALYSIS_LAST_RUN = "kb_crash_analysis_last_run"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isReportingEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REPORTING_ENABLED, false)

    fun setReportingEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_REPORTING_ENABLED, enabled).apply()
    }

    fun hasBeenAsked(context: Context): Boolean =
        prefs(context).getBoolean(KEY_REPORTING_ASKED, false)

    fun setHasBeenAsked(context: Context, asked: Boolean) {
        prefs(context).edit().putBoolean(KEY_REPORTING_ASKED, asked).apply()
    }

    fun lastAnalysisRunMillis(context: Context): Long =
        prefs(context).getLong(KEY_ANALYSIS_LAST_RUN, 0L)

    fun markAnalysisRun(context: Context) {
        prefs(context).edit()
            .putLong(KEY_ANALYSIS_LAST_RUN, System.currentTimeMillis())
            .apply()
    }

    fun clearAnalysisThrottle(context: Context) {
        prefs(context).edit().remove(KEY_ANALYSIS_LAST_RUN).apply()
    }
}
