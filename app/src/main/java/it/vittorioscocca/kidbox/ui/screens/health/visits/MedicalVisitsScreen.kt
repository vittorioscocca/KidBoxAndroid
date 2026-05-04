/*
 * Known limitation: alarms cancelled by VisitReminderScheduler are not automatically
 * rescheduled after a device reboot. A BOOT_COMPLETED BroadcastReceiver is planned for
 * a future phase to restore pending alarms from Room on startup.
 */

package it.vittorioscocca.kidbox.ui.screens.health.visits

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
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
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
import it.vittorioscocca.kidbox.data.local.mapper.KBVisitStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_TIME_FMT = SimpleDateFormat("d MMM yyyy · HH:mm", Locale.ITALIAN)
private val ICON_BLUE = Color(0xFF5996D9)
private val ORANGE = Color(0xFFFF6B00)

@Composable
fun MedicalVisitsScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (visitId: String) -> Unit,
    viewModel: MedicalVisitsViewModel = hiltViewModel(),
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
        // ── Top bar ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KidBoxHeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Indietro",
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            KidBoxHeaderCircleButton(
                icon = Icons.Default.Add,
                contentDescription = "Nuova visita",
                onClick = onAdd,
            )
        }

        // ── Title ──────────────────────────────────────────────────────────────
        Text(
            "Visite",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(12.dp))

        // ── Content ────────────────────────────────────────────────────────────
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val isEmpty = state.pending.isEmpty() && state.booked.isEmpty() &&
                state.completed.isEmpty() && state.resultAvailable.isEmpty() &&
                state.unknownStatus.isEmpty()

            if (isEmpty) {
                EmptyVisits(modifier = Modifier.fillMaxSize(), onAdd = onAdd)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    visitSection("In attesa", state.pending, KBVisitStatus.PENDING, onOpen)
                    visitSection("Prenotate", state.booked, KBVisitStatus.BOOKED, onOpen)
                    visitSection("Risultato disponibile", state.resultAvailable, KBVisitStatus.RESULT_AVAILABLE, onOpen)
                    visitSection("Eseguite", state.completed, KBVisitStatus.COMPLETED, onOpen)
                    visitSection("Senza stato", state.unknownStatus, KBVisitStatus.UNKNOWN_STATUS, onOpen)

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// ── Section helper ─────────────────────────────────────────────────────────────

private fun LazyListScope.visitSection(
    title: String,
    items: List<KBMedicalVisit>,
    status: KBVisitStatus,
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
    items(items, key = { it.id }) { visit ->
        VisitRow(visit = visit, status = status, onTap = { onTap(visit.id) })
    }
}

// ── Row ────────────────────────────────────────────────────────────────────────

@Composable
private fun VisitRow(
    visit: KBMedicalVisit,
    status: KBVisitStatus,
    onTap: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
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
                    .background(ICON_BLUE.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = ICON_BLUE)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    visit.reason.ifBlank { "Visita medica" },
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                    fontSize = 15.sp,
                )
                Text(
                    DATE_TIME_FMT.format(Date(visit.dateEpochMillis)),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
                val doctorLabel = buildString {
                    visit.doctorName?.takeIf { it.isNotBlank() }?.let { append(it) }
                }
                if (doctorLabel.isNotBlank()) {
                    Text(doctorLabel, fontSize = 12.sp, color = kb.subtitle)
                }
            }
            Spacer(Modifier.width(8.dp))
            VisitStatusBadge(status)
        }
    }
}

// ── Status badge ───────────────────────────────────────────────────────────────

@Composable
fun VisitStatusBadge(status: KBVisitStatus) {
    val (bgColor, textColor) = when (status) {
        KBVisitStatus.PENDING -> Color(0xFFE0E0E0) to Color(0xFF616161)
        KBVisitStatus.BOOKED -> Color(0xFFBBDEFB) to Color(0xFF1565C0)
        KBVisitStatus.COMPLETED -> Color(0xFFC8E6C9) to Color(0xFF2E7D32)
        KBVisitStatus.RESULT_AVAILABLE -> Color(0xFFE1BEE7) to Color(0xFF6A1B9A)
        KBVisitStatus.UNKNOWN_STATUS -> Color(0xFFF5F5F5) to Color(0xFF9E9E9E)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            status.displayLabel,
            fontSize = 11.sp,
            color = textColor,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyVisits(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.MedicalServices,
                contentDescription = null,
                tint = kb.subtitle.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Nessuna visita registrata",
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
                Text("Nuova visita", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
