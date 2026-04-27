package it.vittorioscocca.kidbox.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MessageSettingsPreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)

    fun isAudioTranscriptionEnabled(): Boolean =
        prefs.getBoolean(KEY_AUDIO_TRANSCRIPTION_ENABLED, true)

    fun setAudioTranscriptionEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUDIO_TRANSCRIPTION_ENABLED, enabled).apply()
    }

    private companion object {
        private const val KEY_AUDIO_TRANSCRIPTION_ENABLED = "kb_audioTranscriptionEnabled"
    }
}
