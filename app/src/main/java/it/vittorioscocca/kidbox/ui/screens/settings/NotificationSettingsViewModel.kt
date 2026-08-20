package it.vittorioscocca.kidbox.ui.screens.settings

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.notification.PushNotificationManager
import it.vittorioscocca.kidbox.data.notification.PushNotificationManager.PreferenceKeys
import it.vittorioscocca.kidbox.notifications.nudge.NudgeEngine
import it.vittorioscocca.kidbox.notifications.nudge.NudgeState
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationSettingsUiState(
    val isLoading: Boolean = true,
    val notifyOnNewMessages: Boolean = true,
    val notifyOnLocationSharing: Boolean = true,
    val notifyOnTodoAssigned: Boolean = true,
    val notifyOnNewGroceryItem: Boolean = true,
    val notifyOnNewNote: Boolean = true,
    val notifyOnNewCalendarEvent: Boolean = true,
    val notifyOnNewExpense: Boolean = true,
    /**
     * Un solo toggle per tutto il Wallet (biglietti, documenti, carte
     * fedeltà): assorbe le vecchie `notifyOnNewDocs` e
     * `notifyOnNewWalletTicket`. Vedi [PreferenceKeys.NOTIFY_ON_WALLET].
     */
    val notifyOnWallet: Boolean = true,
    /** Suggerimenti su aree mai usate. Locale, indipendente dalle push. */
    val nudgesEnabled: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val pushNotificationManager: PushNotificationManager,
    @ApplicationContext private val appContext: Context,
    private val nudgeEngine: NudgeEngine,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            runCatching { pushNotificationManager.fetchPreferences() }
                .onSuccess { prefs ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        nudgesEnabled = !NudgeState.isOptedOut(appContext),
                        notifyOnNewMessages = prefs[PreferenceKeys.NOTIFY_ON_NEW_MESSAGES] ?: true,
                        notifyOnLocationSharing = prefs[PreferenceKeys.NOTIFY_ON_LOCATION_SHARING] ?: true,
                        notifyOnTodoAssigned = prefs[PreferenceKeys.NOTIFY_ON_TODO_ASSIGNED] ?: true,
                        notifyOnNewGroceryItem = prefs[PreferenceKeys.NOTIFY_ON_NEW_GROCERY_ITEM] ?: true,
                        notifyOnNewNote = prefs[PreferenceKeys.NOTIFY_ON_NEW_NOTE] ?: true,
                        notifyOnNewCalendarEvent = prefs[PreferenceKeys.NOTIFY_ON_NEW_CALENDAR_EVENT] ?: true,
                        notifyOnNewExpense = prefs[PreferenceKeys.NOTIFY_ON_NEW_EXPENSE] ?: true,
                        notifyOnWallet = prefs[PreferenceKeys.NOTIFY_ON_WALLET] ?: true,
                        message = null,
                    )
                }
                .onFailure { err ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        message = err.message ?: "Errore caricamento notifiche",
                    )
                }
        }
    }

    fun setPreference(key: String, enabled: Boolean, registerToken: Boolean) {
        _uiState.value = when (key) {
            PreferenceKeys.NOTIFY_ON_NEW_MESSAGES -> _uiState.value.copy(notifyOnNewMessages = enabled)
            PreferenceKeys.NOTIFY_ON_LOCATION_SHARING -> _uiState.value.copy(notifyOnLocationSharing = enabled)
            PreferenceKeys.NOTIFY_ON_TODO_ASSIGNED -> _uiState.value.copy(notifyOnTodoAssigned = enabled)
            PreferenceKeys.NOTIFY_ON_NEW_GROCERY_ITEM -> _uiState.value.copy(notifyOnNewGroceryItem = enabled)
            PreferenceKeys.NOTIFY_ON_NEW_NOTE -> _uiState.value.copy(notifyOnNewNote = enabled)
            PreferenceKeys.NOTIFY_ON_NEW_CALENDAR_EVENT -> _uiState.value.copy(notifyOnNewCalendarEvent = enabled)
            PreferenceKeys.NOTIFY_ON_NEW_EXPENSE -> _uiState.value.copy(notifyOnNewExpense = enabled)
            PreferenceKeys.NOTIFY_ON_WALLET -> _uiState.value.copy(notifyOnWallet = enabled)
            else -> _uiState.value
        }
        viewModelScope.launch {
            runCatching {
                pushNotificationManager.setPreference(key, enabled)
                if (enabled && registerToken) {
                    pushNotificationManager.registerCurrentFcmToken()
                }
            }.onFailure { err ->
                load()
                _uiState.value = _uiState.value.copy(message = err.message ?: "Errore salvataggio preferenze")
            }
        }
    }

    /**
     * Suggerimenti in-app. Vive solo in locale: non è una preferenza di
     * notifica lato server come le altre di questa schermata, perché i nudge
     * non partono dal server. Spegnendolo la coda viene svuotata subito, senza
     * aspettare il prossimo foreground.
     */
    fun setNudgesEnabled(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(nudgesEnabled = enabled)
        NudgeState.setOptedOut(appContext, !enabled)
        viewModelScope.launch { runCatching { nudgeEngine.refresh() } }
    }

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }
}
