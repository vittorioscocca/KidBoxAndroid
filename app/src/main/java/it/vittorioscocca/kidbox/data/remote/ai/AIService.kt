package it.vittorioscocca.kidbox.data.remote.ai

import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.domain.model.ai.AIResponse
import it.vittorioscocca.kidbox.domain.model.ai.AIServiceError
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Singleton
class AIService @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    suspend fun sendMessage(
        messages: List<KBAIMessage>,
        systemPrompt: String,
        familyId: String,
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedFamilyId = getFamilyIdFromPrefs().ifBlank { familyId }
            val payload = hashMapOf(
                "messages" to messages.map { mapOf("role" to it.roleRaw, "content" to it.content) },
                "systemPrompt" to systemPrompt,
                "familyId" to resolvedFamilyId,
            )
            @Suppress("UNCHECKED_CAST")
            val data = functions.getHttpsCallable("askAI").call(payload).await().getData() as? Map<String, Any?>
                ?: error("Risposta AI non valida")
            AIResponse(
                reply = data["reply"] as? String ?: "",
                usageToday = (data["usageToday"] as? Number)?.toInt() ?: 0,
                dailyLimit = (data["dailyLimit"] as? Number)?.toInt() ?: 30,
            )
        }.mapError()
    }

    suspend fun fetchUsage(familyId: String): Result<AIResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedFamilyId = getFamilyIdFromPrefs().ifBlank { familyId }
            val payload = hashMapOf("familyId" to resolvedFamilyId)
            @Suppress("UNCHECKED_CAST")
            val data = functions.getHttpsCallable("getAIUsage").call(payload).await().getData() as? Map<String, Any?>
                ?: error("Risposta usage non valida")
            AIResponse(
                reply = "",
                usageToday = (data["usageToday"] as? Number)?.toInt() ?: 0,
                dailyLimit = (data["dailyLimit"] as? Number)?.toInt() ?: 30,
            )
        }.mapError()
    }

    private fun getFamilyIdFromPrefs(): String {
        val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
        return prefs.getString("family_id", "") ?: ""
    }
}

private fun Result<AIResponse>.mapError(): Result<AIResponse> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { throwable ->
            val mapped = when {
                throwable is FirebaseFunctionsException &&
                    throwable.details?.toString()?.contains("rate-limit-reached", true) == true ->
                    AIServiceException(AIServiceError.RateLimitReached)
                throwable is FirebaseFunctionsException &&
                    throwable.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                    AIServiceException(AIServiceError.RateLimitReached)
                throwable is FirebaseNetworkException ->
                    AIServiceException(AIServiceError.NetworkError)
                else ->
                    AIServiceException(AIServiceError.ServerError(throwable.message ?: "Errore server"))
            }
            Result.failure(mapped)
        },
    )

class AIServiceException(val serviceError: AIServiceError) : Exception()
