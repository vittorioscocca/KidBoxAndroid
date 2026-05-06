@file:OptIn(
    androidx.compose.foundation.ExperimentalFoundationApi::class,
    androidx.compose.material3.ExperimentalMaterial3Api::class,
)

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.util.Calendar
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
    onOpenVaccine: (vaccineId: String) -> Unit,
    viewModel: HealthTimelineViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isSearchVisible by remember { mutableStateOf(false) }
    var showYearSheet by remember { mutableStateOf(false) }

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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                KidBoxHeaderCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                KidBoxHeaderCircleButton(
                    icon = Icons.Default.Search,
                    contentDescription = "Cerca",
                    onClick = { isSearchVisible = !isSearchVisible },
                )
                Spacer(Modifier.width(6.dp))
                if (isSearchVisible) {
                    KidBoxHeaderCircleButton(
                        icon = Icons.Default.Close,
                        contentDescription = "Chiudi ricerca",
                        onClick = {
                            isSearchVisible = false
                            viewModel.setSearchQuery("")
                        },
                    )
                }
            }

            // Title + subject name
            Column(modifier = Modifier.padding(horizontal = 18.dp)) {
                Text(
                    "Storico",
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = kb.title,
                )
                if (state.subjectName.isNotBlank()) {
                    Text(state.subjectName, fontSize = 15.sp, color = kb.subtitle)
                }
            }

            if (isSearchVisible) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = state.searchQuery,
                    onValueChange = viewModel::setSearchQuery,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp),
                    singleLine = true,
                    placeholder = { Text("Cerca visite, esami, cure...") },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = kb.card,
                        unfocusedContainerColor = kb.card,
                        focusedBorderColor = Color(0xFFD98C59),
                        unfocusedBorderColor = kb.subtitle.copy(alpha = 0.25f),
                    ),
                )
            }

            Spacer(Modifier.height(10.dp))
            FilterBar(
                selectedYear = state.selectedYear,
                activeKinds = state.activeKinds,
                onOpenYearFilter = { showYearSheet = true },
                onToggleKind = viewModel::toggleKind,
            )
            HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
            Spacer(Modifier.height(6.dp))

            when {
                state.isLoading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = Color(0xFFFF6B00))
                    }
                }

                state.filteredCount == 0 -> {
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
                                "Nessun evento trovato",
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
                        state.eventsGroupedByYearMonth.forEach { yearGroup ->
                            stickyHeader(key = "year-${yearGroup.year}") {
                                YearHeader(
                                    year = yearGroup.year,
                                    count = yearGroup.months.sumOf { it.events.size },
                                    kb = kb,
                                )
                            }
                            yearGroup.months.forEach { monthGroup ->
                                item(key = "month-${yearGroup.year}-${monthGroup.month}") {
                                    MonthHeader(month = monthGroup.month, kb = kb)
                                }
                                itemsIndexed(
                                    items = monthGroup.events,
                                    key = { _, event -> event.id },
                                ) { index, event ->
                                    val isLast = index == monthGroup.events.lastIndex
                                    TimelineEventCard(
                                        event = event,
                                        isLastInGroup = isLast,
                                        onOpen = {
                                            when (event.kind) {
                                                HealthTimelineEventKind.VISIT -> onOpenVisit(event.sourceId)
                                                HealthTimelineEventKind.EXAM -> onOpenExam(event.sourceId)
                                                HealthTimelineEventKind.TREATMENT -> onOpenTreatment(event.sourceId)
                                                HealthTimelineEventKind.VACCINE -> onOpenVaccine(event.sourceId)
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showYearSheet) {
        YearFilterSheet(
            selectedYear = state.selectedYear,
            availableYears = state.availableYears,
            allEvents = state.events,
            onSelectYear = {
                viewModel.setSelectedYear(it)
                showYearSheet = false
            },
            onDismiss = { showYearSheet = false },
        )
    }
}

@Composable
private fun YearHeader(
    year: Int,
    count: Int,
    kb: KidBoxColorScheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(kb.background)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            year.toString(),
            fontSize = 28.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            "$count eventi",
            fontSize = 12.sp,
            color = kb.subtitle,
        )
    }
}

@Composable
private fun MonthHeader(
    month: Int,
    kb: KidBoxColorScheme,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            monthLabel(month).uppercase(Locale.ITALIAN),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = kb.subtitle,
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = kb.subtitle.copy(alpha = 0.2f),
        )
    }
}

@Composable
private fun FilterBar(
    selectedYear: Int?,
    activeKinds: Set<HealthTimelineEventKind>,
    onOpenYearFilter: () -> Unit,
    onToggleKind: (HealthTimelineEventKind) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            label = selectedYear?.toString() ?: "Anno",
            leading = if (selectedYear == null) Icons.Default.CalendarMonth else Icons.Default.Check,
            trailing = Icons.Default.ExpandMore,
            active = selectedYear != null,
            activeColor = Color(0xFFD98C59),
            onClick = onOpenYearFilter,
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(18.dp)
                .background(MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.3f)),
        )
        Spacer(Modifier.width(8.dp))
        HealthTimelineEventKind.entries.forEach { kind ->
            val active = activeKinds.contains(kind)
            FilterChip(
                label = kind.rawLabel,
                leading = kind.iconImageVector(),
                active = active,
                activeColor = Color(kind.tintColorArgb),
                onClick = { onToggleKind(kind) },
            )
            Spacer(Modifier.width(8.dp))
        }
    }
}

