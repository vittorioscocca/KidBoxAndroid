@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.health.treatments

import android.content.res.Configuration
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Icon
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.TreatmentSchedulePeriod
import it.vittorioscocca.kidbox.domain.model.schedulePeriodForTime
import it.vittorioscocca.kidbox.domain.model.schedulePeriodLabel
import it.vittorioscocca.kidbox.ui.screens.health.attachments.HealthAttachmentsCard
import it.vittorioscocca.kidbox.ui.screens.health.attachments.KidBoxDocumentPickerSheet
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.io.File
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val PURPLE_DETAIL = Color(0xFF9573D9)
private val GREEN_DETAIL = Color(0xFF4CAF50)
private val DATE_FMT_DAY = SimpleDateFormat("EEE d MMM", Locale.ITALIAN)
private val DATE_FMT_RANGE = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private val DOSE_TAKEN_DETAIL_FMT = SimpleDateFormat("d MMMM yyyy 'alle' HH:mm", Locale.ITALIAN)
private val LocaleItIT = Locale.forLanguageTag("it-IT")

/** Giorno terapia 1-based (allineato a iOS): 1 = primo giorno dalla data di inizio cura. */
private fun therapeuticDayNumber1Based(treatment: KBTreatment, nowMillis: Long = System.currentTimeMillis()): Int {
    val start = Calendar.getInstance().apply {
        timeInMillis = treatment.startDateEpochMillis
        stripToStartOfDay()
    }
    val today = Calendar.getInstance().apply {
        timeInMillis = nowMillis
        stripToStartOfDay()
    }
    val diffDays = ((today.timeInMillis - start.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
    val day = diffDays + 1
    return if (!treatment.isLongTerm) {
        day.coerceIn(1, treatment.durationDays)
    } else {
        day.coerceAtLeast(1)
    }
}

private fun Calendar.stripToStartOfDay() {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}

private fun dayListIndexForTherapeuticDay(wantDay: Int, days: List<DayEntry>): Int {
    if (days.isEmpty()) return 0
    val idx = days.indexOfFirst { it.dayNumber == wantDay }
    return if (idx >= 0) idx else (wantDay - 1).coerceIn(0, days.lastIndex)
}

private fun currentDayRingIndex(treatment: KBTreatment, days: List<DayEntry>): Int {
    if (days.isEmpty()) return 0
    val want = therapeuticDayNumber1Based(treatment)
    return dayListIndexForTherapeuticDay(want, days).coerceIn(0, days.lastIndex)
}

/** Giorno di calendario della cura dopo oggi (timezone dispositivo). */
private fun isTherapeuticCalendarDayFuture(dayMillis: Long): Boolean {
    val zone = ZoneId.systemDefault()
    val dayDate = Instant.ofEpochMilli(dayMillis).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(zone).toLocalDate()
    return dayDate.isAfter(today)
}

private data class ConfirmDoseUi(val slot: DoseSlot, val dayLabel: String, val dayDateMillis: Long)

@Composable
fun MedicalTreatmentDetailScreen(
    familyId: String,
    childId: String,
    treatmentId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: MedicalTreatmentDetailViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, treatmentId) { viewModel.bind(familyId, childId, treatmentId) }
    LaunchedEffect(state.isDeleted) { if (state.isDeleted) onBack() }
    LaunchedEffect(state.uploadError) {
        state.uploadError?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_LONG).show()
            viewModel.consumeUploadError()
        }
    }
    LaunchedEffect(state.openFileEvent) {
        state.openFileEvent?.let { (mime, file) ->
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            runCatching { context.startActivity(intent) }
            viewModel.consumeOpenFileEvent()
        }
    }

    val cameraFile = remember { File(File(context.cacheDir, "health-camera").apply { mkdirs() }, "treatment_camera_tmp.jpg") }
    val cameraUri = remember(cameraFile) { FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cameraFile) }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) viewModel.uploadAttachment(cameraUri)
    }
    val cameraPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            takePictureLauncher.launch(cameraUri)
        } else {
            Toast.makeText(context, "Permesso fotocamera negato", Toast.LENGTH_SHORT).show()
        }
    }
    val pickPhotoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let { viewModel.uploadAttachment(it) }
    }
    val pickFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { viewModel.uploadAttachment(it) }
    }
    var showKidBoxDocPicker by remember { mutableStateOf(false) }

    if (state.isLoading) {
        Box(modifier = Modifier.fillMaxSize().background(kb.background), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val treatment = state.treatment ?: run {
        Box(modifier = Modifier.fillMaxSize().background(kb.background), contentAlignment = Alignment.Center) {
            Text("Cura non trovata", color = kb.title)
        }
        return
    }

    val dosageStr = if (treatment.dosageValue % 1.0 == 0.0) "%.0f".format(treatment.dosageValue) else "%.1f".format(treatment.dosageValue)
    val allSlots = state.calendarDays.flatMap { it.slots }
    val totalDoses = allSlots.size
    val takenDoses = allSlots.count { it.state == DoseState.TAKEN }
    val progressPct = if (totalDoses > 0) ((takenDoses * 100f) / totalDoses).toInt() else 0
    val endMillis = treatment.endDateEpochMillis ?: (treatment.startDateEpochMillis + 24L * 60L * 60L * 1000L * (treatment.durationDays - 1))

    var selectedDayIndex by remember { mutableIntStateOf(0) }
    val dayRingScroll = rememberScrollState()
    val density = LocalDensity.current
    LaunchedEffect(treatment.id, state.calendarDays.size, density) {
        val days = state.calendarDays
        if (days.isEmpty()) return@LaunchedEffect
        selectedDayIndex = currentDayRingIndex(treatment, days).coerceAtMost(days.lastIndex)
        delay(120)
        val idx = selectedDayIndex
        val stepPx = with(density) { (52.dp + 8.dp).toPx().roundToInt() }
        dayRingScroll.scrollTo((idx * stepPx).coerceIn(0, dayRingScroll.maxValue))
    }
    if (state.calendarDays.isNotEmpty() && selectedDayIndex >= state.calendarDays.size) {
        selectedDayIndex = state.calendarDays.lastIndex
    }
    val selectedDay = state.calendarDays.getOrNull(selectedDayIndex)
    var confirmDose by remember { mutableStateOf<ConfirmDoseUi?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KidBoxHeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Indietro",
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            Text("Cura", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = kb.title)
            Spacer(Modifier.weight(1f))
            KidBoxHeaderCircleButton(
                icon = Icons.Default.Edit,
                contentDescription = "Modifica",
                onClick = onEdit,
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(colors = CardDefaults.cardColors(containerColor = kb.card), shape = RoundedCornerShape(18.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier.size(64.dp).clip(CircleShape).background(PURPLE_DETAIL.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Medication, contentDescription = null, tint = PURPLE_DETAIL, modifier = Modifier.size(30.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(treatment.drugName, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = kb.title)
                        if (treatment.reminderEnabled) {
                            Spacer(Modifier.width(6.dp))
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = PURPLE_DETAIL, modifier = Modifier.size(18.dp))
                        }
                    }
                    if (!treatment.activeIngredient.isNullOrBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(treatment.activeIngredient, fontSize = 14.sp, color = kb.subtitle)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("$dosageStr ${treatment.dosageUnit}", fontSize = 24.sp, fontWeight = FontWeight.SemiBold, color = PURPLE_DETAIL)
                    Text("${treatment.dailyFrequency} volte al giorno", fontSize = 14.sp, color = kb.subtitle)
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = kb.card), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                val progressTarget = if (totalDoses > 0) takenDoses.toFloat() / totalDoses.toFloat() else 0f
                val animatedProgress by animateFloatAsState(
                    targetValue = progressTarget.coerceIn(0f, 1f),
                    animationSpec = tween(durationMillis = 400),
                    label = "treatmentProgressRing",
                )
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Progresso", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = kb.title)
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(56.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (treatment.isLongTerm) {
                                Text(
                                    "∞",
                                    color = PURPLE_DETAIL,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                )
                            } else {
                                TreatmentProgressRing(
                                    progress = animatedProgress,
                                    trackColor = PURPLE_DETAIL.copy(alpha = 0.15f),
                                    progressColor = PURPLE_DETAIL,
                                    strokeWidth = 6.dp,
                                ) {
                                    Text(
                                        "$progressPct%",
                                        color = PURPLE_DETAIL,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                    )
                                }
                            }
                        }
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (treatment.isLongTerm) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("∞", color = PURPLE_DETAIL, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        "Cura a lungo termine",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp,
                                        color = kb.title,
                                    )
                                }
                                Text(
                                    "In corso dal ${DATE_FMT_RANGE.format(Date(treatment.startDateEpochMillis))}",
                                    fontSize = 12.sp,
                                    color = kb.subtitle,
                                )
                                Text(
                                    "$takenDoses dosi somministrate",
                                    fontSize = 12.sp,
                                    color = kb.subtitle,
                                )
                            } else {
                                val totalLabel = treatment.durationDays
                                Text(
                                    "Giorno ${selectedDay?.dayNumber ?: 1} di $totalLabel",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = kb.title,
                                )
                                Text(
                                    "${DATE_FMT_RANGE.format(Date(treatment.startDateEpochMillis))} – ${DATE_FMT_RANGE.format(Date(endMillis))}",
                                    fontSize = 12.sp,
                                    color = kb.subtitle,
                                )
                                Text(
                                    "$takenDoses/$totalDoses Dosi totali",
                                    fontSize = 12.sp,
                                    color = kb.subtitle,
                                )
                            }
                        }
                    }
                }
            }

            OutlinedButton(
                onClick = { viewModel.showExtendSheet() },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF5AAE7D).copy(alpha = 0.12f)),
            ) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF46996A))
                Spacer(Modifier.width(8.dp))
                Text("Estendi cura", color = Color(0xFF46996A), fontWeight = FontWeight.SemiBold)
            }

            Text("Orari somministrazione", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = kb.title)

            Row(modifier = Modifier.fillMaxWidth().horizontalScroll(dayRingScroll), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val ringDays = state.calendarDays
                val todayRingIndex =
                    if (ringDays.isEmpty()) 0 else currentDayRingIndex(treatment, ringDays).coerceAtMost(ringDays.lastIndex)
                ringDays.forEachIndexed { index, day ->
                    val selected = index == selectedDayIndex
                    val isTodayRing = index == todayRingIndex
                    Card(
                        onClick = { selectedDayIndex = index },
                        modifier = Modifier.border(
                            width = if (isTodayRing && !selected) 1.5.dp else 0.dp,
                            color = if (isTodayRing && !selected) PURPLE_DETAIL else Color.Transparent,
                            shape = RoundedCornerShape(10.dp),
                        ),
                        colors = CardDefaults.cardColors(containerColor = if (selected) PURPLE_DETAIL else kb.card),
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val d = Date(day.dateMillis)
                            Text(SimpleDateFormat("d", Locale.ITALIAN).format(d), color = if (selected) Color.White else kb.title, fontWeight = FontWeight.Bold)
                            Text(SimpleDateFormat("MMM", Locale.ITALIAN).format(d), color = if (selected) Color.White.copy(alpha = 0.9f) else kb.subtitle, fontSize = 11.sp)
                            val allDosesTaken = day.slots.isNotEmpty() &&
                                day.slots.size == treatment.dailyFrequency &&
                                day.slots.all { it.state == DoseState.TAKEN }
                            if (allDosesTaken) {
                                Spacer(Modifier.height(2.dp))
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    modifier = Modifier.size(12.dp),
                                    tint = if (selected) Color.White else GREEN_DETAIL,
                                )
                            }
                        }
                    }
                }
            }

            selectedDay?.let { day ->
                val dayIsFuture = isTherapeuticCalendarDayFuture(day.dateMillis)
                day.slots.forEach { slot ->
                    SlotCompactRow(
                        slot = slot,
                        registrationsAllowed = !dayIsFuture,
                        onOpenConfirmTaken = {
                            if (dayIsFuture) return@SlotCompactRow
                            confirmDose = ConfirmDoseUi(
                                slot = slot,
                                dayLabel = DATE_FMT_DAY.format(Date(day.dateMillis)),
                                dayDateMillis = day.dateMillis,
                            )
                        },
                        onSkipped = {
                            if (dayIsFuture) return@SlotCompactRow
                            viewModel.markSkipped(slot.dayNumber, slot.slotIndex, slot.scheduledTime)
                        },
                        onUndo = { slot.logId?.let(viewModel::clearLog) },
                        kb = kb,
                    )
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = kb.card), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Imposta gli orari per ${treatment.dailyFrequency} dosi giornaliere", color = kb.subtitle, fontSize = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    treatment.scheduleTimesData.split(",").map { it.trim() }.filter { it.isNotBlank() }.forEachIndexed { i, time ->
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            val p = schedulePeriodForTime(time, i)
                            if (p != null) TreatmentPeriodBadge(p) else NeutralPeriodBadge(schedulePeriodLabel(time, i), kb)
                            Spacer(Modifier.width(8.dp))
                            Spacer(Modifier.weight(1f))
                            Text(time, color = kb.title, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = PURPLE_DETAIL)
                        Spacer(Modifier.width(6.dp))
                        Text("Personalizza orari", color = PURPLE_DETAIL)
                    }
                }
            }

            Card(colors = CardDefaults.cardColors(containerColor = kb.card), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Notifications, contentDescription = null, tint = PURPLE_DETAIL, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Promemoria", color = PURPLE_DETAIL, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(if (treatment.reminderEnabled) "Promemoria attivo" else "Promemoria disattivato", color = kb.title)
                        Text("Notifica per ogni dose agli orari impostati", color = kb.subtitle, fontSize = 12.sp)
                    }
                    Switch(
                        checked = treatment.reminderEnabled,
                        onCheckedChange = { viewModel.setReminderEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = PURPLE_DETAIL),
                    )
                }
            }

            HealthAttachmentsCard(
                attachments = state.attachments,
                tintColor = PURPLE_DETAIL,
                isUploading = state.isUploading,
                onPickFile = { pickFileLauncher.launch(arrayOf("*/*")) },
                onPickPhoto = { pickPhotoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) },
                onTakePhoto = {
                    when {
                        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED -> takePictureLauncher.launch(cameraUri)
                        else -> cameraPermLauncher.launch(Manifest.permission.CAMERA)
                    }
                },
                onOpenAttachment = { viewModel.openAttachment(it) },
                onDeleteAttachment = { viewModel.deleteAttachment(it) },
                onPickFromKidBoxDocuments = { showKidBoxDocPicker = true },
            )
            Text(
                "Visibili anche in Documenti > Salute > Referti",
                color = kb.subtitle,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedButton(
                onClick = { viewModel.setActive(false) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFFFFA726).copy(alpha = 0.12f)),
            ) {
                Icon(Icons.Default.PauseCircle, contentDescription = null, tint = Color(0xFFE68A00))
                Spacer(Modifier.width(8.dp))
                Text("Interrompi cura", color = Color(0xFFE68A00), fontWeight = FontWeight.SemiBold)
            }

            Button(
                onClick = { viewModel.showDeleteDialog() },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F).copy(alpha = 0.12f)),
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, tint = Color(0xFFD32F2F))
                Spacer(Modifier.width(8.dp))
                Text("Elimina", color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (state.showExtendSheet) {
        ExtendSheet(
            onDismiss = { viewModel.dismissExtendSheet() },
            onExtend = { days -> viewModel.extend(days) },
        )
    }

    if (state.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissDeleteDialog() },
            title = { Text("Elimina cura") },
            text = { Text("Eliminare la cura? Tutte le notifiche verranno cancellate.") },
            confirmButton = {
                TextButton(onClick = { viewModel.delete() }) {
                    Text("Elimina", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissDeleteDialog() }) { Text("Annulla") }
            },
        )
    }

    if (showKidBoxDocPicker) {
        KidBoxDocumentPickerSheet(
            familyId = familyId,
            onDismiss = { showKidBoxDocPicker = false },
            onPickedUri = { viewModel.uploadAttachment(it) },
        )
    }

    confirmDose?.let { ctx ->
        ConfirmDoseSheet(
            drugName = treatment.drugName,
            dosageLabel = "$dosageStr ${treatment.dosageUnit}",
            scheduleDayLabel = ctx.dayLabel,
            scheduledTime = ctx.slot.scheduledTime,
            onDismiss = { confirmDose = null },
            onConfirm = { at ->
                if (!isTherapeuticCalendarDayFuture(ctx.dayDateMillis)) {
                    viewModel.markTaken(ctx.slot.dayNumber, ctx.slot.slotIndex, ctx.slot.scheduledTime, at)
                }
                confirmDose = null
            },
        )
    }
}

