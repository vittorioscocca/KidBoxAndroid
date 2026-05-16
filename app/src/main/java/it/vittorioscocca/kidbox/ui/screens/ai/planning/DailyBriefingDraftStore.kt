package it.vittorioscocca.kidbox.ui.screens.ai.planning

import android.content.Context

object DailyBriefingDraftStore {
    private const val PREFS_NAME = "kidbox_prefs"
    private const val KEY_TEXT = "kb_daily_briefing_pending_text"

    fun save(context: Context, text: String) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_TEXT, text)
            .apply()
    }

    fun consume(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val value = prefs.getString(KEY_TEXT, null)
        if (!value.isNullOrBlank()) {
            prefs.edit().remove(KEY_TEXT).apply()
        }
        return value
    }
}
