package it.vittorioscocca.kidbox.domain.model

import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod

enum class KBPlan(val rawValue: String) {
    FREE("free"),
    PRO("pro"),
    MAX("max"), ;

    // Backward-compatible alias for existing Android call sites.
    val raw: String get() = rawValue

    val displayName: String get() = when (this) {
        FREE -> "Free"
        PRO -> "Pro"
        MAX -> "Max"
    }

    val monthlyPrice: String get() = when (this) {
        FREE -> "Gratis"
        PRO -> "€4,99/mese"
        MAX -> "€9,99/mese"
    }

    val storageQuota: Long get() = when (this) {
        FREE -> 200L * 1024 * 1024
        PRO -> 5L * 1024 * 1024 * 1024
        MAX -> 20L * 1024 * 1024 * 1024
    }

    /**
     * Messaggi AI inclusi nel piano: per Free è un bonus UNA TANTUM per famiglia (mai
     * più ricaricato una volta esaurito), per Pro/Max è la quota giornaliera invariata.
     * Vedi [aiQuotaPeriod].
     */
    val aiMessageLimit: Int get() = when (this) {
        FREE -> 5
        PRO -> 30
        MAX -> 100
    }

    // Alias retro-compatibile: molte schermate mostrano ancora "aiDailyLimit" come
    // "limite della finestra di quota corrente" (nome mantenuto anche lato backend).
    val aiDailyLimit: Int get() = aiMessageLimit

    val aiQuotaPeriod: AIQuotaPeriod get() = if (this == FREE) AIQuotaPeriod.LIFETIME else AIQuotaPeriod.DAILY

    val productId: String? get() = when (this) {
        FREE -> null
        PRO -> "it.vittorioscocca.kidbox.pro.monthly"
        MAX -> "it.vittorioscocca.kidbox.max.monthly"
    }

    val badge: String get() = when (this) {
        FREE -> ""
        PRO -> "Più popolare"
        MAX -> "Migliore"
    }

    companion object {
        fun fromRawValue(raw: String?): KBPlan {
            val normalized = raw?.trim()?.lowercase().orEmpty()
            if (normalized.isEmpty()) return FREE
            return entries.firstOrNull { it.rawValue == normalized } ?: FREE
        }

        // Backward-compatible helper for existing Android call sites.
        fun from(raw: String?): KBPlan = fromRawValue(raw)
    }
}
