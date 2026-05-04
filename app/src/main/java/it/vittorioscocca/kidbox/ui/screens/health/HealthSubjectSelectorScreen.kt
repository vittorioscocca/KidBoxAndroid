package it.vittorioscocca.kidbox.ui.screens.health

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Locale

private data class PendingChildMetric(val childId: String, val isWeight: Boolean)

private val WEIGHT_BLUE = Color(0xFF2196F3)
private val HEIGHT_GREEN = Color(0xFF4CAF50)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthSubjectSelectorScreen(
    familyId: String,
    onBack: () -> Unit,
    onSelect: (childId: String) -> Unit,
    viewModel: HealthSubjectSelectorViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var pendingMetric by remember { mutableStateOf<PendingChildMetric?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LaunchedEffect(familyId) { viewModel.load(familyId) }

    LaunchedEffect(state.subjects.size, state.isLoading) {
        if (!state.isLoading && state.subjects.size == 1) {
            onSelect(state.subjects.first().id)
        }
    }

    val pending = pendingMetric
    val sheetSubject = pending?.let { p ->
        state.subjects.find { it.id == p.childId && it.isChild }
    }

    LaunchedEffect(pending, sheetSubject) {
        if (pending != null && sheetSubject == null) {
            pendingMetric = null
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
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                KidBoxHeaderCircleButton(
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Indietro",
                    onClick = onBack,
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Salute",
                fontSize = 40.sp,
                fontWeight = FontWeight.ExtraBold,
                color = kb.title,
            )
            Spacer(Modifier.height(20.dp))

            when {
                state.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 40.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
                state.subjects.isEmpty() -> EmptySubjects()
                else -> {
                    val children = state.subjects.filter { it.isChild }
                    val adults = state.subjects.filter { !it.isChild }

                    if (children.isNotEmpty()) {
                        SectionHeader("Bambini")
                        for (s in children) {
                            ChildSubjectCard(
                                subject = s,
                                onOpenHealth = { onSelect(s.id) },
                                onWeightChip = { pendingMetric = PendingChildMetric(s.id, isWeight = true) },
                                onHeightChip = { pendingMetric = PendingChildMetric(s.id, isWeight = false) },
                            )
                            Spacer(Modifier.height(10.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    if (adults.isNotEmpty()) {
                        SectionHeader("Adulti")
                        for (s in adults) {
                            AdultSubjectCard(subject = s, onClick = { onSelect(s.id) })
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
            Spacer(Modifier.height(40.dp))
        }

        if (pending != null && sheetSubject != null) {
            val pm = pending
            val sj = sheetSubject
            ModalBottomSheet(
                onDismissRequest = { pendingMetric = null },
                sheetState = sheetState,
                containerColor = kb.card,
                dragHandle = {
                    HorizontalDivider(
                        Modifier.padding(vertical = 8.dp),
                        color = kb.subtitle.copy(alpha = 0.2f),
                    )
                },
            ) {
                ChildMeasurementSheet(
                    isWeight = pm.isWeight,
                    initialKg = sj.weightKg,
                    initialCm = sj.heightCm,
                    onDismiss = { pendingMetric = null },
                    onSave = { value ->
                        if (pm.isWeight) {
                            viewModel.saveChildWeightKg(familyId, pm.childId, value)
                        } else {
                            viewModel.saveChildHeightCm(familyId, pm.childId, value)
                        }
                        pendingMetric = null
                    },
                )
            }
        }
    }
}

@Composable
private fun ChildMeasurementSheet(
    isWeight: Boolean,
    initialKg: Double?,
    initialCm: Double?,
    onDismiss: () -> Unit,
    onSave: (Double) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val title = if (isWeight) "Peso" else "Altezza"
    val unitLabel = if (isWeight) "Peso (kg)" else "Altezza (cm)"
    val placeholder = if (isWeight) "es. 12.5" else "es. 90"
    val seedText = remember(isWeight, initialKg, initialCm) {
        if (isWeight) {
            initialKg?.let { String.format(Locale.ITALY, "%.1f", it) } ?: ""
        } else {
            initialCm?.let { String.format(Locale.ITALY, "%.0f", it) } ?: ""
        }
    }
    var text by remember(isWeight, initialKg, initialCm) { mutableStateOf(seedText) }

    val parsed = remember(text, isWeight) { parseMetricInput(text, isWeight) }
    val canSave = parsed != null

    Column(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SheetPillButton(onClick = onDismiss, label = "Annulla")
            Text(
                title,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                color = kb.title,
            )
            SheetPillButton(
                onClick = { parsed?.let(onSave) },
                label = "Salva",
                enabled = canSave,
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(
            unitLabel,
            fontSize = 13.sp,
            color = kb.subtitle,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        val fieldBg = kb.background
        OutlinedTextField(
            value = text,
            onValueChange = { t ->
                text = t.filter { it.isDigit() || it == '.' || it == ',' }
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(placeholder, color = kb.subtitle) },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = fieldBg,
                unfocusedContainerColor = fieldBg,
                disabledContainerColor = fieldBg,
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                cursorColor = kb.title,
                focusedTextColor = kb.title,
                unfocusedTextColor = kb.title,
            ),
        )
    }
}

@Composable
private fun SheetPillButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = kb.subtitle.copy(alpha = 0.12f),
        modifier = Modifier.width(88.dp),
    ) {
        TextButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                color = if (enabled) kb.title else kb.subtitle.copy(alpha = 0.45f),
            )
        }
    }
}

private fun parseMetricInput(raw: String, isWeight: Boolean): Double? {
    val t = raw.trim().replace(',', '.')
    if (t.isEmpty()) return null
    val v = t.toDoubleOrNull() ?: return null
    if (!v.isFinite() || v <= 0.0) return null
    return if (isWeight) {
        if (v > 200.0) null else v
    } else {
        if (v > 250.0) null else v
    }
}

@Composable
private fun SectionHeader(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Text(
        text,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        color = kb.subtitle,
    )
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun ChildSubjectCard(
    subject: HealthSubject,
    onOpenHealth: () -> Unit,
    onWeightChip: () -> Unit,
    onHeightChip: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = kb.card,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onOpenHealth),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(orange.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChildCare,
                            contentDescription = null,
                            tint = orange,
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            subject.name,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 16.sp,
                            color = kb.title,
                        )
                        val ageLine = remember(subject.birthDateEpochMillis) {
                            childAgeSummaryItalian(subject.birthDateEpochMillis)
                        }
                        if (ageLine != null) {
                            Spacer(Modifier.height(4.dp))
                            Text(
                                ageLine,
                                fontSize = 14.sp,
                                color = kb.subtitle,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.padding(start = 58.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val wText = subject.weightKg?.let { String.format(Locale.ITALY, "%.1f kg", it) } ?: "Peso?"
                    val wFilled = subject.weightKg != null
                    ChildMetricChip(
                        icon = Icons.Default.MonitorWeight,
                        label = wText,
                        accent = WEIGHT_BLUE,
                        filled = wFilled,
                        muted = kb.subtitle,
                        onClick = onWeightChip,
                    )
                    val hText = subject.heightCm?.let { String.format(Locale.ITALY, "%.0f cm", it) } ?: "Altezza?"
                    val hFilled = subject.heightCm != null
                    ChildMetricChip(
                        icon = Icons.Default.Straighten,
                        label = hText,
                        accent = HEIGHT_GREEN,
                        filled = hFilled,
                        muted = kb.subtitle,
                        onClick = onHeightChip,
                    )
                }
            }
            Icon(
                imageVector = Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = kb.subtitle,
                modifier = Modifier.clickable(onClick = onOpenHealth),
            )
        }
    }
}

@Composable
private fun AdultSubjectCard(
    subject: HealthSubject,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val orange = Color(0xFFFF6B00)
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(orange.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Person,
                    contentDescription = null,
                    tint = orange,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    subject.name,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = kb.title,
                )
                Spacer(Modifier.height(6.dp))
                MemberRoleBadge(role = subject.memberRole)
            }
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = null,
                tint = kb.subtitle,
            )
        }
    }
}

@Composable
private fun ChildMetricChip(
    icon: ImageVector,
    label: String,
    accent: Color,
    filled: Boolean,
    muted: Color,
    onClick: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (filled) accent.copy(alpha = 0.12f) else muted.copy(alpha = 0.12f),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = if (filled) accent else muted,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = if (filled) accent else muted,
            )
        }
    }
}

