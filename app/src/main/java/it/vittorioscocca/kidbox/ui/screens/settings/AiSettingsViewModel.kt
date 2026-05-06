package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.ai.AiSettings
import it.vittorioscocca.kidbox.ai.WeeklySummaryPrefs
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.remote.ai.AIRemotePreferences
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.domain.model.KBPlan
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AiSettingsUiState(
    val isLoading: Boolean = true,
    val plan: KBPlan = KBPlan.FREE,
    val isEnabled: Boolean = false,
    val consentGiven: Boolean = false,
    val consentDate: Long? = null,
    val aiUsageToday: Int = 0,
    val isWeeklySummaryEnabled: Boolean = true,
    val pendingShowConsent: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val aiSettings: AiSettings,
    private val weeklySummaryPrefs: WeeklySummaryPrefs,
    private val aiRemotePrefs: AIRemotePreferences,
    private val subscriptionRepository: SubscriptionRepository,
    private val familyDao: KBFamilyDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiSettingsUiState(
            isEnabled = aiSettings.isEnabled.value,
            consentGiven = aiSettings.consentGiven.value,
            consentDate = aiSettings.consentDate.value,
            isWeeklySummaryEnabled = weeklySummaryPrefs.isEnabled.value,
        ),
    )
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadRemoteData() }
    }

    private suspend fun loadRemoteData() {
        _uiState.update { it.copy(isLoading = true) }
        val familyId = familyDao.peekAnyFamilyId().orEmpty()
        val plan = subscriptionRepository.getPlan(familyId)
        val remote = aiRemotePrefs.fetch()
        _uiState.update {
            it.copy(
                isLoading = false,
                plan = plan,
                aiUsageToday = remote?.usageToday ?: 0,
            )
        }
    }

    fun toggleEnabled(enabled: Boolean) {
        if (!enabled) {
            aiSettings.setEnabled(false)
            _uiState.update { it.copy(isEnabled = false, pendingShowConsent = false) }
            viewModelScope.launch {
                runCatching { aiRemotePrefs.setAiEnabled(false) }
                    .onFailure {
                        aiSettings.setEnabled(true)
                        _uiState.update { s -> s.copy(isEnabled = true) }
                    }
            }
            return
        }

        if (!_uiState.value.plan.includesAI) return

        if (aiSettings.consentGiven.value) {
            aiSettings.setEnabled(true)
            _uiState.update { it.copy(isEnabled = true) }
            viewModelScope.launch {
                runCatching { aiRemotePrefs.setAiEnabled(true) }
                    .onFailure {
                        aiSettings.setEnabled(false)
                        _uiState.update { s -> s.copy(isEnabled = false) }
                    }
            }
        } else {
            _uiState.update { it.copy(pendingShowConsent = true) }
        }
    }

    fun recordConsent() {
        aiSettings.recordConsent()
        _uiState.update {
            it.copy(
                isEnabled = true,
                consentGiven = true,
                consentDate = aiSettings.consentDate.value,
                pendingShowConsent = false,
            )
        }
        viewModelScope.launch { runCatching { aiRemotePrefs.setAiEnabled(true) } }
    }

    fun dismissPendingConsent() {
        _uiState.update { it.copy(pendingShowConsent = false) }
    }

    fun revokeConsent() {
        aiSettings.resetAll()
        _uiState.update {
            it.copy(
                isEnabled = false,
                consentGiven = false,
                consentDate = null,
                pendingShowConsent = false,
            )
        }
        viewModelScope.launch { runCatching { aiRemotePrefs.setAiEnabled(false) } }
    }

    fun toggleWeeklySummary(enabled: Boolean) {
        weeklySummaryPrefs.setEnabled(enabled)
        _uiState.update { it.copy(isWeeklySummaryEnabled = enabled) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
