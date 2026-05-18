package it.vittorioscocca.kidbox.data.remote.ai

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AIUsageSnapshot(
    val usageToday: Int = 0,
    val dailyLimit: Int = 0,
)

@Singleton
class AIUsageTracker @Inject constructor() {
    private val _state = MutableStateFlow(AIUsageSnapshot())
    val state: StateFlow<AIUsageSnapshot> = _state.asStateFlow()

    fun apply(usageToday: Int, dailyLimit: Int) {
        _state.value = AIUsageSnapshot(usageToday = usageToday, dailyLimit = dailyLimit)
    }
}
