package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod

data class ClinicalRecordAIUsageInfo(
    val messageUnitsConsumed: Int,
    val usageToday: Int,
    val dailyLimit: Int,
    val isLargeContext: Boolean,
    val totalPayloadChars: Int?,
    val period: AIQuotaPeriod = AIQuotaPeriod.DAILY,
) {
    val usageSummary: String get() = if (period == AIQuotaPeriod.LIFETIME) {
        "$usageToday/$dailyLimit messaggi gratuiti"
    } else {
        "$usageToday/$dailyLimit messaggi oggi"
    }

    val largeContextNotice: String?
        get() = if (isLargeContext) {
            "Contesto ampio: questa generazione ha conteggiato $messageUnitsConsumed messaggi AI."
        } else {
            null
        }
}
