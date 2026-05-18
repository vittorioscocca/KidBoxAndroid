@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import java.util.Calendar
import kotlinx.coroutines.delay
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import it.vittorioscocca.kidbox.data.local.TravelStyle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WizardAccent = Color(0xFFF2611A)
private const val TOTAL_STEPS = 7

@Composable
fun TravelWizardScreen(
    familyId: String,
    prefillDestinationName: String? = null,
    navController: NavHostController,
    onNavigateBack: () -> Unit,
    onOpenPlans: () -> Unit,
    viewModel: TravelPlanningViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    var step by remember { mutableIntStateOf(0) }
    var showProposal by remember { mutableStateOf(false) }
    val children by viewModel.children.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val familyPlan by viewModel.familyPlan.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val aiAvailable = familyPlan.includesAI
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(familyId) {
        viewModel.loadParticipants(familyId)
        viewModel.loadTripStylesFromProfile()
    }
    LaunchedEffect(prefillDestinationName) {
        prefillDestinationName?.let { viewModel.applyPrefill(it) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is TravelPlanningViewModel.Event.PlanReady -> showProposal = true
                is TravelPlanningViewModel.Event.Error ->
                    snackbar.showSnackbar(event.message)
                else -> Unit
            }
        }
    }

    if (showProposal) {
        TravelProposalScreen(
            familyId = familyId,
            navController = navController,
            onOpenPlans = onOpenPlans,
            onNavigateBack = { showProposal = false },
            planningViewModel = viewModel,
        )
        return
    }

    if (isGenerating) {
        TravelPlanningLoadingScreen(
            destinationName = viewModel.destinationName.ifBlank { "il viaggio" },
            subtitle = "Sto finalizzando il percorso di ${viewModel.tripDayCount} giorni",
            plannedDayCount = viewModel.tripDayCount,
        )
        return
    }

    val scrollState = rememberScrollState()

    LaunchedEffect(step, viewModel.usesCustomBudget) {
        if (step == 4 && viewModel.usesCustomBudget) {
            delay(120)
            scrollState.animateScrollTo(scrollState.maxValue)
        }
    }

    Scaffold(
        containerColor = kb.background,
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding(),
        ) {
            WizardHeader(
                step = step,
                onBack = { if (step > 0) step -= 1 else onNavigateBack() },
                onCancel = onNavigateBack,
            )
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp),
            ) {
                Text(stepTitle(step), fontWeight = FontWeight.Bold, fontSize = 28.sp, color = kb.title)
                Text(stepSubtitle(step, viewModel.destinationName), color = kb.subtitle, modifier = Modifier.padding(top = 8.dp, bottom = 20.dp))
                when (step) {
                    0 -> DestinationStep(familyId, viewModel)
                    1 -> DatesStep(viewModel)
                    2 -> TransportStep(viewModel)
                    3 -> ParticipantsStep(viewModel, members, children)
                    4 -> BudgetStep(viewModel, members, children)
                    5 -> TripStyleStep(viewModel)
                    else -> BuildStep(viewModel, members, children, aiAvailable, onOpenPlans)
                }
            }
            // Read selection so Compose recomposes when participants change.
            val selectedParticipantIds = viewModel.selectedParticipantIds
            val canContinue = when (step) {
                3 -> selectedParticipantIds.isNotEmpty()
                else -> viewModel.canProceed(step, members, children)
            } && (step < TOTAL_STEPS - 1 || (aiAvailable && viewModel.canGenerate))
            Button(
                onClick = {
                    viewModel.syncTripFromWizardInputs()
                    if (step < TOTAL_STEPS - 1) {
                        step += 1
                    } else if (aiAvailable) {
                        viewModel.generatePlan()
                    } else {
                        onOpenPlans()
                    }
                },
                enabled = canContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = WizardAccent,
                    disabledContainerColor = kb.subtitle.copy(alpha = 0.25f),
                ),
            ) {
                Text(
                    if (step == TOTAL_STEPS - 1) "Crea il mio piano" else "Continua",
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun WizardHeader(step: Int, onBack: () -> Unit, onCancel: () -> Unit) {
    Column(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) {
                Text(if (step > 0) "Indietro" else "Annulla")
            }
            Spacer(Modifier.weight(1f))
            Text("${step + 1} di $TOTAL_STEPS", fontSize = 14.sp, color = MaterialTheme.kidBoxColors.subtitle)
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.15f)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth((step + 1) / TOTAL_STEPS.toFloat())
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(WizardAccent),
            )
        }
    }
}

