package it.vittorioscocca.kidbox.ui.screens.travel

data class TravelPlaceReview(
    val id: String,
    val authorName: String,
    val text: String,
    val rating: Int,
    val relativeTime: String,
    val profilePhotoUrl: String?,
)

data class TravelPlaceDetails(
    val placeId: String,
    val name: String,
    val category: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val rating: Double?,
    val reviewCount: Int,
    val about: String,
    val photoUrls: List<String>,
    val reviews: List<TravelPlaceReview>,
    val googleMapsUri: String?,
) {
    val hasCoordinates: Boolean get() = latitude != 0.0 || longitude != 0.0
}

sealed class TravelPlacesServiceError(message: String) : Exception(message) {
    data object NotConfigured : TravelPlacesServiceError(
        "Google Places non è configurato. Verifica la chiave API sulle Cloud Functions.",
    )
    data object NotFound : TravelPlacesServiceError("Non abbiamo trovato questo luogo su Google.")
    data object InvalidResponse : TravelPlacesServiceError("Risposta non valida dal servizio luoghi.")
    class Network(msg: String) : TravelPlacesServiceError(msg)
}
