package it.vittorioscocca.kidbox.ui.screens.health

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.health.fitness.FitnessExperience
import it.vittorioscocca.kidbox.data.health.fitness.FitnessGoal
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDocument
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanInput
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlace
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSport
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.DateFormat
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Wizard di configurazione degli obiettivi e, con lo stesso codice, la
 * schermata Impostazioni del piano: le due mostrano le stesse scelte, cambiano
 * solo la navigazione e il pulsante finale.
 *
 * Non genera nulla da sé: restituisce l'input al chiamante, che è l'unico a
 * parlare con l'AI e a mostrare il costo in messaggi.
 */
enum class FitnessSetupMode { ONBOARDING, SETTINGS }

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FitnessPlanSetupScreen(
    mode: FitnessSetupMode,
    initialInput: FitnessPlanInput,
    estimatedUnits: Int,
    needsManualMetrics: Boolean,
    plan: FitnessPlanDocument?,
    usageSummary: String?,
    onConfirm: (FitnessPlanInput) -> Unit,
    onDelete: () -> Unit,
    onBack: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var input by remember { mutableStateOf(initialInput) }
    var step by remember { mutableIntStateOf(0) }
    var showRecalcConfirm by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val lastStep = 2

    val canContinue = when (step) {
        0 -> input.goal != FitnessGoal.RACE ||
            (
                input.raceType != null &&
                    (input.raceType != FitnessSport.OTHER || input.raceDetail.isNotBlank())
                )
        1 -> input.trainingWeekdays.isNotEmpty()
        else -> input.isComplete
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
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
                stringResource(
                    if (mode == FitnessSetupMode.ONBOARDING) {
                        R.string.fitness_setup_title
                    } else {
                        R.string.fitness_settings_title
                    },
                ),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = kb.title,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (mode == FitnessSetupMode.ONBOARDING) {
                StepHeader(step = step, lastStep = lastStep)
                when (step) {
                    0 -> GoalSection(input) { input = it }
                    1 -> ScheduleSection(input) { input = it }
                    else -> DetailsSection(input, needsManualMetrics) { input = it }
                }
            } else {
                GoalSection(input) { input = it }
                ScheduleSection(input) { input = it }
                DetailsSection(input, needsManualMetrics) { input = it }
                RecalcSection(estimatedUnits, input.isComplete) { showRecalcConfirm = true }
                plan?.let { current ->
                    if (current.safetyNotes.isNotEmpty()) SafetyNotesSection(current)
                    PlanInfoSection(current, usageSummary) { showDeleteConfirm = true }
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        if (mode == FitnessSetupMode.ONBOARDING) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(kb.card)
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (step == lastStep) {
                    Text(
                        stringResource(R.string.fitness_cost, estimatedUnits),
                        fontSize = 13.sp,
                        color = kb.subtitle,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (step > 0) {
                        OutlinedButton(onClick = { step-- }) {
                            Text(stringResource(R.string.fitness_back))
                        }
                    }
                    Button(
                        onClick = { if (step < lastStep) step++ else onConfirm(input) },
                        enabled = canContinue,
                        colors = ButtonDefaults.buttonColors(containerColor = FITNESS_TINT),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            stringResource(
                                if (step < lastStep) R.string.fitness_next else R.string.fitness_generate_plan,
                            ),
                        )
                    }
                }
            }
        }
    }

    if (showRecalcConfirm) {
        AlertDialog(
            onDismissRequest = { showRecalcConfirm = false },
            title = { Text(stringResource(R.string.fitness_recalc_confirm_title)) },
            text = { Text(stringResource(R.string.fitness_recalc_confirm_body, estimatedUnits)) },
            confirmButton = {
                TextButton(onClick = {
                    showRecalcConfirm = false
                    onConfirm(input)
                }) { Text(stringResource(R.string.fitness_recalc_cta)) }
            },
            dismissButton = {
                TextButton(onClick = { showRecalcConfirm = false }) {
                    Text(stringResource(R.string.meal_plan_delete_cancel))
                }
            },
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(stringResource(R.string.fitness_delete_title)) },
            text = { Text(stringResource(R.string.fitness_delete_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    onDelete()
                    onBack()
                }) { Text(stringResource(R.string.fitness_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text(stringResource(R.string.meal_plan_delete_cancel))
                }
            },
        )
    }
}

@Composable
private fun StepHeader(step: Int, lastStep: Int) {
    val kb = MaterialTheme.kidBoxColors
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..lastStep).forEach { index ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(
                            if (index <= step) FITNESS_TINT else kb.subtitle.copy(alpha = 0.2f),
                        ),
                )
            }
        }
        Text(
            stringResource(
                when (step) {
                    0 -> R.string.fitness_step_goal_title
                    1 -> R.string.fitness_step_days_title
                    else -> R.string.fitness_step_details_title
                },
            ),
            fontWeight = FontWeight.Bold,
            fontSize = 17.sp,
            color = kb.title,
        )
        Text(
            stringResource(
                when (step) {
                    0 -> R.string.fitness_step_goal_subtitle
                    1 -> R.string.fitness_step_days_subtitle
                    else -> R.string.fitness_step_details_subtitle
                },
            ),
            fontSize = 13.sp,
            color = kb.subtitle,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun GoalSection(input: FitnessPlanInput, onChange: (FitnessPlanInput) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    var showRaceDatePicker by remember { mutableStateOf(false) }

    FitnessCard {
        SectionTitle(stringResource(R.string.fitness_goal_section))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FitnessGoal.entries.forEach { goal ->
                FitnessChip(stringResource(goal.labelRes), input.goal == goal) {
                    onChange(
                        if (goal == FitnessGoal.RACE) {
                            val race = input.raceType ?: input.sortedSports.firstOrNull() ?: FitnessSport.RUNNING
                            input.copy(
                                goal = goal,
                                raceType = race,
                                preferredSports = input.preferredSports + race,
                            )
                        } else {
                            input.copy(goal = goal, raceType = null, raceDateEpochMillis = null)
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
        Spacer(Modifier.height(14.dp))

        SectionTitle(stringResource(R.string.fitness_sports_section))
        Text(
            stringResource(R.string.fitness_sports_hint),
            fontSize = 13.sp,
            color = kb.subtitle,
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FitnessSport.entries.forEach { sport ->
                FitnessChip(stringResource(sport.labelRes), sport in input.preferredSports) {
                    onChange(
                        input.copy(
                            preferredSports = if (sport in input.preferredSports) {
                                input.preferredSports - sport
                            } else {
                                input.preferredSports + sport
                            },
                        ),
                    )
                }
            }
        }

        if (input.goal == FitnessGoal.RACE) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
            Spacer(Modifier.height(14.dp))
            SectionTitle(stringResource(R.string.fitness_race_section))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FitnessSport.raceOptions.forEach { sport ->
                    FitnessChip(stringResource(sport.labelRes), input.raceType == sport) {
                        // Chi prepara una gara pratica quello sport: evita di
                        // doverlo selezionare due volte.
                        onChange(
                            input.copy(
                                raceType = sport,
                                preferredSports = input.preferredSports + sport,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = input.raceDetail,
                onValueChange = { onChange(input.copy(raceDetail = it)) },
                label = {
                    Text(
                        stringResource(
                            if (input.raceType == FitnessSport.OTHER) {
                                R.string.fitness_race_detail_required
                            } else {
                                R.string.fitness_race_detail_hint
                            },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.fitness_has_race_date),
                    fontSize = 14.sp,
                    color = kb.title,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = input.raceDateEpochMillis != null,
                    onCheckedChange = { enabled ->
                        onChange(
                            input.copy(
                                raceDateEpochMillis = if (enabled) {
                                    input.raceDateEpochMillis ?: (
                                        System.currentTimeMillis() + 90L * 24 * 3600 * 1000
                                        )
                                } else {
                                    null
                                },
                            ),
                        )
                    },
                )
            }
            input.raceDateEpochMillis?.let { millis ->
                Text(
                    text = "${stringResource(R.string.fitness_race_date)}: ${formatDate(millis)}",
                    fontSize = 14.sp,
                    color = FITNESS_TINT,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showRaceDatePicker = true }
                        .padding(vertical = 8.dp),
                )
            }
        }
    }

    if (showRaceDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = input.raceDateEpochMillis ?: System.currentTimeMillis(),
        )
        DatePickerDialog(
            onDismissRequest = { showRaceDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let {
                        onChange(input.copy(raceDateEpochMillis = it))
                    }
                    showRaceDatePicker = false
                }) { Text(stringResource(R.string.subscription_ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showRaceDatePicker = false }) {
                    Text(stringResource(R.string.meal_plan_delete_cancel))
                }
            },
        ) { DatePicker(state = pickerState) }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun ScheduleSection(input: FitnessPlanInput, onChange: (FitnessPlanInput) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    var showTimePicker by remember { mutableStateOf(false) }

    FitnessCard {
        SectionTitle(stringResource(R.string.fitness_days_section))
        Spacer(Modifier.height(8.dp))
        WeekdayPicker(input.trainingWeekdays) { weekday ->
            onChange(
                input.copy(
                    trainingWeekdays = if (weekday in input.trainingWeekdays) {
                        input.trainingWeekdays - weekday
                    } else {
                        input.trainingWeekdays + weekday
                    },
                ),
            )
        }
        if (input.trainingWeekdays.isEmpty()) {
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.fitness_days_empty),
                fontSize = 13.sp,
                color = Color(0xFFE0952F),
            )
        }

        Spacer(Modifier.height(14.dp))
        HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
        Spacer(Modifier.height(14.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.fitness_reminder_toggle),
                fontSize = 14.sp,
                color = kb.title,
                modifier = Modifier.weight(1f),
            )
            Switch(
                checked = input.reminderEnabled,
                onCheckedChange = { onChange(input.copy(reminderEnabled = it)) },
            )
        }
        if (input.reminderEnabled) {
            Text(
                text = "${stringResource(R.string.fitness_reminder_time)}: " +
                    "%02d:%02d".format(input.reminderHour, input.reminderMinute),
                fontSize = 14.sp,
                color = FITNESS_TINT,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showTimePicker = true }
                    .padding(vertical = 8.dp),
            )
            Text(
                stringResource(R.string.fitness_reminder_hint),
                fontSize = 13.sp,
                color = kb.subtitle,
            )
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.fitness_duration_section))
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(20, 30, 45, 60, 90).forEach { minutes ->
                FitnessChip(
                    stringResource(R.string.fitness_session_minutes, minutes),
                    input.sessionMinutes == minutes,
                ) { onChange(input.copy(sessionMinutes = minutes)) }
            }
        }
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = input.reminderHour,
            initialMinute = input.reminderMinute,
            is24Hour = true,
        )
        Dialog(onDismissRequest = { showTimePicker = false }) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(kb.card)
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                TimePicker(state = pickerState)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showTimePicker = false }) {
                        Text(stringResource(R.string.meal_plan_delete_cancel))
                    }
                    TextButton(onClick = {
                        onChange(
                            input.copy(
                                reminderMinutesFromMidnight = pickerState.hour * 60 + pickerState.minute,
                            ),
                        )
                        showTimePicker = false
                    }) { Text(stringResource(R.string.subscription_ok)) }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DetailsSection(
    input: FitnessPlanInput,
    needsManualMetrics: Boolean,
    onChange: (FitnessPlanInput) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    FitnessCard {
        SectionTitle(stringResource(R.string.fitness_experience_section))
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FitnessExperience.entries.forEach { level ->
                FitnessChip(stringResource(level.labelRes), input.experience == level) {
                    onChange(input.copy(experience = level))
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        SectionTitle(stringResource(R.string.fitness_place_section))
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FitnessPlace.entries.forEach { place ->
                FitnessChip(stringResource(place.labelRes), input.place == place) {
                    onChange(input.copy(place = place))
                }
            }
        }

        if (needsManualMetrics) {
            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = kb.subtitle.copy(alpha = 0.15f))
            Spacer(Modifier.height(14.dp))
            SectionTitle(stringResource(R.string.meal_plan_manual_title))
            Text(
                stringResource(R.string.fitness_manual_hint),
                fontSize = 13.sp,
                color = kb.subtitle,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField(
                    label = stringResource(R.string.meal_plan_manual_age),
                    value = input.manualAgeYears,
                    modifier = Modifier.weight(1f),
                ) { onChange(input.copy(manualAgeYears = it)) }
                NumberField(
                    label = stringResource(R.string.meal_plan_manual_weight),
                    value = input.manualWeightKg,
                    modifier = Modifier.weight(1f),
                ) { onChange(input.copy(manualWeightKg = it)) }
                NumberField(
                    label = stringResource(R.string.meal_plan_manual_height),
                    value = input.manualHeightCm,
                    modifier = Modifier.weight(1f),
                ) { onChange(input.copy(manualHeightCm = it)) }
            }
        }

        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = input.notes,
            onValueChange = { onChange(input.copy(notes = it)) },
            label = { Text(stringResource(R.string.fitness_notes_hint)) },
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.fitness_ai_reads_health),
            fontSize = 13.sp,
            color = kb.subtitle,
        )
    }
}

