@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

/*
 * Known limitation: alarms cancelled by VisitReminderScheduler are not automatically
 * rescheduled after a device reboot. A BOOT_COMPLETED BroadcastReceiver is planned for
 * a future phase to restore pending alarms from Room on startup.
 */

package it.vittorioscocca.kidbox.ui.screens.health.visits

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import it.vittorioscocca.kidbox.ai.AskAiButton
import it.vittorioscocca.kidbox.data.local.mapper.KBVisitStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListAddBottomButton
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListDualSelectionBottomBar
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListTopToolbar
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONArray

private val DATE_ROW = SimpleDateFormat("d MMM yyyy, HH:mm", Locale.ITALIAN)
private val DATE_SHORT = SimpleDateFormat("dd/MM/yy", Locale.ITALIAN)
/** iOS PediatricVisitsView tint (0.35, 0.6, 0.85). */
private val VISIT_TINT = Color(0xFF5996D9)

@Composable
fun MedicalVisitsScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (visitId: String) -> Unit,
    onOpenVisitsListAiChat: (subjectName: String, visitIdsJson: String) -> Unit = { _, _ -> },
    viewModel: MedicalVisitsViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isAiGloballyEnabled by viewModel.isAiGloballyEnabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var searchBarVisible by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState,
        ) {
            VisitFilterSheetContent(
                state = state,
                onPickQuick = { f ->
                    viewModel.setPeriodFilter(f)
                    scope.launch { filterSheetState.hide() }.invokeOnCompletion { showFilterSheet = false }
                },
                onApplyCustom = { start, end ->
                    viewModel.setCustomPeriodRange(start, end)
                    scope.launch { filterSheetState.hide() }.invokeOnCompletion { showFilterSheet = false }
                },
                onDismiss = { showFilterSheet = false },
            )
        }
    }

    if (showDeleteConfirm) {
        val n = state.selectedIds.size
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare $n visit${if (n == 1) "a" else "e"}?") },
            text = { Text("Le visite verranno rimosse da tutti i dispositivi.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteConfirm = false
                    },
                ) { Text("Elimina", color = Color(0xFFD32F2F)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") }
            },
        )
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding(),
        containerColor = kb.background,
        bottomBar = {
            Column(Modifier.navigationBarsPadding()) {
                if (state.isSelecting) {
                    HealthListDualSelectionBottomBar(
                        tint = VISIT_TINT,
                        allSelected = state.selectedIds.size == state.filteredVisitCount && state.filteredVisitCount > 0,
                        hasSelection = state.selectedIds.isNotEmpty(),
                        onToggleAll = { viewModel.selectOrDeselectAllFiltered() },
                        onDelete = { showDeleteConfirm = true },
                    )
                } else {
                    HealthListAddBottomButton(
                        tint = VISIT_TINT,
                        label = "Aggiungi nuova visita",
                        onClick = onAdd,
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 0.dp),
            ) {
            HealthListTopToolbar(
                tint = VISIT_TINT,
                filterActive = state.periodFilter != VisitPeriodFilter.ALL,
                isSelecting = state.isSelecting,
                onBack = onBack,
                onFilterClick = { showFilterSheet = true },
                onToggleSelectClick = { viewModel.toggleSelecting() },
                onAddClick = onAdd,
            )

            Text(
                "Visita Medica",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
                modifier = Modifier.padding(horizontal = 18.dp),
            )

            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                placeholder = { Text("Cerca visita", color = kb.subtitle) },
                leadingIcon = {
                    Icon(Icons.Default.Search, contentDescription = null, tint = kb.subtitle)
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = VISIT_TINT,
                    unfocusedBorderColor = kb.subtitle.copy(alpha = 0.25f),
                ),
            )

            if (state.periodFilter != VisitPeriodFilter.ALL) {
                VisitFilterActivePill(
                    label = visitFilterPillLabel(state),
                    onClear = { viewModel.setPeriodFilter(VisitPeriodFilter.ALL) },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
                )
            }

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                !state.hasAnyVisit -> {
                    EmptyVisitsBody(
                        childName = state.childName,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                }
                state.filteredVisitCount == 0 -> {
                    EmptyVisitFilterBody(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onClear = { viewModel.setPeriodFilter(VisitPeriodFilter.ALL) },
                    )
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f)
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        visitSection(
                            status = KBVisitStatus.BOOKED,
                            items = state.booked,
                            isSelecting = state.isSelecting,
                            selectedIds = state.selectedIds,
                            onRowClick = { id ->
                                if (state.isSelecting) viewModel.toggleVisitSelected(id) else onOpen(id)
                            },
                        )
                        visitSection(
                            status = KBVisitStatus.PENDING,
                            items = state.pending,
                            isSelecting = state.isSelecting,
                            selectedIds = state.selectedIds,
                            onRowClick = { id ->
                                if (state.isSelecting) viewModel.toggleVisitSelected(id) else onOpen(id)
                            },
                        )
                        visitSection(
                            status = KBVisitStatus.RESULT_AVAILABLE,
                            items = state.resultAvailable,
                            isSelecting = state.isSelecting,
                            selectedIds = state.selectedIds,
                            onRowClick = { id ->
                                if (state.isSelecting) viewModel.toggleVisitSelected(id) else onOpen(id)
                            },
                        )
                        visitSection(
                            status = KBVisitStatus.COMPLETED,
                            items = state.completed,
                            isSelecting = state.isSelecting,
                            selectedIds = state.selectedIds,
                            onRowClick = { id ->
                                if (state.isSelecting) viewModel.toggleVisitSelected(id) else onOpen(id)
                            },
                        )
                        item { Spacer(Modifier.height(24.dp)) }
                    }
                }
            }
            }

            val displayName = state.childName.ifBlank { "Profilo" }
            val showVisitsAiFab = isAiGloballyEnabled &&
                !state.isSelecting &&
                !state.isLoading &&
                state.filteredVisitCount > 0
            if (showVisitsAiFab) {
                AskAiButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 96.dp),
                    isEnabled = true,
                    contentDescription = "Chiedi all'AI sulle visite di $displayName",
                    onTap = {
                        val ids = (
                            state.booked + state.pending +
                                state.resultAvailable + state.completed
                            ).map { it.id }
                        val json = JSONArray(ids).toString()
                        onOpenVisitsListAiChat(displayName, json)
                    },
                )
            }
        }
    }
}