private fun stepTitle(step: Int): String = when (step) {
    0 -> "Dove vuoi andare?"
    1 -> "Quando è il tuo viaggio?"
    2 -> "Come ci arriverete?"
    3 -> "Chi viaggia?"
    4 -> "Qual è il tuo budget?"
    5 -> "Qual è il mood di questo viaggio?"
    else -> "Crea il tuo piano"
}

private fun stepSubtitle(step: Int, destination: String): String = when (step) {
    0 -> "Cerca la destinazione del viaggio"
    1 -> "Scegli le date del viaggio"
    2 -> "Scegli come viaggerete"
    3 -> "Definisce tutto il piano"
    4 -> "Costo totale del viaggio"
    5 -> if (destination.isBlank()) "Ogni viaggio è diverso" else "Personalizza per $destination"
    else -> "Verifica e genera con l'AI"
}

@Composable
private fun DestinationStep(familyId: String, vm: TravelPlanningViewModel) {
    TravelDestinationSearchField(
        familyId = familyId,
        destinationName = vm.destinationName,
        destinationRegion = vm.destinationRegion,
        onDestinationNameChange = {
            vm.destinationName = it
            vm.syncTripFromWizardInputs()
        },
        onDestinationRegionChange = { vm.destinationRegion = it },
        onSelectionChanged = { vm.syncTripFromWizardInputs() },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatesStep(vm: TravelPlanningViewModel) {
    var editingDeparture by remember { mutableStateOf(true) }
    val todayStart = remember {
        Calendar.getInstance().apply {
            timeInMillis = System.currentTimeMillis()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        DateSummaryCard(
            label = "PARTENZA",
            dateMillis = vm.startDate,
            modifier = Modifier.weight(1f),
            selected = editingDeparture,
            onClick = { editingDeparture = true },
        )
        DateSummaryCard(
            label = "RITORNO",
            dateMillis = vm.endDate,
            modifier = Modifier.weight(1f),
            selected = !editingDeparture,
            onClick = { editingDeparture = false },
        )
    }

    val activeMillis = if (editingDeparture) vm.startDate else vm.endDate
    val minSelectable = if (editingDeparture) todayStart else vm.startDate

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = activeMillis,
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis >= minSelectable
        },
    )

    LaunchedEffect(editingDeparture, vm.startDate, vm.endDate) {
        datePickerState.selectedDateMillis = if (editingDeparture) vm.startDate else vm.endDate
    }

    LaunchedEffect(datePickerState.selectedDateMillis, editingDeparture) {
        val selected = datePickerState.selectedDateMillis ?: return@LaunchedEffect
        if (editingDeparture) {
            if (selected == vm.startDate) return@LaunchedEffect
            vm.startDate = selected
            if (vm.endDate < selected) vm.endDate = selected
        } else {
            val normalized = maxOf(selected, vm.startDate)
            if (normalized == vm.endDate) return@LaunchedEffect
            vm.endDate = normalized
        }
        vm.syncTripFromWizardInputs()
    }

    DatePicker(
        state = datePickerState,
        modifier = Modifier.fillMaxWidth(),
        showModeToggle = false,
    )
}

@Composable
private fun DateSummaryCard(
    label: String,
    dateMillis: Long,
    modifier: Modifier,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val locale = Locale("it", "IT")
    val dayMonth = remember(dateMillis) {
        SimpleDateFormat("d MMM", locale).format(Date(dateMillis))
    }
    val year = remember(dateMillis) {
        SimpleDateFormat("yyyy", locale).format(Date(dateMillis))
    }
    val weekday = remember(dateMillis) {
        SimpleDateFormat("EEE", locale).format(Date(dateMillis)).lowercase(locale)
    }
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) Color.Black else MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.12f),
        ),
    ) {
        Column(Modifier.padding(14.dp)) {
            Text(label, fontSize = 10.sp, color = MaterialTheme.kidBoxColors.subtitle, fontWeight = FontWeight.SemiBold)
            Text(dayMonth, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = MaterialTheme.kidBoxColors.title)
            Text("$year $weekday", fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
        }
    }
}

