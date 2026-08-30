package it.vittorioscocca.kidbox.ui.screens.health.treatments

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
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.frequencyDisplayLabel
import it.vittorioscocca.kidbox.domain.model.plannedFiniteDosesTotal
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListAddBottomButton
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListTopToolbar
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.DateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit
import it.vittorioscocca.kidbox.util.KBLocale
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import androidx.compose.ui.platform.LocalContext

private val PURPLE = Color(0xFF9573D9)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalTreatmentsScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (treatmentId: String) -> Unit,
    viewModel: MedicalTreatmentsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activeTitle = stringResource(R.string.health_active_treatments)
    val longTermTitle = stringResource(R.string.health_long_term_cap)
    val completedTitle = stringResource(R.string.health_completed)
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var datePickerForStart by remember { mutableStateOf(false) }
    var datePickerForEnd by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState,
            containerColor = kb.card,
        ) {
            TreatmentFilterSheetContent(
                state = state,
                onDismiss = { showFilterSheet = false },
                onPickStartDate = { datePickerForStart = true },
                onPickEndDate = { datePickerForEnd = true },
                onQuickFilter = { f ->
                    viewModel.setTimeFilter(f)
                    showFilterSheet = false
                },
                onApplyCustom = {
                    viewModel.applyCustomFilter()
                    showFilterSheet = false
                },
            )
        }
    }

    if (datePickerForStart) {
        TreatFilterDatePickerDialog(
            initialMillis = state.customFilterStartMillis,
            onDismiss = { datePickerForStart = false },
            onConfirm = {
                viewModel.setCustomFilterStart(it)
                datePickerForStart = false
            },
        )
    }
    if (datePickerForEnd) {
        TreatFilterDatePickerDialog(
            initialMillis = state.customFilterEndMillis,
            onDismiss = { datePickerForEnd = false },
            onConfirm = {
                viewModel.setCustomFilterEnd(it)
                datePickerForEnd = false
            },
        )
    }

    if (showDeleteConfirm) {
        val n = state.selectedIds.size
        val curaWord = if (n == 1) "cura" else "cure"
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare $n $curaWord?") },
            text = { Text(stringResource(R.string.health_treatments_removed_all)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteConfirm = false
                    },
                ) { Text(stringResource(R.string.health_delete), color = Color(0xFFB3261E)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(stringResource(R.string.health_cancel)) }
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
                    SelectionBottomBar(
                        allSelected = state.selectedIds.size == state.allFiltered.size && state.allFiltered.isNotEmpty(),
                        hasSelection = state.selectedIds.isNotEmpty(),
                        onToggleSelectAll = { viewModel.toggleSelectAllFiltered() },
                        onDuplicate = { viewModel.duplicateSelected() },
                        onDelete = { showDeleteConfirm = true },
                    )
                } else {
                    HealthListAddBottomButton(
                        tint = PURPLE,
                        label = stringResource(R.string.health_new_treatment_cap),
                        onClick = onAdd,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            HealthListTopToolbar(
                tint = PURPLE,
                filterActive = state.timeFilter != TreatmentTimeFilter.ALL,
                isSelecting = state.isSelecting,
                onBack = onBack,
                onFilterClick = { showFilterSheet = true },
                onToggleSelectClick = {
                    if (state.isSelecting) viewModel.setSelecting(false) else viewModel.setSelecting(true)
                },
                onAddClick = onAdd,
            )

            Text(
                stringResource(R.string.health_treatments),
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            if (state.timeFilter != TreatmentTimeFilter.ALL) {
                Spacer(Modifier.height(8.dp))
                FilterActivePill(
                    label = filterLabel(context, state),
                    onClear = { viewModel.clearTimeFilter() },
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            }
            Spacer(Modifier.height(12.dp))

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (state.isEmptyDueToFilter) {
                EmptyFilterState(onClear = { viewModel.clearTimeFilter() }, modifier = Modifier.fillMaxSize())
            } else {
                val isEmpty = state.active.isEmpty() && state.longTerm.isEmpty() && state.inactive.isEmpty()
                if (isEmpty) {
                    EmptyTreatments(modifier = Modifier.fillMaxSize())
                } else {
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.forceRefresh() },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item { Spacer(Modifier.height(4.dp)) }
                            treatmentSection(
                                title = activeTitle,
                                items = state.active,
                                takenDosesByTreatmentId = state.takenDosesByTreatmentId,
                                isSelecting = state.isSelecting,
                                selectedIds = state.selectedIds,
                                showBadge = true,
                                onRowClick = { t ->
                                    if (state.isSelecting) viewModel.toggleSelection(t.id) else onOpen(t.id)
                                },
                            )
                            treatmentSection(
                                title = longTermTitle,
                                items = state.longTerm,
                                takenDosesByTreatmentId = state.takenDosesByTreatmentId,
                                isSelecting = state.isSelecting,
                                selectedIds = state.selectedIds,
                                onRowClick = { t ->
                                    if (state.isSelecting) viewModel.toggleSelection(t.id) else onOpen(t.id)
                                },
                            )
                            treatmentSection(
                                title = completedTitle,
                                items = state.inactive,
                                takenDosesByTreatmentId = state.takenDosesByTreatmentId,
                                isSelecting = state.isSelecting,
                                selectedIds = state.selectedIds,
                                onRowClick = { t ->
                                    if (state.isSelecting) viewModel.toggleSelection(t.id) else onOpen(t.id)
                                },
                            )
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
        }
    }
}

private fun filterLabel(context: android.content.Context, state: MedicalTreatmentsState): String {
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, KBLocale.current())
    return when (state.timeFilter) {
        TreatmentTimeFilter.ALL -> context.getString(R.string.health_all)
        TreatmentTimeFilter.MONTHS_3 -> context.getString(R.string.health_filter_3m)
        TreatmentTimeFilter.MONTHS_6 -> context.getString(R.string.health_filter_6m)
        TreatmentTimeFilter.YEAR_LAST -> context.getString(R.string.health_filter_1y)
        TreatmentTimeFilter.CUSTOM ->
            "${fmt.format(state.customFilterStartMillis)} – ${fmt.format(state.customFilterEndMillis)}"
    }
}

@Composable
private fun FilterActivePill(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(999.dp),
        color = PURPLE.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PURPLE, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PURPLE, modifier = Modifier.weight(1f))
            Text(
                "✕",
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onClear)
                    .padding(4.dp),
                color = kb.subtitle,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
private fun EmptyFilterState(onClear: () -> Unit, modifier: Modifier = Modifier) {
    val kb = MaterialTheme.kidBoxColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(
                Icons.Default.CalendarMonth,
                contentDescription = null,
                tint = kb.subtitle,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.health_no_treatments_period),
                fontSize = 14.sp,
                color = kb.subtitle,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClear) {
                Text(stringResource(R.string.health_remove_filter), color = PURPLE, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TreatFilterDatePickerDialog(initialMillis: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { pickerState.selectedDateMillis?.let(onConfirm) ?: onDismiss() }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.health_cancel)) } },
    ) { DatePicker(state = pickerState) }
}

