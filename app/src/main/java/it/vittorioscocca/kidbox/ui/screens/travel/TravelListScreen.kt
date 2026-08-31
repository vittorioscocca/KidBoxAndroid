package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.components.KBSectionHeader
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
    onUpgrade: () -> Unit,
    viewModel: TravelListViewModel = hiltViewModel(),
) {
    LaunchedEffect(familyId) { viewModel.setFamilyId(familyId) }
    val trips by viewModel.trips.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val recentTrips = trips.take(3)
    val legsByTripId by viewModel.legsByTripId.collectAsStateWithLifecycle()
    val needsOnboarding by viewModel.needsOnboarding.collectAsStateWithLifecycle()
    val travelProfile by viewModel.travelProfile.collectAsStateWithLifecycle()
    // Il pianificatore viaggi è incluso nei soli piani a pagamento: `aiAccessBlocked`
    // da solo non basta, perché è true solo per i Free che hanno già esaurito il bonus
    // una tantum — un Free con bonus intatto riuscirebbe a generare itinerari.
    val currentPlan by it.vittorioscocca.kidbox.ai.CurrentPlanStore.plan.collectAsStateWithLifecycle()
    val aiAvailable = currentPlan != it.vittorioscocca.kidbox.domain.model.KBPlan.FREE
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
                onExit = onNavigateBack,
                // Su Free l'avviso va messo qui, non nella lista: la configurazione
                // iniziale è la prima schermata dei Viaggi, e senza avviso l'utente
                // risponde a tre domande per poi scoprire di non poter pianificare.
                header = if (!aiAvailable) {
                    { TravelLockedCard(onUpgrade = onUpgrade) }
                } else {
                    null
                },
            )
            return
        }
        false -> Unit
    }

    Scaffold(
        containerColor = kb.background,
        topBar = {
            KBSectionHeader(
                title = stringResource(R.string.travel_title),
                onBack = onNavigateBack,
                onAdd = onOpenWizard,
                addContentDescription = stringResource(R.string.travel_new_trip),
            )
        },
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.forceRefresh() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize(),
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
                }
                // Stessa carta del Piano Alimentare: dire che serve un piano a
                // pagamento senza dare il modo di arrivarci lasciava l'utente in
                // un vicolo cieco.
                if (!aiAvailable) {
                    item { TravelLockedCard(onUpgrade = onUpgrade) }
                }
            }
        }
    }
}

@Composable
private fun TravelLockedCard(onUpgrade: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = kb.title, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.travel_locked_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = kb.title,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.travel_locked_body), fontSize = 14.sp, color = kb.subtitle, lineHeight = 19.sp)
            Spacer(Modifier.height(12.dp))
            Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.ai_discover_plans))
            }
        }
    }
}
