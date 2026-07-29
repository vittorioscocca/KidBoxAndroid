package it.vittorioscocca.kidbox.ai

import it.vittorioscocca.kidbox.domain.model.KBPlan
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object CurrentPlanStore {
    private val _plan = MutableStateFlow(KBPlan.FREE)
    val plan: StateFlow<KBPlan> = _plan.asStateFlow()

    fun update(plan: KBPlan) { _plan.value = plan }
}
