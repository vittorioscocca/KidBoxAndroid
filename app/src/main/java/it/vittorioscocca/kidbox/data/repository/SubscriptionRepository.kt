package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.domain.model.KBPlan
import kotlinx.coroutines.flow.Flow

/**
 * Prova Pro della famiglia, concessa dal server (functions/proTrial.js) con
 * `planSource: "trial"` e `planExpiresAt`.
 * @property endsAtMillis fine della prova se è in corso adesso, altrimenti null
 * @property ended la prova c'è stata ed è finita senza abbonamento
 */
data class KBTrialState(val endsAtMillis: Long? = null, val ended: Boolean = false) {
    /** Giorni interi rimasti (1 nell'ultimo giorno), null se non in prova. */
    fun daysLeft(nowMillis: Long = System.currentTimeMillis()): Int? {
        val end = endsAtMillis ?: return null
        val left = end - nowMillis
        if (left <= 0) return null
        return ((left + DAY_MS - 1) / DAY_MS).toInt().coerceAtLeast(1)
    }

    private companion object {
        const val DAY_MS = 24L * 60 * 60 * 1000
    }
}

/**
 * Risposta di `getProTrialStatus`: se la prova spetta a chi chiama
 * ([eligible]) o, a un membro non proprietario, se il proprietario può ancora
 * attivarla ([ownerCanStart]) e se gliel'ha già chiesto da poco ([askedOwner]).
 */
data class KBTrialOfferStatus(
    val eligible: Boolean,
    val days: Int,
    val aiLimit: Int,
    val ownerCanStart: Boolean,
    val askedOwner: Boolean,
)

/** Stato della card della prova Pro in Spazio e Piani (vedi ProTrialOfferCard). */
data class KBTrialOfferUi(
    /** Giorni attivabili dal proprietario col pulsante (null = non spetta). */
    val ownerDays: Int? = null,
    /** Giorni che il proprietario può attivare, visti da un altro membro. */
    val askOwnerDays: Int? = null,
    val ownerAsked: Boolean = false,
    val aiLimit: Int = 50,
    val isStarting: Boolean = false,
    val startFailed: Boolean = false,
    val isAsking: Boolean = false,
    val askResult: KBTrialAskResult? = null,
) {
    val isVisible: Boolean get() = ownerDays != null || askOwnerDays != null
}

enum class KBTrialAskResult { SENT, NOT_DELIVERED, FAILED }

interface SubscriptionRepository {
    fun planFlow(familyId: String, uid: String): Flow<KBPlan>

    /** Stato della prova Pro della famiglia (una lettura del documento famiglia). */
    suspend fun loadTrialState(familyId: String): KBTrialState

    suspend fun loadPlan(familyId: String, uid: String): KBPlan

    /**
     * Se la prova Pro spetta, a chi chiama o al proprietario (null = errore).
     * Decide il server (`getProTrialStatus`): `trials/{uid}` non è leggibile.
     */
    suspend fun loadTrialOffer(familyId: String): KBTrialOfferStatus?

    /** «Chiedi di attivarla»: push al proprietario. true = consegnata. */
    suspend fun askOwnerForTrial(familyId: String): Result<Boolean>

    /** Attiva la prova Pro sulla famiglia (`startProTrial`). */
    suspend fun startTrial(familyId: String): Result<Unit>

    suspend fun updatePlanAfterPurchase(
        plan: KBPlan,
        purchaseToken: String,
        familyId: String,
        uid: String,
    ): Result<Unit>

    // Backward-compatible method used by existing screens/viewmodels.
    suspend fun getPlan(familyId: String): KBPlan
}
