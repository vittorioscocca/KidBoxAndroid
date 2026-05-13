package it.vittorioscocca.kidbox.ui.screens.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.passwords.security.PasswordSecurityPreferences
import it.vittorioscocca.kidbox.data.passwords.security.SecurityAuditService
import it.vittorioscocca.kidbox.data.passwords.security.SecurityScanScheduler
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PasswordsSettingsUiState(
    val weeklyEnabled: Boolean = true,
    val pushEnabled: Boolean = true,
    val lastScanLabel: String = "Mai",
)

@HiltViewModel
class PasswordsSettingsViewModel @Inject constructor(
    private val preferences: PasswordSecurityPreferences,
    private val scheduler: SecurityScanScheduler,
    private val audit: SecurityAuditService,
) : ViewModel() {
    val uiState: StateFlow<PasswordsSettingsUiState> = combine(
        preferences.weeklyScanEnabled,
        preferences.pushAlertsEnabled,
        preferences.lastScanAt,
    ) { weekly, push, lastScanAt ->
        PasswordsSettingsUiState(
            weeklyEnabled = weekly,
            pushEnabled = push,
            lastScanLabel = lastScanAt?.let(::formatDate) ?: "Mai",
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PasswordsSettingsUiState())

    fun setWeeklyEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setWeeklyScanEnabled(enabled)
            if (enabled) scheduler.enqueueWeekly() else scheduler.cancelWeekly()
        }
    }

    fun setPushEnabled(enabled: Boolean) {
        viewModelScope.launch {
            preferences.setPushAlertsEnabled(enabled)
        }
    }

    fun runScanNow() {
        viewModelScope.launch {
            audit.runFullScan()
            preferences.setLastScanAt(System.currentTimeMillis())
        }
    }

    private fun formatDate(epochMillis: Long): String {
        return SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(epochMillis))
    }
}
