package it.vittorioscocca.kidbox.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class AiSettings @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_IS_ENABLED, false),
    )
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _consentGiven = MutableStateFlow(
        prefs.getBoolean(KEY_CONSENT_GIVEN, false),
    )
    val consentGiven: StateFlow<Boolean> = _consentGiven.asStateFlow()

    private val _consentDate = MutableStateFlow(
        if (prefs.contains(KEY_CONSENT_DATE)) prefs.getLong(KEY_CONSENT_DATE, 0L) else null,
    )
    val consentDate: StateFlow<Long?> = _consentDate.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ENABLED, enabled).apply()
        _isEnabled.value = enabled
    }

    fun setConsentGiven(given: Boolean) {
        prefs.edit().putBoolean(KEY_CONSENT_GIVEN, given).apply()
        _consentGiven.value = given
    }

    fun setConsentDate(epochMillis: Long?) {
        prefs.edit().apply {
            if (epochMillis == null) {
                remove(KEY_CONSENT_DATE)
            } else {
                putLong(KEY_CONSENT_DATE, epochMillis)
            }
        }.apply()
        _consentDate.value = epochMillis
    }

    fun recordConsent() {
        val now = System.currentTimeMillis()
        prefs.edit()
            .putBoolean(KEY_CONSENT_GIVEN, true)
            .putLong(KEY_CONSENT_DATE, now)
            .putBoolean(KEY_IS_ENABLED, true)
            .apply()
        _consentGiven.value = true
        _consentDate.value = now
        _isEnabled.value = true
    }

    fun resetAll() {
        prefs.edit()
            .putBoolean(KEY_IS_ENABLED, false)
            .putBoolean(KEY_CONSENT_GIVEN, false)
            .remove(KEY_CONSENT_DATE)
            .apply()
        _isEnabled.value = false
        _consentGiven.value = false
        _consentDate.value = null
    }

    private companion object {
        private const val PREFS_NAME = "kidbox_prefs"
        private const val KEY_IS_ENABLED = "kb_ai_is_enabled"
        private const val KEY_CONSENT_GIVEN = "kb_ai_consent_given"
        private const val KEY_CONSENT_DATE = "kb_ai_consent_date"
    }
}
