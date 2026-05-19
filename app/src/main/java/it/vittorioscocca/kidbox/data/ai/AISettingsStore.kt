package it.vittorioscocca.kidbox.data.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.health.ai.HealthContextSendMode
import it.vittorioscocca.kidbox.data.health.ai.HealthContextSendPreference
import javax.inject.Inject
import javax.inject.Singleton

/** Preferenze AI locali (parity iOS `AISettings`). */
@Singleton
class AISettingsStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getHealthContextSendPreference(): HealthContextSendPreference =
        HealthContextSendPreference.fromStorage(
            prefs.getString(KEY_HEALTH_CONTEXT_SEND_PREFERENCE, null),
        )

    fun setHealthContextSendPreference(preference: HealthContextSendPreference) {
        prefs.edit()
            .putString(KEY_HEALTH_CONTEXT_SEND_PREFERENCE, preference.storageValue)
            .apply()
    }

    fun setHealthContextSendPreferenceFromMode(mode: HealthContextSendMode) {
        setHealthContextSendPreference(HealthContextSendPreference.fromSendMode(mode))
    }

    companion object {
        /** Stesso file prefs di [it.vittorioscocca.kidbox.ai.AiSettings] / parity iOS UserDefaults. */
        private const val PREFS_NAME = "kidbox_prefs"
        private const val KEY_HEALTH_CONTEXT_SEND_PREFERENCE = "kb_ai_health_context_send_preference"
    }
}
