package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val TravelAccent = Color(0xFFF2611A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelDestinationDetailScreen(
    familyId: String,
    destinationId: String,
    onNavigateBack: () -> Unit,
    onPlanTrip: (String) -> Unit,
    onTripAccepted: (String) -> Unit,
    viewModel: TravelDiscoverViewModel = hiltViewModel(),
    planningViewModel: TravelPlanningViewModel = hiltViewModel(),
) {
    val destination = viewModel.destinationById(destinationId)
    val kb = MaterialTheme.kidBoxColors
    val snackbar = remember { SnackbarHostState() }
    var isAccepting by remember { mutableStateOf(false) }

    LaunchedEffect(familyId) {
        planningViewModel.loadParticipants(familyId)
    }

    LaunchedEffect(Unit) {
        planningViewModel.events.collect { event ->
            when (event) {
                is TravelPlanningViewModel.Event.TripAccepted -> {
                    isAccepting = false
                    onTripAccepted(event.tripId)
                }
                is TravelPlanningViewModel.Event.Error -> {
                    isAccepting = false
                    snackbar.showSnackbar(event.message)
                }
                is TravelPlanningViewModel.Event.PlanReady -> Unit
            }
        }
    }

    if (destination == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Destinazione non trovata", color = kb.subtitle)
        }
        return
    }

    val hasStructuredPreview = destination.hasStructuredAiPreview
    val overview = TravelItineraryBuilder.buildFromSuggestion(destination)
    val introduction = buildList {
        if (destination.aiHeadline.isNotBlank()) add(destination.aiHeadline)
        if (destination.whyForYou.isNotBlank()) add(destination.whyForYou)
    }.joinToString("\n\n")

    val footerCaption = if (hasStructuredPreview) {
        "Itinerario AI pronto: accetta per salvare il viaggio pianificato con giorni e tappe già impostati."
    } else {
        "Anteprima generata dall'AI in base al tuo profilo. Personalizza date, budget e viaggiatori nel passo successivo."
    }

    Scaffold(
        containerColor = kb.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(destination.name, color = kb.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = {
                        if (hasStructuredPreview) {
                            isAccepting = true
                            planningViewModel.acceptPreviewFromSuggestion(destination)
                        } else {
                            val encoded = URLEncoder.encode(destination.name, StandardCharsets.UTF_8.toString())
                            onPlanTrip(encoded)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isAccepting,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = TravelAccent),
                ) {
                    if (isAccepting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            if (hasStructuredPreview) "Accetta viaggio ✓" else "Pianifica questo viaggio",
                            modifier = Modifier.padding(vertical = 8.dp),
                        )
                    }
                }
                if (hasStructuredPreview) {
                    TextButton(
                        onClick = {
                            val encoded = URLEncoder.encode(destination.name, StandardCharsets.UTF_8.toString())
                            onPlanTrip(encoded)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isAccepting,
                    ) {
                        Text("Personalizza date e viaggiatori", color = TravelAccent)
                    }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            TravelItineraryDetailContent(
                overview = overview,
                legs = emptyList(),
                introduction = introduction,
                heroImageDestination = destination.name,
                heroImageContext = destination.region,
                contentHorizontalPadding = 22.dp,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BriefChip(destination.durationDays + " giorni")
                BriefChip(destination.estimatedCost)
                BriefChip(destination.bestTime)
            }

            Text(
                footerCaption,
                fontSize = 12.sp,
                color = kb.subtitle,
                modifier = Modifier.padding(horizontal = 22.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun BriefChip(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(20.dp)) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = kb.title,
            maxLines = 1,
        )
    }
}
