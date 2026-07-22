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
        if (granted.containsAll(viewModel.healthPermissions)) {
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
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                val snapshot = state.snapshot
                when {
                    snapshot != null && snapshot.hasCardiacOrActivity -> {
                        HealthDetailedMetrics(snapshot)
                    }
                    snapshot != null -> {
                        Text(
                            stringResource(R.string.health_no_recent_data),
                            fontSize = 15.sp,
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

        Spacer(Modifier.height(16.dp))
        SectionLabel(stringResource(R.string.health_profile_from_health), kb.subtitle)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
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
