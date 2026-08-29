package it.vittorioscocca.kidbox.ui.screens.health

import android.widget.Toast
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanActivityLevel
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanGoal
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale

private val MEAL_PLAN_TINT = Color(0xFF66B880)

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

                state.document?.let { PlanCard(it.text, it.generatedAtEpochMillis) }
                Spacer(Modifier.height(24.dp))
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
                state.ageYears?.let { stringResource(R.string.meal_plan_years, it) } ?: notAvailable,
                state.ageYears != null,
            ),
            Triple(
                stringResource(R.string.meal_plan_data_weight),
                state.weightKg?.let { String.format(Locale.getDefault(), "%.1f kg", it) } ?: notAvailable,
                state.weightKg != null,
            ),
            Triple(
                stringResource(R.string.meal_plan_data_height),
                state.heightCm?.let { "${it.toInt()} cm" } ?: notAvailable,
                state.heightCm != null,
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

@Composable
private fun PlanCard(text: String, generatedAtEpochMillis: Long) {
    val kb = MaterialTheme.kidBoxColors
    PlanCardContainer {
        Text(
            stringResource(R.string.meal_plan_your_plan),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = kb.title,
        )
        Text(
            stringResource(R.string.meal_plan_generated_at, formatDateTime(generatedAtEpochMillis)),
            fontSize = 12.sp,
            color = kb.subtitle,
        )
        Spacer(Modifier.height(10.dp))
        Text(text, fontSize = 14.sp, color = kb.title)
    }
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
