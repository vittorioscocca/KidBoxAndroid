package it.vittorioscocca.kidbox.ui.screens.health

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.util.Locale

/**
 * I dati che alimentano un piano AI, nella forma che serve alla card «Dati
 * usati per il piano».
 *
 * Piano Fitness e Piano Alimentare leggono le stesse fonti e mostravano due
 * elenchi copiati: aggiungerne una voleva dire ricordarsi di due punti, e le
 * calorie attive erano infatti finite solo nel primo. Qui la lista è una sola,
 * e ogni schermata la avvolge nel proprio contenitore con la propria tinta.
 */
data class PlanDataSources(
    val ageYears: Int?,
    val manualAgeYears: Int?,
    val weightKg: Double?,
    val manualWeightKg: Double?,
    val heightCm: Double?,
    val manualHeightCm: Double?,
    val workoutCount: Int,
    val activeEnergyKcal: Double?,
    val visitCount: Int,
    val examCount: Int,
    val activeTreatmentCount: Int,
    val hasBodyMetrics: Boolean,
)

private data class PlanDataRow(val label: String, val value: String, val available: Boolean)

/**
 * Contenuto della card: titolo, elenco delle fonti e, se mancano peso o
 * altezza, l'avviso passato in [missingMetricsText]. Va inserito nel
 * contenitore della schermata chiamante.
 */
@Composable
fun PlanDataSourcesContent(
    data: PlanDataSources,
    tint: Color,
    missingMetricsText: String,
) {
    val kb = MaterialTheme.kidBoxColors
    val notAvailable = stringResource(R.string.meal_plan_not_available)
    val manualSuffix = stringResource(R.string.meal_plan_manual_suffix)
    val locale = Locale.getDefault()

    val rows = listOf(
        PlanDataRow(
            stringResource(R.string.meal_plan_data_age),
            data.ageYears?.let { stringResource(R.string.meal_plan_years, it) }
                ?: data.manualAgeYears?.let { stringResource(R.string.meal_plan_years, it) + manualSuffix }
                ?: notAvailable,
            data.ageYears != null || data.manualAgeYears != null,
        ),
        PlanDataRow(
            stringResource(R.string.meal_plan_data_weight),
            data.weightKg?.let { String.format(locale, "%.1f kg", it) }
                ?: data.manualWeightKg?.let { String.format(locale, "%.1f kg", it) + manualSuffix }
                ?: notAvailable,
            data.weightKg != null || data.manualWeightKg != null,
        ),
        PlanDataRow(
            stringResource(R.string.meal_plan_data_height),
            data.heightCm?.let { "${it.toInt()} cm" }
                ?: data.manualHeightCm?.let { "${it.toInt()} cm" + manualSuffix }
                ?: notAvailable,
            data.heightCm != null || data.manualHeightCm != null,
        ),
        PlanDataRow(
            stringResource(R.string.meal_plan_data_workouts),
            data.workoutCount.toString(),
            data.workoutCount > 0,
        ),
        // Le calorie attive alimentano il resoconto del Piano Fitness e la
        // stima del fabbisogno del Piano Alimentare: vanno dichiarate in
        // entrambe le schede, non solo dove capita.
        PlanDataRow(
            stringResource(R.string.fitness_data_active_energy),
            data.activeEnergyKcal?.takeIf { it > 0 }
                ?.let { String.format(locale, "%.0f kcal", it) } ?: notAvailable,
            (data.activeEnergyKcal ?: 0.0) > 0.0,
        ),
        PlanDataRow(
            stringResource(R.string.meal_plan_data_visits),
            data.visitCount.toString(),
            data.visitCount > 0,
        ),
        PlanDataRow(
            stringResource(R.string.meal_plan_data_exams),
            data.examCount.toString(),
            data.examCount > 0,
        ),
        PlanDataRow(
            stringResource(R.string.meal_plan_data_treatments),
            data.activeTreatmentCount.toString(),
            data.activeTreatmentCount > 0,
        ),
    )

    Text(
        stringResource(R.string.meal_plan_data_used),
        fontWeight = FontWeight.Bold,
        fontSize = 15.sp,
        color = kb.title,
    )
    Spacer(Modifier.height(10.dp))

    rows.forEach { row ->
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (row.available) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                contentDescription = null,
                tint = if (row.available) tint else Color(0xFFE0952F),
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(row.label, fontSize = 14.sp, color = kb.title, modifier = Modifier.weight(1f))
            Text(row.value, fontSize = 12.sp, color = kb.subtitle)
        }
    }

    if (!data.hasBodyMetrics) {
        Spacer(Modifier.height(8.dp))
        Text(missingMetricsText, fontSize = 12.sp, color = Color(0xFFE0952F))
    }
}
