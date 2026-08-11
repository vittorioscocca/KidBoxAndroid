package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val CategoryAccent = Color(0xFFF2611A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelCategoryResultsScreen(
    familyId: String,
    tripId: String,
    kind: String,
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
) {
    val detailEntry = remember(backStackEntry) {
        navController.getBackStackEntry(AppDestination.TravelDetail.createRoute(familyId, tripId))
    }
    val viewModel: TravelDetailViewModel = hiltViewModel(detailEntry)
    val resultsViewModel: TravelCategoryResultsViewModel = hiltViewModel(backStackEntry)
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val dayPlans by viewModel.dayPlans.collectAsStateWithLifecycle()
    val legs by viewModel.legs.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val children by viewModel.children.collectAsStateWithLifecycle()
    val placesResults by resultsViewModel.placesResults.collectAsStateWithLifecycle()
    val isLoadingPlaces by resultsViewModel.isLoadingPlaces.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors

    val title = when (kind) {
        "hotels" -> stringResource(R.string.travel_hotels)
        "activities" -> stringResource(R.string.travel_activities)
        else -> stringResource(R.string.travel_restaurants)
    }
    val emoji = when (kind) {
        "hotels" -> "🛏️"
        "activities" -> "🎯"
        else -> "🍽️"
    }
    val searchKind = when (kind) {
        "hotels" -> TravelPlaceSearchKind.HOTEL
        "activities" -> TravelPlaceSearchKind.ATTRACTION
        else -> TravelPlaceSearchKind.RESTAURANT
    }
    // Hotel: solo Google, quelle voci nel testo dell'itinerario sono
    // descrizioni ("Pernottamento in un hotel vicino al centro"), non nomi di
    // strutture — niente da prenotare né da cercare. Ristoranti e Attività
    // mostrano itinerario e Google insieme.
    val showsItinerarySection = kind != "hotels"

    val items = trip?.let { currentTrip ->
        val overview = viewModel.itineraryOverview(currentTrip, dayPlans, legs, members, children)
        when (kind) {
            "hotels" -> TravelItineraryBuilder.collectHotels(dayPlans, overview)
            "activities" -> TravelItineraryBuilder.collectActivities(
                dayPlans,
                overview,
                currentTrip.aiProposalJson,
            )
            else -> TravelItineraryBuilder.collectRestaurants(dayPlans, overview, currentTrip.aiProposalJson)
        }
    }.orEmpty().let { if (showsItinerarySection) it else emptyList() }

    val destinationTitle = trip?.let { currentTrip ->
        viewModel.itineraryOverview(currentTrip, dayPlans, legs, members, children).destinationTitle
    }.orEmpty()

    LaunchedEffect(destinationTitle, searchKind) {
        resultsViewModel.searchPlaces(destinationTitle, searchKind, familyId)
    }

    val canShowMap = placesResults.any { it.hasCoordinates } || items.any { it.isBrowsableOnMap }

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text(title, color = kb.title) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                actions = {
                    if (canShowMap) {
                        IconButton(
                            onClick = {
                                navController.navigate(
                                    AppDestination.TravelPlacesMap.createRoute(familyId, tripId, kind),
                                )
                            },
                        ) {
                            Icon(Icons.Filled.Map, contentDescription = stringResource(R.string.travel_map), tint = CategoryAccent)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
            )
        },
    ) { padding ->
        if (items.isEmpty() && placesResults.isEmpty() && !isLoadingPlaces) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(stringResource(R.string.travel_no_results), fontWeight = FontWeight.Bold, color = kb.title)
                Text(
                    if (showsItinerarySection) {
                        stringResource(R.string.travel_no_suggestions)
                    } else {
                        stringResource(R.string.travel_no_google_places, destinationTitle)
                    },
                    color = kb.subtitle,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (placesResults.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.travel_from_google, destinationTitle),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = kb.subtitle,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(placesResults, key = { "g_${it.placeId}" }) { place ->
                        PlaceRow(
                            place = place,
                            emoji = emoji,
                            destinationTitle = destinationTitle,
                            familyId = familyId,
                            navController = navController,
                        )
                    }
                }
                if (isLoadingPlaces && placesResults.isEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.travel_category_searching_places, destinationTitle),
                            color = kb.subtitle,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                if (showsItinerarySection && items.isNotEmpty()) {
                    item {
                        Text(
                            stringResource(R.string.travel_from_itinerary),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = kb.subtitle,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(items, key = { it.id }) { item ->
                        ItineraryRow(
                            item = item,
                            emoji = emoji,
                            title = title,
                            destinationTitle = destinationTitle,
                            familyId = familyId,
                            navController = navController,
                            ratingStore = resultsViewModel.ratingStore,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(
    place: TravelPlaceSummary,
    emoji: String,
    destinationTitle: String,
    familyId: String,
    navController: NavHostController,
) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        Modifier
            .fillMaxWidth()
            .clickable {
                val context = TravelItineraryStopContext(
                    id = place.placeId,
                    placeName = place.name,
                    locationContext = destinationTitle,
                    scheduleBadge = "",
                    time = "",
                    staySummary = place.category,
                    costSummary = place.address,
                    nextStopTitle = null,
                )
                navController.navigate(context.toPlaceDetailRoute(familyId))
            }
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Text(
                place.name,
                fontWeight = FontWeight.Bold,
                color = kb.title,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CategoryAccent)
        }
        if (place.rating != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.padding(end = 4.dp))
                Text(String.format("%.1f", place.rating), fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = kb.title)
                if (place.reviewCount > 0) {
                    Text(" (${place.reviewCount})", fontSize = 12.sp, color = kb.subtitle)
                }
                if (place.category.isNotBlank()) {
                    Text(" · ${place.category}", fontSize = 12.sp, color = kb.subtitle)
                }
            }
        }
        if (place.address.isNotBlank()) {
            Text(place.address, color = kb.subtitle, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun ItineraryRow(
    item: TravelPlaceResult,
    emoji: String,
    title: String,
    destinationTitle: String,
    familyId: String,
    navController: NavHostController,
    ratingStore: TravelPlaceRatingStore,
) {
    val kb = MaterialTheme.kidBoxColors
    val browsable = item.isBrowsableOnMap
    var rating by remember(item.placeName) { mutableStateOf<TravelPlaceRating?>(null) }

    LaunchedEffect(item.placeName, item.locationContext) {
        if (item.placeName.isNotBlank()) {
            rating = ratingStore.rating(item.placeName, item.locationContext, familyId)
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .then(
                if (browsable) {
                    Modifier.clickable {
                        val context = item.toPlaceContext(title, destinationTitle)
                        navController.navigate(context.toPlaceDetailRoute(familyId))
                    }
                } else {
                    Modifier
                },
            )
            .padding(vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 22.sp)
            Text(
                item.title,
                fontWeight = FontWeight.Bold,
                color = kb.title,
                modifier = Modifier
                    .padding(start = 10.dp)
                    .weight(1f),
            )
            if (browsable) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = CategoryAccent)
            }
        }
        if (item.subtitle.isNotBlank()) {
            Text(item.subtitle, color = kb.subtitle, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
        }
        if (item.meta.isNotBlank()) {
            Text(item.meta, color = kb.subtitle, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
        }
        rating?.let { r ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.padding(end = 4.dp))
                Text(String.format("%.1f", r.value), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = kb.title)
                if (r.reviewCount > 0) {
                    Text(" (${r.reviewCount})", fontSize = 11.sp, color = kb.subtitle)
                }
            }
        }
    }
}
