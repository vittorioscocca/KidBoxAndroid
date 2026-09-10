package it.vittorioscocca.kidbox.ui.screens.health

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Settings
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.health.connect.client.PermissionController
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDatePickerState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.health.fitness.FitnessCompletionSource
import it.vittorioscocca.kidbox.data.health.fitness.FitnessDistanceFormatter
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDates
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDocument
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSession
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSessionStatus
import it.vittorioscocca.kidbox.data.health.fitness.FitnessWeeklyReport
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale

/** Tinta del modulo Piano Fitness (stessa della card in Salute). */
internal val FITNESS_TINT = Color(0xFF5A9EE0)

/** Arancione dell'AI, lo stesso del FAB usato in tutta l'app. */
private val AI_ORANGE = Color(0xFFFF6B00)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FitnessPlanScreen(
    familyId: String,
    childId: String,
    subjectName: String,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    onOpenCopilot: () -> Unit,
    viewModel: FitnessPlanViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    var showSetup by remember { mutableStateOf(false) }
    var setupMode by remember { mutableStateOf(FitnessSetupMode.ONBOARDING) }
    var pendingInput by remember { mutableStateOf<it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanInput?>(null) }
    var sessionToMove by remember { mutableStateOf<FitnessSession?>(null) }
    var showSessions by remember { mutableStateOf(false) }

    LaunchedEffect(familyId, childId, subjectName) {
        viewModel.bind(familyId, childId, subjectName)
    }
    LaunchedEffect(state.message) {
        state.message?.let { message ->
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

    if (showSetup) {
        FitnessPlanSetupScreen(
            mode = setupMode,
            initialInput = state.input,
            estimatedUnits = state.estimatedUnits,
            needsManualMetrics = state.needsManualMetrics,
            plan = if (setupMode == FitnessSetupMode.SETTINGS) state.plan else null,
            usageSummary = state.lastUsage?.let {
                stringResource(
                    R.string.fitness_usage_summary,
                    it.messageUnitsConsumed,
                    it.usageToday,
                    it.dailyLimit,
                )
            },
            onConfirm = { confirmed ->
                showSetup = false
                pendingInput = confirmed
            },
            onDelete = { viewModel.deletePlan() },
            onBack = { showSetup = false },
        )
        return
    }

    val planForSessions = state.plan
    if (showSessions && planForSessions != null) {
        FitnessSessionsScreen(
            plan = planForSessions,
            onBack = { showSessions = false },
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(kb.background)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KidBoxHeaderCircleButton(
                    icon = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = stringResource(R.string.health_back),
                    onClick = onBack,
                )
                Spacer(Modifier.weight(1f))
                Text(
                    stringResource(R.string.fitness_plan_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = kb.title,
                )
                Spacer(Modifier.weight(1f))
                if (state.plan != null && state.isPaidPlan) {
                    KidBoxHeaderCircleButton(
                        icon = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.fitness_settings_title),
                        onClick = {
                            setupMode = FitnessSetupMode.SETTINGS
                            showSetup = true
                        },
                    )
                } else {
                    Spacer(Modifier.width(40.dp))
                }
            }

            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = { viewModel.forceRefresh() },
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    state.banner?.let { BannerCard(it) { viewModel.dismissBanner() } }

                    val plan = state.plan
                    if (!state.isPaidPlan) {
                        IntroCard()
                        // Anche senza piano a pagamento si vede quali dati di
                        // salute alimentano l'allenamento: la lettura da Health
                        // Connect non è ciò che si compra.
                        DataSourcesCard(state)
                        LockedCard(onUpgrade)
                    } else if (plan != null) {
                        state.weeklyReport?.let { report ->
                            WeeklyReportCard(state, report, viewModel)
                        }
                        CalendarCard(state, viewModel) { showSessions = true }
                        DayDetailCard(state, viewModel) { sessionToMove = it }
                        HealthSyncCard(state, viewModel)
                    } else {
                        IntroCard()
                        DataSourcesCard(state)
                        SetupCard(state.estimatedUnits) {
                            setupMode = FitnessSetupMode.ONBOARDING
                            showSetup = true
                        }
                    }
                    Spacer(Modifier.height(90.dp))
                }
            }
        }

        if (state.plan != null && state.isPaidPlan) {
            // Stesso FAB arancione di tutte le altre entrate AI dell'app.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 28.dp)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(AI_ORANGE)
                    .clickable { onOpenCopilot() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = stringResource(R.string.fitness_copilot_open),
                    tint = Color.White,
                )
            }
        }

        if (state.isGenerating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = kb.card),
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = FITNESS_TINT)
                        Text(
                            stringResource(state.generatingMessageRes),
                            color = kb.title,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }

    pendingInput?.let { input ->
        AlertDialog(
            onDismissRequest = { pendingInput = null },
            title = { Text(stringResource(R.string.fitness_generate_confirm_title)) },
            text = {
                Text(stringResource(R.string.fitness_generate_confirm_body, state.estimatedUnits))
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingInput = null
                    viewModel.generate(input)
                }) { Text(stringResource(R.string.fitness_generate)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingInput = null }) {
                    Text(stringResource(R.string.meal_plan_delete_cancel))
                }
            },
        )
    }

    sessionToMove?.let { session ->
        MoveSessionDialog(
            session = session,
            onDismiss = { sessionToMove = null },
            onMove = { newDate ->
                sessionToMove = null
                viewModel.moveSession(session.id, newDate)
            },
        )
    }
}

