package it.vittorioscocca.kidbox.ui.screens.health.vaccines

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.outlined.Biotech
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.LocalPharmacy
import androidx.compose.material.icons.outlined.Psychology
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineStatus
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineType
import it.vittorioscocca.kidbox.data.local.mapper.computedStatus
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListAddBottomButton
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListDualSelectionBottomBar
import it.vittorioscocca.kidbox.ui.screens.health.common.HealthListTopToolbar
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FMT_VACCINE_ROW = SimpleDateFormat("d MMM yyyy", Locale.ENGLISH)
private val SALMON = Color(0xFFF38B75)
private val GREEN = Color(0xFF2E7D32)
private val BLUE = Color(0xFF1565C0)
private val ORANGE = Color(0xFFE65100)
private val RED = Color(0xFFD32F2F)

private fun timeFilterLabel(filter: VaccineListTimeFilter): String = when (filter) {
    VaccineListTimeFilter.ALL -> "Tutti"
    VaccineListTimeFilter.MONTHS3 -> "3 mesi"
    VaccineListTimeFilter.MONTHS6 -> "6 mesi"
    VaccineListTimeFilter.YEAR1 -> "Ultimo anno"
    VaccineListTimeFilter.CUSTOM -> "Personalizzato"
}

@Composable
fun MedicalVaccinesScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (vaccineId: String) -> Unit,
    viewModel: MedicalVaccinesViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var isSelecting by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf(setOf<String>()) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }

    val appointments = state.overdue + state.scheduled
    val sectionsEmpty = appointments.isEmpty() && state.administered.isEmpty() &&
        state.planned.isEmpty() && state.skipped.isEmpty()
    val isTotallyEmpty = !state.isLoading && state.unfilteredCount == 0
    val emptyDueToFilter = !state.isLoading && state.unfilteredCount > 0 && sectionsEmpty &&
        state.timeFilter != VaccineListTimeFilter.ALL

    val allFilteredIds = remember(state) {
        buildSet {
            appointments.forEach { add(it.id) }
            state.administered.forEach { add(it.id) }
            state.planned.forEach { add(it.id) }
            state.skipped.forEach { add(it.id) }
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding(),
        containerColor = kb.background,
        bottomBar = {
            if (!state.isLoading && !isTotallyEmpty) {
                Column(Modifier.navigationBarsPadding()) {
                    if (isSelecting) {
                        HealthListDualSelectionBottomBar(
                            tint = SALMON,
                            allSelected = allFilteredIds.isNotEmpty() && selectedIds.size == allFilteredIds.size,
                            hasSelection = selectedIds.isNotEmpty(),
                            onToggleAll = {
                                selectedIds =
                                    if (selectedIds.size == allFilteredIds.size) emptySet() else allFilteredIds
                            },
                            onDelete = { showDeleteConfirm = true },
                        )
                    } else {
                        HealthListAddBottomButton(
                            tint = SALMON,
                            label = "Aggiungi vaccino",
                            onClick = onAdd,
                        )
                    }
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
                tint = SALMON,
                filterActive = state.timeFilter != VaccineListTimeFilter.ALL,
                isSelecting = isSelecting,
                onBack = onBack,
                onFilterClick = { showFilterDialog = true },
                onToggleSelectClick = {
                    isSelecting = !isSelecting
                    if (!isSelecting) selectedIds = emptySet()
                },
                onAddClick = onAdd,
            )

            Text(
                "Vaccini",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
            )

            if (state.timeFilter != VaccineListTimeFilter.ALL) {
                VaccineFilterActivePill(
                    label = timeFilterLabel(state.timeFilter),
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
                    onClear = { viewModel.setTimeFilter(VaccineListTimeFilter.ALL) },
                )
            }

        if (state.isLoading) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = SALMON)
            }
        } else if (isTotallyEmpty) {
            VaccinesEmptyState(modifier = Modifier.weight(1f), onAdd = onAdd)
        } else if (emptyDueToFilter) {
            VaccinesEmptyFilterState(
                modifier = Modifier.weight(1f),
                onClearFilter = {
                    viewModel.setTimeFilter(VaccineListTimeFilter.ALL)
                },
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item { Spacer(Modifier.height(4.dp)) }
                vaccineSectionIos(
                    title = "Appuntamento fissato",
                    icon = Icons.Default.Event,
                    iconTint = BLUE,
                    items = appointments,
                    isSelecting = isSelecting,
                    selectedIds = selectedIds,
                    onToggleSelect = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onOpen = onOpen,
                )
                vaccineSectionIos(
                    title = "Somministrati",
                    icon = Icons.Default.CheckCircle,
                    iconTint = GREEN,
                    items = state.administered,
                    isSelecting = isSelecting,
                    selectedIds = selectedIds,
                    onToggleSelect = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onOpen = onOpen,
                )
                vaccineSectionIos(
                    title = "Da programmare",
                    icon = Icons.AutoMirrored.Filled.HelpOutline,
                    iconTint = ORANGE,
                    items = state.planned,
                    isSelecting = isSelecting,
                    selectedIds = selectedIds,
                    onToggleSelect = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onOpen = onOpen,
                )
                vaccineSectionIos(
                    title = "Non eseguiti",
                    icon = Icons.Outlined.Cancel,
                    iconTint = Color(0xFF616161),
                    items = state.skipped,
                    isSelecting = isSelecting,
                    selectedIds = selectedIds,
                    onToggleSelect = { id ->
                        selectedIds = if (id in selectedIds) selectedIds - id else selectedIds + id
                    },
                    onOpen = onOpen,
                )
                item { Spacer(Modifier.height(24.dp)) }
            }
        }
        }
    }

    if (showFilterDialog) {
        AlertDialog(
            onDismissRequest = { showFilterDialog = false },
            title = { Text("Periodo") },
            text = {
                Column {
                    FilterOptionRow("Tutti", VaccineListTimeFilter.ALL, state.timeFilter) {
                        viewModel.setTimeFilter(VaccineListTimeFilter.ALL)
                        showFilterDialog = false
                    }
                    FilterOptionRow("3 mesi", VaccineListTimeFilter.MONTHS3, state.timeFilter) {
                        viewModel.setTimeFilter(VaccineListTimeFilter.MONTHS3)
                        showFilterDialog = false
                    }
                    FilterOptionRow("6 mesi", VaccineListTimeFilter.MONTHS6, state.timeFilter) {
                        viewModel.setTimeFilter(VaccineListTimeFilter.MONTHS6)
                        showFilterDialog = false
                    }
                    FilterOptionRow("Ultimo anno", VaccineListTimeFilter.YEAR1, state.timeFilter) {
                        viewModel.setTimeFilter(VaccineListTimeFilter.YEAR1)
                        showFilterDialog = false
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showFilterDialog = false }) { Text("Chiudi") }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Eliminare ${selectedIds.size} vaccin${if (selectedIds.size == 1) "o" else "i"}?") },
            text = { Text("I vaccini verranno rimossi da tutti i dispositivi.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteVaccines(selectedIds)
                        selectedIds = emptySet()
                        isSelecting = false
                        showDeleteConfirm = false
                    },
                ) { Text("Elimina", color = RED) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun VaccineFilterActivePill(
    label: String,
    modifier: Modifier = Modifier,
    onClear: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = kb.card,
        shadowElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Event, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
            Spacer(Modifier.weight(1f))
            Icon(
                Icons.Default.Close,
                contentDescription = "Rimuovi filtro",
                tint = kb.subtitle,
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClear),
            )
        }
    }
}

@Composable
private fun FilterOptionRow(
    label: String,
    value: VaccineListTimeFilter,
    current: VaccineListTimeFilter,
    onPick: () -> Unit,
) {
    TextButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            fontWeight = if (current == value) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Start,
        )
    }
}

