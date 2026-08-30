package it.vittorioscocca.kidbox.ui.screens.health

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.LocalDrink
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanActivityLevel
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanDocument
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanGoal
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanSection
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val MEAL_PLAN_TINT = Color(0xFF66B880)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealPlanScreen(
    familyId: String,
    childId: String,
    subjectName: String,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    viewModel: MealPlanViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, subjectName) {
        viewModel.bind(familyId, childId, subjectName)
    }
    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
            viewModel.consumeMessage()
        }
    }

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
                    stringResource(R.string.meal_plan_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = kb.title,
                )
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(40.dp))
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
                    IntroCard()
                    DataSourcesCard(state)

                    if (!state.isPaidPlan) {
                        LockedCard(onUpgrade = onUpgrade)
                    } else {
                        PreferencesCard(state, viewModel)
                        CostCard(state)
                        Button(
                            onClick = { viewModel.generate() },
                            enabled = !state.isGenerating && state.hasBodyMetrics,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                stringResource(
                                    if (state.document == null) {
                                        R.string.meal_plan_generate
                                    } else {
                                        R.string.meal_plan_regenerate
                                    },
                                ),
                            )
                        }
                    }

                    state.document?.let { PlanCard(it, onDelete = viewModel::deletePlan) }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        if (state.isGenerating) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Card(shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = kb.card)) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(color = MEAL_PLAN_TINT)
                        Text(
                            stringResource(R.string.meal_plan_generating),
                            color = kb.title,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun IntroCard() {
    val kb = MaterialTheme.kidBoxColors
    PlanCardContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(MEAL_PLAN_TINT.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Restaurant, contentDescription = null, tint = MEAL_PLAN_TINT)
            }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    stringResource(R.string.meal_plan_title),
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = kb.title,
                )
                Text(
                    stringResource(R.string.meal_plan_subtitle),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.meal_plan_intro), fontSize = 14.sp, color = kb.title)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.meal_plan_intro_detail), fontSize = 14.sp, color = kb.subtitle)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.meal_plan_intro_health), fontSize = 14.sp, color = kb.title)
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.meal_plan_disclaimer),
            fontSize = 12.sp,
            color = Color(0xFFE0952F),
        )
    }
}

@Composable
private fun DataSourcesCard(state: MealPlanUiState) {
    val kb = MaterialTheme.kidBoxColors
    val notAvailable = stringResource(R.string.meal_plan_not_available)
    val manualSuffix = stringResource(R.string.meal_plan_manual_suffix)
    val manualAge = state.input.manualAgeValue
    val manualWeight = state.input.manualWeightValue
    val manualHeight = state.input.manualHeightValue
    PlanCardContainer {
        Text(
            stringResource(R.string.meal_plan_data_used),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = kb.title,
        )
        Spacer(Modifier.height(10.dp))

        val rows = listOf(
            Triple(
                stringResource(R.string.meal_plan_data_age),
                state.ageYears?.let { stringResource(R.string.meal_plan_years, it) }
                    ?: manualAge?.let { stringResource(R.string.meal_plan_years, it) + manualSuffix }
                    ?: notAvailable,
                state.ageYears != null || manualAge != null,
            ),
            Triple(
                stringResource(R.string.meal_plan_data_weight),
                state.weightKg?.let { String.format(Locale.getDefault(), "%.1f kg", it) }
                    ?: manualWeight?.let { String.format(Locale.getDefault(), "%.1f kg", it) + manualSuffix }
                    ?: notAvailable,
                state.weightKg != null || manualWeight != null,
            ),
            Triple(
                stringResource(R.string.meal_plan_data_height),
                state.heightCm?.let { "${it.toInt()} cm" }
                    ?: manualHeight?.let { "${it.toInt()} cm" + manualSuffix }
                    ?: notAvailable,
                state.heightCm != null || manualHeight != null,
            ),
            Triple(
                stringResource(R.string.meal_plan_data_workouts),
                state.workoutCount.toString(),
                state.workoutCount > 0,
            ),
            Triple(
                stringResource(R.string.meal_plan_data_visits),
                state.visitCount.toString(),
                state.visitCount > 0,
            ),
            Triple(
                stringResource(R.string.meal_plan_data_exams),
                state.examCount.toString(),
                state.examCount > 0,
            ),
            Triple(
                stringResource(R.string.meal_plan_data_treatments),
                state.activeTreatmentCount.toString(),
                state.activeTreatmentCount > 0,
            ),
        )

        rows.forEach { (label, value, available) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (available) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = null,
                    tint = if (available) MEAL_PLAN_TINT else Color(0xFFE0952F),
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(10.dp))
                Text(label, fontSize = 14.sp, color = kb.title)
                Spacer(Modifier.weight(1f))
                Text(value, fontSize = 12.sp, color = kb.subtitle)
            }
        }

        if (!state.hasBodyMetrics) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.meal_plan_missing_metrics),
                fontSize = 12.sp,
                color = Color(0xFFE0952F),
            )
        }
    }
}