@Composable
private fun TreatmentProgressRing(
    progress: Float,
    trackColor: Color,
    progressColor: Color,
    strokeWidth: Dp,
    modifier: Modifier = Modifier.size(56.dp),
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val strokePx = strokeWidth.toPx()
            val diameter = (size.minDimension - strokePx).coerceAtLeast(0f)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
            drawArc(
                color = progressColor,
                startAngle = -90f,
                sweepAngle = 360f * progress.coerceIn(0f, 1f),
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round),
            )
        }
        content()
    }
}

@Composable
private fun TreatmentPeriodBadge(period: TreatmentSchedulePeriod) {
    val (bg, fg) = periodBadgeColors(period)
    Surface(shape = RoundedCornerShape(50), color = bg, shadowElevation = 0.dp) {
        Text(
            period.labelIt,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = fg,
        )
    }
}

private fun periodBadgeColors(period: TreatmentSchedulePeriod): Pair<Color, Color> =
    when (period) {
        TreatmentSchedulePeriod.MATTINA -> Color(0xFFFEF9C3) to Color(0xFF854D0E)
        TreatmentSchedulePeriod.PRANZO -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        TreatmentSchedulePeriod.SERA -> Color(0xFFFFEDD5) to Color(0xFFC2410C)
        TreatmentSchedulePeriod.NOTTE -> Color(0xFFE9D5FF) to Color(0xFF581C87)
    }

