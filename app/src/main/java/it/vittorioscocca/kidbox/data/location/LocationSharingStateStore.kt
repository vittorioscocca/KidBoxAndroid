package it.vittorioscocca.kidbox.data.location

import android.content.Context

/**
 * Stato locale persistito della condivisione posizione, complementare a Firestore.
 * Serve a decidere — dopo un kill di sistema o un reboot, quando il ViewModel non
 * è vivo — se [LocationSharingService] vada riavviato. Vedi BootReceiver e
 * [LocationSharingWatchdogWorker].
 */
object LocationSharingStateStore {
    private const val PREFS_NAME = "kidbox_prefs"
    private const val KEY_ACTIVE = "location_sharing_active"
    private const val KEY_EXPIRES_AT = "location_sharing_expires_at"
    private const val KEY_DISPLAY_NAME = "location_sharing_display_name"

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** [expiresAtEpochMillis] = 0 per condivisione permanente (REALTIME). */
    fun markActive(context: Context, displayName: String, expiresAtEpochMillis: Long = 0L) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_EXPIRES_AT, expiresAtEpochMillis)
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()
    }

    fun markInactive(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .remove(KEY_EXPIRES_AT)
            .apply()
    }

    /**
     * True se la condivisione dovrebbe essere attiva ora: flag attivo e — per le
     * condivisioni temporanee — non ancora scaduta. Se scaduta, pulisce il flag.
     */
    fun shouldBeActive(context: Context): Boolean {
        val p = prefs(context)
        if (!p.getBoolean(KEY_ACTIVE, false)) return false
        val expiresAt = p.getLong(KEY_EXPIRES_AT, 0L)
        if (expiresAt != 0L && expiresAt <= System.currentTimeMillis()) {
            markInactive(context)
            return false
        }
        return true
    }

    fun displayName(context: Context): String =
        prefs(context).getString(KEY_DISPLAY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() } ?: "Utente"
}