@Composable
private fun TransportStep(vm: TravelPlanningViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        WizardPrimaryTransport.entries.forEach { mode ->
            val selected = vm.primaryTransport == mode
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        vm.primaryTransport = mode
                        vm.syncTripFromWizardInputs()
                    },
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, if (selected) WizardAccent else MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.2f)),
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(mode.emoji, fontSize = 28.sp)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(mode.title, fontWeight = FontWeight.SemiBold)
                        Text(mode.subtitle, fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
                    }
                }
            }
        }
    }
}

@Composable
private fun ParticipantsStep(
    vm: TravelPlanningViewModel,
    members: List<it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity>,
    children: List<it.vittorioscocca.kidbox.data.local.entity.KBChildEntity>,
) {
    LaunchedEffect(members, children) {
        if (vm.selectedParticipantIds.isEmpty() && (members.isNotEmpty() || children.isNotEmpty())) {
            vm.selectAllParticipants(children.map { it.id }, members.map { it.userId })
        }
    }
    val lines = vm.participantLines(members, children)
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        lines.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { line ->
                    ParticipantCard(line, selected = line.id in vm.selectedParticipantIds) {
                        vm.toggleParticipant(line.id, !(line.id in vm.selectedParticipantIds))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (lines.isEmpty()) {
            Text("Aggiungi membri della famiglia in Impostazioni.", color = MaterialTheme.kidBoxColors.subtitle)
        }
        if (vm.selectedParticipantIds.isNotEmpty()) {
            val count = vm.selectedParticipantIds.size
            Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(16.dp)) {
                    Text("SELEZIONATI", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.kidBoxColors.subtitle)
                    Text(vm.selectedParticipantSummary(members, children), fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 6.dp))
                    Text(
                        "Perfetto. Pianificheremo per $count ${if (count == 1) "persona" else "persone"}.",
                        fontSize = 14.sp,
                        color = MaterialTheme.kidBoxColors.subtitle,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun RowScope.ParticipantCard(
    line: TravelWizardParticipantLine,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (selected) WizardAccent else MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.2f)),
    ) {
        Box(Modifier.fillMaxWidth()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 18.dp, horizontal = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(line.emoji, fontSize = 32.sp)
                Text(line.name, fontWeight = FontWeight.Bold, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Text(line.ageLabel, fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
            }
            if (selected) {
                Icon(Icons.Filled.CheckCircle, null, tint = WizardAccent, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp))
            }
        }
    }
}

@Composable
private fun BudgetStep(
    vm: TravelPlanningViewModel,
    members: List<it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity>,
    children: List<it.vittorioscocca.kidbox.data.local.entity.KBChildEntity>,
) {
    val symbol = if (vm.currency == "EUR") "€" else "$"
    val presets = TravelWizardBudgetPreset.entries + listOf(null)
    val customBudgetBringIntoView = remember { BringIntoViewRequester() }
    val customBudgetFocusRequester = remember { FocusRequester() }
    var customBudgetFieldFocused by remember { mutableStateOf(false) }

    LaunchedEffect(vm.usesCustomBudget) {
        if (vm.usesCustomBudget) {
            delay(100)
            runCatching { customBudgetFocusRequester.requestFocus() }
        }
    }

    LaunchedEffect(vm.usesCustomBudget, customBudgetFieldFocused) {
        if (vm.usesCustomBudget && customBudgetFieldFocused) {
            delay(250)
            customBudgetBringIntoView.bringIntoView()
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("EUR", "USD").forEach { code ->
                val selected = vm.currency == code
                Surface(
                    modifier = Modifier.clickable {
                        vm.currency = code
                        if (vm.usesCustomBudget) {
                            vm.updateCustomBudget(vm.customBudgetInput)
                        } else {
                            TravelWizardBudgetPreset.entries
                                .firstOrNull { vm.matchesBudgetPreset(it) }
                                ?.let { vm.applyBudgetPreset(it) }
                        }
                    },
                    shape = RoundedCornerShape(20.dp),
                    color = if (selected) WizardAccent else MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(code, color = if (selected) Color.White else MaterialTheme.kidBoxColors.title, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
                }
            }
        }
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${vm.budgetTotal.toInt()} $symbol", fontSize = 36.sp, fontWeight = FontWeight.Bold)
                Text(vm.budgetFootnote(members, children), fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle, modifier = Modifier.padding(top = 8.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
        }
        Text("SELEZIONE RAPIDA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.kidBoxColors.subtitle)
        presets.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { preset ->
                    val selected = if (preset == null) vm.usesCustomBudget else vm.matchesBudgetPreset(preset)
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (preset == null) vm.enableCustomBudget() else vm.applyBudgetPreset(preset)
                            },
                        shape = RoundedCornerShape(12.dp),
                        color = if (selected) Color.Black else MaterialTheme.colorScheme.surfaceVariant,
                    ) {
                        Text(
                            if (preset == null) "Personalizzato" else preset.label(vm.currency),
                            color = if (selected) Color.White else MaterialTheme.kidBoxColors.title,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 12.dp, horizontal = 4.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        if (vm.usesCustomBudget) {
            OutlinedTextField(
                value = vm.customBudgetInput,
                onValueChange = vm::updateCustomBudget,
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(customBudgetFocusRequester)
                    .bringIntoViewRequester(customBudgetBringIntoView)
                    .onFocusEvent { customBudgetFieldFocused = it.isFocused },
                label = { Text("Importo personalizzato") },
                suffix = { Text(symbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        Row(Modifier.fillMaxWidth()) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(56.dp)
                    .background(WizardAccent),
            )
            Column(
                Modifier
                    .weight(1f)
                    .background(Color(0xFFFFF8E1))
                    .padding(12.dp),
            ) {
                Text("Suggerimento", fontWeight = FontWeight.Bold)
                Text(
                    "Il budget include voli, alloggio, cibo, attività e trasporti locali.",
                    fontSize = 14.sp,
                    color = MaterialTheme.kidBoxColors.subtitle,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun TripStyleStep(vm: TravelPlanningViewModel) {
    val styles = listOf(
        TravelStyle.CULTURE, TravelStyle.FOOD, TravelStyle.NIGHTLIFE,
        TravelStyle.ADVENTURE, TravelStyle.RELAXATION, TravelStyle.SHOPPING,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(color = Color(0xFFFFF8E1), shape = RoundedCornerShape(12.dp)) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Info, null, tint = WizardAccent)
                Text("Precompilato dal tuo profilo — modifica quando vuoi.", modifier = Modifier.padding(start = 8.dp), fontSize = 14.sp)
            }
        }
        styles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { style ->
                    val selected = style in vm.tripStyles
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                vm.tripStyles = if (selected) vm.tripStyles - style else vm.tripStyles + style
                            },
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (selected) WizardAccent else MaterialTheme.kidBoxColors.subtitle.copy(alpha = 0.2f)),
                    ) {
                        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(style.emoji, fontSize = 28.sp)
                            Text(style.title, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            Text(style.subtitle, fontSize = 10.sp, color = MaterialTheme.kidBoxColors.subtitle, textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun BuildStep(
    vm: TravelPlanningViewModel,
    members: List<it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity>,
    children: List<it.vittorioscocca.kidbox.data.local.entity.KBChildEntity>,
    aiAvailable: Boolean,
    onOpenPlans: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        summaryLine("Destinazione", vm.destinationName)
        summaryLine("Viaggiatori", vm.selectedParticipantSummary(members, children))
        summaryLine("Budget", "${vm.budgetTotal.toInt()} ${if (vm.currency == "EUR") "€" else "$"}")
        if (!aiAvailable) {
            Text("Richiede piano Pro o Max.", color = kb.subtitle)
            TextButton(onClick = onOpenPlans) { Text("Scopri Pro e Max") }
        } else {
            OutlinedTextField(
                value = vm.freeTextPrompt,
                onValueChange = { vm.freeTextPrompt = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp),
                placeholder = { Text("Note per l'AI (opzionale)") },
            )
            if (!vm.canGenerate) {
                Text(
                    "Completa destinazione, viaggiatori, stili e budget per generare il piano.",
                    color = kb.subtitle,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                val messageCost = TravelPlanningCountdown.messageCost(vm.tripDayCount)
                Text(
                    "Pianificazione AI: ${vm.tripDayCount} giorni · $messageCost messaggi sul limite giornaliero della famiglia.",
                    color = kb.subtitle,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun summaryLine(label: String, value: String) {
    Column {
        Text(label, fontSize = 12.sp, color = MaterialTheme.kidBoxColors.subtitle)
        Text(value.ifBlank { "—" }, fontWeight = FontWeight.Medium)
    }
}