// ── Storico sessioni ───────────────────────────────────────────────────────

/**
 * Accesso all'elenco delle sedute già svolte. Sta sotto il calendario perché è
 * la domanda che viene dopo "cosa devo fare oggi": cosa ho fatto finora.
 */
@Composable
private fun SessionsButton(plan: FitnessPlanDocument, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val done = plan.allSessions.count { it.status == FitnessSessionStatus.DONE }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(FITNESS_TINT.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Default.FormatListBulleted, contentDescription = null, tint = FITNESS_TINT)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                stringResource(R.string.fitness_sessions_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                color = kb.title,
            )
            Text(
                stringResource(R.string.fitness_sessions_recorded, done),
                fontSize = 14.sp,
                color = kb.subtitle,
            )
        }
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = kb.subtitle,
        )
    }
}

// ── Introduzione e paywall ─────────────────────────────────────────────────

@Composable
private fun IntroCard() {
    val kb = MaterialTheme.kidBoxColors
    FitnessCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(FITNESS_TINT.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = FITNESS_TINT)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.fitness_plan_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = kb.title,
                )
                Text(stringResource(R.string.fitness_subtitle), fontSize = 13.sp, color = kb.subtitle)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.fitness_intro), fontSize = 15.sp, color = kb.title)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.fitness_intro_detail), fontSize = 15.sp, color = kb.subtitle)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.fitness_intro_safety), fontSize = 15.sp, color = kb.title)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.fitness_disclaimer),
            fontSize = 13.sp,
            color = Color(0xFFE0952F),
        )
    }
}

@Composable
private fun LockedCard(onUpgrade: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    FitnessCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = kb.title)
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.meal_plan_locked_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = kb.title,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.fitness_locked_body), fontSize = 15.sp, color = kb.subtitle)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onUpgrade,
            colors = ButtonDefaults.buttonColors(containerColor = FITNESS_TINT),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.meal_plan_see_plans)) }
    }
}

@Composable
private fun DataSourcesCard(state: FitnessPlanUiState) {
    FitnessCard {
        PlanDataSourcesContent(
            data = state.toPlanDataSources(),
            tint = FITNESS_TINT,
            missingMetricsText = stringResource(R.string.fitness_missing_metrics),
        )
    }
}

private fun FitnessPlanUiState.toPlanDataSources() = PlanDataSources(
    ageYears = ageYears,
    manualAgeYears = input.manualAgeValue,
    weightKg = weightKg,
    manualWeightKg = input.manualWeightValue,
    heightCm = heightCm,
    manualHeightCm = input.manualHeightValue,
    workoutCount = workoutCount,
    activeEnergyKcal = activeEnergyKcal,
    workoutDistanceMeters = workoutDistanceMeters,
    visitCount = visitCount,
    examCount = examCount,
    activeTreatmentCount = activeTreatmentCount,
    hasBodyMetrics = hasBodyMetrics,
)

