@file:OptIn(ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health.exams

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ai.AskAiButton
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListAddBottomButton
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListDualSelectionBottomBar
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListTopToolbar
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import org.json.JSONArray

private val DATE_FMT_EXAM = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private val DATE_FMT_SHORT = SimpleDateFormat("dd/MM/yy", Locale.ITALIAN)
private val TEAL = Color(0xFF40A6BF)
private val ORANGE_EXAMS = Color(0xFFFF6B00)
private val ORANGE_STATUS = Color(0xFFFF9800)
private val MINT_RESULT = Color(0xFF66BB6A)

@Composable
fun MedicalExamsScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (examId: String) -> Unit,
    onOpenExamsListAiChat: (subjectName: String, examIdsJson: String) -> Unit = { _, _ -> },
    viewModel: MedicalExamsViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isAiGloballyEnabled by viewModel.isAiGloballyEnabled.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    var showFilterSheet by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val filterSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }

    if (showFilterSheet) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = filterSheetState,
        ) {
            ExamFilterSheetContent(
                state = state,
                onPickQuick = { f ->
                    viewModel.setTimeFilter(f)
                    scope.launch { filterSheetState.hide() }.invokeOnCompletion { showFilterSheet = false }
                },
                onApplyCustom = { start, end ->
                    viewModel.setCustomFilterRange(start, end)
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
            title = { Text("Eliminare $n esam${if (n == 1) "e" else "i"}?") },
            text = { Text("Gli esami verranno rimossi da tutti i dispositivi.") },
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
                        tint = TEAL,
                        allSelected = state.selectedIds.size == state.filteredExamCount && state.filteredExamCount > 0,
                        hasSelection = state.selectedIds.isNotEmpty(),
                        onToggleAll = { viewModel.selectOrDeselectAllFiltered() },
                        onDelete = { showDeleteConfirm = true },
                    )
                } else {
                    HealthListAddBottomButton(
                        tint = TEAL,
                        label = "Nuovo Esame",
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
                modifier = Modifier.fillMaxSize(),
            ) {
            HealthListTopToolbar(
                tint = TEAL,
                filterActive = state.timeFilter != ExamTimeFilter.ALL,
                isSelecting = state.isSelecting,
                onBack = onBack,
                onFilterClick = { showFilterSheet = true },
                onToggleSelectClick = { viewModel.toggleSelecting() },
                onAddClick = onAdd,
            )

            Text(
                "Analisi & Esami",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
                modifier = Modifier.padding(horizontal = 18.dp),
            )

            if (state.timeFilter != ExamTimeFilter.ALL) {
                FilterActivePill(
                    label = filterPillLabel(state),
                    onClear = { viewModel.setTimeFilter(ExamTimeFilter.ALL) },
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                )
            } else {
                Spacer(Modifier.height(8.dp))
            }

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val isEmpty = !state.hasAnyExam
                val emptyFilter = state.hasAnyExam && state.filteredExamCount == 0 && state.timeFilter != ExamTimeFilter.ALL

                when {
                    isEmpty -> ExamsEmptyState(modifier = Modifier.fillMaxSize(), onAdd = onAdd)
                    emptyFilter -> EmptyFilterState(
                        modifier = Modifier.fillMaxSize(),
                        onClearFilter = { viewModel.setTimeFilter(ExamTimeFilter.ALL) },
                    )
                    else -> {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 18.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            item { Spacer(Modifier.height(4.dp)) }
                            examSection(
                                title = "In attesa",
                                icon = Icons.Default.Schedule,
                                tint = ORANGE_STATUS,
                                items = state.pending,
                                isSelecting = state.isSelecting,
                                selectedIds = state.selectedIds,
                                onRowClick = { id ->
                                    if (state.isSelecting) viewModel.toggleExamSelected(id) else onOpen(id)
                                },
                            )
                            examSection(
                                title = "Prenotati",
                                icon = Icons.Default.EventAvailable,
                                tint = TEAL,
                                items = state.booked,
                                isSelecting = state.isSelecting,
                                selectedIds = state.selectedIds,
                                onRowClick = { id ->
                                    if (state.isSelecting) viewModel.toggleExamSelected(id) else onOpen(id)
                                },
                            )
                            examSection(
                                title = "Eseguiti",
                                icon = Icons.Default.CheckCircle,
                                tint = Color(0xFF43A047),
                                items = state.executed,
                                isSelecting = state.isSelecting,
                                selectedIds = state.selectedIds,
                                onRowClick = { id ->
                                    if (state.isSelecting) viewModel.toggleExamSelected(id) else onOpen(id)
                                },
                            )
                            examSection(
                                title = "Senza stato",
                                icon = Icons.Default.Search,
                                tint = kb.subtitle,
                                items = state.unknownStatus,
                                isSelecting = state.isSelecting,
                                selectedIds = state.selectedIds,
                                onRowClick = { id ->
                                    if (state.isSelecting) viewModel.toggleExamSelected(id) else onOpen(id)
                                },
                            )
                            item { Spacer(Modifier.height(24.dp)) }
                        }
                    }
                }
            }
            }

            val displayName = state.childName.ifBlank { "Profilo" }
            val showExamsAiFab = isAiGloballyEnabled &&
                !state.isSelecting &&
                !state.isLoading &&
                state.filteredExamCount > 0
            if (showExamsAiFab) {
                AskAiButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = 96.dp),
                    isEnabled = true,
                    contentDescription = "Chiedi all'AI sugli esami di $displayName",
                    onTap = {
                        val ids = (
                            state.pending + state.booked +
                                state.executed + state.unknownStatus
                            ).map { it.id }
                        val json = JSONArray(ids).toString()
                        onOpenExamsListAiChat(displayName, json)
                    },
                )
            }
        }
    }
}

