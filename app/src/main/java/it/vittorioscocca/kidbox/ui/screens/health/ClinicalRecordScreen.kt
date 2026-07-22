package it.vittorioscocca.kidbox.ui.screens.health

import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Biotech
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Medication
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordAIUsageInfo
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordSection
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordSnapshot
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordTextSanitizer
import it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordTopicBuilder
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private val CLINICAL_TINT = Color(0xFF738FE6)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClinicalRecordScreen(
    familyId: String,
    childId: String,
    subjectName: String,
    onBack: () -> Unit,
    viewModel: ClinicalRecordViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, subjectName) {
        viewModel.bind(familyId, childId, subjectName)
    }
    LaunchedEffect(state.exportPdfIntent) {
        val intent = state.exportPdfIntent ?: return@LaunchedEffect
        runCatching { context.startActivity(intent) }
            .onFailure {
                Toast.makeText(context, context.getString(R.string.health_install_pdf_reader), Toast.LENGTH_LONG).show()
            }
        viewModel.consumeExportIntent()
    }
    LaunchedEffect(state.message) {
        state.message?.let { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (state.selectedArea != null) {
        ModalBottomSheet(onDismissRequest = { viewModel.dismissSection() }, sheetState = sheetState) {
            SectionDetailContent(state.selectedArea!!, kb)
        }
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
                stringResource(R.string.health_clinical_record),
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = kb.title,
            )
            Spacer(Modifier.weight(1f))
            Spacer(Modifier.width(40.dp))
        }

        Box(modifier = Modifier.weight(1f)) {
            when {
                state.isRefreshing && state.snapshot == null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator()
                        Text(
                            state.refreshStatusMessage,
                            fontSize = 14.sp,
                            color = kb.subtitle,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        )
                    }
                }
                state.snapshot?.hasAnyData == true -> {
                    ClinicalRecordContent(
                        snapshot = state.snapshot!!,
                        visitCount = state.visitCount,
                        examCount = state.examCount,
                        treatmentCount = state.treatmentCount,
                        lastAIUsage = state.lastAIUsage,
                        pendingAIUnits = state.pendingAIUnits,
                        kb = kb,
                        onSectionClick = { viewModel.openSection(it) },
                    )
                }
                else -> ClinicalRecordEmptyState(
                    kb = kb,
                    isRefreshing = state.isRefreshing,
                    refreshMessage = state.refreshStatusMessage,
                )
            }
            if (state.isRefreshing && state.snapshot != null) {
                RefreshingOverlay(message = state.refreshStatusMessage, kb = kb)
            }
        }

        Surface(shadowElevation = 8.dp) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedButton(
                    onClick = { viewModel.refreshContent() },
                    enabled = !state.isRefreshing && !state.isExporting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.isRefreshing) stringResource(R.string.health_updating) else stringResource(R.string.health_refresh))
                }
                Button(
                    onClick = { viewModel.exportPdf() },
                    enabled = (state.snapshot?.hasAnyData == true) && !state.isRefreshing && !state.isExporting,
                    modifier = Modifier.weight(1f),
                ) {
                    if (state.isExporting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(if (state.isExporting) stringResource(R.string.health_exporting) else stringResource(R.string.health_export_pdf))
                }
            }
        }
    }
}