@Composable
private fun SetupCard(estimatedUnits: Int, onSetup: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    FitnessCard {
        Text(
            stringResource(R.string.fitness_cost, estimatedUnits),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = kb.title,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.fitness_cost_detail), fontSize = 13.sp, color = kb.subtitle)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onSetup,
            colors = ButtonDefaults.buttonColors(containerColor = FITNESS_TINT),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.fitness_setup_cta)) }
    }
}

// ── Calendario mensile ─────────────────────────────────────────────────────

@Composable
private fun CalendarCard(
    state: FitnessPlanUiState,
    viewModel: FitnessPlanViewModel,
    onOpenSessions: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val plan = state.plan ?: return

    FitnessCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.fitness_calendar),
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = kb.title,
                modifier = Modifier.weight(1f),
            )
            plan.weekIndexFor(state.selectedDayEpochMillis)?.let { weekIndex ->
                Text(
                    stringResource(R.string.fitness_week, weekIndex),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FITNESS_TINT,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(FITNESS_TINT.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        MonthHeader(state, viewModel)
        Spacer(Modifier.height(8.dp))
        WeekdayHeader()
        Spacer(Modifier.height(4.dp))
        MonthGrid(state, viewModel)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf(
                FitnessSessionStatus.DONE,
                FitnessSessionStatus.PLANNED,
                FitnessSessionStatus.SKIPPED,
            ).forEach { status ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        statusIcon(status),
                        contentDescription = null,
                        tint = statusColor(status, kb.subtitle),
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(status.labelRes), fontSize = 12.sp, color = kb.subtitle)
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
        Spacer(Modifier.height(4.dp))
        SessionsButton(plan, onOpenSessions)
    }
}

@Composable
private fun MonthHeader(state: FitnessPlanUiState, viewModel: FitnessPlanViewModel) {
    val kb = MaterialTheme.kidBoxColors
    val plan = state.plan ?: return
    val firstMonth = FitnessPlanDates.startOfMonth(plan.startDateEpochMillis)
    val lastMonth = FitnessPlanDates.startOfMonth(
        plan.allSessions.lastOrNull()?.dateEpochMillis ?: plan.startDateEpochMillis,
    )
    val current = state.displayedMonthEpochMillis
    val canGoBack = current > firstMonth
    val canGoForward = current < lastMonth

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(
            Icons.Default.KeyboardArrowLeft,
            contentDescription = null,
            tint = if (canGoBack) FITNESS_TINT else kb.subtitle.copy(alpha = 0.35f),
            modifier = Modifier
                .size(28.dp)
                .clickable(enabled = canGoBack) { viewModel.showMonth(addMonths(current, -1)) },
        )
        Text(
            monthTitle(current),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = kb.title,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = if (canGoForward) FITNESS_TINT else kb.subtitle.copy(alpha = 0.35f),
            modifier = Modifier
                .size(28.dp)
                .clickable(enabled = canGoForward) { viewModel.showMonth(addMonths(current, 1)) },
        )
    }
}

