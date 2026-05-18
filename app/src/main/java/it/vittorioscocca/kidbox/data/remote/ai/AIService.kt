package it.vittorioscocca.kidbox.data.remote.ai

import android.content.Context
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.ui.screens.travel.toTravelPlanMap
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.ui.screens.travel.TravelDestination
import it.vittorioscocca.kidbox.ui.screens.travel.TravelSuggestionsResult
import it.vittorioscocca.kidbox.domain.model.ai.AIResponse
import it.vittorioscocca.kidbox.domain.model.ai.AIServiceError
import javax.inject.Inject
import javax.inject.Singleton
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

@Singleton
class AIService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val familySessionPreferences: FamilySessionPreferences,
    private val subscriptionRepository: SubscriptionRepository,
    private val aiUsageTracker: AIUsageTracker,
) {
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    suspend fun sendMessage(
        messages: List<KBAIMessage>,
        systemPrompt: String,
        familyId: String,
    ): Result<AIResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedFamilyId = resolveFamilyId(familyId)
            val payload = hashMapOf(
                "messages" to messages.map { mapOf("role" to it.roleRaw, "content" to it.content) },
                "systemPrompt" to systemPrompt,
                "familyId" to resolvedFamilyId,
            )
            @Suppress("UNCHECKED_CAST")
            val data = functions.getHttpsCallable("askAI").call(payload).await().getData() as? Map<String, Any?>
                ?: error("Risposta AI non valida")
            val usageToday = (data["usageToday"] as? Number)?.toInt() ?: 0
            val dailyLimit = (data["dailyLimit"] as? Number)?.toInt() ?: 30
            aiUsageTracker.apply(usageToday, dailyLimit)
            AIResponse(
                reply = data["reply"] as? String ?: "",
                usageToday = usageToday,
                dailyLimit = dailyLimit,
            )
        }.mapError()
    }

    suspend fun fetchUsage(familyId: String): Result<AIResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedFamilyId = resolveFamilyId(familyId)
            val payload = hashMapOf("familyId" to resolvedFamilyId)
            @Suppress("UNCHECKED_CAST")
            val data = functions.getHttpsCallable("getAIUsage").call(payload).await().getData() as? Map<String, Any?>
                ?: error("Risposta usage non valida")
            val usageToday = (data["usageToday"] as? Number)?.toInt() ?: 0
            val dailyLimit = (data["dailyLimit"] as? Number)?.toInt() ?: 30
            aiUsageTracker.apply(usageToday, dailyLimit)
            AIResponse(
                reply = "",
                usageToday = usageToday,
                dailyLimit = dailyLimit,
            )
        }.mapError()
    }

    suspend fun suggestTravelDestinations(
        travelProfile: Map<String, Any>,
        familyId: String,
    ): Result<TravelSuggestionsResult> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedFamilyId = resolveFamilyId(familyId)
            if (!subscriptionRepository.getPlan(resolvedFamilyId).includesAI) {
                error("I suggerimenti AI richiedono un piano Pro o Max.")
            }
            val payload = hashMapOf(
                "familyId" to resolvedFamilyId,
                "travelProfile" to travelProfile,
            )
            @Suppress("UNCHECKED_CAST")
            val data = functions.getHttpsCallable("suggestTravelDestinations")
                .apply { setTimeout(90, TimeUnit.SECONDS) }
                .call(payload)
                .await()
                .getData() as? Map<String, Any?>
                ?: error("Risposta suggerimenti non valida")
            val rawList = data["destinations"] as? List<Map<String, Any?>>
                ?: error("Risposta suggerimenti non valida")
            val destinations = rawList.mapNotNull { TravelDestination.fromMap(it) }
            if (destinations.isEmpty()) error("Nessuna destinazione suggerita")
            val usageToday = (data["usageToday"] as? Number)?.toInt() ?: 0
            val dailyLimit = (data["dailyLimit"] as? Number)?.toInt() ?: 0
            aiUsageTracker.apply(usageToday, dailyLimit)
            TravelSuggestionsResult(
                destinations = destinations,
                profileSummary = data["profileSummary"] as? String ?: "",
                usageToday = usageToday,
                dailyLimit = dailyLimit,
            )
        }.mapTravelCallableError()
    }

    suspend fun generateTravelPlan(
        request: TravelPlanRequest,
        familyId: String,
    ): Result<TravelPlanResponse> = withContext(Dispatchers.IO) {
        runCatching {
            val resolvedFamilyId = resolveFamilyId(familyId)
            if (!subscriptionRepository.getPlan(resolvedFamilyId).includesAI) {
                error("La pianificazione AI richiede un piano Pro o Max.")
            }
            val payload = hashMapOf<String, Any>(
                "familyId" to resolvedFamilyId,
                "wizardData" to request.wizardData,
                "freeTextPrompt" to request.freeTextPrompt,
                "familyContext" to request.familyContext,
            )
            if (request.regenerateSingleDay) {
                payload["regenerateSingleDay"] = true
            }
            android.util.Log.i(
                "AIService",
                "generateTravelPlan call familyId=$resolvedFamilyId single=${request.regenerateSingleDay} promptLen=${request.freeTextPrompt.length}",
            )
            @Suppress("UNCHECKED_CAST")
            val data = functions.getHttpsCallable("generateTravelPlan")
                .apply { setTimeout(150, TimeUnit.SECONDS) }
                .call(payload)
                .await()
                .getData() as? Map<String, Any?>
                ?: error("Risposta viaggio AI non valida")
            val usageToday = (data["usageToday"] as? Number)?.toInt() ?: 0
            val dailyLimit = (data["dailyLimit"] as? Number)?.toInt() ?: 0
            aiUsageTracker.apply(usageToday, dailyLimit)
            val travelPlanMap = data["travelPlan"].toTravelPlanMap()
            val dayPlanCount = (travelPlanMap?.get("dayPlans") as? List<*>)?.size ?: 0
            val narrativeText = data["narrativeText"] as? String ?: ""
            android.util.Log.i(
                "AIService",
                "generateTravelPlan success usage=$usageToday/$dailyLimit dayPlans=$dayPlanCount narrativeLen=${narrativeText.length}",
            )
            TravelPlanResponse(
                travelPlan = travelPlanMap,
                narrativeText = narrativeText,
                usageToday = usageToday,
                dailyLimit = dailyLimit,
            )
        }.onFailure { err ->
            android.util.Log.e("AIService", "generateTravelPlan failed", err)
        }.mapTravelCallableError()
    }

    /**
     * Famiglia attiva: [FamilySessionPreferences] (`active_family_id`), poi parametro esplicito,
     * infine legacy `family_id` nelle prefs (build vecchie).
     */
    private fun resolveFamilyId(explicitFamilyId: String): String {
        familySessionPreferences.getActiveFamilyId()?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
        explicitFamilyId.trim().takeIf { it.isNotEmpty() }?.let { return it }
        val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
        return prefs.getString("family_id", null)?.trim().orEmpty()
    }
}

