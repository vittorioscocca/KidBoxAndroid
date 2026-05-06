package it.vittorioscocca.kidbox.ui.screens.health

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import org.json.JSONArray

/** Stesso family del viola cure / tema iOS (avatar cerchio sotto nome). */
private val HEALTH_HEADER_TINT = Color(0xFF9573D9)

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
    val isAiGloballyEnabled by viewModel.isAiGloballyEnabled.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId) { viewModel.load(familyId, childId) }

    val cards = remember(
        state.subjectName,
        state.activeTreatmentCount,
        state.vaccineCount,
        state.visitCount,
        state.examCount,
        state.pendingExamCount,
        state.timelineEventCount,
    ) {
        val cureSubtitle = when {
            state.activeTreatmentCount == 1 -> "1 attiva"
            state.activeTreatmentCount > 1 -> "${state.activeTreatmentCount} attive"
            else -> "Farmaci attivi"
        }
        val examSubtitle = when {
            state.pendingExamCount > 0 -> "${state.pendingExamCount} in attesa"
            else -> "${state.examCount} registrati"
        }
        listOf(
            HealthCard(
                title = "Cure",
                subtitle = cureSubtitle,
                icon = Icons.Default.Medication,
                tint = Color(0xFF9573D9),
                badgeCount = state.activeTreatmentCount.takeIf { it > 0 },
                onClick = { onNavigate(AppDestination.Treatments.route(familyId, childId)) },
            ),
            HealthCard(
                title = "Vaccini",
                subtitle = "${state.vaccineCount} registrati",
                icon = Icons.Default.Vaccines,
                tint = Color(0xFFF38D73),
                badgeCount = state.vaccineCount.takeIf { it > 0 },
                onClick = { onNavigate(AppDestination.Vaccines.route(familyId, childId)) },
            ),
            HealthCard(
                title = "Visite",
                subtitle = "${state.visitCount} registrate",
                icon = Icons.Default.MonitorHeart,
                tint = Color(0xFF5996D9),
                badgeCount = state.visitCount.takeIf { it > 0 },
                onClick = { onNavigate(AppDestination.MedicalVisits.route(familyId, childId)) },
            ),
            HealthCard(
                title = "Analisi & Esami",
                subtitle = examSubtitle,
                icon = Icons.Default.Biotech,
                tint = Color(0xFF40A6BF),
                badgeCount = state.pendingExamCount.takeIf { it > 0 },
                onClick = { onNavigate(AppDestination.MedicalExams.route(familyId, childId)) },
            ),
            HealthCard(
                title = "Scheda Medica",
                subtitle = "Allergie, pediatra",
                icon = Icons.Default.Description,
                tint = Color(0xFF66BFA6),
                onClick = { onNavigate(AppDestination.MedicalRecord.route(familyId, childId)) },
            ),
            HealthCard(
                title = "Storico Salute",
                subtitle = when (state.timelineEventCount) {
                    1 -> "1 evento"
                    else -> "${state.timelineEventCount} eventi"
                },
                icon = Icons.Default.Folder,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KidBoxHeaderCircleButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = "Indietro",
                    onClick = onBack,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Salute",
                fontWeight = FontWeight.Bold,
                fontSize = 34.sp,
                color = kb.title,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(HEALTH_HEADER_TINT.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🧑", fontSize = 24.sp)
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        state.subjectName.ifBlank { "Profilo" },
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = kb.title,
                    )
                    Text("Diario di salute", fontSize = 14.sp, color = kb.subtitle)
                }
            }
            Spacer(Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        bottom = if (state.hasAnyHealthData) 108.dp else 32.dp,
                    ),
                ) {
                    items(cards) { card -> HealthModuleCard(card) }
                }
            }
        }

        // ── AI salute (AskAiButton: consenso / upgrade come altre schermate Salute) ──
        AnimatedVisibility(
            visible = state.hasAnyHealthData,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 96.dp),
        ) {
            val askEnabled = isAiGloballyEnabled &&
                (state.visitCount > 0 || state.examCount > 0 ||
                    state.activeTreatmentCount > 0 || state.vaccineCount > 0)
            HealthAskAiButton(
                subjectName = state.subjectName,
                isEnabled = askEnabled,
                onTap = {
                    val subjectLabel = state.subjectName.ifBlank { "Profilo" }
                    onNavigate(
                        AppDestination.HealthAIChat.routeWithContext(
                            familyId = familyId,
                            childId = childId,
                            subjectName = subjectLabel,
                            visitIdsJson = JSONArray(state.visitIds).toString(),
                            examIdsJson = JSONArray(state.examIds).toString(),
                            treatmentIdsJson = JSONArray(state.treatmentIds).toString(),
                            vaccineIdsJson = JSONArray(state.vaccineIds).toString(),
                        ),
                    )
                },
            )
        }
    }
}

private data class HealthCard(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val tint: Color,
    val badgeCount: Int? = null,
    val onClick: () -> Unit,
)

@Composable
private fun HealthModuleCard(card: HealthCard) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        onClick = card.onClick,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(18.dp),
        elevation = CardDefaults.cardElevation(
            defaultElevation = 3.dp,
            pressedElevation = 5.dp,
            hoveredElevation = 4.dp,
            focusedElevation = 4.dp,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(152.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BadgedBox(
                badge = {
                    val n = card.badgeCount ?: 0
                    if (n > 0) {
                        Badge(containerColor = card.tint) {
                            Text(
                                n.toString(),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                },
            ) {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(card.tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(card.icon, contentDescription = null, tint = card.tint, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                card.title,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = kb.title,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                card.subtitle,
                fontSize = 12.sp,
                color = kb.subtitle,
                textAlign = TextAlign.Center,
                maxLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
