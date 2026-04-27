package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.MessageSettingsPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MessageSettingsUiState(
    val audioTranscriptionEnabled: Boolean = true,
)

@HiltViewModel
class MessageSettingsViewModel @Inject constructor(
    private val messageSettingsPreferences: MessageSettingsPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MessageSettingsUiState(
            audioTranscriptionEnabled = messageSettingsPreferences.isAudioTranscriptionEnabled(),
        ),
    )
    val uiState: StateFlow<MessageSettingsUiState> = _uiState.asStateFlow()

    fun setAudioTranscriptionEnabled(enabled: Boolean) {
        messageSettingsPreferences.setAudioTranscriptionEnabled(enabled)
        _uiState.value = _uiState.value.copy(audioTranscriptionEnabled = enabled)
    }
}
