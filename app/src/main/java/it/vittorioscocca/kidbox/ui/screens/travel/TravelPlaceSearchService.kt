package it.vittorioscocca.kidbox.ui.screens.travel

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import it.vittorioscocca.kidbox.data.remote.travel.TravelPlacesService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

data class TravelPlaceSuggestion(
    val title: String,
    val subtitle: String,
)

@Singleton
class TravelPlaceSearchService @Inject constructor() {
    private val functions = FirebaseFunctions.getInstance("europe-west1")

    suspend fun search(query: String, familyId: String): List<TravelPlaceSuggestion> {
        val trimmed = query.trim()
        if (trimmed.length < 2 || familyId.isBlank()) return emptyList()

        return runCatching {
            val payload = mapOf(
                "familyId" to familyId,
                "query" to trimmed,
                "languageCode" to TravelPlacesService.PLACES_LANGUAGE_CODE,
            )
            @Suppress("UNCHECKED_CAST")
            val data = functions
                .getHttpsCallable("searchTravelDestinations")
                .call(payload)
                .await()
                .getData() as? Map<String, Any?> ?: return emptyList()

            @Suppress("UNCHECKED_CAST")
            val raw = data["suggestions"] as? List<Map<String, Any?>> ?: emptyList()
            raw.mapNotNull { item ->
                val title = item["title"] as? String ?: return@mapNotNull null
                if (title.isBlank()) return@mapNotNull null
                TravelPlaceSuggestion(
                    title = title.trim(),
                    subtitle = (item["subtitle"] as? String).orEmpty().trim(),
                )
            }
        }.getOrElse { error ->
            if (error is FirebaseFunctionsException &&
                error.code == FirebaseFunctionsException.Code.FAILED_PRECONDITION
            ) {
                emptyList()
            } else {
                emptyList()
            }
        }
    }

    fun resetSession() {
        // Nessuna sessione lato client quando si usa la Cloud Function.
    }
}
