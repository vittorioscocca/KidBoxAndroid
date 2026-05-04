package it.vittorioscocca.kidbox.ui.screens.health.exams

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
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FMT_EXAM = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private val TEAL = Color(0xFF40A6BF)
private val ORANGE_EXAMS = Color(0xFFFF6B00)

@Composable
fun MedicalExamsScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (examId: String) -> Unit,
    viewModel: MedicalExamsViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // ── Top bar ────────────────────────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            KidBoxHeaderCircleButton(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Indietro",
                onClick = onBack,
            )
            Spacer(Modifier.weight(1f))
            KidBoxHeaderCircleButton(
                icon = Icons.Default.Add,
                contentDescription = "Nuovo esame",
                onClick = onAdd,
            )
        }

        // ── Title ──────────────────────────────────────────────────────────────
        Text(
            "Analisi & Esami",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(12.dp))

        // ── Content ────────────────────────────────────────────────────────────
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val isEmpty = state.pending.isEmpty() && state.booked.isEmpty() &&
                state.done.isEmpty() && state.resultIn.isEmpty() &&
                state.unknownStatus.isEmpty()

            if (isEmpty) {
                ExamsEmptyState(modifier = Modifier.fillMaxSize(), onAdd = onAdd)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }

                    examSection("Urgenti", state.urgentPending, isUrgentSection = true, onOpen)
                    examSection("In attesa", state.pending, onTap = onOpen)
                    examSection("Prenotati", state.booked, onTap = onOpen)
                    examSection("Risultato disponibile", state.resultIn, onTap = onOpen)
                    examSection("Eseguiti", state.done, onTap = onOpen)
                    examSection("Senza stato", state.unknownStatus, onTap = onOpen)

                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

// ── Section helper ─────────────────────────────────────────────────────────────

private fun LazyListScope.examSection(
    title: String,
    items: List<KBMedicalExam>,
    isUrgentSection: Boolean = false,
    onTap: (String) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        Text(
            title.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (isUrgentSection) Color(0xFFD32F2F) else MaterialTheme.kidBoxColors.subtitle,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    items(items, key = { it.id }) { exam ->
        ExamRow(exam = exam, onTap = { onTap(exam.id) })
    }
}

// ── Row card ──────────────────────────────────────────────────────────────────

@Composable
private fun ExamRow(exam: KBMedicalExam, onTap: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val status = KBExamStatus.values().firstOrNull { it.rawValue == exam.statusRaw }
        ?: KBExamStatus.PENDING
    Card(
        onClick = onTap,
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(TEAL.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Science, contentDescription = null, tint = TEAL)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(exam.name, fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 15.sp)
                Text(
                    exam.deadlineEpochMillis?.let { DATE_FMT_EXAM.format(Date(it)) } ?: "Senza scadenza",
                    fontSize = 12.sp,
                    color = kb.subtitle,
                )
                if (!exam.location.isNullOrBlank()) {
                    Text(exam.location, fontSize = 12.sp, color = kb.subtitle)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                ExamStatusBadge(status)
                if (exam.isUrgent) {
                    Spacer(Modifier.height(4.dp))
                    UrgentChip()
                }
            }
        }
    }
}

// ── Status badge ──────────────────────────────────────────────────────────────

@Composable
fun ExamStatusBadge(status: KBExamStatus) {
    val (bgColor, textColor) = when (status) {
        KBExamStatus.PENDING -> Color(0xFFE0E0E0) to Color(0xFF616161)
        KBExamStatus.BOOKED -> Color(0xFFBBDEFB) to Color(0xFF1565C0)
        KBExamStatus.DONE -> Color(0xFFC8E6C9) to Color(0xFF2E7D32)
        KBExamStatus.RESULT_IN -> Color(0xFFE1BEE7) to Color(0xFF6A1B9A)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(status.rawValue, fontSize = 11.sp, color = textColor, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun UrgentChip() {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(Color(0xFFFFCDD2))
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text("Urgente", fontSize = 10.sp, color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
    }
}

// ── Empty state ────────────────────────────────────────────────────────────────

@Composable
private fun ExamsEmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Science,
                contentDescription = null,
                tint = kb.subtitle.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Nessun esame registrato",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = kb.title,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = ORANGE_EXAMS),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Nuovo esame", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
