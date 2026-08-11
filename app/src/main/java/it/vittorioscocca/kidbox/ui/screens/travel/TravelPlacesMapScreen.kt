package it.vittorioscocca.kidbox.ui.screens.travel

import android.content.Intent
import android.location.Geocoder
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.CameraPositionState
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val MapAccent = Color(0xFFF2611A)
private val ItineraryAccent = Color(0xFF2F6FE4)

/** Un luogo disegnato sulla mappa della categoria. */
private data class TravelMapPlace(
    val id: String,
    val name: String,
    val subtitle: String,
    val address: String,
    val rating: Double?,
    val reviewCount: Int,
    val latLng: LatLng,
    val mapsUri: String?,
    /** Vero per le voci che vengono dall'itinerario e non da Google. */
    val isFromItinerary: Boolean,
)

/**
 * Mappa di tutti i luoghi elencati in una sezione (Ristoranti / Hotel / Attività).
 *
 * I locali di Google arrivano già con le coordinate: si disegnano subito. Le
 * voci dell'itinerario invece sono solo testo, quindi la posizione si cerca
 * con Geocoder — gratis e senza chiamare Places una volta per riga.
 */
@Composable
fun TravelPlacesMapScreen(
    familyId: String,
    tripId: String,
    kind: String,
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
) {
    val context = LocalContext.current
    val kb = MaterialTheme.kidBoxColors

    val detailEntry = remember(backStackEntry) {
        navController.getBackStackEntry(AppDestination.TravelDetail.createRoute(familyId, tripId))
    }
    val detailViewModel: TravelDetailViewModel = hiltViewModel(detailEntry)
    val categoryEntry = remember(backStackEntry) {
        navController.getBackStackEntry(AppDestination.TravelCategoryResults.createRoute(familyId, tripId, kind))
    }
    val resultsViewModel: TravelCategoryResultsViewModel = hiltViewModel(categoryEntry)

    val trip by detailViewModel.trip.collectAsStateWithLifecycle()
    val dayPlans by detailViewModel.dayPlans.collectAsStateWithLifecycle()
    val legs by detailViewModel.legs.collectAsStateWithLifecycle()
    val members by detailViewModel.members.collectAsStateWithLifecycle()
    val children by detailViewModel.children.collectAsStateWithLifecycle()
    val placesResults by resultsViewModel.placesResults.collectAsStateWithLifecycle()

    val showsItinerarySection = kind != "hotels"

    val overview = trip?.let { detailViewModel.itineraryOverview(it, dayPlans, legs, members, children) }
    val destinationTitle = overview?.destinationTitle.orEmpty()
    val itineraryItems = if (showsItinerarySection) {
        trip?.let { currentTrip ->
            when (kind) {
                "activities" -> TravelItineraryBuilder.collectActivities(dayPlans, overview!!, currentTrip.aiProposalJson)
                else -> TravelItineraryBuilder.collectRestaurants(dayPlans, overview!!, currentTrip.aiProposalJson)
            }
        }.orEmpty().filter { it.isBrowsableOnMap }
    } else {
        emptyList()
    }

    var places by remember { mutableStateOf<List<TravelMapPlace>>(emptyList()) }
    var isResolvingItinerary by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<TravelMapPlace?>(null) }

    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(placesResults, itineraryItems, destinationTitle) {
        val fromGoogle = placesResults.mapNotNull { place ->
            val lat = place.latitude
            val lon = place.longitude
            if (lat == null || lon == null || (lat == 0.0 && lon == 0.0)) return@mapNotNull null
            TravelMapPlace(
                id = place.placeId,
                name = place.name,
                subtitle = place.category,
                address = place.address,
                rating = place.rating,
                reviewCount = place.reviewCount,
                latLng = LatLng(lat, lon),
                mapsUri = place.googleMapsUri,
                isFromItinerary = false,
            )
        }
        places = fromGoogle
        focusOn(cameraPositionState, fromGoogle.map { it.latLng })

        val resolvable = itineraryItems.take(12)
        if (resolvable.isEmpty()) return@LaunchedEffect

        isResolvingItinerary = true
        val geocoder = if (Geocoder.isPresent()) Geocoder(context, Locale.getDefault()) else null
        val resolved = mutableListOf<TravelMapPlace>()
        for (itemPlace in resolvable) {
            val alreadyThere = fromGoogle.any { it.name.equals(itemPlace.title, ignoreCase = true) }
            if (alreadyThere || geocoder == null) continue
            val location = itemPlace.locationContext.ifBlank { destinationTitle }
            val query = if (location.isBlank()) itemPlace.placeName else "${itemPlace.placeName}, $location"
            val address = withContext(Dispatchers.IO) {
                runCatching {
                    @Suppress("DEPRECATION")
                    geocoder.getFromLocationName(query.trim(), 1)?.firstOrNull()
                }.getOrNull()
            } ?: continue
            resolved += TravelMapPlace(
                id = itemPlace.id,
                name = itemPlace.title,
                subtitle = itemPlace.subtitle,
                address = address.getAddressLine(0).orEmpty(),
                rating = null,
                reviewCount = 0,
                latLng = LatLng(address.latitude, address.longitude),
                mapsUri = null,
                isFromItinerary = true,
            )
        }
        isResolvingItinerary = false
        if (resolved.isNotEmpty()) {
            places = fromGoogle + resolved
            focusOn(cameraPositionState, places.map { it.latLng })
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            properties = MapProperties(),
            uiSettings = MapUiSettings(zoomControlsEnabled = false, mapToolbarEnabled = false),
            onMapClick = { selected = null },
        ) {
            places.forEach { place ->
                Marker(
                    state = MarkerState(position = place.latLng),
                    icon = rememberCategoryMarkerDescriptor(kind, isFromItinerary = place.isFromItinerary, isSelected = selected?.id == place.id),
                    anchor = Offset(0.5f, 1.0f),
                    onClick = {
                        selected = place
                        true
                    },
                )
            }
        }

        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clickableRipple { navController.popBackStack() },
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.92f),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = Color.Black)
                }
            }
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.92f)) {
                Text(
                    "${places.size}",
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    fontWeight = FontWeight.Bold,
                    color = Color.Black,
                )
            }
        }

        AnimatedVisibility(
            visible = selected != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            selected?.let { place ->
                SelectedPlaceCard(
                    place = place,
                    kb = kb,
                    onDetails = {
                        val stopContext = TravelItineraryStopContext(
                            id = place.id,
                            placeName = place.name,
                            locationContext = destinationTitle,
                            scheduleBadge = "",
                            time = "",
                            staySummary = place.subtitle,
                            costSummary = place.address,
                            nextStopTitle = null,
                        )
                        navController.navigate(stopContext.toPlaceDetailRoute(familyId))
                    },
                    onDirections = { openDirectionsTo(context, place) },
                )
            }
        }
    }
}