@Composable
private fun NeutralPeriodBadge(text: String, kb: KidBoxColorScheme) {
    Surface(shape = RoundedCornerShape(50), color = kb.subtitle.copy(alpha = 0.12f), shadowElevation = 0.dp) {
        Text(
            text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = kb.title,
        )
    }
}

@Composable
private fun SlotCompactRow(
    slot: DoseSlot,
    registrationsAllowed: Boolean,
    onOpenConfirmTaken: () -> Unit,
    onSkipped: () -> Unit,
    onUndo: () -> Unit,
    kb: KidBoxColorScheme,
) {
    Card(colors = CardDefaults.cardColors(containerColor = kb.card), shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            val leadBg = when (slot.state) {
                DoseState.TAKEN -> PURPLE_DETAIL.copy(alpha = 0.12f)
                DoseState.SKIPPED -> Color(0xFFFF9800).copy(alpha = 0.12f)
                DoseState.PENDING -> kb.subtitle.copy(alpha = 0.14f)
            }
            val leadIcon = when (slot.state) {
                DoseState.TAKEN -> Icons.Default.Check to PURPLE_DETAIL
                DoseState.SKIPPED -> Icons.Default.Close to Color(0xFFE65100)
                DoseState.PENDING -> Icons.Default.Schedule to kb.subtitle
            }
            Box(
                modifier = Modifier.size(36.dp).clip(CircleShape).background(leadBg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(leadIcon.first, contentDescription = null, tint = leadIcon.second, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val p = slot.displayPeriod
                    if (p != null) TreatmentPeriodBadge(p) else NeutralPeriodBadge(slot.label, kb)
                    Spacer(Modifier.width(8.dp))
                    Text(slot.scheduledTime, fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 18.sp)
                }
                when (slot.state) {
                    DoseState.PENDING -> {
                        if (!registrationsAllowed) {
                            Text("Giorno futuro: non puoi registrare assunzioni.", color = kb.subtitle, fontSize = 13.sp)
                        } else {
                            Text("Da prendere", color = kb.subtitle, fontSize = 14.sp)
                        }
                    }
                    DoseState.TAKEN -> {
                        val at = slot.takenAtEpochMillis
                        if (at != null) {
                            Text(DOSE_TAKEN_DETAIL_FMT.format(Date(at)), color = GREEN_DETAIL, fontSize = 13.sp)
                        } else {
                            Text("Assunta", color = GREEN_DETAIL, fontSize = 14.sp)
                        }
                    }
                    DoseState.SKIPPED -> Text("Saltata", color = Color(0xFFE65100), fontSize = 14.sp)
                }
            }
            when (slot.state) {
                DoseState.TAKEN, DoseState.SKIPPED -> {
                    val (label, color) = when (slot.state) {
                        DoseState.TAKEN -> "Annulla" to PURPLE_DETAIL
                        else -> "Riprendi" to Color(0xFFE65100)
                    }
                    OutlinedButton(
                        onClick = onUndo,
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(36.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = color),
                        border = BorderStroke(1.dp, color.copy(alpha = 0.45f)),
                    ) {
                        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
                DoseState.PENDING -> {
                    if (registrationsAllowed) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            DoseActionCircle(
                                onClick = onSkipped,
                                containerColor = Color(0xFFE0E0E0),
                                actionLabel = "Segna saltata",
                            ) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(16.dp))
                            }
                            DoseActionCircle(
                                onClick = onOpenConfirmTaken,
                                containerColor = GREEN_DETAIL,
                                actionLabel = "Segna assunta",
                            ) {
                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDoseSheet(
    drugName: String,
    dosageLabel: String,
    scheduleDayLabel: String,
    scheduledTime: String,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var takenAtMillis by remember { mutableStateOf(System.currentTimeMillis()) }
    var selectedQuickOffset by remember { mutableStateOf(0L) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    val quickOptions = listOf(
        "Adesso" to 0L,
        "30 min fa" to -30L * 60 * 1000,
        "1 ora fa" to -60L * 60 * 1000,
        "2 ore fa" to -120L * 60 * 1000,
    )

    fun applyQuick(offset: Long) {
        val now = System.currentTimeMillis()
        takenAtMillis = (now + offset).coerceIn(0L, now)
        selectedQuickOffset = offset
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .widthIn(max = 400.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(kb.card)
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) { Text("Annulla") }
                Spacer(Modifier.weight(1f))
                Text("Conferma dose", fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 16.sp)
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.width(64.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(56.dp).clip(CircleShape).background(PURPLE_DETAIL.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Medication, contentDescription = null, tint = PURPLE_DETAIL, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(drugName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = kb.title)
                Text(dosageLabel, color = PURPLE_DETAIL, fontSize = 14.sp)
                Text("$scheduleDayLabel · $scheduledTime", fontSize = 12.sp, color = kb.subtitle)
                Spacer(Modifier.height(6.dp))
                val fasciaRegistrata = remember(takenAtMillis) { TreatmentSchedulePeriod.fromEpochMillis(takenAtMillis) }
                TreatmentPeriodBadge(fasciaRegistrata)
                Text("Quando hai dato la medicina?", fontSize = 13.sp, color = kb.subtitle)
            }
            Text("SELEZIONE RAPIDA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = kb.subtitle, letterSpacing = 0.6.sp)
            quickOptions.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    pair.forEach { (label, offset) ->
                        val sel = selectedQuickOffset == offset
                        OutlinedButton(
                            onClick = { applyQuick(offset) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = if (sel) GREEN_DETAIL else kb.title,
                                containerColor = if (sel) GREEN_DETAIL.copy(alpha = 0.1f) else kb.background,
                            ),
                            border = BorderStroke(
                                width = if (sel) 1.5.dp else 1.dp,
                                color = if (sel) GREEN_DETAIL else kb.subtitle.copy(alpha = 0.3f),
                            ),
                        ) { Text(label, fontWeight = FontWeight.SemiBold, fontSize = 14.sp) }
                    }
                }
            }
            Text("DATA E ORA", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = kb.subtitle, letterSpacing = 0.6.sp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        selectedQuickOffset = Long.MIN_VALUE
                        showDatePicker = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Data") }
                OutlinedButton(
                    onClick = {
                        selectedQuickOffset = Long.MIN_VALUE
                        showTimePicker = true
                    },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) { Text("Ora") }
            }
            Text(
                DOSE_TAKEN_DETAIL_FMT.format(Date(takenAtMillis)),
                fontSize = 13.sp,
                color = kb.subtitle,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onConfirm(takenAtMillis.coerceAtMost(System.currentTimeMillis())) },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = GREEN_DETAIL),
            ) {
                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Conferma dose", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }

    if (showDatePicker) {
        ConfirmDoseDatePickerDialog(
            initialMillis = takenAtMillis,
            onDismiss = { showDatePicker = false },
            onConfirm = { utcDay ->
                takenAtMillis = mergeCalendarDayFromPicker(utcDay, takenAtMillis)
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        val cal = Calendar.getInstance().apply { timeInMillis = takenAtMillis }
        val pickerState = rememberTimePickerState(
            initialHour = cal.get(Calendar.HOUR_OF_DAY),
            initialMinute = cal.get(Calendar.MINUTE),
            is24Hour = true,
        )
        val timeConfig = Configuration(LocalConfiguration.current).apply { setLocale(LocaleItIT) }
        Dialog(onDismissRequest = { showTimePicker = false }) {
            CompositionLocalProvider(LocalConfiguration provides timeConfig) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(kb.card)
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    TimePicker(state = pickerState)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showTimePicker = false }) { Text("Annulla") }
                        TextButton(onClick = {
                            takenAtMillis = mergeLocalTime(takenAtMillis, pickerState.hour, pickerState.minute)
                            showTimePicker = false
                        }) { Text("OK") }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConfirmDoseDatePickerDialog(initialMillis: Long, onDismiss: () -> Unit, onConfirm: (Long) -> Unit) {
    val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
    val dateConfig = Configuration(LocalConfiguration.current).apply { setLocale(LocaleItIT) }
    CompositionLocalProvider(LocalConfiguration provides dateConfig) {
        DatePickerDialog(
            onDismissRequest = onDismiss,
            confirmButton = {
                TextButton(onClick = { pickerState.selectedDateMillis?.let(onConfirm) ?: onDismiss() }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
        ) { DatePicker(state = pickerState) }
    }
}

private fun mergeCalendarDayFromPicker(utcSelectedDayMillis: Long, keepLocalMillis: Long): Long {
    val keep = Calendar.getInstance()
    keep.timeInMillis = keepLocalMillis
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    utc.timeInMillis = utcSelectedDayMillis
    val out = Calendar.getInstance()
    out.set(Calendar.YEAR, utc.get(Calendar.YEAR))
    out.set(Calendar.MONTH, utc.get(Calendar.MONTH))
    out.set(Calendar.DAY_OF_MONTH, utc.get(Calendar.DAY_OF_MONTH))
    out.set(Calendar.HOUR_OF_DAY, keep.get(Calendar.HOUR_OF_DAY))
    out.set(Calendar.MINUTE, keep.get(Calendar.MINUTE))
    out.set(Calendar.SECOND, 0)
    out.set(Calendar.MILLISECOND, 0)
    val now = System.currentTimeMillis()
    return out.timeInMillis.coerceIn(0L, now)
}

private fun mergeLocalTime(keepLocalMillis: Long, hour: Int, minute: Int): Long {
    val c = Calendar.getInstance()
    c.timeInMillis = keepLocalMillis
    c.set(Calendar.HOUR_OF_DAY, hour)
    c.set(Calendar.MINUTE, minute)
    c.set(Calendar.SECOND, 0)
    c.set(Calendar.MILLISECOND, 0)
    val now = System.currentTimeMillis()
    return c.timeInMillis.coerceIn(0L, now)
}

@Composable
private fun DoseActionCircle(
    onClick: () -> Unit,
    containerColor: Color,
    actionLabel: String,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(32.dp)
            .semantics { contentDescription = actionLabel }
            .clip(CircleShape)
            .background(containerColor)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

@Composable
private fun ExtendSheet(onDismiss: () -> Unit, onExtend: (Int) -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    var customDays by remember { androidx.compose.runtime.mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(kb.card).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Estendi cura", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = kb.title)
            listOf(3 to "+3 giorni", 7 to "+7 giorni", 14 to "+14 giorni").forEach { (days, label) ->
                Button(
                    onClick = { onExtend(days) },
                    colors = ButtonDefaults.buttonColors(containerColor = PURPLE_DETAIL),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(label, color = Color.White) }
            }
            androidx.compose.material3.OutlinedTextField(
                value = customDays,
                onValueChange = { customDays = it },
                placeholder = { Text("Giorni personalizzati") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Annulla") }
                TextButton(onClick = {
                    val d = customDays.toIntOrNull()
                    if (d != null && d > 0) onExtend(d) else onDismiss()
                }) { Text("Conferma") }
            }
        }
    }
}
