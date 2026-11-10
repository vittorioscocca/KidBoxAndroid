package it.vittorioscocca.kidbox.ui.screens.health.treatments

import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.domain.model.KBDoseLog
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.TreatmentSchedulePeriod
import it.vittorioscocca.kidbox.domain.model.isScheduledDoseDayTherapeutic
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.KBLocale
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val PURPLE = Color(0xFF9573D9)
private val GREEN = Color(0xFF46996A)
private val ORANGE = Color(0xFFE65100)
private val RED = Color(0xFFD32F2F)

private val CARD_HEIGHT = 50.dp
private val CARD_GAP = 4.dp
private val MIN_HOUR_HEIGHT = 44.dp
private val GUTTER_WIDTH = 46.dp

/** Un colore stabile per cura (per posizione fra le cure ordinate per id), come su iOS. */
private val DRUG_PALETTE = listOf(
    PURPLE,
    Color(0xFF3385F2),
    Color(0xFF1AAD9E),
    Color(0xFFEB578C),
    Color(0xFFF28C26),
    Color(0xFF615CD9),
)

private data class DayDose(
    val treatment: KBTreatment,
    val dayNumber: Int,
    val slotIndex: Int,
    val scheduledTime: String,
    /** Minuti dalla mezzanotte; `Int.MAX_VALUE` se l'orario non è leggibile. */
    val sortMinutes: Int,
    val state: DoseState,
    val logId: String?,
    val takenAtEpochMillis: Long?,
    val isFutureDay: Boolean,
    val isPastDay: Boolean,
) {
    val key: String get() = "${treatment.id}|$dayNumber|$slotIndex"
    /** Ora (0-23) a cui sta la dose nella griglia. */
    val hour: Int get() = if (sortMinutes == Int.MAX_VALUE) 8 else (sortMinutes / 60).coerceIn(0, 23)
}

private data class ConfirmRequest(val dose: DayDose, val dayLabel: String)

/**
 * Vista per giorno di tutte le cure di un figlio (gemella di `TreatmentsCalendarView` su iOS):
 * in alto il giorno, sotto una linea del tempo con le ore e le dosi come card piccole sull'ora
 * giusta. Si passa da un giorno all'altro scorrendo in orizzontale o con le frecce.
 * Se in un'ora ci sono più dosi le card si impilano e la riga dell'ora si allunga.
 */
