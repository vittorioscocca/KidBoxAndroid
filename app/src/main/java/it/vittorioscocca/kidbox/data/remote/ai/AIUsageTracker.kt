package it.vittorioscocca.kidbox.data.remote.ai

import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AIUsageSnapshot(
    val usageToday: Int = 0,
    val dailyLimit: Int = 0,
    val period: AIQuotaPeriod = AIQuotaPeriod.DAILY,
)

/**
 * Stato di utilizzo AI condiviso, aggiornato dopo ogni chiamata askAI/getAIUsage.
 * Ogni [apply] propaga anche a [CurrentPlanStore] così che i punti di ingresso AI
 * in tutta l'app vedano l'esaurimento del bonus Free immediatamente, senza dover
 * attendere un refresh esplicito (equivalente Android della notifica
 * `.aiUsageDidChange` di iOS).
 */
@Singleton
class AIUsageTracker @Inject constructor() {
    private val _state = MutableStateFlow(AIUsageSnapshot())
    val state: StateFlow<AIUsageSnapshot> = _state.asStateFlow()

    fun apply(usageToday: Int, dailyLimit: Int, period: AIQuotaPeriod = AIQuotaPeriod.DAILY) {
        _state.value = AIUsageSnapshot(usageToday = usageToday, dailyLimit = dailyLimit, period = period)
        CurrentPlanStore.updateAIUsage(usageToday, dailyLimit, period)
    }
}