@Composable
private fun WeekdayHeader() {
    val kb = MaterialTheme.kidBoxColors
    val first = Calendar.getInstance().firstDayOfWeek
    val symbols = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
    Row(modifier = Modifier.fillMaxWidth()) {
        (0 until 7).forEach { offset ->
            val weekday = ((first - 1 + offset) % 7) + 1
            Text(
                symbols.getOrNull(weekday).orEmpty().take(2).uppercase(Locale.getDefault()),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = kb.subtitle,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

/**
 * Griglia del mese: le celle vuote iniziali allineano il primo giorno alla sua
 * colonna, i giorni fuori dal piano restano visibili ma spenti.
 */
@Composable
private fun MonthGrid(state: FitnessPlanUiState, viewModel: FitnessPlanViewModel) {
    val plan = state.plan ?: return
    val month = Calendar.getInstance().apply { timeInMillis = state.displayedMonthEpochMillis }
    val daysInMonth = month.getActualMaximum(Calendar.DAY_OF_MONTH)
    val firstWeekday = month.get(Calendar.DAY_OF_WEEK)
    val leading = (firstWeekday - Calendar.getInstance().firstDayOfWeek + 7) % 7
    val cells: List<Long?> = List(leading) { null } +
        (0 until daysInMonth).map { FitnessPlanDates.plusDays(state.displayedMonthEpochMillis, it) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(7).forEach { week ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                week.forEach { day ->
                    if (day == null) {
                        Spacer(Modifier.weight(1f).height(54.dp))
                    } else {
                        DayCell(day, plan, state, Modifier.weight(1f)) { viewModel.selectDay(day) }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f).height(54.dp)) }
            }
        }
    }
}

@Composable
private fun DayCell(
    dayEpochMillis: Long,
    plan: FitnessPlanDocument,
    state: FitnessPlanUiState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val sessions = plan.sessionsOn(dayEpochMillis)
    val isSelected = FitnessPlanDates.startOfDay(dayEpochMillis) ==
        FitnessPlanDates.startOfDay(state.selectedDayEpochMillis)
    val isToday = FitnessPlanDates.startOfDay(dayEpochMillis) == FitnessPlanDates.today()
    val inPlan = plan.weekIndexFor(dayEpochMillis) != null
    val status = sessions.firstOrNull()?.status
    val dayNumber = Calendar.getInstance().apply { timeInMillis = dayEpochMillis }
        .get(Calendar.DAY_OF_MONTH)

    Column(
        modifier = modifier
            .height(54.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                when {
                    isSelected -> FITNESS_TINT
                    sessions.isEmpty() -> Color.Transparent
                    else -> kb.subtitle.copy(alpha = 0.08f)
                },
            )
            .border(
                width = if (isToday && !isSelected) 1.5.dp else 0.dp,
                color = if (isToday && !isSelected) FITNESS_TINT else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            dayNumber.toString(),
            fontSize = 15.sp,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
            color = when {
                isSelected -> Color.White
                inPlan -> kb.title
                else -> kb.subtitle.copy(alpha = 0.45f)
            },
        )
        Spacer(Modifier.height(2.dp))
        if (status != null) {
            Icon(
                statusIcon(status),
                contentDescription = null,
                tint = if (isSelected) Color.White else statusColor(status, kb.subtitle),
                modifier = Modifier.size(14.dp),
            )
        } else {
            Spacer(Modifier.height(14.dp))
        }
    }
}

// ── Dettaglio giornata ─────────────────────────────────────────────────────

@Composable
private fun DayDetailCard(
    state: FitnessPlanUiState,
    viewModel: FitnessPlanViewModel,
    onMove: (FitnessSession) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val sessions = state.sessionsOfSelectedDay

    FitnessCard {
        Text(
            formatDate(state.selectedDayEpochMillis),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = kb.title,
        )
        Spacer(Modifier.height(10.dp))
        if (sessions.isEmpty()) {
            // Fuori dal piano non è riposo: il piano semplicemente non copre
            // quel giorno, e dirlo evita di far sembrare vuoto un mese intero.
            Text(
                stringResource(
                    if (state.selectedDayInPlan) {
                        R.string.fitness_rest_day
                    } else {
                        R.string.fitness_out_of_plan
                    },
                ),
                fontSize = 15.sp,
                color = kb.subtitle,
            )
        } else {
            sessions.forEach { session ->
                SessionCard(session, viewModel, onMove)
                Spacer(Modifier.height(10.dp))
            }
        }

        val logged = state.plan?.loggedWorkoutsOn(state.selectedDayEpochMillis).orEmpty()
        if (logged.isNotEmpty()) {
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.fitness_logged_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = kb.title,
            )
            Text(
                stringResource(R.string.fitness_logged_hint),
                fontSize = 13.sp,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(6.dp))
            logged.forEach { workout ->
                LoggedWorkoutRow(
                    workout = workout,
                    openSessions = sessions.filter { it.status != FitnessSessionStatus.DONE },
                    viewModel = viewModel,
                )
            }
        }
    }
}

/**
 * Riga di un allenamento svolto fuori programma, con la possibilità di farlo
 * valere come una delle sedute aperte della giornata.
 */
@Composable
private fun LoggedWorkoutRow(
    workout: it.vittorioscocca.kidbox.data.health.fitness.FitnessLoggedWorkout,
    openSessions: List<FitnessSession>,
    viewModel: FitnessPlanViewModel,
) {
    val kb = MaterialTheme.kidBoxColors
    var showMenu by remember { mutableStateOf(false) }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(FITNESS_TINT.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.DirectionsRun,
                contentDescription = null,
                tint = FITNESS_TINT,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(workout.title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = kb.title)
            val parts = buildList {
                workout.durationMinutes?.let {
                    add(stringResource(R.string.fitness_session_minutes, it))
                }
                workout.kcal?.let { add("$it kcal") }
                FitnessDistanceFormatter.kilometers(workout.distanceMeters)?.let { add(it) }
                workout.heartRateBpm?.let { add("$it bpm") }
            }
            if (parts.isNotEmpty()) {
                Text(parts.joinToString(" · "), fontSize = 13.sp, color = kb.subtitle)
            }
        }
        if (openSessions.isNotEmpty()) {
            Box {
                Text(
                    stringResource(R.string.fitness_count_as),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = FITNESS_TINT,
                    modifier = Modifier.clickable { showMenu = true },
                )
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    openSessions.forEach { session ->
                        DropdownMenuItem(
                            text = { Text(session.title) },
                            onClick = {
                                showMenu = false
                                viewModel.countLoggedWorkout(workout, session.id)
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SessionCard(
    session: FitnessSession,
    viewModel: FitnessPlanViewModel,
    onMove: (FitnessSession) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var showEditor by remember { mutableStateOf(false) }

    if (showEditor) {
        SessionEditDialog(
            session = session,
            onDismiss = { showEditor = false },
            onSave = { status, title, minutes, kcal ->
                showEditor = false
                viewModel.applyManualEdit(session.id, status, title, minutes, kcal)
            },
        )
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(kb.subtitle.copy(alpha = 0.06f))
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(FITNESS_TINT.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = FITNESS_TINT)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                // A seduta chiusa comanda quello che è stato fatto: il titolo
                // grande è l'attività reale, con i suoi numeri. Il programma
                // scende a riga di contorno.
                Text(
                    headlineTitle(session),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = kb.title,
                )
                Text(
                    headlineSubtitle(session),
                    fontSize = 13.sp,
                    color = if (session.status == FitnessSessionStatus.DONE) {
                        statusColor(session.status, kb.subtitle)
                    } else {
                        kb.subtitle
                    },
                )
                if (session.status == FitnessSessionStatus.DONE) {
                    Text(plannedFootnote(session), fontSize = 12.sp, color = kb.subtitle)
                }
            }
            Icon(
                statusIcon(session.status),
                contentDescription = stringResource(session.status.labelRes),
                tint = statusColor(session.status, kb.subtitle),
            )
        }

        if (session.status == FitnessSessionStatus.DONE &&
            (session.exercises.isNotEmpty() || session.targets.isNotEmpty())
        ) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.fitness_planned_program),
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = kb.subtitle,
            )
        }

        if (session.exercises.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            session.exercises.forEach { exercise ->
                Text("• ${exercise.name}", fontSize = 15.sp, color = kb.title)
                if (exercise.detail.isNotBlank()) {
                    Text(
                        exercise.detail,
                        fontSize = 13.sp,
                        color = kb.subtitle,
                        modifier = Modifier.padding(start = 14.dp),
                    )
                }
            }
        }

        if (session.targets.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.fitness_targets),
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                color = kb.title,
            )
            session.targets.forEach { target ->
                Text("– $target", fontSize = 14.sp, color = kb.subtitle)
            }
        }

        session.notes?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, fontSize = 13.sp, color = kb.subtitle)
        }

        if (session.wasSubstituted && session.actualActivityTitle != null) {
            // Il piano diceva un'altra cosa: dirlo, invece di far finta che la
            // seduta programmata sia stata svolta.
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.fitness_done_different, session.actualActivityTitle!!),
                fontSize = 13.sp,
                color = Color(0xFFF2AD40),
            )
        }

        Spacer(Modifier.height(12.dp))
        if (session.status == FitnessSessionStatus.DONE) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = null,
                    tint = statusColor(session.status, kb.subtitle),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(completionText(session), fontSize = 13.sp, color = kb.subtitle)
                Spacer(Modifier.weight(1f))
                TextButton(onClick = {
                    viewModel.markSession(session.id, FitnessSessionStatus.PLANNED)
                }) { Text(stringResource(R.string.fitness_undo), color = FITNESS_TINT) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { viewModel.markSession(session.id, FitnessSessionStatus.DONE) },
                    colors = ButtonDefaults.buttonColors(containerColor = FITNESS_TINT),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.fitness_action_done)) }
                OutlinedButton(onClick = { onMove(session) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.fitness_action_move))
                }
                OutlinedButton(onClick = {
                    viewModel.markSession(session.id, FitnessSessionStatus.SKIPPED)
                }) { Text(stringResource(R.string.fitness_action_skip)) }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.fitness_edit_session),
            fontSize = 13.sp,
            color = FITNESS_TINT,
            modifier = Modifier.clickable { showEditor = true },
        )
    }
}