@Composable
private fun RecalcSection(estimatedUnits: Int, enabled: Boolean, onRecalc: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    FitnessCard {
        Text(
            stringResource(R.string.fitness_recalc_cost, estimatedUnits),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = kb.title,
        )
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.fitness_recalc_hint), fontSize = 13.sp, color = kb.subtitle)
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onRecalc,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(containerColor = FITNESS_TINT),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.fitness_recalc_cta)) }
    }
}

@Composable
private fun SafetyNotesSection(plan: FitnessPlanDocument) {
    val kb = MaterialTheme.kidBoxColors
    FitnessCard {
        Text(
            stringResource(R.string.fitness_safety_title),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = kb.title,
        )
        Spacer(Modifier.height(4.dp))
        Text(stringResource(R.string.fitness_safety_detail), fontSize = 13.sp, color = kb.subtitle)
        Spacer(Modifier.height(8.dp))
        plan.safetyNotes.forEach { note ->
            Text("• $note", fontSize = 14.sp, color = kb.title)
            Spacer(Modifier.height(6.dp))
        }
    }
}

@Composable
private fun PlanInfoSection(
    plan: FitnessPlanDocument,
    usageSummary: String?,
    onDelete: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    FitnessCard {
        Text(
            stringResource(R.string.fitness_current_plan),
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            color = kb.title,
        )
        if (plan.summary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(plan.summary, fontSize = 14.sp, color = kb.subtitle)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.fitness_generated_at, formatDateTime(plan.generatedAtEpochMillis)),
            fontSize = 12.sp,
            color = kb.subtitle,
        )
        usageSummary?.let {
            Spacer(Modifier.height(4.dp))
            Text(it, fontSize = 12.sp, color = FITNESS_TINT)
        }
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onDelete) {
            Text(stringResource(R.string.fitness_delete), color = Color(0xFFD9534F))
        }
    }
}

