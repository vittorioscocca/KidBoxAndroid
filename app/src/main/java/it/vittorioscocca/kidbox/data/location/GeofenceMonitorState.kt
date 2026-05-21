package it.vittorioscocca.kidbox.data.location

import android.content.Context

object GeofenceMonitorState {
    private const val PREFS = "geofence_monitor_state"
    private const val KEY_FAMILY_ID = "family_id"
    private const val KEY_UID = "uid"
    private const val KEY_DISPLAY_NAME = "display_name"

    fun save(context: Context, familyId: String, uid: String, displayName: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_FAMILY_ID, familyId)
            .putString(KEY_UID, uid)
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()
    }

    fun familyId(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_FAMILY_ID, null)

    fun uid(context: Context): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_UID, null)

    fun displayName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY_DISPLAY_NAME, "Utente")
            ?: "Utente"

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