@Composable
private fun FilterChip(
    label: String,
    leading: ImageVector,
    active: Boolean,
    activeColor: Color,
    onClick: () -> Unit,
    trailing: ImageVector? = null,
) {
    val bg = if (active) activeColor else activeColor.copy(alpha = 0.12f)
    val fg = if (active) Color.White else activeColor
    Surface(
        onClick = onClick,
        color = bg,
        shape = RoundedCornerShape(50),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Icon(
                leading,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = fg,
            )
            trailing?.let {
                Icon(
                    trailing,
                    contentDescription = null,
                    tint = fg,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun TimelineEventCard(
    event: HealthTimelineEvent,
    isLastInGroup: Boolean,
    onOpen: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val tintColor = Color(event.kind.tintColorArgb)
    val isNavigable = event.kind != HealthTimelineEventKind.VACCINE
    val dateText = remember(event.dateEpochMillis) {
        SimpleDateFormat("dd MMM yyyy", Locale.ITALIAN).format(Date(event.dateEpochMillis))
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(if (isNavigable) Modifier.clickable(onClick = onOpen) else Modifier)
            .padding(bottom = if (isLastInGroup) 8.dp else 14.dp),
    ) {
        // Timeline connector column (continuous line across rows)
        Column(
            modifier = Modifier
                .width(32.dp)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(tintColor.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    event.kind.iconImageVector(),
                    contentDescription = null,
                    tint = tintColor,
                    modifier = Modifier.size(15.dp),
                )
            }
            if (!isLastInGroup) {
                Box(
                    modifier = Modifier
                        .width(1.5.dp)
                        .weight(1f)
                        .background(kb.subtitle.copy(alpha = 0.2f)),
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(top = 2.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        event.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = kb.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!event.subtitle.isNullOrBlank()) {
                        Text(
                            event.subtitle,
                            fontSize = 14.sp,
                            color = kb.subtitle,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        dateText,
                        fontSize = 12.sp,
                        color = kb.subtitle.copy(alpha = 0.8f),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tintColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Text(
                            event.kind.rawLabel,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = tintColor,
                        )
                    }
                    if (isNavigable) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = kb.subtitle.copy(alpha = 0.6f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun YearFilterSheet(
    selectedYear: Int?,
    availableYears: List<Int>,
    allEvents: List<HealthTimelineEvent>,
    onSelectYear: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AlertDialogDefaults.containerColor,
        modifier = Modifier.windowInsetsPadding(WindowInsets.ime),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
        ) {
            Text(
                "Filtra per anno",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
            )
            YearFilterRow(
                text = "Tutti gli anni",
                selected = selectedYear == null,
                trailingText = null,
                onClick = { onSelectYear(null) },
            )
            availableYears.forEach { year ->
                val count = allEvents.count {
                    Calendar.getInstance().apply { timeInMillis = it.dateEpochMillis }.get(Calendar.YEAR) == year
                }
                YearFilterRow(
                    text = year.toString(),
                    selected = selectedYear == year,
                    trailingText = "$count eventi",
                    onClick = { onSelectYear(year) },
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.End)
                    .padding(horizontal = 12.dp),
            ) {
                Text("Chiudi")
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun YearFilterRow(
    text: String,
    selected: Boolean,
    trailingText: String?,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
        },
        supportingContent = trailingText?.let { { Text(it) } },
        trailingContent = {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = Color(0xFFD98C59),
                )
            }
        },
        modifier = Modifier.clickable(onClick = onClick),
    )
}

private fun monthLabel(month: Int): String {
    val calendar = Calendar.getInstance().apply {
        set(Calendar.MONTH, month - 1)
    }
    return SimpleDateFormat("MMMM", Locale.ITALIAN).format(calendar.time)
}

private fun HealthTimelineEventKind.iconImageVector(): ImageVector = when (iconKey) {
    "medical_services" -> Icons.Default.MedicalServices
    "science" -> Icons.Default.Science
    "medication" -> Icons.Default.Medication
    "vaccines" -> Icons.Default.Vaccines
    else -> Icons.Default.MedicalServices
}
