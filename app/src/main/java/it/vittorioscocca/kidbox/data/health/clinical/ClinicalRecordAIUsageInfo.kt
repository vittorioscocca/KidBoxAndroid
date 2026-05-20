package it.vittorioscocca.kidbox.data.health.clinical

data class ClinicalRecordAIUsageInfo(
    val messageUnitsConsumed: Int,
    val usageToday: Int,
    val dailyLimit: Int,
    val isLargeContext: Boolean,
    val totalPayloadChars: Int?,
) {
    val usageSummary: String get() = "$usageToday/$dailyLimit messaggi oggi"

    val largeContextNotice: String?
        get() = if (isLargeContext) {
            "Contesto ampio: questa generazione ha conteggiato $messageUnitsConsumed messaggi AI."
        } else {
            null
        }
}
