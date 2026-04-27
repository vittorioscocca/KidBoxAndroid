package it.vittorioscocca.kidbox.notifications

import android.content.Context

object NotificationBadgeStore {
    private const val PREFS_NOTIFICATIONS = "kidbox_notifications"
    private const val KEY_UNREAD_COUNT = "unread_badge_count"

    fun increment(context: Context): Int {
        val prefs = context.getSharedPreferences(PREFS_NOTIFICATIONS, Context.MODE_PRIVATE)
        val current = prefs.getInt(KEY_UNREAD_COUNT, 0)
        val next = (current + 1).coerceAtMost(9999)
        prefs.edit().putInt(KEY_UNREAD_COUNT, next).apply()
        return next
    }

    fun reset(context: Context) {
        context.getSharedPreferences(PREFS_NOTIFICATIONS, Context.MODE_PRIVATE)
            .edit()
            .putInt(KEY_UNREAD_COUNT, 0)
            .apply()
    }
}
