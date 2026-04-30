package it.vittorioscocca.kidbox.ui.screens.health.treatments

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.util.concurrent.TimeUnit

private val PURPLE = Color(0xFF9573D9)
private val ORANGE = Color(0xFFFF6B00)

@Composable
fun MedicalTreatmentsScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (treatmentId: String) -> Unit,
    viewModel: MedicalTreatmentsViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = kb.title)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Nuova cura", tint = kb.title)
            }
        }

        Text(
            "Cure",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val isEmpty = state.active.isEmpty() && state.longTerm.isEmpty() && state.inactive.isEmpty()
            if (isEmpty) {
                EmptyTreatments(modifier = Modifier.fillMaxSize(), onAdd = onAdd)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    treatmentSection("Cure attive", state.active, onOpen)
                    treatmentSection("Lungo termine", state.longTerm, onOpen)
                    treatmentSection("Concluse", state.inactive, onOpen)
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun LazyListScope.treatmentSection(
    title: String,
    items: List<KBTreatment>,
    onTap: (String) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        Text(
            title.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = MaterialTheme.kidBoxColors.subtitle,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    items(items, key = { it.id }) { treatment ->
        TreatmentRow(treatment = treatment, onTap = { onTap(treatment.id) })
    }
}

@Composable
private fun TreatmentRow(treatment: KBTreatment, onTap: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val now = System.currentTimeMillis()
    val daysSinceStart = TimeUnit.MILLISECONDS.toDays(now - treatment.startDateEpochMillis).coerceAtLeast(0) + 1

    Card(
        onClick = onTap,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(PURPLE.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Medication, contentDescription = null, tint = PURPLE)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        treatment.drugName,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.title,
                        fontSize = 15.sp,
                        modifier = Modifier.weight(1f),
                    )
                    if (treatment.reminderEnabled) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = PURPLE,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                val dosageStr = if (treatment.dosageValue % 1.0 == 0.0) {
                    "%.0f".format(treatment.dosageValue)
                } else {
                    "%.1f".format(treatment.dosageValue)
                }
                Text(
                    "$dosageStr ${treatment.dosageUnit} · ${treatment.dailyFrequency}x/die",
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
                if (treatment.isLongTerm) {
                    Text("Lungo termine", fontSize = 12.sp, color = PURPLE)
                } else {
                    val currentDay = daysSinceStart.coerceAtMost(treatment.durationDays.toLong())
                    Text(
                        "Giorno $currentDay/${treatment.durationDays}",
                        fontSize = 12.sp,
                        color = kb.subtitle,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyTreatments(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Medication,
                contentDescription = null,
                tint = kb.subtitle.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Nessuna cura registrata",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = kb.title,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = ORANGE),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Nuova cura", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
