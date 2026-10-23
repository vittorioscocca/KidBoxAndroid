package it.vittorioscocca.kidbox.ai

import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.domain.model.KBPlan

/**
 * Cancello dei worker della Mente proattiva (briefing, recap, pattern salute).
 *
 * È una funzione Pro/Max e i worker partono da soli: senza questo controllo un
 * Free con l'AI accesa consumerebbe il bonus una tantum senza averlo chiesto.
 * Il piano si legge dal server (`planOverride` e scadenza della prova compresi),
 * non da [CurrentPlanStore], che a processo appena avviato vale FREE finché la
 * Home non lo aggiorna. Una lettura fallita vale FREE: il worker esce senza
 * segnare il periodo, e ci riprova alla prossima apertura dell'app.
 */
object ProactiveAIGate {
    suspend fun allows(
        aiSettings: AiSettings,
        subscriptionRepository: SubscriptionRepository,
        familyId: String,
    ): Boolean {
        if (!aiSettings.isEnabled.value) return false
        return subscriptionRepository.getPlan(familyId) != KBPlan.FREE
    }
}