/** Il titolo grande: l'attività svolta se c'è, altrimenti quella prevista. */
@Composable
private fun headlineTitle(session: FitnessSession): String {
    val actual = session.actualActivityTitle?.takeIf { it.isNotBlank() }
    return if (session.status == FitnessSessionStatus.DONE && actual != null) actual else session.title
}

/** Numeri in evidenza: quelli reali a seduta chiusa, quelli previsti prima. */
@Composable
private fun headlineSubtitle(session: FitnessSession): String {
    if (session.status != FitnessSessionStatus.DONE) return sessionSubtitle(session)
    val parts = mutableListOf<String>()
    parts += stringResource(
        R.string.fitness_session_minutes,
        session.actualMinutes ?: session.durationMinutes,
    )
    // Con la seduta chiusa da Health Connect le calorie sono il dato che dice
    // quanto è costata davvero: se mancano si dice che mancano, altrimenti
    // l'unico «kcal» a schermo resta quello previsto dall'AI nella riga sotto,
    // e sembra che le calorie lette non servano a niente.
    val actualKcal = session.actualKcal
    when {
        actualKcal != null -> parts += "$actualKcal kcal"
        session.completionSource == FitnessCompletionSource.HEALTH_CONNECT ->
            parts += stringResource(R.string.fitness_session_kcal_missing)
        else -> Unit
    }
    FitnessDistanceFormatter.kilometers(session.actualDistanceMeters)?.let { parts += it }
    session.actualHeartRateBpm?.let { parts += "$it bpm" }
    return parts.joinToString(" · ")
}

