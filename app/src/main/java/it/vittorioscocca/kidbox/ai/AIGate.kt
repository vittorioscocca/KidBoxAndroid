package it.vittorioscocca.kidbox.ai

import com.google.firebase.functions.FirebaseFunctionsException
import it.vittorioscocca.kidbox.data.remote.ai.AIServiceException
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod
import it.vittorioscocca.kidbox.domain.model.ai.AIServiceError

object AIGate {
    fun mapErrorMessage(
        error: Throwable,
        currentPlan: KBPlan,
        dailyLimit: Int? = null,
        period: AIQuotaPeriod? = null,
    ): String {
        val serviceError = (error as? AIServiceException)?.serviceError
        val isExhausted = serviceError == AIServiceError.RateLimitReached ||
            (error is FirebaseFunctionsException && error.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED)
        val resolvedPeriod = period ?: currentPlan.aiQuotaPeriod
        return when {
            isExhausted && resolvedPeriod == AIQuotaPeriod.LIFETIME ->
                "Hai usato tutti i ${dailyLimit ?: currentPlan.aiMessageLimit} messaggi AI gratuiti inclusi nel piano Free. Passa a Pro per continuare a usare l'assistente."

            isExhausted ->
                "La famiglia ha raggiunto il limite di ${dailyLimit ?: currentPlan.aiMessageLimit} messaggi AI per oggi. Riprova domani."

            else -> error.localizedMessage ?: "Errore AI inatteso."
        }
    }
}
