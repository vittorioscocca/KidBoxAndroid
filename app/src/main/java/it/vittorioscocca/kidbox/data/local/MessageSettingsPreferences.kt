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

    /**
     * Returns the last viewed message id and its pixel offset for [familyId],
     * or null when the user was at the bottom (no anchor to restore).
     */
    fun getChatScrollAnchor(familyId: String): Pair<String, Int>? {
        if (familyId.isBlank()) return null
        val id = prefs.getString(keyAnchorId(familyId), null) ?: return null
        if (id.isBlank()) return null
        val offset = prefs.getInt(keyAnchorOffset(familyId), 0)
        return id to offset
    }

    fun setChatScrollAnchor(familyId: String, messageId: String?, offset: Int) {
        if (familyId.isBlank()) return
        val editor = prefs.edit()
        if (messageId.isNullOrBlank()) {
            editor.remove(keyAnchorId(familyId)).remove(keyAnchorOffset(familyId))
        } else {
            editor.putString(keyAnchorId(familyId), messageId)
                .putInt(keyAnchorOffset(familyId), offset)
        }
        editor.apply()
    }

    private fun keyAnchorId(familyId: String) = "kb_chatAnchorId_$familyId"
    private fun keyAnchorOffset(familyId: String) = "kb_chatAnchorOffset_$familyId"

    private companion object {
        private const val KEY_AUDIO_TRANSCRIPTION_ENABLED = "kb_audioTranscriptionEnabled"
    }
}
