package it.vittorioscocca.kidbox.domain.model

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

    val aiDailyLimit: Int get() = when (this) {
        FREE -> 0
        PRO -> 30
        MAX -> 100
    }

    val includesAI: Boolean get() = this != FREE

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
        fun fromRawValue(raw: String?): KBPlan =
            entries.firstOrNull { it.rawValue == raw } ?: FREE

        // Backward-compatible helper for existing Android call sites.
        fun from(raw: String?): KBPlan = fromRawValue(raw)
    }
}
