package it.vittorioscocca.kidbox.util.analytics

/**
 * Come l'utente sta raggiungendo il prossimo contenuto.
 *
 * Equivalente Android di `AppCoordinator.setRetrievalOrigin` / `consumeRetrievalOrigin`
 * su iOS. Serve solo a [KBAnalytics]: lo schermo di dettaglio non può sapere da sé se
 * ci si è arrivati da una notifica, da una ricerca o sfogliando una lista, ma è
 * esattamente la differenza tra "a portata di click" e "l'ho dovuto cercare".
 *
 * Chi naviga la imposta, il dettaglio la consuma. **Consumare azzera**: senza, una
 * notifica colorerebbe tutte le aperture successive fatte sfogliando.
 *
 * Design: docs/analytics-active-users.md
 */
object KBAnalyticsOrigin {

    @Volatile
    private var pending: KBAnalyticsEntryPoint? = null

    fun set(origin: KBAnalyticsEntryPoint) {
        pending = origin
    }

    /** Legge e azzera. Default [KBAnalyticsEntryPoint.LIST]: sfogliare è il caso base. */
    fun consume(): KBAnalyticsEntryPoint {
        val current = pending
        pending = null
        return current ?: KBAnalyticsEntryPoint.LIST
    }
}
