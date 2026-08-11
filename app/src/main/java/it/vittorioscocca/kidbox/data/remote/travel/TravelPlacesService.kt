package it.vittorioscocca.kidbox.data.remote.travel

import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import it.vittorioscocca.kidbox.ui.screens.travel.TravelPlaceDetails
import it.vittorioscocca.kidbox.ui.screens.travel.TravelPlaceReview
import it.vittorioscocca.kidbox.ui.screens.travel.TravelPlaceSearchKind
import it.vittorioscocca.kidbox.ui.screens.travel.TravelPlaceSummary
import it.vittorioscocca.kidbox.ui.screens.travel.TravelPlacesServiceError
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class TravelPlacesService @Inject constructor() {
    companion object {
        /** Lingua richiesta a Google Places (Cloud Function). */
        const val PLACES_LANGUAGE_CODE = "it"
    }

    private val functions = FirebaseFunctions.getInstance("europe-west1")

    suspend fun fetchDetails(
        placeName: String,
        locationContext: String,
        familyId: String,
    ): Result<TravelPlaceDetails> = runCatching {
        val payload = hashMapOf(
            "familyId" to familyId,
            "placeName" to placeName.trim(),
            "locationContext" to locationContext.trim(),
            "languageCode" to PLACES_LANGUAGE_CODE,
        )
        @Suppress("UNCHECKED_CAST")
        val data = functions
            .getHttpsCallable("getTravelPlaceDetails")
            .call(payload)
            .await()
            .getData() as? Map<String, Any?> ?: throw TravelPlacesServiceError.InvalidResponse

        if (data["found"] as? Boolean == false) {
            throw TravelPlacesServiceError.NotFound
        }
        @Suppress("UNCHECKED_CAST")
        val place = data["place"] as? Map<String, Any?> ?: throw TravelPlacesServiceError.InvalidResponse
        parsePlace(place)
    }.recoverCatching { error ->
        if (error is TravelPlacesServiceError) throw error
        val code = (error as? FirebaseFunctionsException)?.code
        if (code == FirebaseFunctionsException.Code.FAILED_PRECONDITION) {
            throw TravelPlacesServiceError.NotConfigured
        }
        throw TravelPlacesServiceError.Network(error.localizedMessage ?: "Errore di rete")
    }

    /**
     * Luoghi reali di una categoria nella località (Places Text Search).
     *
     * Sostituisce l'estrazione dal testo dell'itinerario: quei "nomi" erano
     * frasi o spezzoni, e per definizione non erano cercabili su Google —
     * quindi niente voti. Qui i risultati sono locali esistenti e il voto
     * arriva nella stessa risposta, senza una chiamata per riga.
     */
    suspend fun searchPlaces(
        locationContext: String,
        kind: TravelPlaceSearchKind,
        familyId: String,
    ): List<TravelPlaceSummary> = runCatching {
        val payload = hashMapOf(
            "locationContext" to locationContext,
            "kind" to kind.rawValue,
            "languageCode" to PLACES_LANGUAGE_CODE,
        )
        @Suppress("UNCHECKED_CAST")
        val data = functions
            .getHttpsCallable("searchTravelPlaces")
            .call(payload)
            .await()
            .getData() as? Map<String, Any?> ?: return@runCatching emptyList()

        @Suppress("UNCHECKED_CAST")
        val raw = data["places"] as? List<Map<String, Any?>> ?: emptyList()
        raw.mapNotNull { dict ->
            val name = dict["name"] as? String ?: return@mapNotNull null
            if (name.isBlank()) return@mapNotNull null
            TravelPlaceSummary(
                placeId = dict["placeId"] as? String ?: UUID.randomUUID().toString(),
                name = name,
                address = dict["address"] as? String ?: "",
                category = dict["category"] as? String ?: "",
                rating = (dict["rating"] as? Number)?.toDouble(),
                reviewCount = (dict["reviewCount"] as? Number)?.toInt() ?: 0,
                latitude = (dict["latitude"] as? Number)?.toDouble(),
                longitude = (dict["longitude"] as? Number)?.toDouble(),
                googleMapsUri = dict["googleMapsUri"] as? String,
            )
        }
    }.getOrElse { emptyList() }

    private fun parsePlace(dict: Map<String, Any?>): TravelPlaceDetails {
        val name = dict["name"] as? String ?: ""
        if (name.isBlank()) throw TravelPlacesServiceError.InvalidResponse

        @Suppress("UNCHECKED_CAST")
        val reviewDicts = dict["reviews"] as? List<Map<String, Any?>> ?: emptyList()
        val reviews = reviewDicts.mapNotNull { review ->
            val text = review["text"] as? String ?: return@mapNotNull null
            if (text.isBlank()) return@mapNotNull null
            TravelPlaceReview(
                id = review["id"] as? String ?: java.util.UUID.randomUUID().toString(),
                authorName = review["authorName"] as? String ?: "Recensione",
                text = text,
                rating = (review["rating"] as? Number)?.toInt() ?: 0,
                relativeTime = review["relativeTime"] as? String ?: "",
                profilePhotoUrl = review["profilePhotoUrl"] as? String,
            )
        }

        @Suppress("UNCHECKED_CAST")
        val photoUrls = (dict["photoUrls"] as? List<String>).orEmpty()

        return TravelPlaceDetails(
            placeId = dict["placeId"] as? String ?: "",
            name = name,
            category = dict["category"] as? String ?: "Luogo di interesse",
            address = dict["address"] as? String ?: "",
            latitude = (dict["latitude"] as? Number)?.toDouble() ?: 0.0,
            longitude = (dict["longitude"] as? Number)?.toDouble() ?: 0.0,
            rating = (dict["rating"] as? Number)?.toDouble(),
            reviewCount = (dict["reviewCount"] as? Number)?.toInt() ?: 0,
            about = dict["about"] as? String ?: "",
            photoUrls = photoUrls,
            reviews = reviews,
            googleMapsUri = dict["googleMapsUri"] as? String,
        )
    }
}
