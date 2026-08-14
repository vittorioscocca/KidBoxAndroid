package it.vittorioscocca.kidbox.ai

import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Stato reattivo globale del piano famiglia e dell'accesso all'AI, letto dai punti
 * di ingresso AI sparsi in tutta l'app (pulsante flottante, Home, Viaggi, Impostazioni)
 * che vivono fuori dal grafo Hilt di una singola schermata.
 *
 * [aiAccessBlocked] è true SOLO per Free con il bonus di 5 messaggi AI una tantum
 * esaurito: per Pro/Max resta sempre false, perché la loro quota giornaliera si
 * resetta da sola e non deve mai bloccare l'ingresso alla UI.
 */
object CurrentPlanStore {
    private val _plan = MutableStateFlow(KBPlan.FREE)
    val plan: StateFlow<KBPlan> = _plan.asStateFlow()

    private val _aiAccessBlocked = MutableStateFlow(false)
    val aiAccessBlocked: StateFlow<Boolean> = _aiAccessBlocked.asStateFlow()
    val isAIAccessible: Boolean get() = !_aiAccessBlocked.value

    private var lastUsageToday: Int = 0
    private var lastLimit: Int = 0
    private var lastPeriod: AIQuotaPeriod = AIQuotaPeriod.DAILY

    fun update(plan: KBPlan) {
        _plan.value = plan
        recompute()
    }

    /** Chiamato da [it.vittorioscocca.kidbox.data.remote.ai.AIUsageTracker] dopo ogni fetch/uso AI. */
    fun updateAIUsage(usageToday: Int, limit: Int, period: AIQuotaPeriod) {
        lastUsageToday = usageToday
        lastLimit = limit
        lastPeriod = period
        recompute()
    }

    private fun recompute() {
        _aiAccessBlocked.value = _plan.value == KBPlan.FREE &&
            lastPeriod == AIQuotaPeriod.LIFETIME &&
            lastLimit > 0 &&
            lastUsageToday >= lastLimit
    }
}
