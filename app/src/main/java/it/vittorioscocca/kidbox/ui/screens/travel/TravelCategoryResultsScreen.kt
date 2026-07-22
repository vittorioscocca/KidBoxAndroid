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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

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
    val trip by viewModel.trip.collectAsStateWithLifecycle()
    val dayPlans by viewModel.dayPlans.collectAsStateWithLifecycle()
    val legs by viewModel.legs.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val children by viewModel.children.collectAsStateWithLifecycle()
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
    }.orEmpty()

    val destinationTitle = trip?.let { currentTrip ->
        viewModel.itineraryOverview(currentTrip, dayPlans, legs, members, children).destinationTitle
    }.orEmpty()

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
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
                    stringResource(R.string.travel_no_suggestions),
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
                items(items, key = { it.id }) { item ->
                    val browsable = item.isBrowsableOnMap
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
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = CategoryAccent,
                                )
                            }
                        }
                        if (item.subtitle.isNotBlank()) {
                            Text(item.subtitle, color = kb.subtitle, fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (item.meta.isNotBlank()) {
                            Text(item.meta, color = kb.subtitle, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
                        }
                    }
                }
            }
        }
    }
}