@Composable
private fun TreatmentFilterSheetContent(
    state: MedicalTreatmentsState,
    onDismiss: () -> Unit,
    onPickStartDate: () -> Unit,
    onPickEndDate: () -> Unit,
    onQuickFilter: (TreatmentTimeFilter) -> Unit,
    onApplyCustom: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM, KBLocale.current()) }
    val quickOptions = listOf(
        TreatmentTimeFilter.ALL,
        TreatmentTimeFilter.MONTHS_3,
        TreatmentTimeFilter.MONTHS_6,
        TreatmentTimeFilter.YEAR_LAST,
    )

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.width(72.dp))
            Text(
                stringResource(R.string.health_filter_period),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = kb.title,
            )
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.health_close)) }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.health_quick_period),
            fontSize = 13.sp,
            color = kb.subtitle,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = kb.background,
        ) {
            Column {
                quickOptions.forEachIndexed { index, f ->
                    if (index > 0) HorizontalDivider(color = kb.subtitle.copy(alpha = 0.12f))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onQuickFilter(f) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(stringResource(f.sheetLabelRes), color = kb.title, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        if (state.timeFilter == f) {
                            Text("✓", color = PURPLE, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            stringResource(R.string.health_custom_period),
            fontSize = 13.sp,
            color = kb.subtitle,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            shape = RoundedCornerShape(12.dp),
            color = PURPLE.copy(alpha = 0.08f),
        ) {
            Column(Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Da", color = kb.title, fontSize = 15.sp, modifier = Modifier.width(36.dp))
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onPickStartDate,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(dateFmt.format(state.customFilterStartMillis), color = kb.title)
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("A", color = kb.title, fontSize = 15.sp, modifier = Modifier.width(36.dp))
                    Spacer(Modifier.weight(1f))
                    TextButton(
                        onClick = onPickEndDate,
                        shape = RoundedCornerShape(20.dp),
                    ) {
                        Text(dateFmt.format(state.customFilterEndMillis), color = kb.title)
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onApplyCustom,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = PURPLE),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.health_apply), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun SelectionBottomBar(
    allSelected: Boolean,
    hasSelection: Boolean,
    onToggleSelectAll: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    val deleteTint = Color(0xFFD32F2F)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(kb.background),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onToggleSelectAll,
            modifier = Modifier.weight(1f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = if (allSelected) Icons.Filled.CheckCircle else Icons.Default.GridView,
                    contentDescription = null,
                    tint = PURPLE,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (allSelected) stringResource(R.string.health_deselect) else stringResource(R.string.health_all),
                    fontSize = 11.sp,
                    color = PURPLE,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        VerticalBarDivider()
        TextButton(
            onClick = onDuplicate,
            enabled = hasSelection,
            modifier = Modifier.weight(1f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Outlined.FileCopy,
                    contentDescription = null,
                    tint = if (hasSelection) PURPLE else kb.subtitle,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.health_duplicate),
                    fontSize = 11.sp,
                    color = if (hasSelection) PURPLE else kb.subtitle,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
        VerticalBarDivider()
        TextButton(
            onClick = onDelete,
            enabled = hasSelection,
            modifier = Modifier.weight(1f),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = if (hasSelection) deleteTint else kb.subtitle,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.health_delete),
                    fontSize = 11.sp,
                    color = if (hasSelection) deleteTint else kb.subtitle,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun VerticalBarDivider() {
    val kb = MaterialTheme.kidBoxColors
    Box(
        modifier = Modifier
            .width(1.dp)
            .fillMaxHeight()
            .background(kb.subtitle.copy(alpha = 0.2f)),
    )
}

private fun LazyListScope.treatmentSection(
    title: String,
    items: List<KBTreatment>,
    takenDosesByTreatmentId: Map<String, Int>,
    isSelecting: Boolean,
    selectedIds: Set<String>,
    onRowClick: (KBTreatment) -> Unit,
    showBadge: Boolean = false,
) {
    if (items.isEmpty()) return
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Medication, contentDescription = null, tint = PURPLE, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = PURPLE)
            Spacer(Modifier.weight(1f))
            if (showBadge) {
                Surface(color = PURPLE.copy(alpha = 0.18f), shape = CircleShape) {
                    Text(
                        "${items.size}",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        color = PURPLE,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
    items(items, key = { it.id }) { treatment ->
        TreatmentRow(
            treatment = treatment,
            takenSoFar = takenDosesByTreatmentId[treatment.id] ?: 0,
            isSelecting = isSelecting,
            isSelected = selectedIds.contains(treatment.id),
            onClick = { onRowClick(treatment) },
        )
    }
}

@Composable
private fun TreatmentRow(
    treatment: KBTreatment,
    takenSoFar: Int,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val now = System.currentTimeMillis()
    val daysSinceStart = TimeUnit.MILLISECONDS.toDays(now - treatment.startDateEpochMillis).coerceAtLeast(0) + 1

    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (isSelecting) {
                Icon(
                    imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) PURPLE else kb.subtitle.copy(alpha = 0.55f),
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
            }
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
                        Icon(Icons.Default.Notifications, contentDescription = null, tint = PURPLE, modifier = Modifier.size(14.dp))
                    }
                }
                val dosageStr = if (treatment.dosageValue % 1.0 == 0.0) "%.0f".format(treatment.dosageValue) else "%.1f".format(treatment.dosageValue)
                Text(
                    "$dosageStr ${treatment.dosageUnit} · ${treatment.frequencyDisplayLabel}",
                    fontSize = 12.sp,
                    color = PURPLE,
                )
                if (treatment.isLongTerm) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.health_long_term_cap), fontSize = 12.sp, color = kb.subtitle)
                    }
                } else {
                    val currentDay = daysSinceStart.coerceAtMost(treatment.durationDays.toLong())
                    val totalPlanned = treatment.plannedFiniteDosesTotal()
                    val taken = takenSoFar.coerceIn(0, totalPlanned)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Giorno $currentDay di ${treatment.durationDays}  \u2013  $taken/$totalPlanned",
                            fontSize = 12.sp,
                            color = kb.subtitle,
                        )
                    }
                }
            }
            if (!isSelecting) {
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = kb.subtitle.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun EmptyTreatments(modifier: Modifier = Modifier) {
    val kb = MaterialTheme.kidBoxColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Default.Medication, contentDescription = null, tint = kb.subtitle.copy(alpha = 0.4f), modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.health_no_treatments), fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = kb.title)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.health_no_treatments_hint), color = kb.subtitle, fontSize = 12.sp)
        }
    }
}