@Composable
fun TreatmentsDayCalendar(
    treatments: List<KBTreatment>,
    doseLogs: List<KBDoseLog>,
    onOpenTreatment: (String) -> Unit,
    onMarkTaken: (KBTreatment, dayNumber: Int, slotIndex: Int, scheduledTime: String, takenAt: Long) -> Unit,
    onMarkSkipped: (KBTreatment, dayNumber: Int, slotIndex: Int, scheduledTime: String) -> Unit,
    onUndo: (logId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val todayId = today.toEpochDay().toInt()

    // Dal primo giorno utile (inizio della cura più vecchia, almeno una settimana fa)
    // all'ultimo (fine della cura più lunga, almeno due settimane avanti).
    val range = remember(treatments, todayId) {
        var lo = todayId - 7
        var hi = todayId + 14
        for (t in treatments) {
            val s = Instant.ofEpochMilli(t.startDateEpochMillis).atZone(zone).toLocalDate().toEpochDay().toInt()
            lo = minOf(lo, s)
            hi = maxOf(hi, if (t.isLongTerm) todayId + 30 else s + t.durationDays - 1)
        }
        maxOf(lo, todayId - 400)..minOf(hi, todayId + 400)
    }

    val pagerState = rememberPagerState(initialPage = (todayId - range.first).coerceAtLeast(0)) {
        range.last - range.first + 1
    }
    val scope = rememberCoroutineScope()
    val selectedId = range.first + pagerState.currentPage
    val selectedDay = LocalDate.ofEpochDay(selectedId.toLong())

    // Registri per (cura, giorno, slot); il giorno 0 è la compatibilità con le build che salvavano il primo giorno come 0.
    val logIndex = remember(doseLogs) {
        val m = HashMap<String, KBDoseLog>()
        for (l in doseLogs) m["${l.treatmentId}|${l.dayNumber}|${l.slotIndex}"] = l
        m
    }
    val colorIndex = remember(treatments) { treatments.map { it.id }.sorted() }
    fun colorFor(t: KBTreatment): Color = DRUG_PALETTE[(colorIndex.indexOf(t.id).coerceAtLeast(0)) % DRUG_PALETTE.size]

    fun dosesOn(day: LocalDate): List<DayDose> {
        val result = ArrayList<DayDose>()
        for (t in treatments) {
            val start = Instant.ofEpochMilli(t.startDateEpochMillis).atZone(zone).toLocalDate()
            val offset = ChronoUnit.DAYS.between(start, day).toInt()
            if (offset < 0) continue
            if (!t.isLongTerm && offset >= t.durationDays) continue
            val dayNumber = offset + 1
            val times = t.scheduleTimesList()
            val slots = if (t.intervalBetweenDosesDays > 0) {
                if (t.isScheduledDoseDayTherapeutic(dayNumber)) listOf(0) else emptyList()
            } else {
                times.indices.toList()
            }
            for (slot in slots) {
                val time = times.getOrNull(slot) ?: times.firstOrNull() ?: "08:00"
                val log = logIndex["${t.id}|$dayNumber|$slot"]
                    ?: if (dayNumber == 1) logIndex["${t.id}|0|$slot"] else null
                // Cura interrotta: restano solo le dosi già registrate.
                if (!t.isActive && log == null) continue
                result += DayDose(
                    treatment = t,
                    dayNumber = dayNumber,
                    slotIndex = slot,
                    scheduledTime = time,
                    sortMinutes = TreatmentSchedulePeriod.parseScheduleTimeToMinutesOfDay(time) ?: Int.MAX_VALUE,
                    state = when {
                        log == null -> DoseState.PENDING
                        log.taken -> DoseState.TAKEN
                        else -> DoseState.SKIPPED
                    },
                    logId = log?.id,
                    takenAtEpochMillis = log?.takeIf { it.taken }?.takenAtEpochMillis,
                    isFutureDay = day.isAfter(today),
                    isPastDay = day.isBefore(today),
                )
            }
        }
        return result.sortedWith(compareBy<DayDose> { it.sortMinutes }.thenBy { it.treatment.drugName.lowercase() })
    }

    var confirm by remember { mutableStateOf<ConfirmRequest?>(null) }
    val dayFmt = remember(KBLocale.current()) { DateFormat.getBestDateTimePattern(KBLocale.current(), "EEEEdMMMM") }
    val dayYearFmt = remember(KBLocale.current()) { DateFormat.getBestDateTimePattern(KBLocale.current(), "EEEEdMMMMy") }
    fun dayTitle(day: LocalDate): String {
        val pattern = if (day.year == today.year) dayFmt else dayYearFmt
        val text = SimpleDateFormat(pattern, KBLocale.current()).format(Date.from(day.atStartOfDay(zone).toInstant()))
        return text.replaceFirstChar { it.uppercase() }
    }

    val selectedDoses = dosesOn(selectedDay)
    val doneCount = selectedDoses.count { it.state != DoseState.PENDING }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Intestazione: il giorno ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(kb.card)
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = pagerState.currentPage > 0) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val prevDesc = stringResource(R.string.treatments_day_prev)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = prevDesc,
                    tint = if (pagerState.currentPage > 0) PURPLE else PURPLE.copy(alpha = 0.3f),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable(enabled = selectedId != todayId) {
                        scope.launch { pagerState.animateScrollToPage(todayId - range.first) }
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val rel = when (selectedId - todayId) {
                    -1 -> stringResource(R.string.treatments_day_yesterday)
                    0 -> stringResource(R.string.treatments_day_today)
                    1 -> stringResource(R.string.treatments_day_tomorrow)
                    else -> null
                }
                if (rel != null) Text(rel, color = PURPLE, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    dayTitle(selectedDay),
                    color = PURPLE,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (selectedDoses.isEmpty()) {
                    Text(" ", fontSize = 12.sp)
                } else {
                    Text(
                        stringResource(R.string.treatments_day_doses_count, doneCount, selectedDoses.size),
                        color = if (doneCount == selectedDoses.size) GREEN else kb.subtitle,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .clickable(enabled = pagerState.currentPage < pagerState.pageCount - 1) {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                contentAlignment = Alignment.Center,
            ) {
                val nextDesc = stringResource(R.string.treatments_day_next)
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = nextDesc,
                    tint = if (pagerState.currentPage < pagerState.pageCount - 1) PURPLE else PURPLE.copy(alpha = 0.3f),
                )
            }
        }
        HorizontalDivider(color = kb.divider)

        // ── Pagine: un giorno ciascuna ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val id = range.first + page
            val day = LocalDate.ofEpochDay(id.toLong())
            val list = dosesOn(day)
            DayTimeline(
                day = day,
                isToday = id == todayId,
                doses = list,
                colorFor = ::colorFor,
                onOpenTreatment = onOpenTreatment,
                onSkip = { d -> onMarkSkipped(d.treatment, d.dayNumber, d.slotIndex, d.scheduledTime) },
                onTake = { d -> confirm = ConfirmRequest(d, dayTitle(day)) },
                onUndo = { d -> d.logId?.let(onUndo) },
            )
        }
    }

    confirm?.let { req ->
        val t = req.dose.treatment
        val v = t.dosageValue
        val amount = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
        ConfirmDoseSheet(
            drugName = t.drugName,
            dosageLabel = "$amount ${t.dosageUnit}",
            scheduleDayLabel = req.dayLabel,
            scheduledTime = req.dose.scheduledTime,
            onDismiss = { confirm = null },
            onConfirm = { at ->
                onMarkTaken(t, req.dose.dayNumber, req.dose.slotIndex, req.dose.scheduledTime, at)
                confirm = null
            },
        )
    }
}