data class TravelPlanRequest(
    val wizardData: Map<String, Any>,
    val freeTextPrompt: String,
    val familyContext: Map<String, Any>,
    val regenerateSingleDay: Boolean = false,
)

data class TravelPlanResponse(
    val travelPlan: Map<String, Any>?,
    val narrativeText: String,
    val usageToday: Int,
    val dailyLimit: Int,
)

private fun <T> Result<T>.mapTravelCallableError(): Result<T> =
    fold(
        onSuccess = { Result.success(it) },
        onFailure = { throwable ->
            val mapped = when {
                throwable is FirebaseFunctionsException &&
                    throwable.code == FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED ->
                    AIServiceException(AIServiceError.RateLimitReached)
                throwable is FirebaseFunctionsException &&
                    throwable.code == FirebaseFunctionsException.Code.PERMISSION_DENIED ->
                    AIServiceException(AIServiceError.RateLimitReached)
                throwable is FirebaseFunctionsException &&
                    throwable.code == FirebaseFunctionsException.Code.NOT_FOUND ->
                    AIServiceException(
                        AIServiceError.ServerError(
                            "Servizio AI viaggi non disponibile. Aggiorna l'app e riprova.",
                        ),
                    )
                throwable is FirebaseFunctionsException &&
                    throwable.code == FirebaseFunctionsException.Code.DEADLINE_EXCEEDED ->
                    AIServiceException(
                        AIServiceError.ServerError(
                            "La richiesta ha impiegato troppo tempo. Riprova.",
                        ),
                    )
                throwable is FirebaseNetworkException ->
                    AIServiceException(AIServiceError.NetworkError)
                else ->
                    AIServiceException(AIServiceError.ServerError(throwable.message ?: "Errore server"))
            }
            Result.failure(mapped)
        },
    )

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
