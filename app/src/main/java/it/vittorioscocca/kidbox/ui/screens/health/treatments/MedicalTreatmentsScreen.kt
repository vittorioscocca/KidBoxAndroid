package it.vittorioscocca.kidbox.ui.screens.health.treatments

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.FileCopy
import androidx.compose.material.icons.outlined.FilterList
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.DateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

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
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
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
            text = { Text("Le cure verranno rimosse da tutti i dispositivi.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteConfirm = false
                    },
                ) { Text("Elimina", color = Color(0xFFB3261E)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") }
            },
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KidBoxHeaderCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                if (state.isSelecting) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = kb.card,
                        shadowElevation = 0.dp,
                    ) {
                        Row(
                            modifier = Modifier.height(44.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            IconButton(
                                onClick = { showFilterSheet = true },
                                modifier = Modifier.size(44.dp),
                            ) {
                                Icon(
                                    imageVector = if (state.timeFilter == TreatmentTimeFilter.ALL) {
                                        Icons.Outlined.FilterList
                                    } else {
                                        Icons.Filled.FilterList
                                    },
                                    contentDescription = "Filtra per periodo",
                                    tint = if (state.timeFilter == TreatmentTimeFilter.ALL) kb.title else PURPLE,
                                )
                            }
                            TextButton(
                                onClick = { viewModel.setSelecting(false) },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            ) {
                                Text(
                                    "Fine",
                                    color = kb.title,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                } else {
                    KidBoxHeaderCircleButton(
                        icon = if (state.timeFilter == TreatmentTimeFilter.ALL) {
                            Icons.Outlined.FilterList
                        } else {
                            Icons.Filled.FilterList
                        },
                        contentDescription = "Filtra per periodo",
                        onClick = { showFilterSheet = true },
                        iconTint = if (state.timeFilter == TreatmentTimeFilter.ALL) null else PURPLE,
                    )
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { viewModel.setSelecting(true) }) {
                        Text(
                            "Seleziona",
                            color = kb.title,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                    Spacer(Modifier.width(2.dp))
                    KidBoxHeaderCircleButton(
                        icon = Icons.Default.Add,
                        contentDescription = "Nuova cura",
                        onClick = onAdd,
                    )
                }
            }

            Text(
                "Cure",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
                modifier = Modifier.padding(horizontal = 18.dp),
            )
            if (state.timeFilter != TreatmentTimeFilter.ALL) {
                Spacer(Modifier.height(8.dp))
                FilterActivePill(
                    label = filterLabel(state),
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
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item { Spacer(Modifier.height(4.dp)) }
                        treatmentSection(
                            title = "Cure Attive",
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
                            title = "Lungo termine",
                            items = state.longTerm,
                            takenDosesByTreatmentId = state.takenDosesByTreatmentId,
                            isSelecting = state.isSelecting,
                            selectedIds = state.selectedIds,
                            onRowClick = { t ->
                                if (state.isSelecting) viewModel.toggleSelection(t.id) else onOpen(t.id)
                            },
                        )
                        treatmentSection(
                            title = "Concluse",
                            items = state.inactive,
                            takenDosesByTreatmentId = state.takenDosesByTreatmentId,
                            isSelecting = state.isSelecting,
                            selectedIds = state.selectedIds,
                            onRowClick = { t ->
                                if (state.isSelecting) viewModel.toggleSelection(t.id) else onOpen(t.id)
                            },
                        )
                        item { Spacer(Modifier.height(if (state.isSelecting) 120.dp else 84.dp)) }
                    }
                }
            }
        }

        if (!state.isSelecting) {
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = PURPLE),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 20.dp, vertical = 20.dp)
                    .fillMaxWidth()
                    .height(54.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Nuova Cura", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        } else {
            SelectionBottomBar(
                allSelected = state.selectedIds.size == state.allFiltered.size && state.allFiltered.isNotEmpty(),
                hasSelection = state.selectedIds.isNotEmpty(),
                onToggleSelectAll = { viewModel.toggleSelectAllFiltered() },
                onDuplicate = { viewModel.duplicateSelected() },
                onDelete = { showDeleteConfirm = true },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

private fun filterLabel(state: MedicalTreatmentsState): String {
    val fmt = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.ITALY)
    return when (state.timeFilter) {
        TreatmentTimeFilter.ALL -> "Tutte"
        TreatmentTimeFilter.MONTHS_3 -> "Ultimi 3 mesi"
        TreatmentTimeFilter.MONTHS_6 -> "Ultimi 6 mesi"
        TreatmentTimeFilter.YEAR_LAST -> "Ultimo anno"
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
                "Nessuna cura nel periodo selezionato",
                fontSize = 14.sp,
                color = kb.subtitle,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClear) {
                Text("Rimuovi filtro", color = PURPLE, fontWeight = FontWeight.SemiBold)
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
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
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
    val dateFmt = remember { DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.ITALY) }
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
                "Filtra per periodo",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = kb.title,
            )
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Periodo rapido",
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
                        Text(f.sheetLabel, color = kb.title, fontSize = 16.sp)
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
            "Periodo personalizzato",
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
            Text("Applica", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
    Column(modifier = modifier.fillMaxWidth()) {
        HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(kb.background)
                .padding(top = 6.dp, bottom = 10.dp),
        ) {
            TextButton(
                onClick = onToggleSelectAll,
                modifier = Modifier.weight(1f),
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = if (allSelected) Icons.Filled.CheckCircle else Icons.Outlined.Apps,
                        contentDescription = null,
                        tint = PURPLE,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (allSelected) "Deseleziona" else "Tutte",
                        fontSize = 12.sp,
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
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Duplica",
                        fontSize = 12.sp,
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
                        tint = if (hasSelection) Color(0xFFB3261E) else kb.subtitle,
                        modifier = Modifier.size(24.dp),
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Elimina",
                        fontSize = 12.sp,
                        color = if (hasSelection) Color(0xFFB3261E) else kb.subtitle,
                        fontWeight = FontWeight.Medium,
                    )
                }
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
            .height(40.dp)
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
                    "$dosageStr ${treatment.dosageUnit} · ${treatment.dailyFrequency} volte al giorno",
                    fontSize = 12.sp,
                    color = PURPLE,
                )
                if (treatment.isLongTerm) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Lungo termine", fontSize = 12.sp, color = kb.subtitle)
                    }
                } else {
                    val currentDay = daysSinceStart.coerceAtMost(treatment.durationDays.toLong())
                    val totalPlanned = treatment.durationDays * treatment.dailyFrequency
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
            Text("Nessuna cura registrata", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = kb.title)
            Spacer(Modifier.height(16.dp))
            Text("Usa il pulsante in basso per aggiungere una nuova cura", color = kb.subtitle, fontSize = 12.sp)
        }
    }
}
