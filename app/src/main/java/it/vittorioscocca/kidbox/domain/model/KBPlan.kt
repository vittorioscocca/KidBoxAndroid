package it.vittorioscocca.kidbox.domain.model

import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod

enum class KBPlan(val rawValue: String) {
    FREE("free"),
    PRO("pro"),
    MAX("max"), ;

    // Backward-compatible alias for existing Android call sites.
    val raw: String get() = rawValue

    /**
     * Spec pubblicata su `config/plans`, con i valori compilati come fallback.
     * Vedi [KBPlanCatalog]: quote, prezzi e feature hanno un'unica fonte di
     * verità (`functions/plans.json`), non più una copia per client.
     */
    val spec: KBPlanSpec get() = KBPlanCatalog.spec(this)

    val displayName: String get() = spec.displayName

    /**
     * Ripiego quando Google Play non risponde: il prezzo che conta è quello di
     * `ProductDetails`, con valuta e tasse del paese dell'utente.
     */
    val monthlyPrice: String get() = spec.localizedPriceLabel

    val storageQuota: Long get() = spec.storageBytes

    /**
     * Messaggi AI inclusi nel piano: per Free è un bonus UNA TANTUM per famiglia (mai
     * più ricaricato una volta esaurito), per Pro/Max è la quota giornaliera invariata.
     * Vedi [aiQuotaPeriod].
     */
    val aiMessageLimit: Int get() = spec.aiLimit

    // Alias retro-compatibile: molte schermate mostrano ancora "aiDailyLimit" come
    // "limite della finestra di quota corrente" (nome mantenuto anche lato backend).
    val aiDailyLimit: Int get() = aiMessageLimit

    val aiQuotaPeriod: AIQuotaPeriod get() = AIQuotaPeriod.fromRaw(spec.aiPeriod)

    val productId: String? get() = spec.productId

    val badge: String get() = spec.localizedBadge

    /** Elenco feature del piano, nella lingua del device e con quote già risolte. */
    val features: List<KBPlanFeature> get() = spec.renderedFeatures

    /** Sottotitolo di listino, es. "Per famiglia · rinnovo automatico". */
    val tagline: String get() = spec.localizedTagline

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
