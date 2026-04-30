package it.vittorioscocca.kidbox.ui.screens.health

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@Composable
fun HealthHomeScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: HealthHomeViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId) { viewModel.load(familyId, childId) }

    // Build the 6 module cards. Lambdas are recreated only when subjectName changes.
    val cards = remember(state.subjectName, state.activeTreatmentCount) {
        listOf(
            HealthCard(
                title = "Cure",
                subtitle = if (state.activeTreatmentCount > 0) "${state.activeTreatmentCount} attive" else "Farmaci attivi",
                icon = Icons.Default.Medication,
                tint = Color(0xFF9573D9),
                onClick = { onNavigate(AppDestination.Treatments.route(familyId, childId)) },
            ),
            HealthCard(
                title = "Vaccini",
                subtitle = "Calendario vaccinale",
                icon = Icons.Default.Vaccines,
                tint = Color(0xFFF38D73),
                onClick = { onNavigate(AppDestination.Vaccines.route(familyId, childId)) },
            ),
            HealthCard(
                title = "Visite",
                subtitle = "Storico visite",
                icon = Icons.Default.MedicalServices,
                tint = Color(0xFF5996D9),
                onClick = { onNavigate(AppDestination.MedicalVisits.route(familyId, childId)) },
            ),
            HealthCard(
                title = "Analisi & Esami",
                subtitle = "Referti",
                icon = Icons.Default.Science,
                tint = Color(0xFF40A6BF),
                onClick = { onNavigate(AppDestination.MedicalExams.route(familyId, childId)) },
            ),
            HealthCard(
                title = "Scheda Medica",
                subtitle = "Allergie, pediatra",
                icon = Icons.Default.HealthAndSafety,
                tint = Color(0xFF66BFA6),
                onClick = { onNavigate(AppDestination.MedicalRecord.route(familyId, childId)) },
            ),
            HealthCard(
                title = "Storico Salute",
                subtitle = "Timeline",
                icon = Icons.Default.Timeline,
                tint = Color(0xFFD98C59),
                onClick = { onNavigate(AppDestination.HealthTimeline.route(familyId, childId)) },
            ),
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        tint = kb.title,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))

            // Hero header: avatar + name
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFFF6B00).copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🧑", fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        state.subjectName.ifBlank { "Profilo" },
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 22.sp,
                        color = kb.title,
                    )
                    Text("Diario di salute", fontSize = 13.sp, color = kb.subtitle)
                }
            }
            Spacer(Modifier.height(20.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(cards) { card -> HealthModuleCard(card) }
            }
        }
    }
}

private data class HealthCard(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val onClick: () -> Unit,
)

@Composable
private fun HealthModuleCard(card: HealthCard) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        onClick = card.onClick,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(card.tint.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(card.icon, contentDescription = null, tint = card.tint)
            }
            Column {
                Text(
                    card.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = kb.title,
                )
                Text(card.subtitle, fontSize = 12.sp, color = kb.subtitle)
            }
        }
    }
}