@Composable
private fun DayTimeline(
    day: LocalDate,
    isToday: Boolean,
    doses: List<DayDose>,
    colorFor: (KBTreatment) -> Color,
    onOpenTreatment: (String) -> Unit,
    onSkip: (DayDose) -> Unit,
    onTake: (DayDose) -> Unit,
    onUndo: (DayDose) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val byHour = remember(doses) { doses.groupBy { it.hour } }
    val heights: List<Dp> = (0..23).map { h ->
        val n = byHour[h]?.size ?: 0
        maxOf(MIN_HOUR_HEIGHT, (CARD_HEIGHT + CARD_GAP) * n + CARD_GAP)
    }
    val tops: List<Dp> = heights.runningFold(0.dp) { acc, h -> acc + h }

    // Dove far partire la scroll: un'ora prima dell'ora attuale (oggi) o della prima dose.
    val startHour = if (isToday) {
        (LocalTime.now().hour - 1).coerceAtLeast(0)
    } else {
        doses.minOfOrNull { it.hour }?.let { (it - 1).coerceAtLeast(0) } ?: 7
    }
    val scroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(day, doses.isEmpty()) {
        scroll.scrollTo(with(density) { tops[startHour].roundToPx() })
    }

    var now by remember { mutableStateOf(LocalTime.now()) }
    if (isToday) {
        LaunchedEffect(Unit) {
            while (true) {
                delay(60_000)
                now = LocalTime.now()
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(scroll)) {
        if (doses.isEmpty()) {
            Text(
                stringResource(R.string.treatments_day_empty),
                color = kb.subtitle,
                fontSize = 14.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(12.dp),
            )
        }
        Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Column {
                for (h in 0..23) {
                    Row(modifier = Modifier.fillMaxWidth().height(heights[h])) {
                        Text(
                            String.format("%02d:00", h),
                            color = kb.subtitle,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .width(GUTTER_WIDTH)
                                .padding(end = 6.dp)
                                .offset(y = (-6).dp),
                        )
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .padding(end = 12.dp),
                        ) {
                            HorizontalDivider(color = kb.divider.copy(alpha = 0.5f), thickness = 0.5.dp)
                            Column(
                                modifier = Modifier.padding(top = CARD_GAP),
                                verticalArrangement = Arrangement.spacedBy(CARD_GAP),
                            ) {
                                byHour[h].orEmpty().forEach { dose ->
                                    DoseCard(
                                        dose = dose,
                                        accent = colorFor(dose.treatment),
                                        onOpen = { onOpenTreatment(dose.treatment.id) },
                                        onSkip = { onSkip(dose) },
                                        onTake = { onTake(dose) },
                                        onUndo = { onUndo(dose) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
            if (isToday) {
                // La linea rossa dell'ora attuale, come nel calendario.
                val y = tops[now.hour] + heights[now.hour] * (now.minute / 60f)
                Row(
                    modifier = Modifier.fillMaxWidth().offset(y = y - 3.5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Spacer(Modifier.width(GUTTER_WIDTH + 6.dp - 3.5.dp))
                    Box(Modifier.size(7.dp).clip(CircleShape).background(RED))
                    Box(Modifier.weight(1f).height(1.5.dp).background(RED))
                }
            }
        }
    }
}

@Composable
private fun DoseCard(
    dose: DayDose,
    accent: Color,
    onOpen: () -> Unit,
    onSkip: () -> Unit,
    onTake: () -> Unit,
    onUndo: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val t = dose.treatment
    val taken = dose.state == DoseState.TAKEN
    val skipped = dose.state == DoseState.SKIPPED
    val amount = run {
        val v = t.dosageValue
        val n = if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString()
        "$n ${t.dosageUnit}"
    }
    val overdue = dose.state == DoseState.PENDING && !dose.isFutureDay && (
        dose.isPastDay || (dose.sortMinutes != Int.MAX_VALUE && dose.sortMinutes < LocalTime.now().let { it.hour * 60 + it.minute })
        )
    val timeFmt = remember(KBLocale.current()) { SimpleDateFormat("HH:mm", KBLocale.current()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(CARD_HEIGHT)
            .clip(RoundedCornerShape(8.dp))
            .background(accent.copy(alpha = if (taken) 0.10f else 0.18f))
            .clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(3.5.dp).fillMaxHeight().background(accent))
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (taken) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = GREEN, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                } else if (skipped) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = ORANGE, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                }
                Text(
                    dose.scheduledTime,
                    color = kb.subtitle,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    t.drugName,
                    color = kb.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(amount, color = accent, fontSize = 12.sp, maxLines = 1)
                Spacer(Modifier.width(5.dp))
                when {
                    taken -> dose.takenAtEpochMillis?.let {
                        Text(
                            stringResource(R.string.treatments_day_taken_at, timeFmt.format(Date(it))),
                            color = GREEN,
                            fontSize = 12.sp,
                            maxLines = 1,
                        )
                    }
                    skipped -> Text(stringResource(R.string.health_skipped), color = ORANGE, fontSize = 12.sp, maxLines = 1)
                    overdue -> Text(stringResource(R.string.treatments_day_not_logged), color = RED, fontSize = 12.sp, maxLines = 1)
                    else -> {}
                }
            }
        }
        Spacer(Modifier.width(4.dp))
        when {
            taken || skipped -> {
                val color = if (taken) PURPLE else ORANGE
                val label = stringResource(if (taken) R.string.health_cancel else R.string.health_resume)
                Box(
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Transparent)
                        .clickable(onClick = onUndo)
                        .padding(horizontal = 8.dp, vertical = 5.dp),
                ) {
                    Text(label, color = color, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
            }
            dose.isFutureDay || !t.isActive -> {}
            else -> {
                val skipDesc = stringResource(R.string.health_mark_skipped)
                val takeDesc = stringResource(R.string.health_mark_taken)
                Row(
                    modifier = Modifier.padding(end = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFE0E0E0))
                            .clickable(onClick = onSkip)
                            .semantics { contentDescription = skipDesc },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, contentDescription = null, tint = Color(0xFF616161), modifier = Modifier.size(15.dp))
                    }
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(GREEN)
                            .clickable(onClick = onTake)
                            .semantics { contentDescription = takeDesc },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }
}