// ── Pezzi condivisi ────────────────────────────────────────────────────────

@Composable
internal fun FitnessCard(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(kb.card)
            .padding(16.dp),
        content = content,
    )
}

@Composable
internal fun SectionTitle(text: String) {
    Text(
        text,
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = MaterialTheme.kidBoxColors.title,
    )
}

@Composable
internal fun FitnessChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Text(
        text = label,
        fontSize = 13.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        color = if (selected) FITNESS_TINT else kb.title,
        modifier = Modifier
            .padding(vertical = 4.dp)
            .clip(CircleShape)
            .background(
                if (selected) FITNESS_TINT.copy(alpha = 0.18f) else kb.subtitle.copy(alpha = 0.08f),
            )
            .border(
                width = 1.dp,
                color = if (selected) FITNESS_TINT else kb.subtitle.copy(alpha = 0.28f),
                shape = CircleShape,
            )
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 9.dp),
    )
}

@Composable
private fun WeekdayPicker(selected: Set<Int>, onToggle: (Int) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val first = Calendar.getInstance().firstDayOfWeek
    val symbols = DateFormatSymbols.getInstance(Locale.getDefault()).shortWeekdays
    val ordered = (0 until 7).map { ((first - 1 + it) % 7) + 1 }

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
        ordered.forEach { weekday ->
            val isSelected = weekday in selected
            Text(
                text = symbols.getOrNull(weekday).orEmpty().take(2).uppercase(Locale.getDefault()),
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) FITNESS_TINT else kb.title,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isSelected) {
                            FITNESS_TINT.copy(alpha = 0.18f)
                        } else {
                            kb.subtitle.copy(alpha = 0.08f)
                        },
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) FITNESS_TINT else kb.subtitle.copy(alpha = 0.24f),
                        shape = RoundedCornerShape(10.dp),
                    )
                    .clickable { onToggle(weekday) }
                    .padding(vertical = 12.dp),
            )
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier,
    )
}

internal fun formatDate(epochMillis: Long): String =
    DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(epochMillis))

internal fun formatDateTimeFitness(epochMillis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT, Locale.getDefault())
        .format(Date(epochMillis))

private fun formatDateTime(epochMillis: Long): String = formatDateTimeFitness(epochMillis)
