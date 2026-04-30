package it.vittorioscocca.kidbox.ui.screens.health.vaccines

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineStatus
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineType
import it.vittorioscocca.kidbox.data.local.mapper.computedStatus
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_LONG_VACCINE = SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN)
private val SALMON_DETAIL = Color(0xFFF38D73)
private val DANGER_VACCINE = Color(0xFFD32F2F)
private val GREEN_ADMINISTER = Color(0xFF2E7D32)

@Composable
fun MedicalVaccineDetailScreen(
    familyId: String,
    childId: String,
    vaccineId: String,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    viewModel: MedicalVaccineDetailViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(familyId, childId, vaccineId) { viewModel.bind(familyId, childId, vaccineId) }
    LaunchedEffect(state.deleted) { if (state.deleted) onBack() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
            state.vaccine == null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(18.dp),
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = kb.title)
                    }
                    Spacer(Modifier.height(40.dp))
                    Text(state.error ?: "Vaccino non trovato.", color = kb.subtitle, fontSize = 16.sp)
                }
            }
            else -> {
                val vaccine = state.vaccine!!
                val status = vaccine.computedStatus()
                val type = KBVaccineType.fromRaw(vaccine.vaccineTypeRaw)

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .padding(bottom = 88.dp)
                        .padding(horizontal = 18.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = kb.title)
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = onEdit) {
                            Icon(Icons.Default.Edit, contentDescription = "Modifica", tint = SALMON_DETAIL)
                        }
                        IconButton(onClick = { viewModel.requestDelete() }) {
                            Icon(Icons.Default.Delete, contentDescription = "Elimina", tint = kb.subtitle)
                        }
                    }
                    Spacer(Modifier.height(8.dp))

                    // ── Header card ────────────────────────────────────────────
                    Card(
                        colors = CardDefaults.cardColors(containerColor = kb.card),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(52.dp)
                                        .clip(CircleShape)
                                        .background(SALMON_DETAIL.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Default.Vaccines,
                                        contentDescription = null,
                                        tint = SALMON_DETAIL,
                                        modifier = Modifier.size(28.dp),
                                    )
                                }
                                Spacer(Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        vaccine.name,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 20.sp,
                                        color = kb.title,
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    if (type != null) VaccineTypeChip(type)
                                    Spacer(Modifier.height(4.dp))
                                    VaccineStatusBadge(status)
                                }
                            }
                            if (vaccine.reminderOn && status == KBVaccineStatus.SCHEDULED) {
                                Spacer(Modifier.height(10.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = null,
                                        tint = SALMON_DETAIL,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text("Promemoria attivo", fontSize = 12.sp, color = SALMON_DETAIL)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))

                    // ── Date card ──────────────────────────────────────────────
                    if (vaccine.scheduledDateEpochMillis != null || vaccine.administeredDateEpochMillis != null) {
                        VaccineDetailCard(title = "Date") {
                            vaccine.scheduledDateEpochMillis?.let { ms ->
                                VaccineDetailRow(
                                    label = "Data programmata",
                                    value = DATE_LONG_VACCINE.format(Date(ms)),
                                )
                            }
                            vaccine.administeredDateEpochMillis?.let { ms ->
                                VaccineDetailRow(
                                    label = "Data somministrazione",
                                    value = DATE_LONG_VACCINE.format(Date(ms)),
                                )
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Medico e luogo ─────────────────────────────────────────
                    if (!vaccine.doctorName.isNullOrBlank() || !vaccine.location.isNullOrBlank()) {
                        VaccineDetailCard(title = "Medico e luogo") {
                            if (!vaccine.doctorName.isNullOrBlank()) {
                                VaccineDetailRow("Medico", vaccine.doctorName)
                            }
                            if (!vaccine.location.isNullOrBlank()) {
                                VaccineDetailRow("Luogo", vaccine.location)
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Lotto card ─────────────────────────────────────────────
                    if (!vaccine.lotNumber.isNullOrBlank()) {
                        VaccineDetailCard(title = "Lotto") {
                            Text(vaccine.lotNumber, fontSize = 14.sp, color = kb.title)
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Note card ──────────────────────────────────────────────
                    if (!vaccine.notes.isNullOrBlank()) {
                        VaccineDetailCard(title = "Note") {
                            Text(vaccine.notes, fontSize = 14.sp, color = kb.title)
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Prossimo richiamo ──────────────────────────────────────
                    if (vaccine.nextDoseDateEpochMillis != null) {
                        VaccineDetailCard(title = "Prossimo richiamo") {
                            Text(
                                DATE_LONG_VACCINE.format(Date(vaccine.nextDoseDateEpochMillis)),
                                fontSize = 14.sp,
                                color = kb.title,
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    // ── Quick action ───────────────────────────────────────────
                    if (status == KBVaccineStatus.SCHEDULED || status == KBVaccineStatus.OVERDUE) {
                        Button(
                            onClick = { viewModel.markAdministered() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = GREEN_ADMINISTER),
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Segna come somministrato", color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                        Spacer(Modifier.height(12.dp))
                    }

                    Spacer(Modifier.height(24.dp))
                }

                // ── Bottom action bar ──────────────────────────────────────────
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onEdit,
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SALMON_DETAIL),
                    ) {
                        Text("Modifica", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { viewModel.requestDelete() },
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = DANGER_VACCINE),
                    ) {
                        Text("Elimina", color = Color.White, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }

    if (state.confirmDelete) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Eliminare il vaccino?") },
            text = { Text("L'azione non può essere annullata.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Elimina", color = DANGER_VACCINE)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun VaccineDetailCard(title: String, content: @Composable () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Card(
        colors = CardDefaults.cardColors(containerColor = kb.card),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title.uppercase(),
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = kb.subtitle,
                letterSpacing = 0.8.sp,
            )
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
private fun VaccineDetailRow(label: String, value: String) {
    val kb = MaterialTheme.kidBoxColors
    Column(modifier = Modifier.padding(bottom = 6.dp)) {
        Text(label, fontSize = 11.sp, color = kb.subtitle, fontWeight = FontWeight.Medium)
        Text(value, fontSize = 14.sp, color = kb.title)
    }
}