@Composable
private fun MemberRoleBadge(role: String?) {
    val label = memberRoleLabelItalian(role)
    val color = memberRoleColor(role)
    Surface(
        shape = RoundedCornerShape(50),
        color = color.copy(alpha = 0.12f),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = color,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                label,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = color,
            )
        }
    }
}

private fun memberRoleLabelItalian(role: String?): String =
    when (role?.lowercase()) {
        "owner" -> "Proprietario"
        "admin" -> "Amministratore"
        else -> "Membro"
    }

private fun memberRoleColor(role: String?): Color =
    when (role?.lowercase()) {
        "owner" -> Color(0xFF9573D9)
        "admin" -> Color(0xFF2196F3)
        else -> Color(0xFF009688)
    }

private fun childAgeSummaryItalian(birthMillis: Long?): String? {
    if (birthMillis == null) return null
    val birth = Instant.ofEpochMilli(birthMillis).atZone(ZoneId.systemDefault()).toLocalDate()
    val today = LocalDate.now()
    if (birth.isAfter(today)) return null
    val years = ChronoUnit.YEARS.between(birth, today).toInt()
    if (years >= 1) {
        return if (years == 1) "1 anno" else "$years anni"
    }
    val months = ChronoUnit.MONTHS.between(birth, today).toInt().coerceAtLeast(0)
    return when {
        months <= 0 -> "Neonato"
        months == 1 -> "1 mese"
        else -> "$months mesi"
    }
}

@Composable
private fun EmptySubjects() {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "Nessun profilo disponibile",
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = kb.title,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "Aggiungi figli o verifica i membri nelle impostazioni famiglia.",
            fontSize = 14.sp,
            color = kb.subtitle,
        )
    }
}
