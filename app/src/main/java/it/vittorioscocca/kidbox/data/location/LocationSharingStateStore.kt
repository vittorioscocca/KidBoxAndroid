package it.vittorioscocca.kidbox.data.location

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stato locale persistito della condivisione posizione di QUESTO dispositivo.
 *
 * È la fonte di verità su «questo telefono sta condividendo»: la leggono il
 * servizio, il watchdog, il BootReceiver, `MainActivity.onResume`, la push di
 * ripresa e la schermata Posizione. Firestore dice cosa vedono gli altri, non
 * se questo dispositivo deve inviare: prima la schermata riaccendeva il
 * servizio ogni volta che trovava il proprio documento ancora «in
 * condivisione», e così annullava lo stop dato dalla notifica.
 */
object LocationSharingStateStore {
    private const val PREFS_NAME = "kidbox_prefs"
    private const val KEY_ACTIVE = "location_sharing_active"
    private const val KEY_EXPIRES_AT = "location_sharing_expires_at"
    private const val KEY_DISPLAY_NAME = "location_sharing_display_name"
    private const val KEY_FAMILY_ID = "location_sharing_family_id"
    private const val KEY_UID = "location_sharing_uid"
    /** Famiglia attiva dell'app: ripiego per chi condivideva con una build che non salvava la sua. */
    private const val KEY_LEGACY_ACTIVE_FAMILY = "active_family_id"

    data class State(
        val active: Boolean = false,
        val familyId: String? = null,
        /** 0 = condivisione permanente (REALTIME). */
        val expiresAtEpochMillis: Long = 0L,
    )

    private val state = MutableStateFlow(State())
    @Volatile private var loaded = false

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** Stato osservabile, per la schermata Posizione. */
    fun observe(context: Context): StateFlow<State> {
        ensureLoaded(context)
        return state.asStateFlow()
    }

    private fun ensureLoaded(context: Context) {
        if (loaded) return
        synchronized(this) {
            if (loaded) return
            publish(context)
            loaded = true
        }
    }

    private fun publish(context: Context) {
        val p = prefs(context)
        state.value = State(
            active = p.getBoolean(KEY_ACTIVE, false),
            familyId = familyId(context),
            expiresAtEpochMillis = p.getLong(KEY_EXPIRES_AT, 0L),
        )
    }

    /** [expiresAtEpochMillis] = 0 per condivisione permanente (REALTIME). */
    fun markActive(
        context: Context,
        familyId: String,
        uid: String,
        displayName: String,
        expiresAtEpochMillis: Long = 0L,
    ) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, true)
            .putLong(KEY_EXPIRES_AT, expiresAtEpochMillis)
            .putString(KEY_DISPLAY_NAME, displayName)
            .putString(KEY_FAMILY_ID, familyId)
            .putString(KEY_UID, uid)
            .commit()
        publish(context)
        loaded = true
    }

    fun markInactive(context: Context) {
        prefs(context).edit()
            .putBoolean(KEY_ACTIVE, false)
            .remove(KEY_EXPIRES_AT)
            .remove(KEY_FAMILY_ID)
            .remove(KEY_UID)
            .commit()
        publish(context)
        loaded = true
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

    /** Famiglia in cui si condivide; per le build precedenti ripiega sulla famiglia attiva. */
    fun familyId(context: Context): String? {
        val p = prefs(context)
        return p.getString(KEY_FAMILY_ID, null)?.trim()?.takeIf { it.isNotEmpty() }
            ?: p.getString(KEY_LEGACY_ACTIVE_FAMILY, null)?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** Account che ha avviato la condivisione; `null` per le build che non lo salvavano. */
    fun uid(context: Context): String? =
        prefs(context).getString(KEY_UID, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun expiresAt(context: Context): Long = prefs(context).getLong(KEY_EXPIRES_AT, 0L)

    fun displayName(context: Context): String =
        prefs(context).getString(KEY_DISPLAY_NAME, null)?.trim()?.takeIf { it.isNotEmpty() } ?: "Utente"
}
