package it.vittorioscocca.kidbox.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sessione famiglia lato client (complementare a Room).
 * Usato dopo revoca accesso per evitare bootstrap Firestore fantasma.
 */
@Singleton
class FamilySessionPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Rimuove `active_family_id` da [PREFS_NAME] e dal file legacy **KidBoxPrefs**
     * (stesso key), così dopo una revoca nessun bootstrap riusa un familyId vecchio.
     */
    fun clearActiveFamilyId() {
        prefs.edit().remove(KEY_ACTIVE_FAMILY_ID).apply()
        try {
            context.getSharedPreferences(LEGACY_PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ACTIVE_FAMILY_ID)
                .apply()
        } catch (_: Exception) {
        }
    }

    fun setActiveFamilyId(id: String) {
        prefs.edit().putString(KEY_ACTIVE_FAMILY_ID, id).apply()
    }

    /** Chiamare prima di [markSkipHomeBootstrapOnce] non necessario: usato da access lost. */
    fun markSkipHomeBootstrapOnce() {
        prefs.edit().putBoolean(KEY_SKIP_HOME_BOOTSTRAP_ONCE, true).apply()
    }

    fun consumeSkipHomeBootstrapOnce(): Boolean {
        if (!prefs.getBoolean(KEY_SKIP_HOME_BOOTSTRAP_ONCE, false)) return false
        prefs.edit().putBoolean(KEY_SKIP_HOME_BOOTSTRAP_ONCE, false).apply()
        return true
    }

    private companion object {
        private const val PREFS_NAME = "kidbox_prefs"
        /** Allineato a richieste legacy / log di bootstrap errati. */
        private const val LEGACY_PREFS_NAME = "KidBoxPrefs"
        private const val KEY_ACTIVE_FAMILY_ID = "active_family_id"
        private const val KEY_SKIP_HOME_BOOTSTRAP_ONCE = "skip_home_bootstrap_once"
    }
}