@Composable
private fun VisitFilterActivePill(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(50))
            .background(VISIT_TINT.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = VISIT_TINT, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = VISIT_TINT, modifier = Modifier.weight(1f))
        TextButton(onClick = onClear, modifier = Modifier.height(32.dp)) {
            Text("×", fontSize = 18.sp, color = kb.subtitle)
        }
    }
}

private fun visitFilterPillLabel(state: MedicalVisitsState): String = when (state.periodFilter) {
    VisitPeriodFilter.ALL -> "Tutto"
    VisitPeriodFilter.THREE_MONTHS -> "Ultimi 3 mesi"
    VisitPeriodFilter.SIX_MONTHS -> "Ultimi 6 mesi"
    VisitPeriodFilter.ONE_YEAR -> "Ultimo anno"
    VisitPeriodFilter.CUSTOM ->
        "${DATE_SHORT.format(Date(state.customFilterStartEpoch))} – ${DATE_SHORT.format(Date(state.customFilterEndEpoch))}"
}

@Composable
private fun VisitFilterSheetContent(
    state: MedicalVisitsState,
    onPickQuick: (VisitPeriodFilter) -> Unit,
    onApplyCustom: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var customStart by remember { mutableStateOf(state.customFilterStartEpoch) }
    var customEnd by remember { mutableStateOf(state.customFilterEndEpoch) }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val startPickerState = rememberDatePickerState(initialSelectedDateMillis = customStart)
    val endPickerState = rememberDatePickerState(initialSelectedDateMillis = customEnd)

    LaunchedEffect(state.customFilterStartEpoch, state.customFilterEndEpoch) {
        customStart = state.customFilterStartEpoch
        customEnd = state.customFilterEndEpoch
    }

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Filtra per periodo", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = kb.title)
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        }
        Spacer(Modifier.height(8.dp))
        Text("Periodo rapido", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = kb.subtitle, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))
        VisitPeriodFilter.entries.filter { it != VisitPeriodFilter.CUSTOM }.forEach { f ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPickQuick(f) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.displayLabel, color = kb.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (state.periodFilter == f) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = VISIT_TINT, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("Personalizzato", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = kb.subtitle, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            VisitDateChip("Da", customStart) { showStartPicker = true }
            VisitDateChip("A", customEnd) { showEndPicker = true }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onApplyCustom(customStart, customEnd) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = VISIT_TINT),
        ) {
            Text("Applica", color = Color.White)
        }
        Spacer(Modifier.height(24.dp))
    }

    if (showStartPicker) {
        DatePickerDialog(
            onDismissRequest = { showStartPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        startPickerState.selectedDateMillis?.let { customStart = it }
                        showStartPicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showStartPicker = false }) { Text("Annulla") } },
        ) { DatePicker(state = startPickerState) }
    }
    if (showEndPicker) {
        DatePickerDialog(
            onDismissRequest = { showEndPicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        endPickerState.selectedDateMillis?.let { customEnd = it }
                        showEndPicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showEndPicker = false }) { Text("Annulla") } },
        ) { DatePicker(state = endPickerState) }
    }
}