private fun LazyListScope.vaccineSectionIos(
    title: String,
    icon: ImageVector,
    iconTint: Color,
    items: List<KBVaccine>,
    isSelecting: Boolean,
    selectedIds: Set<String>,
    onToggleSelect: (String) -> Unit,
    onOpen: (String) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                title.uppercase(Locale.ITALIAN),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = MaterialTheme.kidBoxColors.subtitle,
                letterSpacing = 0.5.sp,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(iconTint.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "${items.size}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = Color.White,
                )
            }
        }
    }
    items(items, key = { it.id }) { vaccine ->
        VaccineRowIos(
            vaccine = vaccine,
            isSelecting = isSelecting,
            isSelected = vaccine.id in selectedIds,
            onToggleSelect = { onToggleSelect(vaccine.id) },
            onOpen = { onOpen(vaccine.id) },
        )
    }
}

private fun vaccineTypeIcon(type: KBVaccineType): ImageVector = when (type) {
    KBVaccineType.ESAVALENTE, KBVaccineType.HPV, KBVaccineType.INFLUENZA -> Icons.Default.Vaccines
    KBVaccineType.PNEUMOCOCCO, KBVaccineType.MENINGOCOCCO_B, KBVaccineType.MENINGOCOCCO_ACWY -> Icons.Outlined.Psychology
    KBVaccineType.MPR -> Icons.Default.Medication
    KBVaccineType.VARICELLA -> Icons.Outlined.Biotech
    KBVaccineType.ALTRO -> Icons.Outlined.LocalPharmacy
}

