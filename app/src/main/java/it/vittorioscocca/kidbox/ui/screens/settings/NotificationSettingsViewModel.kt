package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.notification.PushNotificationManager
import it.vittorioscocca.kidbox.data.notification.PushNotificationManager.PreferenceKeys
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.wallet.WalletReminderPrefs
import it.vittorioscocca.kidbox.notifications.WalletReminderScheduler
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class NotificationSettingsUiState(
    val isLoading: Boolean = true,
    val notifyOnNewDocs: Boolean = false,
    val notifyOnNewMessages: Boolean = true,
    val notifyOnLocationSharing: Boolean = false,
    val notifyOnTodoAssigned: Boolean = true,
    val notifyOnNewGroceryItem: Boolean = true,
    val notifyOnNewNote: Boolean = true,
    val notifyOnNewCalendarEvent: Boolean = true,
    val notifyOnNewExpense: Boolean = true,
    val notifyOnWalletReminder: Boolean = true,
    val message: String? = null,
)

@HiltViewModel
class NotificationSettingsViewModel @Inject constructor(
    private val pushNotificationManager: PushNotificationManager,
    private val walletReminderPrefs: WalletReminderPrefs,
    private val familyDao: KBFamilyDao,
    private val walletReminderScheduler: WalletReminderScheduler,
) : ViewModel() {
    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState: StateFlow<NotificationSettingsUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            runCatching { pushNotificationManager.fetchPreferences() }
                .onSuccess { prefs ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        notifyOnNewDocs = prefs[PreferenceKeys.NOTIFY_ON_NEW_DOCS] ?: false,
                        notifyOnNewMessages = prefs[PreferenceKeys.NOTIFY_ON_NEW_MESSAGES] ?: true,
                        notifyOnLocationSharing = prefs[PreferenceKeys.NOTIFY_ON_LOCATION_SHARING] ?: false,
                        notifyOnTodoAssigned = prefs[PreferenceKeys.NOTIFY_ON_TODO_ASSIGNED] ?: true,
                        notifyOnNewGroceryItem = prefs[PreferenceKeys.NOTIFY_ON_NEW_GROCERY_ITEM] ?: true,
                        notifyOnNewNote = prefs[PreferenceKeys.NOTIFY_ON_NEW_NOTE] ?: true,
                        notifyOnNewCalendarEvent = prefs[PreferenceKeys.NOTIFY_ON_NEW_CALENDAR_EVENT] ?: true,
                        notifyOnNewExpense = prefs[PreferenceKeys.NOTIFY_ON_NEW_EXPENSE] ?: true,
                        notifyOnWalletReminder = walletReminderPrefs.isReminderEnabled(),
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
            PreferenceKeys.NOTIFY_ON_NEW_DOCS -> _uiState.value.copy(notifyOnNewDocs = enabled)
            PreferenceKeys.NOTIFY_ON_NEW_MESSAGES -> _uiState.value.copy(notifyOnNewMessages = enabled)
            PreferenceKeys.NOTIFY_ON_LOCATION_SHARING -> _uiState.value.copy(notifyOnLocationSharing = enabled)
            PreferenceKeys.NOTIFY_ON_TODO_ASSIGNED -> _uiState.value.copy(notifyOnTodoAssigned = enabled)
            PreferenceKeys.NOTIFY_ON_NEW_GROCERY_ITEM -> _uiState.value.copy(notifyOnNewGroceryItem = enabled)
            PreferenceKeys.NOTIFY_ON_NEW_NOTE -> _uiState.value.copy(notifyOnNewNote = enabled)
            PreferenceKeys.NOTIFY_ON_NEW_CALENDAR_EVENT -> _uiState.value.copy(notifyOnNewCalendarEvent = enabled)
            PreferenceKeys.NOTIFY_ON_NEW_EXPENSE -> _uiState.value.copy(notifyOnNewExpense = enabled)
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

    fun clearMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    fun setWalletReminderLocal(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(notifyOnWalletReminder = enabled)
        walletReminderPrefs.setReminderEnabled(enabled)
        viewModelScope.launch {
            val familyId = familyDao.peekAnyFamilyId() ?: return@launch
            walletReminderScheduler.rescheduleForFamily(familyId)
        }
    }
}
