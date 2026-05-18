package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val TravelAccent = Color(0xFFF2611A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelDiscoverScreen(
    familyId: String,
    onNavigateBack: () -> Unit,
    onOpenDestination: (String) -> Unit,
    viewModel: TravelDiscoverViewModel = hiltViewModel(),
) {
    LaunchedEffect(familyId) {
        viewModel.setFamilyId(familyId)
        viewModel.loadSuggestions()
    }

    val destinations by viewModel.destinations.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text("Per te, questa settimana", color = kb.title) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.loadSuggestions(force = true) }, enabled = !isLoading) {
                        Icon(Icons.Filled.Refresh, contentDescription = null, tint = kb.title)
                        Text("Nuovi", color = kb.title, modifier = Modifier.padding(start = 4.dp))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
            )
        },
    ) { padding ->
        when {
            isLoading && destinations.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding)) {
                    TravelPlanningLoadingScreen(
                        destinationName = "le destinazioni",
                        subtitle = "Sto cercando i posti giusti per te",
                    )
                }
            }
            error != null && destinations.isEmpty() -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    Text(error ?: "", color = kb.subtitle)
                }
            }
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(viewModel.subtitle(), color = kb.subtitle)
                    val top = destinations.firstOrNull { it.isTopMatch } ?: destinations.firstOrNull()
                    top?.let { TopMatchCard(it, onClick = { onOpenDestination(it.id) }) }
                    destinations.filter { !it.isTopMatch }.forEach { dest ->
                        CompactDestinationCard(dest, onClick = { onOpenDestination(dest.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMatchCard(destination: TravelDestination, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 4.dp,
    ) {
        Column {
            Box {
                TravelDestinationImage(
                    destinationName = destination.name,
                    locationContext = destination.region,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)),
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(0.65f)),
                            ),
                        ),
                )
                Column(Modifier.fillMaxWidth().padding(12.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Surface(color = Color.White.copy(0.2f), shape = RoundedCornerShape(12.dp)) {
                            Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(8.dp).background(TravelAccent, CircleShape))
                                Text("MIGLIOR SCELTA", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                        Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = Color.White)
                    }
                    Text(destination.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 28.sp, modifier = Modifier.padding(top = 80.dp, start = 4.dp))
                }
            }
            Column(Modifier.padding(14.dp)) {
                Text(destination.tagline, color = kb.subtitle, maxLines = 2)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${destination.estimatedCost} · ${destination.durationDays} giorni", fontSize = 12.sp, color = kb.subtitle)
                    Text(destination.bestTime, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF2E7D32))
                }
            }
        }
    }
}

@Composable
private fun CompactDestinationCard(destination: TravelDestination, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.15f)),
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            TravelDestinationImage(
                destinationName = destination.name,
                locationContext = destination.region,
                modifier = Modifier
                    .width(88.dp)
                    .height(88.dp)
                    .clip(RoundedCornerShape(14.dp)),
            )
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(destination.name, fontWeight = FontWeight.Bold, color = kb.title)
                Text(destination.region, fontSize = 12.sp, color = kb.subtitle)
                Text(destination.tagline, fontSize = 12.sp, color = kb.subtitle, maxLines = 2, modifier = Modifier.padding(top = 4.dp))
                Text("${destination.estimatedCost} · ${destination.durationDays} giorni", fontSize = 11.sp, color = kb.subtitle, modifier = Modifier.padding(top = 4.dp))
            }
            Icon(Icons.Filled.FavoriteBorder, contentDescription = null, tint = kb.subtitle)
        }
    }
}
