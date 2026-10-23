package it.vittorioscocca.kidbox.ai

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.ui.screens.ai.planning.HealthPatternAnalyzerService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Singleton
class HealthPatternPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _isEnabled = MutableStateFlow(prefs.getBoolean(KEY_ENABLED, true))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        context.getSharedPreferences(KIDBOX_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(WORKER_KEY_ENABLED, enabled)
            .apply()
        _isEnabled.value = enabled
    }

    fun isEnabledNow(): Boolean = prefs.getBoolean(KEY_ENABLED, true)

    /** Nelle preferenze lette dal worker e dall'allarme, come `WORKER_KEY_ENABLED`. */
    private val workerPrefs = context.getSharedPreferences(KIDBOX_PREFS, Context.MODE_PRIVATE)

    private val _frequency = MutableStateFlow(
        HealthPatternFrequency.fromRaw(workerPrefs.getString(WORKER_KEY_FREQUENCY, null)),
    )
    val frequency: StateFlow<HealthPatternFrequency> = _frequency.asStateFlow()

    fun setFrequency(frequency: HealthPatternFrequency) {
        if (frequency == _frequency.value) return
        workerPrefs.edit().putString(WORKER_KEY_FREQUENCY, frequency.raw).apply()
        _frequency.value = frequency
        HealthPatternAnalyzerService.rescheduleAfterFrequencyChange(context)
    }

    companion object {
        private const val PREFS_NAME = "kb_ai_prefs"
        private const val KIDBOX_PREFS = "kidbox_prefs"
        private const val KEY_ENABLED = "health_pattern_enabled"
        const val WORKER_KEY_ENABLED = "kb_healthPatternEnabled"
        const val WORKER_KEY_FREQUENCY = "kb_healthPatternFrequency"
    }
}