/** Il programma previsto, ridotto a nota quando la seduta è chiusa. */
@Composable
private fun plannedFootnote(session: FitnessSession): String =
    stringResource(R.string.fitness_planned_recap, session.title, sessionSubtitle(session))

@Composable
private fun completionText(session: FitnessSession): String = completionSourceText(session)

@Composable
private fun completionSourceText(session: FitnessSession): String = when (session.completionSource) {
    FitnessCompletionSource.HEALTH_CONNECT -> stringResource(R.string.fitness_done_health)
    FitnessCompletionSource.NOTIFICATION -> stringResource(R.string.fitness_done_notification)
    else -> stringResource(R.string.fitness_done_manual)
}

@Composable
private fun sessionSubtitle(session: FitnessSession): String {
    val parts = mutableListOf<String>()
    if (session.durationMinutes > 0) {
        parts += stringResource(R.string.fitness_session_minutes, session.durationMinutes)
    }
    if (session.intensity.isNotBlank()) parts += session.intensity
    session.targetKcal?.let { parts += "$it kcal" }
    return parts.joinToString(" · ")
}

/**
 * Editor della singola seduta: stato, attività realmente svolta, durata e
 * calorie effettive.
 *
 * Serve quando l'orologio non ha registrato nulla, o quando ha registrato una
 * cosa diversa da quella prevista: l'ultima parola su cosa è stato fatto resta
 * della persona, non della riconciliazione automatica.
 */
