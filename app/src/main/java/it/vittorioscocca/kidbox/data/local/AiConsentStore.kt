package it.vittorioscocca.kidbox.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AiConsentStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)

    fun hasHealthAiConsent(): Boolean = prefs.getBoolean(KEY_HEALTH_AI_CONSENT, false)

    fun setHealthAiConsent(given: Boolean) {
        prefs.edit().putBoolean(KEY_HEALTH_AI_CONSENT, given).apply()
    }

    private companion object {
        private const val KEY_HEALTH_AI_CONSENT = "kb_health_ai_consent"
    }
}
