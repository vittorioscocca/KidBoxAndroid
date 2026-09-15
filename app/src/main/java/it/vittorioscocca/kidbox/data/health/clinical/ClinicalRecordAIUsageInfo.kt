package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod

data class ClinicalRecordAIUsageInfo(
    val messageUnitsConsumed: Int,
    val usageToday: Int,
    val dailyLimit: Int,
    val isLargeContext: Boolean,
    val totalPayloadChars: Int?,
    val period: AIQuotaPeriod = AIQuotaPeriod.DAILY,
)

/** Il contesto supera il massimo assoluto di `askAI`: la UI lo traduce in `health_ai_error_payload`. */
class ClinicalRecordPayloadTooLargeException(val chars: Int, val maxChars: Int) :
    IllegalStateException("Clinical record payload too large: $chars > $maxChars")