@Composable
private fun FilterActivePill(label: String, onClear: () -> Unit, modifier: Modifier = Modifier) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(TEAL.copy(alpha = 0.12f))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = TEAL, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = TEAL, modifier = Modifier.weight(1f))
        TextButton(onClick = onClear, modifier = Modifier.height(32.dp)) {
            Text("×", fontSize = 18.sp, color = kb.subtitle)
        }
    }
}

private fun filterPillLabel(state: MedicalExamsState): String = when (state.timeFilter) {
    ExamTimeFilter.ALL -> "Tutti"
    ExamTimeFilter.MONTHS_3 -> "Ultimi 3 mesi"
    ExamTimeFilter.MONTHS_6 -> "Ultimi 6 mesi"
    ExamTimeFilter.YEAR_1 -> "Ultimo anno"
    ExamTimeFilter.CUSTOM ->
        "${DATE_FMT_SHORT.format(Date(state.customFilterStartEpoch))} – ${DATE_FMT_SHORT.format(Date(state.customFilterEndEpoch))}"
}

@Composable
private fun ExamFilterSheetContent(
    state: MedicalExamsState,
    onPickQuick: (ExamTimeFilter) -> Unit,
    onApplyCustom: (Long, Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var customStart by remember { mutableStateOf(state.customFilterStartEpoch) }
    var customEnd by remember { mutableStateOf(state.customFilterEndEpoch) }
    LaunchedEffect(state.customFilterStartEpoch, state.customFilterEndEpoch) {
        customStart = state.customFilterStartEpoch
        customEnd = state.customFilterEndEpoch
    }
    var showStartPicker by remember { mutableStateOf(false) }
    var showEndPicker by remember { mutableStateOf(false) }
    val startPickerState = rememberDatePickerState(initialSelectedDateMillis = customStart)
    val endPickerState = rememberDatePickerState(initialSelectedDateMillis = customEnd)

    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Filtra per periodo", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = kb.title)
            TextButton(onClick = onDismiss) { Text("Chiudi") }
        }
        Spacer(Modifier.height(8.dp))
        Text("Periodo rapido", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = kb.subtitle, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(6.dp))
        ExamTimeFilter.entries.filter { it != ExamTimeFilter.CUSTOM }.forEach { f ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onPickQuick(f) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(f.displayLabel, color = kb.title, fontSize = 15.sp, modifier = Modifier.weight(1f))
                if (state.timeFilter == f) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = TEAL, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(Modifier.height(12.dp))
        Text("Personalizzato", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = kb.subtitle, letterSpacing = 0.8.sp)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedDateChip("Da", customStart) { showStartPicker = true }
            OutlinedDateChip("A", customEnd) { showEndPicker = true }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { onApplyCustom(customStart, customEnd) },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = TEAL),
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
private fun OutlinedDateChip(label: String, epochMillis: Long, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    OutlinedCard(
        onClick = onClick,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.25f)),
        colors = CardDefaults.outlinedCardColors(containerColor = kb.card),
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, fontSize = 11.sp, color = kb.subtitle)
            Text(DATE_FMT_SHORT.format(Date(epochMillis)), fontSize = 14.sp, color = kb.title, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun EmptyFilterState(modifier: Modifier = Modifier, onClearFilter: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(40.dp))
            Spacer(Modifier.height(12.dp))
            Text(
                "Nessun esame nel periodo selezionato",
                fontSize = 14.sp,
                color = kb.subtitle,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onClearFilter) {
                Text("Rimuovi filtro", color = TEAL, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun LazyListScope.examSection(
    title: String,
    icon: ImageVector,
    tint: Color,
    items: List<KBMedicalExam>,
    isSelecting: Boolean,
    selectedIds: Set<String>,
    onRowClick: (String) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        ExamSectionHeader(title = title, icon = icon, count = items.size, tint = tint)
    }
    items(items, key = { it.id }) { exam ->
        ExamRow(
            exam = exam,
            isSelecting = isSelecting,
            isSelected = selectedIds.contains(exam.id),
            onClick = { onRowClick(exam.id) },
        )
    }
}

@Composable
private fun ExamSectionHeader(title: String, icon: ImageVector, count: Int, tint: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = tint)
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

private fun statusIcon(status: KBExamStatus): ImageVector = when (status) {
    KBExamStatus.PENDING -> Icons.Default.Schedule
    KBExamStatus.BOOKED -> Icons.Default.EventAvailable
    KBExamStatus.DONE -> Icons.Default.CheckCircle
    KBExamStatus.RESULT_IN -> Icons.Default.Description
}

private fun statusTint(status: KBExamStatus): Color = when (status) {
    KBExamStatus.PENDING -> ORANGE_STATUS
    KBExamStatus.BOOKED -> TEAL
    KBExamStatus.DONE -> Color(0xFF43A047)
    KBExamStatus.RESULT_IN -> MINT_RESULT
}

@Composable
private fun ExamRow(
    exam: KBMedicalExam,
    isSelecting: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val status = KBExamStatus.entries.firstOrNull { it.rawValue == exam.statusRaw } ?: KBExamStatus.PENDING
    val tint = statusTint(status)
    val overdue = exam.deadlineEpochMillis != null &&
        exam.deadlineEpochMillis!! < System.currentTimeMillis() &&
        status == KBExamStatus.PENDING

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
                    imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.CheckCircleOutline,
                    contentDescription = null,
                    tint = if (isSelected) TEAL else kb.subtitle,
                    modifier = Modifier.size(26.dp),
                )
                Spacer(Modifier.width(8.dp))
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(statusIcon(status), contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(exam.name, fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 15.sp)
                    if (exam.isUrgent) {
                        Spacer(Modifier.width(6.dp))
                        Text("⚠", fontSize = 12.sp, color = Color(0xFFD32F2F))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(statusIcon(status), contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(status.rawValue, fontSize = 12.sp, color = tint, fontWeight = FontWeight.Medium)
                }
                if (exam.deadlineEpochMillis != null) {
                    Spacer(Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(12.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "Entro ${DATE_FMT_EXAM.format(Date(exam.deadlineEpochMillis!!))}",
                            fontSize = 12.sp,
                            color = if (overdue) Color(0xFFD32F2F) else kb.subtitle,
                        )
                    }
                }
                if (status == KBExamStatus.RESULT_IN && !exam.resultText.isNullOrBlank()) {
                    Spacer(Modifier.height(4.dp))
                    Text(exam.resultText!!, fontSize = 12.sp, color = kb.subtitle, maxLines = 1)
                }
            }
            if (!isSelecting) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = kb.subtitle.copy(alpha = 0.5f),
                )
            }
        }
    }
}

// ── Empty state (lista vuota assoluta) ─────────────────────────────────────────

@Composable
private fun ExamsEmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(TEAL.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Description, contentDescription = null, tint = TEAL, modifier = Modifier.size(32.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                "Nessun esame registrato",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = kb.title,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = ORANGE_EXAMS),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Nuovo esame", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
fun ExamStatusBadge(status: KBExamStatus) {
    val (bgColor, textColor) = when (status) {
        KBExamStatus.PENDING -> Color(0xFFE0E0E0) to Color(0xFF616161)
        KBExamStatus.BOOKED -> Color(0xFFBBDEFB) to Color(0xFF1565C0)
        KBExamStatus.DONE -> Color(0xFFC8E6C9) to Color(0xFF2E7D32)
        KBExamStatus.RESULT_IN -> Color(0xFFE1BEE7) to Color(0xFF6A1B9A)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(status.rawValue, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun UrgentChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Color(0xFFFFCDD2))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("Urgente", fontSize = 10.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
    }
}