@Composable
private fun SectionDetailContent(area: it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordReportArea, kb: KidBoxColorScheme) {
    Column(modifier = Modifier.padding(20.dp).fillMaxWidth()) {
        Text(area.title, fontWeight = FontWeight.Bold, fontSize = 20.sp, color = kb.title)
        Spacer(Modifier.height(8.dp))
        area.analisiNarrativa?.takeIf { it.isNotBlank() }?.let { narrative ->
            val synthTitle = when (area.id) {
                ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY.raw,
                ClinicalRecordTopicBuilder.TopicId.UROLOGY.raw -> stringResource(R.string.health_reports_summary)
                else -> stringResource(R.string.health_trend_summary)
            }
            Text(synthTitle, fontWeight = FontWeight.SemiBold, color = CLINICAL_TINT)
            Spacer(Modifier.height(4.dp))
            Text(ClinicalRecordTextSanitizer.sanitize(narrative), fontSize = 14.sp, color = kb.title)
            Spacer(Modifier.height(12.dp))
        }
        area.parameters?.forEach { param ->
            Text(param.name, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, color = kb.title)
            param.points.forEach { pt ->
                Text("• ${pt.displayValue} (${formatRefresh(pt.dateEpochMillis)})", fontSize = 12.sp, color = kb.subtitle)
            }
            Spacer(Modifier.height(8.dp))
        }
        area.trendNarrative?.takeIf { it.isNotBlank() }?.let { trend ->
            Text(stringResource(R.string.health_trend_over_time), fontWeight = FontWeight.SemiBold, color = CLINICAL_TINT)
            Spacer(Modifier.height(4.dp))
            Text(ClinicalRecordTextSanitizer.sanitize(trend), fontSize = 14.sp, color = kb.title)
            Spacer(Modifier.height(12.dp))
        }
        val timelineTitle = when (area.id) {
            ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY.raw,
            ClinicalRecordTopicBuilder.TopicId.UROLOGY.raw -> stringResource(R.string.health_reports_over_time)
            else -> stringResource(R.string.section_history)
        }
        Text(timelineTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = kb.title)
        Spacer(Modifier.height(8.dp))
        Text(ClinicalRecordTextSanitizer.sanitize(area.narrative), fontSize = 14.sp, color = kb.title)
        area.bullets.forEach { b ->
            Spacer(Modifier.height(6.dp))
            Text(ClinicalRecordTextSanitizer.sanitize(b), fontSize = 14.sp, color = kb.subtitle)
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ClinicalRecordContent(
    snapshot: ClinicalRecordSnapshot,
    visitCount: Int,
    examCount: Int,
    treatmentCount: Int,
    lastAIUsage: ClinicalRecordAIUsageInfo?,
    pendingAIUnits: Int?,
    kb: KidBoxColorScheme,
    onSectionClick: (String) -> Unit,
) {
    val scroll = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scroll)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(4.dp))
        HeaderCard(snapshot, kb)
        when {
            lastAIUsage != null -> AiUsageBanner(lastAIUsage, kb)
            pendingAIUnits != null && pendingAIUnits > 1 -> PendingAiUsageBanner(pendingAIUnits, kb)
        }
        snapshot.globalSummary?.let { GlobalSummaryCard(it, kb) }
        OverviewRow(snapshot, visitCount, examCount, treatmentCount, kb)
        snapshot.sections.forEach { section ->
            SectionCard(section, kb, onClick = { onSectionClick(section.id) })
        }
        Text(
            stringResource(R.string.health_topic_hint),
            fontSize = 12.sp,
            color = kb.subtitle,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
        )
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun AiUsageBanner(usage: ClinicalRecordAIUsageInfo, kb: KidBoxColorScheme) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CLINICAL_TINT.copy(alpha = 0.12f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CLINICAL_TINT, modifier = Modifier.size(18.dp))
                Text(usage.usageSummary, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = kb.title)
            }
            usage.largeContextNotice?.let {
                Text(it, fontSize = 11.sp, color = kb.subtitle)
            }
        }
    }
}

@Composable
private fun PendingAiUsageBanner(units: Int, kb: KidBoxColorScheme) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CLINICAL_TINT.copy(alpha = 0.08f)),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CLINICAL_TINT, modifier = Modifier.size(18.dp))
            Text(
                "Questa generazione userà circa $units messaggi AI.",
                fontSize = 12.sp,
                color = kb.subtitle,
            )
        }
    }
}

@Composable
private fun GlobalSummaryCard(global: it.vittorioscocca.kidbox.data.health.clinical.ClinicalRecordGlobalSummary, kb: KidBoxColorScheme) {
    Card(colors = CardDefaults.cardColors(containerColor = kb.card), shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.health_clinical_summary), fontWeight = FontWeight.Bold, color = kb.title)
            Text("${global.monitoredSpecialtiesCount} aree · ${global.attentionCount} da monitorare", fontSize = 12.sp, color = kb.subtitle)
            global.statusLines.take(4).forEach { row ->
                Text("${row.status.emoji} ${row.specialtyTitle}: ${row.headline}", fontSize = 12.sp, color = kb.title)
            }
            Text(stringResource(R.string.health_not_medical_advice), fontSize = 11.sp, color = kb.subtitle)
        }
    }
}

