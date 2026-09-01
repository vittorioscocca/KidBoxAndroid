package it.vittorioscocca.kidbox.data.remote.ai

import com.google.firebase.functions.FirebaseFunctions
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.ai.CurrentPlanStore
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

/** Purpose askAI riservati ai piani a pagamento. */
private val PAID_ONLY_PURPOSES = setOf("mealPlan", "fitnessPlan", "fitnessAdjust", "fitnessCopilot")

data class AiReply(
    val reply: String,
    val usageToday: Int,
    val dailyLimit: Int,
    val messageUnitsConsumed: Int = 1,
    val isLargeContext: Boolean = false,
    val period: AIQuotaPeriod = AIQuotaPeriod.DAILY,
)

@Singleton
class AiRepository @Inject constructor(
    private val aiUsageTracker: AIUsageTracker,
) {

    private val functions = FirebaseFunctions.getInstance("europe-west1")

    suspend fun askAI(
        familyId: String,
        systemPrompt: String,
        messages: List<KBAIMessage>,
        purpose: String? = null,
    ): Result<AiReply> = runCatching {
        // Presidio client sui purpose riservati ai piani a pagamento: è il collo
        // di bottiglia attraversato da ogni percorso (generazione, rigenerazione,
        // spostamento seduta, copilota). Il gate vero resta lato server
        // (`quota.period === "lifetime"`), questo evita di bruciare una chiamata.
        if (purpose in PAID_ONLY_PURPOSES && CurrentPlanStore.plan.value == KBPlan.FREE) {
            error("Questa funzione AI è inclusa nei piani Pro e Max. Passa a Pro per usarla.")
        }
        val payload = hashMapOf(
            "familyId" to familyId,
            "systemPrompt" to systemPrompt,
            "messages" to messages.map { msg ->
                hashMapOf(
                    "role" to msg.roleRaw,
                    "content" to msg.content,
                )
            },
        )
        if (!purpose.isNullOrBlank()) {
            payload["purpose"] = purpose
        }
        val callable = functions.getHttpsCallable("askAI")
        // Sonnet senza streaming: la cartella clinica e soprattutto il piano
        // alimentare (8192 token di output) sforavano i 120s → DEADLINE_EXCEEDED.
        when (purpose) {
            "mealPlan", "fitnessPlan" -> callable.setTimeout(300, java.util.concurrent.TimeUnit.SECONDS)
            "clinicalRecord" -> callable.setTimeout(240, java.util.concurrent.TimeUnit.SECONDS)
        }
        val result = callable
            .call(payload)
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as? Map<String, Any> ?: error("Risposta non valida dall'AI")
        val units = (data["messageUnitsConsumed"] as? Number)?.toInt() ?: 1
        val usageToday = (data["usageToday"] as? Number)?.toInt() ?: 0
        val dailyLimit = (data["dailyLimit"] as? Number)?.toInt() ?: 50
        val period = AIQuotaPeriod.fromRaw(data["period"] as? String)
        aiUsageTracker.apply(usageToday, dailyLimit, period)
        AiReply(
            reply = data["reply"] as? String ?: error("Campo reply mancante"),
            usageToday = usageToday,
            dailyLimit = dailyLimit,
            messageUnitsConsumed = units,
            isLargeContext = data["isLargeContext"] as? Boolean ?: (units > 1),
            period = period,
        )
    }
}