@Composable
private fun LockedCard(onUpgrade: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    PlanCardContainer {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, contentDescription = null, tint = kb.title, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                stringResource(R.string.meal_plan_locked_title),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = kb.title,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.meal_plan_locked_body), fontSize = 14.sp, color = kb.subtitle)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onUpgrade, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.meal_plan_see_plans))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreferencesCard(state: MealPlanUiState, viewModel: MealPlanViewModel) {
    val kb = MaterialTheme.kidBoxColors
    PlanCardContainer {
        Text(
            stringResource(R.string.meal_plan_preferences),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = kb.title,
        )
        Spacer(Modifier.height(10.dp))

        if (state.needsManualMetrics) {
            ManualMetricsSection(state, viewModel)
            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = kb.subtitle.copy(alpha = 0.18f))
            Spacer(Modifier.height(12.dp))
        }

        Text(stringResource(R.string.meal_plan_goal), fontSize = 12.sp, color = kb.subtitle)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MealPlanGoal.entries.forEach { goal ->
                MealPlanChoiceChip(
                    text = stringResource(goal.labelRes),
                    selected = state.input.goal == goal,
                    onClick = { viewModel.updateInput { it.copy(goal = goal) } },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.meal_plan_activity), fontSize = 12.sp, color = kb.subtitle)
        Spacer(Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MealPlanActivityLevel.entries.forEach { level ->
                MealPlanChoiceChip(
                    text = stringResource(level.labelRes),
                    selected = state.input.activityLevel == level,
                    onClick = { viewModel.updateInput { it.copy(activityLevel = level) } },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = state.input.preferredFoods,
            onValueChange = { value -> viewModel.updateInput { it.copy(preferredFoods = value) } },
            label = { Text(stringResource(R.string.meal_plan_preferred_foods)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.input.avoidedFoods,
            onValueChange = { value -> viewModel.updateInput { it.copy(avoidedFoods = value) } },
            label = { Text(stringResource(R.string.meal_plan_avoided_foods)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = state.input.notes,
            onValueChange = { value -> viewModel.updateInput { it.copy(notes = value) } },
            label = { Text(stringResource(R.string.meal_plan_notes)) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun MealPlanChoiceChip(text: String, selected: Boolean, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val background = if (selected) MEAL_PLAN_TINT.copy(alpha = 0.18f) else kb.surfaceOverlay
    val border = if (selected) MEAL_PLAN_TINT else kb.subtitle.copy(alpha = 0.28f)
    val content = if (selected) MEAL_PLAN_TINT else kb.title
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(background)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = content,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun CostCard(state: MealPlanUiState) {
    val kb = MaterialTheme.kidBoxColors
    PlanCardContainer {
        Text(
            stringResource(R.string.meal_plan_cost, state.estimatedUnits),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = kb.title,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.meal_plan_cost_detail), fontSize = 12.sp, color = kb.subtitle)
        state.lastUsage?.let { usage ->
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(
                    R.string.meal_plan_usage_summary,
                    usage.messageUnitsConsumed,
                    usage.usageToday,
                    usage.dailyLimit,
                ),
                fontSize = 12.sp,
                color = MEAL_PLAN_TINT,
            )
        }
    }
}

/** Chiesti solo quando Health Connect non fornisce età, peso o altezza. */
@Composable
private fun ManualMetricsSection(state: MealPlanUiState, viewModel: MealPlanViewModel) {
    val kb = MaterialTheme.kidBoxColors
    Text(
        stringResource(R.string.meal_plan_manual_title),
        fontWeight = FontWeight.SemiBold,
        fontSize = 13.sp,
        color = kb.title,
    )
    Spacer(Modifier.height(4.dp))
    Text(stringResource(R.string.meal_plan_manual_hint), fontSize = 12.sp, color = kb.subtitle)
    Spacer(Modifier.height(8.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        NumberField(
            label = stringResource(R.string.meal_plan_manual_age),
            value = state.input.manualAgeYears,
            onValueChange = { value -> viewModel.updateInput { it.copy(manualAgeYears = value) } },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = stringResource(R.string.meal_plan_manual_weight),
            value = state.input.manualWeightKg,
            onValueChange = { value -> viewModel.updateInput { it.copy(manualWeightKg = value) } },
            modifier = Modifier.weight(1f),
        )
        NumberField(
            label = stringResource(R.string.meal_plan_manual_height),
            value = state.input.manualHeightCm,
            onValueChange = { value -> viewModel.updateInput { it.copy(manualHeightCm = value) } },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { raw -> onValueChange(raw.filter { it.isDigit() || it == '.' || it == ',' }) },
        label = { Text(label, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

/**
 * Il piano non è più un unico blocco: ogni sezione prodotta dall'AI diventa una
 * scheda che si consulta scorrendo in orizzontale.
 */
@Composable
private fun PlanCard(document: MealPlanDocument, onDelete: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val sections = remember(document.text) { document.sections }
    val pagerState = rememberPagerState(pageCount = { sections.size })
    val scope = rememberCoroutineScope()
    val tabScrollState = rememberScrollState()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        PlanCardContainer {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.meal_plan_your_plan),
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = kb.title,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = stringResource(R.string.meal_plan_delete),
                    tint = Color(0xFFD9534F),
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { showDeleteDialog = true },
                )
            }
            Text(
                stringResource(
                    R.string.meal_plan_generated_at,
                    formatDateTime(document.generatedAtEpochMillis),
                ),
                fontSize = 12.sp,
                color = kb.subtitle,
            )
            if (sections.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.SwipeLeft,
                        contentDescription = null,
                        tint = MEAL_PLAN_TINT,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.meal_plan_swipe_hint),
                        fontSize = 12.sp,
                        color = MEAL_PLAN_TINT,
                    )
                }
            }
        }

        if (sections.isEmpty()) {
            PlanCardContainer {
                Text(document.text, fontSize = 14.sp, color = kb.title)
            }
            return@Column
        }

        // Striscia di capsule coi titoli: salta a una scheda senza scorrere le altre.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(tabScrollState),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sections.forEachIndexed { index, section ->
                val selected = index == pagerState.currentPage
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            if (selected) MEAL_PLAN_TINT.copy(alpha = 0.18f) else kb.surfaceOverlay,
                        )
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        sectionIcon(section.title),
                        contentDescription = null,
                        tint = if (selected) MEAL_PLAN_TINT else kb.subtitle,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        sectionTitle(section.title, index),
                        fontSize = 12.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (selected) MEAL_PLAN_TINT else kb.subtitle,
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            pageSpacing = 12.dp,
            modifier = Modifier.height(470.dp),
        ) { page ->
            SectionCard(sections[page], page, sections.size)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            repeat(sections.size) { index ->
                val selected = index == pagerState.currentPage
                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(
                            if (selected) MEAL_PLAN_TINT else kb.subtitle.copy(alpha = 0.28f),
                        )
                        .width(if (selected) 18.dp else 6.dp)
                        .height(6.dp)
                        .clickable { scope.launch { pagerState.animateScrollToPage(index) } },
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.meal_plan_delete_title)) },
            text = { Text(stringResource(R.string.meal_plan_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        onDelete()
                    },
                ) {
                    Text(stringResource(R.string.meal_plan_delete), color = Color(0xFFD9534F))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.meal_plan_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionCard(section: MealPlanSection, index: Int, total: Int) {
    val kb = MaterialTheme.kidBoxColors
    val bodyScroll = rememberScrollState()
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(462.dp)
            .border(1.dp, MEAL_PLAN_TINT.copy(alpha = 0.22f), RoundedCornerShape(20.dp)),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(MEAL_PLAN_TINT.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    sectionIcon(section.title),
                    contentDescription = null,
                    tint = MEAL_PLAN_TINT,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Text(
                sectionTitle(section.title, index),
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = kb.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(MEAL_PLAN_TINT.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    "${index + 1}/$total",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MEAL_PLAN_TINT,
                )
            }
        }

        HorizontalDivider(
            color = kb.subtitle.copy(alpha = 0.18f),
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        Text(
            section.body,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = kb.title,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(bodyScroll)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 20.dp),
        )
    }
}

/** Titolo leggibile: l'AI scrive i titoli in MAIUSCOLO, qui li ammorbidiamo. */
@Composable
private fun sectionTitle(rawTitle: String, index: Int): String {
    val title = rawTitle.trim()
    if (title.isBlank()) return stringResource(R.string.meal_plan_card_index, index + 1)
    return title.lowercase(Locale.getDefault())
        .replaceFirstChar { it.titlecase(Locale.getDefault()) }
}

/** Icona scelta sul titolo di sezione, con fallback neutro. */
private fun sectionIcon(rawTitle: String): ImageVector {
    val title = rawTitle.lowercase(Locale.getDefault())
    val map = listOf(
        listOf("calor", "energ") to Icons.Default.LocalFireDepartment,
        listOf("macro", "protein", "carboidr", "grass") to Icons.Default.PieChart,
        listOf("pasti", "pasto", "meal", "menu") to Icons.Default.Restaurant,
        listOf("idrat", "acqua", "water") to Icons.Default.LocalDrink,
        listOf("spesa", "shopping", "lista") to Icons.Default.ShoppingCart,
        listOf("90", "giorni", "progress", "piano") to Icons.Default.CalendarMonth,
        listOf("salute", "note", "health", "clinic") to Icons.Default.FavoriteBorder,
        listOf("allenam", "workout", "training") to Icons.Default.DirectionsRun,
    )
    return map.firstOrNull { (keys, _) -> keys.any { title.contains(it) } }?.second
        ?: Icons.Default.FormatListBulleted
}

@Composable
private fun PlanCardContainer(content: @Composable () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = kb.card),
    ) {
        Column(modifier = Modifier.padding(16.dp)) { content() }
    }
}

private fun formatDateTime(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(Date(epochMillis))