private fun statusDotColor(status: KBVaccineStatus): Color = when (status) {
    KBVaccineStatus.ADMINISTERED -> GREEN
    KBVaccineStatus.SCHEDULED -> BLUE
    KBVaccineStatus.PLANNED -> ORANGE
    KBVaccineStatus.OVERDUE -> ORANGE
    KBVaccineStatus.SKIPPED -> Color(0xFF757575)
}

@Composable
private fun VaccineRowIos(
    vaccine: KBVaccine,
    isSelecting: Boolean,
    isSelected: Boolean,
    onToggleSelect: () -> Unit,
    onOpen: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val type = KBVaccineType.fromRaw(vaccine.vaccineTypeRaw)
    val status = vaccine.computedStatus()
    val dateMillis = vaccine.administeredDateEpochMillis ?: vaccine.scheduledDateEpochMillis
    val cardBg = kb.card
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(2.dp, RoundedCornerShape(16.dp), clip = false)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .clickable {
                if (isSelecting) onToggleSelect() else onOpen()
            }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (isSelecting) {
            Icon(
                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = null,
                tint = if (isSelected) SALMON else kb.subtitle,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(10.dp))
        }
        Box(
            modifier = Modifier
                .size(46.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(SALMON.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                vaccineTypeIcon(type),
                contentDescription = null,
                tint = SALMON,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                type.displayName,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = kb.title,
            )
            val cn = vaccine.commercialName?.trim().orEmpty()
            if (cn.isNotEmpty()) {
                Text(cn, fontSize = 12.sp, color = kb.subtitle)
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(
                    "Dose ${vaccine.doseNumber}/${vaccine.totalDoses}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    color = Color.White,
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(SALMON.copy(alpha = 0.85f))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
                if (dateMillis != null) {
                    Text(
                        DATE_FMT_VACCINE_ROW.format(Date(dateMillis)),
                        fontSize = 11.sp,
                        color = kb.subtitle,
                    )
                }
            }
        }
        if (!isSelecting) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(statusDotColor(status)),
                )
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = kb.subtitle.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
private fun VaccinesEmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(SALMON.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.Vaccines, contentDescription = null, tint = SALMON, modifier = Modifier.size(36.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            "Libretto vaccinale vuoto",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = kb.title,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Inizia a registrare i vaccini per tenere\ntraccia del calendario vaccinale",
            fontSize = 14.sp,
            color = kb.subtitle,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = SALMON,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onAdd),
        ) {
            Text(
                "Aggiungi vaccino",
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 14.dp),
                color = Color.White,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun VaccinesEmptyFilterState(modifier: Modifier = Modifier, onClearFilter: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Event,
            contentDescription = null,
            tint = kb.subtitle,
            modifier = Modifier.size(40.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Nessun vaccino nel periodo selezionato",
            fontSize = 14.sp,
            color = kb.subtitle,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        TextButton(onClick = onClearFilter) {
            Text("Rimuovi filtro", color = SALMON, fontWeight = FontWeight.Medium)
        }
    }
}