@Composable
private fun VisitDateChip(label: String, epochMillis: Long, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.25f)),
        colors = CardDefaults.outlinedCardColors(containerColor = kb.card),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, fontSize = 11.sp, color = kb.subtitle)
            Text(DATE_SHORT.format(Date(epochMillis)), fontSize = 14.sp, color = kb.title, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmptyVisitsBody(childName: String, modifier: Modifier = Modifier) {
    val kb = MaterialTheme.kidBoxColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(VISIT_TINT.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.MedicalServices,
                    contentDescription = null,
                    tint = VISIT_TINT,
                    modifier = Modifier.size(40.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "Nessuna visita registrata",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = kb.title,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Aggiungi la prima visita per $childName",
                fontSize = 14.sp,
                color = kb.subtitle,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun EmptyVisitFilterBody(modifier: Modifier = Modifier, onClear: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "Nessuna visita nel periodo selezionato",
                fontSize = 14.sp,
                color = kb.subtitle,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClear) {
                Text("Rimuovi filtro", color = VISIT_TINT, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun visitStatusIcon(status: KBVisitStatus): ImageVector = when (status) {
    KBVisitStatus.PENDING -> Icons.Default.Schedule
    KBVisitStatus.BOOKED -> Icons.Default.EventAvailable
    KBVisitStatus.COMPLETED -> Icons.Default.CheckCircle
    KBVisitStatus.RESULT_AVAILABLE -> Icons.Default.Description
    KBVisitStatus.UNKNOWN_STATUS -> Icons.Default.Schedule
}

private fun visitStatusColor(status: KBVisitStatus): Color = when (status) {
    KBVisitStatus.PENDING -> Color(0xFF757575)
    KBVisitStatus.BOOKED -> Color(0xFF1565C0)
    KBVisitStatus.COMPLETED -> Color(0xFF2E7D32)
    KBVisitStatus.RESULT_AVAILABLE -> Color(0xFF6A1B9A)
    KBVisitStatus.UNKNOWN_STATUS -> Color(0xFF757575)
}

private fun LazyListScope.visitSection(
    status: KBVisitStatus,
    items: List<KBMedicalVisit>,
    isSelecting: Boolean,
    selectedIds: Set<String>,
    onRowClick: (String) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        VisitSectionHeader(
            displayLabel = status.displayLabel,
            icon = visitStatusIcon(status),
            tint = visitStatusColor(status),
            count = items.size,
        )
    }
    items(items, key = { it.id }) { visit ->
        VisitRow(
            visit = visit,
            rowStatus = KBVisitStatus.fromRaw(visit.visitStatusRaw),
            isSelecting = isSelecting,
            isSelected = selectedIds.contains(visit.id),
            onClick = { onRowClick(visit.id) },
        )
    }
}

@Composable
private fun VisitSectionHeader(displayLabel: String, icon: ImageVector, tint: Color, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(
            displayLabel.uppercase(Locale.ITALIAN),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = tint,
            letterSpacing = 0.6.sp,
        )
        Spacer(Modifier.weight(1f))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(tint)
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Text(
                "$count",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }
    }
}

@Composable
private fun VisitRow(
    visit: KBMedicalVisit,
    rowStatus: KBVisitStatus,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val badgeStatus = if (rowStatus == KBVisitStatus.UNKNOWN_STATUS) KBVisitStatus.PENDING else rowStatus
    val statusColor = visitStatusColor(badgeStatus)

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelecting) {
                Icon(
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.CheckCircleOutline,
                    contentDescription = null,
                    tint = if (isSelected) VISIT_TINT else kb.subtitle,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(VISIT_TINT.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.MedicalServices, contentDescription = null, tint = VISIT_TINT, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    visit.reason.ifBlank { "Visita" },
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                visit.doctorName?.takeIf { it.isNotBlank() }?.let { doc ->
                    Spacer(Modifier.height(2.dp))
                    Text(doc, fontSize = 12.sp, color = VISIT_TINT, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    DATE_ROW.format(Date(visit.dateEpochMillis)),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        visitStatusIcon(badgeStatus),
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        badgeStatus.displayLabel,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = statusColor,
                    )
                }
            }
            if (!isSelecting) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!visit.diagnosis.isNullOrBlank()) {
                        Icon(
                            Icons.Default.Description,
                            contentDescription = null,
                            tint = VISIT_TINT.copy(alpha = 0.55f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    if (visit.reminderOn || visit.nextVisitReminderOn) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = VISIT_TINT,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = kb.subtitle.copy(alpha = 0.45f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

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