@Composable
private fun SessionEditDialog(
    session: FitnessSession,
    onDismiss: () -> Unit,
    onSave: (FitnessSessionStatus, String?, Int?, Int?) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var status by remember { mutableStateOf(session.status) }
    var activityTitle by remember { mutableStateOf(session.actualActivityTitle.orEmpty()) }
    var minutes by remember { mutableStateOf(session.actualMinutes?.toString().orEmpty()) }
    var kcal by remember { mutableStateOf(session.actualKcal?.toString().orEmpty()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.fitness_edit_session_title)) },
        text = {
            Column {
                Text(session.title, fontSize = 13.sp, color = kb.subtitle)
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.fitness_edit_status),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.title,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        FitnessSessionStatus.PLANNED,
                        FitnessSessionStatus.DONE,
                        FitnessSessionStatus.SKIPPED,
                    ).forEach { value ->
                        FitnessChip(stringResource(value.labelRes), status == value) { status = value }
                    }
                }
                if (status == FitnessSessionStatus.DONE) {
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = activityTitle,
                        onValueChange = { activityTitle = it },
                        label = { Text(stringResource(R.string.fitness_edit_activity)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = minutes,
                            onValueChange = { minutes = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.fitness_edit_minutes)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = kcal,
                            onValueChange = { kcal = it.filter(Char::isDigit) },
                            label = { Text(stringResource(R.string.fitness_edit_kcal)) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.fitness_edit_hint),
                        fontSize = 12.sp,
                        color = kb.subtitle,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    status,
                    activityTitle.takeIf { it.isNotBlank() },
                    minutes.toIntOrNull(),
                    kcal.toIntOrNull(),
                )
            }) { Text(stringResource(R.string.fitness_edit_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.meal_plan_delete_cancel))
            }
        },
    )
}

// ── Health Connect ─────────────────────────────────────────────────────────

@Composable
private fun HealthSyncCard(state: FitnessPlanUiState, viewModel: FitnessPlanViewModel) {
    val kb = MaterialTheme.kidBoxColors
    FitnessCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Favorite, contentDescription = null, tint = FITNESS_TINT)
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.fitness_health_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = kb.title,
                )
                Text(
                    state.lastHealthSyncEpochMillis?.let {
                        stringResource(R.string.fitness_health_last, formatDateTimeFitness(it))
                    } ?: stringResource(R.string.fitness_health_never),
                    fontSize = 13.sp,
                    color = kb.subtitle,
                )
            }
            if (state.isSyncingHealth) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp, color = FITNESS_TINT)
            } else {
                val permissionLauncher = rememberLauncherForActivityResult(
                    PermissionController.createRequestPermissionResultContract(),
                ) { viewModel.syncHealthNow() }
                OutlinedButton(
                    onClick = { permissionLauncher.launch(viewModel.healthPermissions) },
                    enabled = state.healthConnectAvailable,
                ) { Text(stringResource(R.string.fitness_sync_now), fontSize = 13.sp) }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(
                if (state.healthConnectAvailable) {
                    R.string.fitness_health_detail
                } else {
                    R.string.fitness_health_unavailable
                },
            ),
            fontSize = 13.sp,
            color = kb.subtitle,
        )
    }
}

// ── Report settimanale ─────────────────────────────────────────────────────

@Composable
private fun WeeklyReportCard(
    state: FitnessPlanUiState,
    report: FitnessWeeklyReport,
    viewModel: FitnessPlanViewModel,
) {
    val kb = MaterialTheme.kidBoxColors
    FitnessCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = FITNESS_TINT)
            Spacer(Modifier.width(10.dp))
            Text(
                stringResource(R.string.fitness_report_title, report.weekIndex),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = kb.title,
                modifier = Modifier.weight(1f),
            )
            Text(
                "${report.completionPercent}%",
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = FITNESS_TINT,
            )
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { report.completionRate },
            color = FITNESS_TINT,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(report.headlineRes, report.completionPercent),
            fontSize = 15.sp,
            color = kb.title,
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            ReportMetric(
                stringResource(R.string.fitness_report_completed),
                "${report.completedSessions}/${report.plannedSessions}",
            )
            ReportMetric(stringResource(R.string.fitness_report_minutes), "${report.totalMinutes}")
            // Zero è un risultato, non un motivo per nascondere la metrica.
            ReportMetric(
                stringResource(R.string.fitness_sessions_distance),
                FitnessDistanceFormatter.kilometers(report.totalDistanceMeters) ?: "—",
            )
            ReportMetric(stringResource(R.string.fitness_report_kcal), "${report.totalKcal}")
        }

        val proposal = state.adjustmentProposal
        Spacer(Modifier.height(12.dp))
        if (proposal != null && proposal.weekIndex == report.weekIndex + 1) {
            HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.fitness_proposal_title),
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                color = kb.title,
            )
            if (proposal.rationale.isNotBlank()) {
                Text(proposal.rationale, fontSize = 13.sp, color = kb.subtitle)
            }
            proposal.changes.forEach { change ->
                Text("• $change", fontSize = 13.sp, color = kb.subtitle)
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.keepCurrentPlan() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.fitness_keep_plan))
                }
                Button(
                    onClick = { viewModel.applyProposal() },
                    enabled = proposal.updatedSessions.isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(containerColor = FITNESS_TINT),
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.fitness_apply_changes)) }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.keepCurrentPlan() }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.fitness_keep_plan))
                }
                Button(
                    onClick = { viewModel.askWeeklyAdjustment() },
                    enabled = !state.isAdjusting,
                    colors = ButtonDefaults.buttonColors(containerColor = FITNESS_TINT),
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isAdjusting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
                        )
                    } else {
                        Text(stringResource(R.string.fitness_ask_adjustment))
                    }
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.fitness_adjust_cost), fontSize = 12.sp, color = kb.subtitle)
        }
    }
}