@Composable
private fun SelectedPlaceCard(
    place: TravelMapPlace,
    kb: KidBoxColorScheme,
    onDetails: () -> Unit,
    onDirections: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(place.name, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = kb.title)
            if (place.rating != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(14.dp))
                    Text(
                        String.format(" %.1f", place.rating),
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = kb.title,
                    )
                    if (place.reviewCount > 0) {
                        Text(" (${place.reviewCount})", fontSize = 12.sp, color = kb.subtitle)
                    }
                    if (place.subtitle.isNotBlank()) {
                        Text(" · ${place.subtitle}", fontSize = 12.sp, color = kb.subtitle, maxLines = 1)
                    }
                }
            } else if (place.subtitle.isNotBlank()) {
                Text(place.subtitle, fontSize = 12.sp, color = kb.subtitle)
            }
            if (place.address.isNotBlank()) {
                Text(place.address, fontSize = 12.sp, color = kb.subtitle, maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.weight(1f).clickableRipple(onDetails),
                    shape = RoundedCornerShape(50),
                    color = MapAccent.copy(alpha = 0.14f),
                ) {
                    Row(
                        Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Info, contentDescription = null, tint = MapAccent, modifier = Modifier.size(16.dp))
                        Text(" Dettagli", color = MapAccent, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f).clickableRipple(onDirections),
                    shape = RoundedCornerShape(50),
                    color = kb.subtitle.copy(alpha = 0.10f),
                ) {
                    Row(
                        Modifier.padding(vertical = 10.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Navigation, contentDescription = null, tint = kb.title, modifier = Modifier.size(16.dp))
                        Text(" Indicazioni", color = kb.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

private fun focusOn(cameraPositionState: CameraPositionState, points: List<LatLng>) {
    if (points.isEmpty()) return
    if (points.size == 1) {
        cameraPositionState.move(CameraUpdateFactory.newLatLngZoom(points.first(), 14f))
        return
    }
    val builder = LatLngBounds.Builder()
    points.forEach { builder.include(it) }
    runCatching {
        cameraPositionState.move(CameraUpdateFactory.newLatLngBounds(builder.build(), 80))
    }
}

private fun openDirectionsTo(context: android.content.Context, place: TravelMapPlace) {
    place.mapsUri?.let { uri ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri)))
        return
    }
    val uri = Uri.parse("google.navigation:q=${place.latLng.latitude},${place.latLng.longitude}&mode=d")
    val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
        return
    }
    val geo = Uri.parse("geo:${place.latLng.latitude},${place.latLng.longitude}?q=${Uri.encode(place.name)}")
    context.startActivity(Intent(Intent.ACTION_VIEW, geo))
}

