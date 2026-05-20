package it.vittorioscocca.kidbox.data.remote.ai

import com.google.firebase.functions.FirebaseFunctions
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class AiReply(
    val reply: String,
    val usageToday: Int,
    val dailyLimit: Int,
    val messageUnitsConsumed: Int = 1,
    val isLargeContext: Boolean = false,
)

@Singleton
class AiRepository @Inject constructor() {

    private val functions = FirebaseFunctions.getInstance("europe-west1")

    suspend fun askAI(
        familyId: String,
        systemPrompt: String,
        messages: List<KBAIMessage>,
        purpose: String? = null,
    ): Result<AiReply> = runCatching {
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
        if (purpose == "clinicalRecord") {
            callable.setTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        }
        val result = callable
            .call(payload)
            .await()

        @Suppress("UNCHECKED_CAST")
        val data = result.getData() as? Map<String, Any> ?: error("Risposta non valida dall'AI")
        val units = (data["messageUnitsConsumed"] as? Number)?.toInt() ?: 1
        AiReply(
            reply = data["reply"] as? String ?: error("Campo reply mancante"),
            usageToday = (data["usageToday"] as? Number)?.toInt() ?: 0,
            dailyLimit = (data["dailyLimit"] as? Number)?.toInt() ?: 50,
            messageUnitsConsumed = units,
            isLargeContext = data["isLargeContext"] as? Boolean ?: (units > 1),
        )
    }
}
