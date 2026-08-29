@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import it.vittorioscocca.kidbox.util.KBLocale
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@Composable
fun HealthConnectAppScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    viewModel: HealthConnectAppViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    val healthPermissionLauncher = rememberLauncherForActivityResult(
        PermissionController.createRequestPermissionResultContract(),
    ) { granted ->
        // Basta l'insieme obbligatorio: se manca solo una facoltativa (l'altezza)
        // si importa lo stesso, senza dire all'utente che non ha dato l'accesso.
        if (granted.containsAll(viewModel.requiredHealthPermissions)) {
            viewModel.importFromHealthConnect()
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.health_hc_permission),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }
    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState()),
    ) {
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KidBoxHeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.health_back),
                onClick = onBack,
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.health_app),
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(20.dp))

        SectionLabel(stringResource(R.string.health_data_imported), kb.subtitle)
        Card(
            colors = CardDefaults.cardColors(containerColor = kb.card),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val snapshot = state.snapshot
                // `hasCardiacOrActivity` non guarda il peso: chi in Health
                // Connect ha solo quello vedeva "nessun dato recente" pur
                // avendo un dato appena letto. Il riepilogo vale la pena anche
                // per un campo solo.
                val hasAnything = snapshot != null && (
                    snapshot.hasCardiacOrActivity ||
                        snapshot.weightKg != null ||
                        state.childWeightKg != null ||
                        state.childHeightCm != null ||
                        snapshot.heightCm != null ||
                        !state.ageDescription.isNullOrBlank()
                    )
                when {
                    snapshot != null && hasAnything -> {
                        HealthMetricsSummary(
                            snapshot = snapshot,
                            ageDescription = state.ageDescription,
                            childWeightKg = state.childWeightKg,
                            childHeightCm = state.childHeightCm,
                        )
                        if (snapshot.hasCardiacOrActivity) {
                            Spacer(Modifier.height(20.dp))
                            HealthDetailedMetrics(snapshot)
                        }
                    }
                    snapshot != null -> {
                        // Health Connect è un contenitore, non una sorgente:
                        // se nessuna app ci scrive dentro resta vuoto, e senza
                        // dirlo l'utente pensa che sia KidBox a non funzionare.
                        Text(
                            stringResource(R.string.health_no_recent_data),
                            fontSize = 15.sp,
                            color = kb.title,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            stringResource(R.string.health_hc_empty_hint),
                            fontSize = 13.sp,
                            color = kb.subtitle,
                        )
                    }
                    else -> {
                        Text(
                            stringResource(R.string.health_not_connected),
                            fontSize = 15.sp,
                            color = kb.subtitle,
                        )
                    }
                }
            }
        }

        // Card profilo solo quando ha qualcosa da dire: vuota sembrava un
        // riquadro rotto.
        val hasProfileData = !state.ageDescription.isNullOrBlank() ||
            state.childWeightKg != null ||
            state.childHeightCm != null ||
            !state.snapshot?.bloodGroup.isNullOrBlank()
        if (hasProfileData) {
        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.health_profile_from_health), kb.subtitle)
        Card(
            colors = CardDefaults.cardColors(containerColor = kb.card),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                state.ageDescription?.let { Text("Età: $it", fontSize = 15.sp, color = kb.title) }
                state.childWeightKg?.let { w ->
                    Text(
                        String.format(KBLocale.current(), "Peso: %.1f kg", w),
                        fontSize = 15.sp,
                        color = kb.title,
                    )
                }
                state.snapshot?.bloodGroup?.takeIf { it.isNotBlank() }?.let { bg ->
                    Text("Gruppo sanguigno: $bg", fontSize = 15.sp, color = kb.title)
                }
                state.childHeightCm?.let { h ->
                    Text(
                        String.format(KBLocale.current(), "Altezza: %.0f cm", h),
                        fontSize = 15.sp,
                        color = kb.title,
                    )
                }
            }
        }
        }

        Spacer(Modifier.height(16.dp))
        TextButton(
            onClick = {
                if (!state.healthConnectAvailable) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.health_install_hc),
                        Toast.LENGTH_LONG,
                    ).show()
                    return@TextButton
                }
                healthPermissionLauncher.launch(viewModel.healthPermissions)
            },
            enabled = !state.isImporting,
            modifier = Modifier.padding(horizontal = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.isImporting) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    if (state.snapshot == null) stringResource(R.string.health_connect_hc) else stringResource(R.string.health_refresh_pairing),
                    color = Color(0xFF0A84FF),
                    fontSize = 17.sp,
                )
            }
        }
        Text(
            stringResource(R.string.health_weight_note),
            fontSize = 12.sp,
            color = kb.subtitle,
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun SectionLabel(text: String, kb: androidx.compose.ui.graphics.Color) {
    Text(text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = kb, modifier = Modifier.padding(horizontal = 18.dp))
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun HealthDetailedMetrics(snapshot: HealthImportSnapshot) {
    val kb = MaterialTheme.kidBoxColors
    val timeFmt = remember { SimpleDateFormat("HH:mm", KBLocale.current()) }
    val dayFmt = remember { SimpleDateFormat("d MMM yyyy", KBLocale.current()) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        snapshot.heartRateBpm?.let { bpm ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Favorite, null, tint = Color(0xFFFF2D55), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    String.format(KBLocale.current(), "Ultimo battito: %.0f bpm", bpm),
                    fontSize = 14.sp,
                    color = kb.title,
                )
            }
        }
        snapshot.stepsToday?.takeIf { it > 0 }?.let { steps ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DirectionsWalk, null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Passi oggi: $steps", fontSize = 14.sp, color = kb.title)
            }
        }
        snapshot.activeEnergyKcal?.takeIf { it > 0 }?.let { kcal ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.LocalFireDepartment, null, tint = Color(0xFFFF9500), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    String.format(KBLocale.current(), "Energia attiva oggi: %.0f kcal", kcal),
                    fontSize = 14.sp,
                    color = kb.title,
                )
            }
        }

        if (snapshot.recentHeartRates.isNotEmpty()) {
            Text(stringResource(R.string.health_last_heartbeats), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = kb.title)
            snapshot.recentHeartRates.forEach { reading ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        String.format(KBLocale.current(), "%.0f bpm", reading.bpm),
                        fontSize = 14.sp,
                        color = Color(0xFFFF2D55),
                    )
                    Spacer(Modifier.weight(1f))
                    Text(timeFmt.format(Date(reading.measuredAtEpochMillis)), fontSize = 12.sp, color = kb.subtitle)
                }
            }
        }

        if (snapshot.recentWorkouts.isNotEmpty() || snapshot.recentDailyActivity.isNotEmpty()) {
            Text(stringResource(R.string.health_recent_activity), fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = kb.title)
            snapshot.recentWorkouts.forEach { workout ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.DirectionsRun, null, tint = kb.subtitle, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(workout.title, fontSize = 14.sp, color = kb.title)
                    }
                    val details = buildList {
                        add(dayFmt.format(Date(workout.startedAtEpochMillis)))
                        workout.durationMinutes?.takeIf { it > 0 }?.let { add("$it min") }
                        workout.activeEnergyKcal?.takeIf { it > 0 }?.let {
                            add(String.format(KBLocale.current(), "%.0f kcal", it))
                        }
                    }.joinToString(" · ")
                    Text(details, fontSize = 12.sp, color = kb.subtitle)
                }
            }
            snapshot.recentDailyActivity.forEach { day ->
                Row(Modifier.fillMaxWidth()) {
                    Text(dayFmt.format(Date(day.dayEpochMillis)), fontSize = 14.sp, color = kb.title)
                    Spacer(Modifier.weight(1f))
                    val parts = buildList {
                        day.steps?.takeIf { it > 0 }?.let { add("$it passi") }
                        day.activeEnergyKcal?.takeIf { it > 0 }?.let {
                            add(String.format(KBLocale.current(), "%.0f kcal", it))
                        }
                    }.joinToString(" · ")
                    if (parts.isNotBlank()) {
                        Text(parts, fontSize = 12.sp, color = kb.subtitle)
                    }
                }
            }
        }

        Text("ECG", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = kb.title)
        if (snapshot.recentECGs.isEmpty()) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Default.MonitorHeart, null, tint = kb.subtitle, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.health_no_ecg),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
            }
        } else {
            snapshot.recentECGs.forEach { ecg ->
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(ecg.classificationLabel, fontSize = 14.sp, color = kb.title)
                    Text(dayFmt.format(Date(ecg.recordedAtEpochMillis)), fontSize = 12.sp, color = kb.subtitle)
                }
            }
        }
    }
}