@Composable
private fun HeaderCard(snapshot: ClinicalRecordSnapshot, kb: KidBoxColorScheme) {
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(CLINICAL_TINT.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text("🧑", fontSize = 26.sp)
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(snapshot.subjectName, fontWeight = FontWeight.Bold, fontSize = 18.sp, color = kb.title)
                snapshot.ageDescription?.let {
                    Text(it, fontSize = 14.sp, color = kb.subtitle)
                }
                Text(
                    "Generata ${formatRefresh(snapshot.refreshedAtEpochMillis)} — tocca «Aggiorna» per rigenerare",
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
                if (snapshot.reportSourceAiEnhanced) {
                    Text(
                        stringResource(R.string.health_ai_narrative),
                        fontSize = 11.sp,
                        color = CLINICAL_TINT,
                    )
                }
            }
            Icon(Icons.Default.Folder, contentDescription = null, tint = CLINICAL_TINT, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun OverviewRow(
    snapshot: ClinicalRecordSnapshot,
    visitCount: Int,
    examCount: Int,
    treatmentCount: Int,
    kb: KidBoxColorScheme,
) {
    val filled = snapshot.sections.count { !it.isEmpty }
    val pending = snapshot.sections.firstOrNull { it.id == "pending" }?.badgeCount ?: 0
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OverviewPill("$filled", "argomenti", kb, Modifier.weight(1f))
        OverviewPill("$treatmentCount", "terapie", kb, Modifier.weight(1f))
        OverviewPill("$pending", stringResource(R.string.health_pending), kb, Modifier.weight(1f))
        OverviewPill("$examCount", stringResource(R.string.health_archived_exams), kb, Modifier.weight(1f))
    }
}

@Composable
private fun OverviewPill(value: String, label: String, kb: KidBoxColorScheme, modifier: Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = CLINICAL_TINT)
            Text(label, fontSize = 11.sp, color = kb.subtitle)
        }
    }
}

@Composable
private fun SectionCard(section: ClinicalRecordSection, kb: KidBoxColorScheme, onClick: () -> Unit) {
    val tint = Color(section.tintArgb)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(tint.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(sectionIcon(section.iconKey), contentDescription = null, tint = tint)
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(section.title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = kb.title)
                        section.overallStatus?.let { st ->
                            Text("${st.emoji} ${st.badgeLabel}", fontSize = 11.sp, color = kb.subtitle)
                        }
                    }
                    Text(
                        section.summary,
                        fontSize = 12.sp,
                        color = if (section.isEmpty) kb.subtitle else tint,
                    )
                }
                if (!section.isEmpty && section.badgeCount != null && section.badgeCount > 0) {
                    Surface(color = tint, shape = CircleShape) {
                        Text(
                            "${section.badgeCount}",
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = kb.subtitle)
            }
            if (section.highlights.isEmpty()) {
                Text(
                    stringResource(R.string.health_no_data_section),
                    fontSize = 12.sp,
                    color = kb.subtitle,
                    modifier = Modifier.padding(start = 56.dp),
                )
            } else {
                section.highlights.forEach { line ->
                    Row(modifier = Modifier.padding(start = 8.dp)) {
                        Box(
                            modifier = Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(tint.copy(alpha = 0.5f)),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(line, fontSize = 14.sp, color = kb.title, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun RefreshingOverlay(message: String, kb: KidBoxColorScheme) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 28.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                CircularProgressIndicator()
                Text(
                    message,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = kb.title,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun ClinicalRecordEmptyState(
    kb: KidBoxColorScheme,
    isRefreshing: Boolean = false,
    refreshMessage: String = stringResource(R.string.health_integrating),
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (isRefreshing) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                refreshMessage,
                textAlign = TextAlign.Center,
                color = kb.subtitle,
                fontSize = 14.sp,
            )
        } else {
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(CLINICAL_TINT.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, tint = CLINICAL_TINT, modifier = Modifier.size(40.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.health_empty_record), fontWeight = FontWeight.Bold, fontSize = 18.sp, color = kb.title)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.health_empty_record_hint),
                textAlign = TextAlign.Center,
                color = kb.subtitle,
                fontSize = 14.sp,
            )
        }
    }
}

private fun sectionIcon(key: String): ImageVector = when (key) {
    "doc" -> Icons.Default.Description
    "heart" -> Icons.Default.MonitorHeart
    "pill" -> Icons.Default.Medication
    "vaccine" -> Icons.Default.Vaccines
    "visit" -> Icons.Default.MonitorHeart
    "exam" -> Icons.Default.Biotech
    else -> Icons.Default.Folder
}

private fun formatRefresh(epoch: Long): String {
    val fmt = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT, Locale.getDefault())
    return fmt.format(Date(epoch))
}
