package it.vittorioscocca.kidbox.ui.screens.travel
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

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

enum class TravelPlaceSearchKind(val rawValue: String) {
    RESTAURANT("restaurant"),
    HOTEL("hotel"),
    ATTRACTION("attraction"),
}

data class TravelPlaceSummary(
    val placeId: String,
    val name: String,
    val address: String,
    val category: String,
    val rating: Double?,
    val reviewCount: Int,
    val latitude: Double?,
    val longitude: Double?,
    val googleMapsUri: String?,
) {
    /** Coordinata utilizzabile sulla mappa, quando Google l'ha restituita. */
    val hasCoordinates: Boolean
        get() = latitude != null && longitude != null && (latitude != 0.0 || longitude != 0.0)
}

sealed class TravelPlacesServiceError(message: String) : Exception(message) {
    data object NotConfigured : TravelPlacesServiceError(
        "",
    )
    data object NotFound : TravelPlacesServiceError("")
    data object InvalidResponse : TravelPlacesServiceError("")
    class Network(msg: String) : TravelPlacesServiceError(msg)
}
