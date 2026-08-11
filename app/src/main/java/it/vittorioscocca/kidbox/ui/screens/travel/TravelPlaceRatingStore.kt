package it.vittorioscocca.kidbox.ui.screens.travel

import it.vittorioscocca.kidbox.data.remote.travel.TravelPlacesService
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CompletableDeferred

data class TravelPlaceRating(val value: Double, val reviewCount: Int)

/**
 * Cache dei voti Google per le righe delle liste luoghi (ristoranti, hotel,
 * attività), condivisa fra le liste.
 *
 * Ogni voto è una chiamata a Places, che si paga: la cache serve a non
 * ripagare la stessa riga a ogni scroll o rientro nella lista. Si memorizza
 * anche l'ESITO NEGATIVO (null), altrimenti un locale che Places non trova —
 * il caso frequente quando il nome viene da un itinerario generato — verrebbe
 * richiesto daccapo ogni volta che la riga ricompare.
 */
@Singleton
class TravelPlaceRatingStore @Inject constructor(
    private val placesService: TravelPlacesService,
) {
    private val cache = ConcurrentHashMap<String, TravelPlaceRating?>()
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<TravelPlaceRating?>>()

    suspend fun rating(placeName: String, locationContext: String, familyId: String): TravelPlaceRating? {
        val key = "${placeName.lowercase()}|${locationContext.lowercase()}"
        if (cache.containsKey(key)) return cache[key]

        val existing = inFlight[key]
        if (existing != null) return existing.await()

        val deferred = CompletableDeferred<TravelPlaceRating?>()
        val winner = inFlight.putIfAbsent(key, deferred) == null
        if (!winner) return inFlight[key]?.await()

        val result = placesService.fetchDetails(placeName, locationContext, familyId)
            .getOrNull()
            ?.let { details -> details.rating?.takeIf { it > 0 }?.let { TravelPlaceRating(it, details.reviewCount) } }

        cache[key] = result
        inFlight.remove(key)
        deferred.complete(result)
        return result
    }
}