// ── Riepilogo in stile iOS ───────────────────────────────────────────────────
//
// Gemello di `AppleHealthDashboardView`. Dopo l'abbinamento serve una risposta
// immediata alla domanda "cosa ha letto?": un elenco di righe la dà solo a chi
// lo legge tutto, un riquadro di caselle la dà a colpo d'occhio. Il dettaglio
// resta sotto, per chi vuole i singoli campioni.
//
// La griglia è costruita a mano con delle Row invece che con LazyVGrid perché
// questa schermata è già dentro un `verticalScroll`: annidare due contenitori
// scrollabili sullo stesso asse è un errore a runtime, non una scelta di stile.

@Composable
private fun HealthMetricsSummary(
    snapshot: HealthImportSnapshot,
    ageDescription: String?,
    childWeightKg: Double?,
    childHeightCm: Double?,
) {
    val kb = MaterialTheme.kidBoxColors
    val locale = KBLocale.current()
    val syncFmt = remember { SimpleDateFormat("d MMM, HH:mm", KBLocale.current()) }

    // Il peso di Health Connect ha la precedenza su quello inserito a mano: è il
    // dato appena letto, ed è la ragione per cui si è premuto "aggiorna".
    val weight = snapshot.weightKg ?: childWeightKg
    val height = snapshot.heightCm ?: childHeightCm

    val tiles = buildList {
        snapshot.stepsToday?.takeIf { it > 0 }?.let {
            add(
                MetricTileData(
                    title = stringResource(R.string.health_metric_steps),
                    value = "$it",
                    subtitle = stringResource(R.string.health_metric_steps_sub),
                    icon = Icons.Default.DirectionsWalk,
                    tint = Color(0xFF34C759),
                )
            )
        }
        weight?.let {
            add(
                MetricTileData(
                    title = stringResource(R.string.health_metric_weight),
                    value = String.format(locale, "%.1f kg", it),
                    subtitle = stringResource(R.string.health_metric_weight_sub),
                    icon = Icons.Default.MonitorWeight,
                    tint = Color(0xFF5A8DEA),
                )
            )
        }
        height?.let {
            add(
                MetricTileData(
                    title = stringResource(R.string.health_metric_height),
                    value = String.format(locale, "%.0f cm", it),
                    subtitle = stringResource(R.string.health_metric_weight_sub),
                    icon = Icons.Default.Straighten,
                    tint = Color(0xFF59B4D1),
                )
            )
        }
        snapshot.heartRateBpm?.let {
            add(
                MetricTileData(
                    title = stringResource(R.string.health_metric_heart),
                    value = String.format(locale, "%.0f", it),
                    subtitle = stringResource(R.string.health_metric_heart_sub),
                    icon = Icons.Default.Favorite,
                    tint = Color(0xFFFF2D55),
                )
            )
        }
        snapshot.restingHeartRateBpm?.let {
            add(
                MetricTileData(
                    title = stringResource(R.string.health_metric_resting),
                    value = String.format(locale, "%.0f", it),
                    subtitle = stringResource(R.string.health_metric_resting_sub),
                    icon = Icons.Default.MonitorHeart,
                    tint = Color(0xFFD94080),
                )
            )
        }
        snapshot.activeEnergyKcal?.takeIf { it > 0 }?.let {
            add(
                MetricTileData(
                    title = stringResource(R.string.health_metric_energy),
                    value = String.format(locale, "%.0f kcal", it),
                    subtitle = stringResource(R.string.health_metric_energy_sub),
                    icon = Icons.Default.LocalFireDepartment,
                    tint = Color(0xFFFF9500),
                )
            )
        }
        add(
            MetricTileData(
                title = stringResource(R.string.health_metric_workouts),
                value = "${snapshot.recentWorkouts.size}",
                subtitle = when {
                    snapshot.recentWorkouts.isEmpty() ->
                        stringResource(R.string.health_metric_workouts_none)
                    snapshot.weeklyExerciseMinutesAvg != null -> stringResource(
                        R.string.health_metric_workouts_avg,
                        String.format(locale, "%.0f", snapshot.weeklyExerciseMinutesAvg),
                    )
                    else -> stringResource(R.string.health_metric_workouts_recent)
                },
                icon = Icons.Default.DirectionsRun,
                tint = Color(0xFF8B5CF6),
            )
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!ageDescription.isNullOrBlank()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(kb.background)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Cake,
                    contentDescription = null,
                    tint = Color(0xFFFF9500),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        stringResource(R.string.health_metric_age_source),
                        fontSize = 12.sp,
                        color = kb.subtitle,
                    )
                    Text(
                        "${stringResource(R.string.health_metric_age)}: $ageDescription",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = kb.title,
                    )
                }
            }
        }

        Text(
            stringResource(R.string.health_metrics_title),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = kb.title,
        )

        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { tile ->
                    MetricTile(data = tile, modifier = Modifier.weight(1f))
                }
                // Riga dispari: la casella singola non deve allargarsi a tutta
                // la larghezza, altrimenti stona con quelle sopra.
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Text(
            stringResource(
                R.string.health_metrics_synced,
                syncFmt.format(Date(snapshot.syncedAtEpochMillis)),
            ),
            fontSize = 12.sp,
            color = kb.subtitle,
        )
    }
}

private data class MetricTileData(
    val title: String,
    val value: String,
    val subtitle: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color,
)

@Composable
private fun MetricTile(data: MetricTileData, modifier: Modifier = Modifier) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(kb.background)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Icon(data.icon, contentDescription = null, tint = data.tint, modifier = Modifier.size(20.dp))
        Text(data.value, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = kb.title)
        Text(data.title, fontSize = 13.sp, fontWeight = FontWeight.Medium, color = kb.title)
        Text(data.subtitle, fontSize = 11.sp, color = kb.subtitle)
    }
}
