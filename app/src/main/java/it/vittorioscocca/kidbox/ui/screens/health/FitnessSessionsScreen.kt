package it.vittorioscocca.kidbox.ui.screens.health

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.health.fitness.FitnessCompletionSource
import it.vittorioscocca.kidbox.data.health.fitness.FitnessDistanceFormatter
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDates
import it.vittorioscocca.kidbox.data.health.fitness.FitnessPlanDocument
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSession
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSessionStatus
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Storico delle sedute del Piano Fitness segnate come fatte: elenco per mese,
 * dalla più recente, con il riepilogo del mese in testa (quante sedute, quanto
 * tempo, quante calorie — totale e media).
 *
 * Mostra solo le sedute con stato [FitnessSessionStatus.DONE]: qui si guarda ciò
 * che è stato davvero svolto, non il calendario di quello che resta da fare —
 * per quello c'è la dashboard.
 */
@Composable
fun FitnessSessionsScreen(
    plan: FitnessPlanDocument,
    onBack: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors

    // La data che conta è quella di completamento quando c'è: una seduta
    // spostata è stata svolta il giorno in cui è stata chiusa, non quello in cui
    // era stata programmata.
    val sessions = remember(plan) {
        plan.allSessions
            .filter { it.status == FitnessSessionStatus.DONE }
            .sortedByDescending { performedAt(it) }
    }
    val months = remember(sessions) {
        sessions.map { startOfMonth(performedAt(it)) }.distinct()
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
                stringResource(R.string.fitness_sessions_title),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = kb.title,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
        }

        if (sessions.isEmpty()) {
            EmptySessions()
            return@Column
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            months.forEach { month ->
                val monthSessions = sessions.filter { startOfMonth(performedAt(it)) == month }

                item(key = "header-$month") {
                    Text(
                        monthTitle(month),
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                        color = kb.title,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
                item(key = "summary-$month") { MonthSummary(monthSessions) }
                items(monthSessions.size, key = { monthSessions[it].id }) { index ->
                    SessionRow(monthSessions[index])
                }
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

/**
 * Riepilogo del mese. Le calorie compaiono solo se almeno una seduta le ha: una
 * media calcolata su zero dati sarebbe un numero inventato.
 */
@Composable
private fun MonthSummary(sessions: List<FitnessSession>) {
    val kb = MaterialTheme.kidBoxColors
    val count = sessions.size
    val minutes = sessions.sumOf { performedMinutes(it) }
    val withKcal = sessions.filter { it.actualKcal != null }
    val kcal = withKcal.sumOf { it.actualKcal ?: 0 }
    val withDistance = sessions.filter { (it.actualDistanceMeters ?: 0.0) >= 10.0 }
    val meters = withDistance.sumOf { it.actualDistanceMeters ?: 0.0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(kb.subtitle.copy(alpha = 0.06f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row {
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.fitness_sessions_total),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = kb.subtitle,
                modifier = Modifier.width(96.dp),
            )
            Text(
                stringResource(R.string.fitness_sessions_average),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = kb.subtitle,
                modifier = Modifier.width(96.dp),
            )
        }
        SummaryRow(
            label = stringResource(R.string.fitness_sessions_title),
            total = "$count",
            average = null,
            color = kb.title,
        )
        SummaryRow(
            label = stringResource(R.string.fitness_sessions_time),
            total = durationText(minutes),
            average = if (count > 0) durationText(minutes / count) else null,
            color = FITNESS_TINT,
        )
        // Sempre presente, anche a zero: una metrica di salute che sparisce
        // quando il dato manca rende il permesso indimostrabile.
        SummaryRow(
            label = stringResource(R.string.fitness_sessions_distance),
            total = FitnessDistanceFormatter.kilometers(meters) ?: "—",
            average = if (withDistance.isEmpty()) {
                null
            } else {
                FitnessDistanceFormatter.kilometers(meters / withDistance.size)
            },
            color = Color(0xFF4CB872),
        )
        if (withKcal.isNotEmpty()) {
            SummaryRow(
                label = stringResource(R.string.fitness_sessions_calories),
                total = stringResource(R.string.fitness_sessions_kcal, kcal),
                average = stringResource(R.string.fitness_sessions_kcal, kcal / withKcal.size),
                color = Color(0xFFE56B59),
            )
        }
    }
}

@Composable
private fun SummaryRow(label: String, total: String, average: String?, color: Color) {
    val kb = MaterialTheme.kidBoxColors
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(label, fontSize = 14.sp, color = kb.subtitle)
        Spacer(Modifier.weight(1f))
        Text(
            total,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.width(96.dp),
        )
        Text(
            average ?: "—",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.width(96.dp),
        )
    }
}

@Composable
private fun SessionRow(session: FitnessSession) {
    val kb = MaterialTheme.kidBoxColors

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(kb.subtitle.copy(alpha = 0.06f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(FITNESS_TINT.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(sessionIcon(session), contentDescription = null, tint = FITNESS_TINT)
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            // Titolo della riga: l'attività svolta se è stata registrata,
            // altrimenti quella programmata.
            Text(
                session.actualActivityTitle?.takeIf { it.isNotBlank() } ?: session.title,
                fontSize = 14.sp,
                color = kb.subtitle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Numero in evidenza: le calorie quando ci sono (è il dato che
            // l'orologio misura), altrimenti i minuti.
            // I chilometri per le discipline che ne hanno, poi le calorie, e
            // come ultima risorsa i minuti: l'ordine con cui l'attività si
            // riconosce a colpo d'occhio.
            val km = FitnessDistanceFormatter.kilometers(session.actualDistanceMeters)
            Text(
                km?.uppercase()
                    ?: session.actualKcal
                        ?.let { stringResource(R.string.fitness_sessions_kcal_big, it) }
                    ?: stringResource(R.string.fitness_sessions_min_big, performedMinutes(session)),
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = FITNESS_TINT,
            )
            val detail = buildList {
                if (km != null || session.actualKcal != null) {
                    add(stringResource(R.string.fitness_session_minutes, performedMinutes(session)))
                }
                if (km != null) {
                    session.actualKcal?.let { add(stringResource(R.string.fitness_sessions_kcal, it)) }
                }
                session.actualHeartRateBpm?.let { add("$it bpm") }
                if (session.wasSubstituted) {
                    add(stringResource(R.string.fitness_sessions_planned, session.title))
                }
            }
            if (detail.isNotEmpty()) {
                Text(
                    detail.joinToString(" · "),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(dayLabel(performedAt(session)), fontSize = 12.sp, color = kb.subtitle)
            if (session.completionSource == FitnessCompletionSource.HEALTH_CONNECT) {
                Spacer(Modifier.height(4.dp))
                Icon(
                    Icons.Default.Watch,
                    contentDescription = null,
                    tint = kb.subtitle,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptySessions() {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp, vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.DirectionsRun,
            contentDescription = null,
            tint = FITNESS_TINT.copy(alpha = 0.6f),
            modifier = Modifier.size(44.dp),
        )
        Text(
            stringResource(R.string.fitness_sessions_empty_title),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = kb.title,
        )
        Text(
            stringResource(R.string.fitness_sessions_empty_body),
            fontSize = 14.sp,
            color = kb.subtitle,
            textAlign = TextAlign.Center,
        )
    }
}

// ── Icone, date e numeri ───────────────────────────────────────────────────

private fun sessionIcon(session: FitnessSession): ImageVector =
    FitnessActivityIcons.forPerformed(session)

/** Giorno in cui la seduta è stata svolta. */
private fun performedAt(session: FitnessSession): Long =
    session.completedAtEpochMillis ?: session.dateEpochMillis

private fun performedMinutes(session: FitnessSession): Int =
    session.actualMinutes ?: session.durationMinutes

private fun startOfMonth(epochMillis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = epochMillis
    set(Calendar.DAY_OF_MONTH, 1)
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

@Composable
private fun durationText(minutes: Int): String =
    if (minutes < 60) {
        stringResource(R.string.fitness_session_minutes, minutes)
    } else {
        stringResource(R.string.fitness_sessions_hours, minutes / 60, minutes % 60)
    }

/** "oggi", il nome del giorno nell'ultima settimana, poi la data breve. */
@Composable
private fun dayLabel(epochMillis: Long): String {
    val day = FitnessPlanDates.startOfDay(epochMillis)
    val today = FitnessPlanDates.startOfDay(System.currentTimeMillis())
    val days = ((today - day) / 86_400_000L).toInt()
    return when {
        days == 0 -> stringResource(R.string.fitness_sessions_today)
        days == 1 -> stringResource(R.string.fitness_sessions_yesterday)
        days in 2..6 -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(epochMillis))
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(epochMillis))
    }
}

private fun monthTitle(epochMillis: Long): String {
    val text = SimpleDateFormat("LLLL yyyy", Locale.getDefault()).format(Date(epochMillis))
    // I nomi dei mesi arrivano minuscoli in italiano e maiuscoli in inglese: qui
    // servono come titolo, quindi si alza solo la prima lettera.
    return text.replaceFirstChar { it.uppercase() }
}