@Composable
private fun ReportMetric(label: String, value: String) {
    val kb = MaterialTheme.kidBoxColors
    Column {
        Text(value, fontWeight = FontWeight.Bold, fontSize = 17.sp, color = kb.title)
        Text(label, fontSize = 12.sp, color = kb.subtitle)
    }
}

@Composable
private fun BannerCard(text: String, onDismiss: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    FitnessCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = FITNESS_TINT)
            Spacer(Modifier.width(10.dp))
            Text(text, fontSize = 14.sp, color = kb.title, modifier = Modifier.weight(1f))
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = kb.subtitle,
                modifier = Modifier.size(18.dp).clickable { onDismiss() },
            )
        }
    }
}

// ── Sposta seduta ──────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MoveSessionDialog(
    session: FitnessSession,
    onDismiss: () -> Unit,
    onMove: (Long) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = FitnessPlanDates.plusDays(session.dateEpochMillis, 1),
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                pickerState.selectedDateMillis?.let { onMove(FitnessPlanDates.startOfDay(it)) }
            }) { Text(stringResource(R.string.fitness_move_cta)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.meal_plan_delete_cancel))
            }
        },
    ) {
        Column {
            Text(
                stringResource(R.string.fitness_move_title),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp),
            )
            Text(
                stringResource(R.string.fitness_move_current, formatDate(session.dateEpochMillis)),
                fontSize = 13.sp,
                modifier = Modifier.padding(start = 24.dp, top = 4.dp),
            )
            DatePicker(state = pickerState)
            Text(
                stringResource(R.string.fitness_move_hint),
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }
    }
}

// ── Utility ────────────────────────────────────────────────────────────────

private fun statusIcon(status: FitnessSessionStatus) = when (status) {
    FitnessSessionStatus.DONE -> Icons.Default.CheckCircle
    FitnessSessionStatus.SKIPPED -> Icons.Default.Close
    FitnessSessionStatus.MOVED -> Icons.Default.KeyboardArrowRight
    FitnessSessionStatus.PLANNED -> Icons.Default.RadioButtonUnchecked
}

private fun statusColor(status: FitnessSessionStatus, planned: Color) = when (status) {
    FitnessSessionStatus.DONE -> Color(0xFF4CB870)
    FitnessSessionStatus.SKIPPED -> Color(0xFFE56B59)
    FitnessSessionStatus.MOVED -> Color(0xFFF2AD40)
    FitnessSessionStatus.PLANNED -> planned
}

private fun addMonths(epochMillis: Long, months: Int): Long = Calendar.getInstance().apply {
    timeInMillis = epochMillis
    add(Calendar.MONTH, months)
}.timeInMillis

/** "Settembre 2026", con l'iniziale maiuscola anche dove il locale non la mette. */
private fun monthTitle(epochMillis: Long): String {
    val formatter = java.text.SimpleDateFormat("LLLL yyyy", Locale.getDefault())
    val text = formatter.format(java.util.Date(epochMillis))
    return text.replaceFirstChar { it.uppercase(Locale.getDefault()) }
}