/** Emoji per categoria — la stessa già usata nel resto della UI Viaggi. */
private fun categoryEmoji(kind: String): String = when (kind) {
    "hotels" -> "🛏️"
    "activities" -> "🎯"
    else -> "🍽️"
}

/**
 * Segnaposto dedicato: cerchio colorato con emoji e punta verso il basso,
 * così si distingue dai pin di sistema. Arancione per i locali Google, blu
 * per le voci dell'itinerario — la fonte si vede a colpo d'occhio.
 */
@Composable
private fun rememberCategoryMarkerDescriptor(
    kind: String,
    isFromItinerary: Boolean,
    isSelected: Boolean,
): BitmapDescriptor {
    val density = LocalDensity.current.density
    return remember(kind, isFromItinerary, isSelected, density) {
        val tint = if (isFromItinerary) ItineraryAccent else MapAccent
        val sizeDp = if (isSelected) 46 else 38
        val sizePx = (sizeDp * density).toInt()
        val pointerHeightPx = (10 * density).toInt()
        val bitmap = android.graphics.Bitmap.createBitmap(
            sizePx,
            sizePx + pointerHeightPx,
            android.graphics.Bitmap.Config.ARGB_8888,
        )
        val canvas = android.graphics.Canvas(bitmap)
        val radius = sizePx / 2f
        val center = radius

        val fillPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.argb(
                255,
                (tint.red * 255).toInt(),
                (tint.green * 255).toInt(),
                (tint.blue * 255).toInt(),
            )
        }
        val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = android.graphics.Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        // Punta verso il basso, sotto il cerchio.
        val pointerPath = android.graphics.Path().apply {
            moveTo(center - 6f * density, sizePx - 2f * density)
            lineTo(center + 6f * density, sizePx - 2f * density)
            lineTo(center, sizePx + pointerHeightPx - 2f * density)
            close()
        }
        canvas.drawPath(pointerPath, fillPaint)

        canvas.drawCircle(center, center, radius - 2f * density, fillPaint)
        canvas.drawCircle(center, center, radius - 2f * density, borderPaint)

        val emojiPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            textSize = sizePx * 0.5f
            textAlign = android.graphics.Paint.Align.CENTER
        }
        val emoji = categoryEmoji(kind)
        val textBounds = android.graphics.Rect()
        emojiPaint.getTextBounds(emoji, 0, emoji.length, textBounds)
        canvas.drawText(emoji, center, center - textBounds.exactCenterY(), emojiPaint)

        BitmapDescriptorFactory.fromBitmap(bitmap)
    }
}

@Composable
private fun Modifier.clickableRipple(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
