package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.MessageSettingsPreferences
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch

data class MessageSettingsUiState(
    val audioTranscriptionEnabled: Boolean = true,
    val chatEnabled: Boolean = true,
)

@HiltViewModel
class MessageSettingsViewModel @Inject constructor(
    private val messageSettingsPreferences: MessageSettingsPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        MessageSettingsUiState(
            audioTranscriptionEnabled = messageSettingsPreferences.isAudioTranscriptionEnabled(),
            chatEnabled = messageSettingsPreferences.isChatEnabled(),
        ),
    )
    val uiState: StateFlow<MessageSettingsUiState> = _uiState.asStateFlow()

    init {
        // La scelta può essere stata fatta su un altro dispositivo.
        viewModelScope.launch {
            messageSettingsPreferences.refreshChatEnabled()
            _uiState.value = _uiState.value.copy(chatEnabled = messageSettingsPreferences.isChatEnabled())
        }
    }

    fun setChatEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(chatEnabled = enabled)
        viewModelScope.launch { messageSettingsPreferences.setChatEnabled(enabled) }
    }

    fun setAudioTranscriptionEnabled(enabled: Boolean) {
        messageSettingsPreferences.setAudioTranscriptionEnabled(enabled)
        _uiState.value = _uiState.value.copy(audioTranscriptionEnabled = enabled)
    }
}
