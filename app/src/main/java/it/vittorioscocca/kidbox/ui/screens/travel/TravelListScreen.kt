package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelListScreen(
    familyId: String,
    onNavigateBack: () -> Unit,
    onOpenWizard: () -> Unit,
    onOpenDiscover: () -> Unit,
    onOpenTrip: (String) -> Unit,
    onOpenAllTrips: () -> Unit,
    viewModel: TravelListViewModel = hiltViewModel(),
) {
    LaunchedEffect(familyId) { viewModel.setFamilyId(familyId) }
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val recentTrips = trips.take(3)
    val legsByTripId by viewModel.legsByTripId.collectAsStateWithLifecycle()
    val familyPlan by viewModel.familyPlan.collectAsStateWithLifecycle()
    val needsOnboarding by viewModel.needsOnboarding.collectAsStateWithLifecycle()
    val travelProfile by viewModel.travelProfile.collectAsStateWithLifecycle()
    val aiAvailable = familyPlan.includesAI
    val kb = MaterialTheme.kidBoxColors

    when (needsOnboarding) {
        null -> {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return
        }
        true -> {
            TravelOnboardingScreen(
                onComplete = { profile -> viewModel.completeOnboarding(profile) },
            )
            return
        }
        false -> Unit
    }

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.travel_title), color = kb.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenWizard) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.travel_new_trip))
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TravelHubSection(
                    profile = travelProfile,
                    aiAvailable = aiAvailable,
                    onPlanTrip = onOpenWizard,
                    onDiscover = onOpenDiscover,
                )
            }
            if (trips.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            stringResource(R.string.travel_your_trips),
                            fontWeight = FontWeight.Bold,
                            color = kb.title,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = onOpenAllTrips) {
                            Text(stringResource(R.string.travel_see_all), color = kb.title)
                        }
                    }
                }
                items(recentTrips, key = { it.id }) { trip ->
                    val tripLegs = legsByTripId[trip.id].orEmpty()
                    TravelTripCard(
                        trip = trip,
                        legs = tripLegs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenTrip(trip.id) },
                    )
                }
            } else if (!aiAvailable) {
                item {
                    Text(
                        stringResource(R.string.travel_upgrade_hint),
                        color = kb.subtitle,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}
