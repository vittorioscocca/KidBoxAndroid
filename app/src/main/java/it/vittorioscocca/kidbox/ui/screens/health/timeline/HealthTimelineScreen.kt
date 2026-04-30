// TODO: Period filters (all/year/month) from iOS are not implemented in this phase.
//       Add a filter bar above the LazyColumn in a future iteration.
package it.vittorioscocca.kidbox.ui.screens.health.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Vaccines
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.HealthTimelineEvent
import it.vittorioscocca.kidbox.domain.model.HealthTimelineEventKind
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HealthTimelineScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onOpenVisit: (visitId: String) -> Unit,
    onOpenExam: (examId: String) -> Unit,
    onOpenTreatment: (treatmentId: String) -> Unit,
    viewModel: HealthTimelineViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            // Top bar
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        tint = kb.title,
                    )
                }
            }

            // Title + subject name
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Text(
                    "Storico Salute",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = kb.title,
                )
                if (state.subjectName.isNotBlank()) {
                    Text(state.subjectName, fontSize = 15.sp, color = kb.subtitle)
                }
            }

            Spacer(Modifier.height(16.dp))

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFF6B00))
                    }
                }

                state.events.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(32.dp),
                        ) {
                            Icon(
                                Icons.Default.Timeline,
                                contentDescription = null,
                                tint = kb.subtitle,
                                modifier = Modifier.size(56.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                            Text(
                                "Nessun evento registrato",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                                color = kb.title,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Le tue visite, esami, cure e vaccini appariranno qui",
                                fontSize = 13.sp,
                                color = kb.subtitle,
                            )
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                    ) {
                        state.eventsGroupedByYear.forEach { (year, yearEvents) ->
                            stickyHeader(key = "year-$year") {
                                YearHeader(year = year, kb = kb)
                            }
                            itemsIndexed(
                                items = yearEvents,
                                key = { _, event -> event.id },
                            ) { index, event ->
                                val isFirst = index == 0
                                val isLast = index == yearEvents.lastIndex
                                TimelineEventCard(
                                    event = event,
                                    isFirstInGroup = isFirst,
                                    isLastInGroup = isLast,
                                    onOpen = {
                                        when (event.kind) {
                                            HealthTimelineEventKind.VISIT -> onOpenVisit(event.sourceId)
                                            HealthTimelineEventKind.EXAM -> onOpenExam(event.sourceId)
                                            HealthTimelineEventKind.TREATMENT -> onOpenTreatment(event.sourceId)
                                            HealthTimelineEventKind.VACCINE -> Unit
                                        }
                                    },
                                )
                                Spacer(Modifier.height(if (isLast) 16.dp else 8.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearHeader(
    year: Int,
    kb: KidBoxColorScheme,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(kb.background)
            .padding(vertical = 12.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFFFF6B00).copy(alpha = 0.06f))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                year.toString(),
                fontSize = 28.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
            )
        }
    }
}

@Composable
private fun TimelineEventCard(
    event: HealthTimelineEvent,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    onOpen: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val tintColor = Color(event.kind.tintColorArgb)
    val isNavigable = event.kind != HealthTimelineEventKind.VACCINE
    val dateText = remember(event.dateEpochMillis) {
        SimpleDateFormat("dd MMM yyyy", Locale.ITALIAN).format(Date(event.dateEpochMillis))
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isNavigable) {
                    Modifier.clickable(onClick = onOpen)
                } else {
                    Modifier
                },
            ),
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
            // Timeline connector column
            Column(
                modifier = Modifier
                    .width(36.dp)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Line above
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(
                            if (isFirstInGroup) Color.Transparent else tintColor.copy(alpha = 0.35f),
                        ),
                )
                // Dot
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(tintColor),
                )
                // Line below
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .height(16.dp)
                        .background(
                            if (isLastInGroup) Color.Transparent else tintColor.copy(alpha = 0.35f),
                        ),
                )
            }

            // Card body
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 10.dp, bottom = 12.dp, end = 12.dp),
            ) {
                // Date row
                Text(
                    dateText,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.subtitle,
                )
                Spacer(Modifier.height(2.dp))
                // Title + kind chip row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        event.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = kb.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // Kind chip
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(tintColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            Text(
                                event.kind.rawLabel,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = tintColor,
                            )
                        }
                        // Chevron for navigable kinds
                        if (isNavigable) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = kb.subtitle,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
                // Subtitle
                if (event.subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        event.subtitle,
                        fontSize = 12.sp,
                        color = kb.subtitle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun HealthTimelineEventKind.iconImageVector(): ImageVector = when (iconKey) {
    "medical_services" -> Icons.Default.MedicalServices
    "science" -> Icons.Default.Science
    "medication" -> Icons.Default.Medication
    "vaccines" -> Icons.Default.Vaccines
    else -> Icons.Default.MedicalServices
}
