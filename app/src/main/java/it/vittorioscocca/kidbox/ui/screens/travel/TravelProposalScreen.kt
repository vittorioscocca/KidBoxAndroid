package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.navigation.travelPlanningViewModelOwner
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/**
 * Proposta itinerario. Come su iOS, va mostrata con lo stesso [TravelPlanningViewModel] del wizard.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelProposalScreen(
    familyId: String,
    navController: NavHostController,
    onOpenPlans: () -> Unit,
    onNavigateBack: () -> Unit,
    planningViewModel: TravelPlanningViewModel? = null,
    planningViewModelOwner: NavBackStackEntry? = null,
) {
    val viewModel = planningViewModel ?: hiltViewModel(
        checkNotNull(planningViewModelOwner) {
            "Serve planningViewModel o planningViewModelOwner"
        },
    )
    TravelProposalContent(
        familyId = familyId,
        viewModel = viewModel,
        onNavigateBack = onNavigateBack,
        onOpenPlans = onOpenPlans,
        onOpenAiChat = { navController.navigate(AppDestination.AiChat.createRoute(familyId)) },
        onStopClick = { stopContext -> navController.navigate(stopContext.toPlaceDetailRoute(familyId)) },
        onTripAccepted = { tripId ->
            navController.navigate(AppDestination.TravelDetail.createRoute(familyId, tripId)) {
                popUpTo(AppDestination.TravelList.createRoute(familyId)) { inclusive = false }
            }
        },
    )
}

/** Route Nav dedicata (fallback legacy). */
@Composable
fun TravelProposalRoute(
    familyId: String,
    navController: NavHostController,
    backStackEntry: NavBackStackEntry,
    onOpenPlans: () -> Unit,
) {
    val planningOwner = navController.travelPlanningViewModelOwner(familyId, backStackEntry)
    TravelProposalScreen(
        familyId = familyId,
        navController = navController,
        onOpenPlans = onOpenPlans,
        onNavigateBack = { navController.popBackStack() },
        planningViewModelOwner = planningOwner,
    )

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TravelProposalContent(
    familyId: String,
    viewModel: TravelPlanningViewModel,
    onNavigateBack: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenAiChat: () -> Unit,
    onStopClick: (TravelItineraryStopContext) -> Unit = {},
    onTripAccepted: (String) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val narrative by viewModel.proposalNarrative.collectAsStateWithLifecycle()
    val plan by viewModel.proposalPlan.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val children by viewModel.children.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val familyPlan by viewModel.familyPlan.collectAsStateWithLifecycle()
    val usageToday by viewModel.usageToday.collectAsStateWithLifecycle()
    val dailyLimit by viewModel.dailyLimit.collectAsStateWithLifecycle()
    val regeneratingDayIndex by viewModel.regeneratingDayIndex.collectAsStateWithLifecycle()
    val proposalRevision by viewModel.proposalRevision.collectAsStateWithLifecycle()
    val aiAvailable = familyPlan.includesAI
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(error) {
        error?.let { msg ->
            snackbar.showSnackbar(msg)
        }
    }

    val hasStructuredPlan = plan.isStructuredTravelPlan()
    val hasNarrative = !narrative.isNullOrBlank()
    val hasProposalContent = hasStructuredPlan || hasNarrative
    val showSubscriptionGate = !aiAvailable && !isGenerating && !hasProposalContent

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TravelPlanningViewModel.Event.TripAccepted -> onTripAccepted(event.tripId)
                is TravelPlanningViewModel.Event.Error ->
                    snackbar.showSnackbar(event.message)
                is TravelPlanningViewModel.Event.PlanReady -> Unit
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = kb.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (hasStructuredPlan) "" else viewModel.tripName.ifBlank { "Proposta" },
                        color = kb.title,
                    )
                },
                actions = {
                    if (dailyLimit > 0 && !isGenerating) {
                        Text(
                            "$usageToday/$dailyLimit",
                            color = kb.subtitle,
                            modifier = Modifier.padding(end = 16.dp),
                        )
                    }
                },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onNavigateBack) {
                        androidx.compose.material3.Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = kb.title,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
            )
        },
        bottomBar = {
            when {
                showSubscriptionGate -> {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("Piano Pro o Max richiesto per la pianificazione AI.", color = kb.subtitle)
                        Button(onClick = onOpenPlans, modifier = Modifier.fillMaxWidth()) {
                            Text("Scopri Pro e Max")
                        }
                    }
                }
                hasProposalContent && !isGenerating -> {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (aiAvailable) {
                            OutlinedButton(onClick = { viewModel.regenerate() }, modifier = Modifier.weight(1f)) {
                                Text("Rigenera")
                            }
                            OutlinedButton(
                                onClick = onOpenAiChat,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text("Modifica")
                            }
                        }
                        Button(onClick = { viewModel.acceptProposal() }, modifier = Modifier.weight(1f)) {
                            Text("Accetta ✓")
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                showSubscriptionGate -> {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Piano Pro o Max richiesto per la pianificazione AI.", color = kb.subtitle)
                        Button(onClick = onOpenPlans) { Text("Scopri Pro e Max") }
                    }
                }
                isGenerating -> {
                    TravelPlanningLoadingScreen(
                        destinationName = viewModel.destinationName.ifBlank { "il viaggio" },
                        subtitle = "Sto finalizzando il percorso di ${viewModel.tripDayCount} giorni",
                        plannedDayCount = viewModel.tripDayCount,
                    )
                }
                hasStructuredPlan -> {
                    val proposal = plan!!
                    val overview = remember(proposalRevision, proposal, members, children) {
                        TravelItineraryBuilder.buildFromProposal(
                            proposal = proposal,
                            tripName = viewModel.tripName,
                            budgetLimit = viewModel.budgetTotal,
                            currency = viewModel.currency,
                            participantIdsJson = org.json.JSONArray(viewModel.selectedParticipantIds.toList()).toString(),
                            members = members,
                            children = children,
                            plannedDayCount = viewModel.tripDayCount,
                        )
                    }
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        item {
                            TravelItineraryDetailContent(
                                overview = overview,
                                legs = emptyList(),
                                introduction = narrative,
                                heroImageDestination = viewModel.destinationName.ifBlank { overview.destinationTitle },
                                heroImageContext = viewModel.destinationRegion,
                                onStopClick = onStopClick,
                                onRegenerateDayClick = { day -> viewModel.regenerateDayPlan(day) },
                                regeneratingDayIndex = regeneratingDayIndex,
                            )
                        }
                        val packing = proposal["packingList"].asMapList().take(5)
                        if (packing.isNotEmpty()) {
                            item {
                                Text("Da portare", style = MaterialTheme.typography.titleMedium, color = kb.title)
                            }
                            items(packing) { item ->
                                Text("• ${item["label"]}", modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
                hasNarrative -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item {
                            Text("Introduzione", style = MaterialTheme.typography.titleMedium, color = kb.title)
                            Text(narrative.orEmpty(), color = kb.subtitle)
                        }
                        item {
                            Text(
                                "L'itinerario strutturato non è disponibile. Prova «Rigenera».",
                                color = kb.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
                error != null -> {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(error ?: "", color = MaterialTheme.colorScheme.error)
                        if (aiAvailable && viewModel.canGenerate) {
                            Button(onClick = { viewModel.regenerate() }) {
                                Text("Rigenera")
                            }
                        }
                        OutlinedButton(onClick = onNavigateBack) {
                            Text("Indietro")
                        }
                    }
                }
                else -> {
                    Column(
                        Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("Nessun itinerario da mostrare.", color = kb.subtitle)
                        OutlinedButton(onClick = onNavigateBack) {
                            Text("Indietro")
                        }
                        if (aiAvailable && viewModel.canGenerate) {
                            Button(onClick = { viewModel.regenerate() }) {
                                Text("Rigenera piano")
                            }
                        }
                    }
                }
            }
        }
    }

        if (regeneratingDayIndex != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = Color.White)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(8.dp))
                    Text("Rigenerazione giorno $regeneratingDayIndex…", color = Color.White)
                }
            }
        }
    }
}
