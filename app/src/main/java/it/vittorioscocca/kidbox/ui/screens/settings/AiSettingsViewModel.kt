package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.ai.AiSettings
import it.vittorioscocca.kidbox.ai.DailyBriefingPrefs
import it.vittorioscocca.kidbox.ai.HealthPatternPrefs
import it.vittorioscocca.kidbox.ai.WeeklySummaryPrefs
import it.vittorioscocca.kidbox.data.ai.AISettingsStore
import it.vittorioscocca.kidbox.data.health.ai.HealthContextSendPreference
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.remote.ai.AIService
import it.vittorioscocca.kidbox.data.remote.ai.AIRemotePreferences
import it.vittorioscocca.kidbox.data.remote.ai.AIUsageTracker
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.domain.model.KBPlan
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val isDailyBriefingEnabled: Boolean = true,
    val isHealthPatternEnabled: Boolean = true,
    val healthContextSendPreference: HealthContextSendPreference = HealthContextSendPreference.ASK_EACH_TIME,
    val pendingShowConsent: Boolean = false,
    val message: String? = null,
)

@HiltViewModel
class AiSettingsViewModel @Inject constructor(
    private val aiSettings: AiSettings,
    private val weeklySummaryPrefs: WeeklySummaryPrefs,
    private val dailyBriefingPrefs: DailyBriefingPrefs,
    private val healthPatternPrefs: HealthPatternPrefs,
    private val aiRemotePrefs: AIRemotePreferences,
    private val subscriptionRepository: SubscriptionRepository,
    private val familyDao: KBFamilyDao,
    private val aiService: AIService,
    private val familySessionPreferences: FamilySessionPreferences,
    private val aiUsageTracker: AIUsageTracker,
    private val aiSettingsStore: AISettingsStore,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        AiSettingsUiState(
            isEnabled = aiSettings.isEnabled.value,
            consentGiven = aiSettings.consentGiven.value,
            consentDate = aiSettings.consentDate.value,
            isWeeklySummaryEnabled = weeklySummaryPrefs.isEnabled.value,
            isDailyBriefingEnabled = dailyBriefingPrefs.isEnabled.value,
            isHealthPatternEnabled = healthPatternPrefs.isEnabled.value,
            healthContextSendPreference = aiSettingsStore.getHealthContextSendPreference(),
        ),
    )
    val uiState: StateFlow<AiSettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch { loadRemoteData() }
        viewModelScope.launch {
            aiUsageTracker.state.collectLatest { snapshot ->
                if (snapshot.dailyLimit > 0) {
                    _uiState.update { it.copy(aiUsageToday = snapshot.usageToday) }
                }
            }
        }
    }

    private suspend fun loadRemoteData() {
        _uiState.update { it.copy(isLoading = true) }
        val familyId = familySessionPreferences.getActiveFamilyId()
            ?: familyDao.peekAnyFamilyId().orEmpty()
        val plan = subscriptionRepository.getPlan(familyId)
        val remote = aiRemotePrefs.fetch()
        val usage = aiService.fetchUsage(familyId).getOrNull()
        val usageToday = usage?.usageToday ?: aiUsageTracker.state.value.usageToday
        usage?.let { aiUsageTracker.apply(it.usageToday, it.dailyLimit) }
        remote?.healthContextSendPreference?.let { pref ->
            aiSettingsStore.setHealthContextSendPreference(pref)
        }
        _uiState.update {
            it.copy(
                isLoading = false,
                plan = plan,
                isEnabled = remote?.aiEnabled ?: it.isEnabled,
                aiUsageToday = usageToday,
                healthContextSendPreference = remote?.healthContextSendPreference
                    ?: aiSettingsStore.getHealthContextSendPreference(),
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

    fun toggleDailyBriefing(enabled: Boolean) {
        dailyBriefingPrefs.setEnabled(enabled)
        _uiState.update { it.copy(isDailyBriefingEnabled = enabled) }
    }

    fun toggleHealthPattern(enabled: Boolean) {
        healthPatternPrefs.setEnabled(enabled)
        _uiState.update { it.copy(isHealthPatternEnabled = enabled) }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }

    fun setHealthContextSendPreference(preference: HealthContextSendPreference) {
        aiSettingsStore.setHealthContextSendPreference(preference)
        _uiState.update { it.copy(healthContextSendPreference = preference) }
        viewModelScope.launch {
            runCatching { aiRemotePrefs.setHealthContextSendPreference(preference) }
                .onFailure {
                    _uiState.update { s -> s.copy(message = it.message ?: "Errore sincronizzazione preferenza") }
                }
        }
    }
}
