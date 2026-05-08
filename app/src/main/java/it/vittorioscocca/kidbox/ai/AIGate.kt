package it.vittorioscocca.kidbox.ai

import com.google.firebase.functions.FirebaseFunctionsException
import it.vittorioscocca.kidbox.data.remote.ai.AIServiceException
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.ai.AIServiceError

object AIGate {
    fun mapErrorMessage(
        error: Throwable,
        currentPlan: KBPlan,
        dailyLimit: Int? = null,
    ): String {
        val serviceError = (error as? AIServiceException)?.serviceError
        return when {
            serviceError == AIServiceError.RateLimitReached && currentPlan == KBPlan.FREE ->
                "L'assistente AI è disponibile con i piani Pro e Max."

            serviceError == AIServiceError.RateLimitReached ->
                "La famiglia ha raggiunto il limite di ${dailyLimit ?: currentPlan.aiDailyLimit} messaggi AI per oggi. Riprova domani."

            error is FirebaseFunctionsException &&
                error.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED &&
                currentPlan == KBPlan.FREE ->
                "L'assistente AI è disponibile con i piani Pro e Max."

            error is FirebaseFunctionsException &&
                error.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                "La famiglia ha raggiunto il limite di ${dailyLimit ?: currentPlan.aiDailyLimit} messaggi AI per oggi. Riprova domani."

            else -> error.localizedMessage ?: "Errore AI inatteso."
        }
    }
}
