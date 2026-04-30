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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Vaccines
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineStatus
import it.vittorioscocca.kidbox.data.local.mapper.KBVaccineType
import it.vittorioscocca.kidbox.data.local.mapper.computedStatus
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DATE_FMT_VACCINE = SimpleDateFormat("d MMM yyyy", Locale.ITALIAN)
private val SALMON = Color(0xFFF38D73)
private val OVERDUE_RED = Color(0xFFD32F2F)

@Composable
fun MedicalVaccinesScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (vaccineId: String) -> Unit,
    viewModel: MedicalVaccinesViewModel = hiltViewModel(),
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro", tint = kb.title)
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = "Nuovo vaccino", tint = kb.title)
            }
        }

        Text(
            "Vaccini",
            fontSize = 32.sp,
            fontWeight = FontWeight.ExtraBold,
            color = kb.title,
            modifier = Modifier.padding(horizontal = 18.dp),
        )
        Spacer(Modifier.height(12.dp))

        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            val isEmpty = state.overdue.isEmpty() && state.scheduled.isEmpty() &&
                state.administered.isEmpty() && state.skipped.isEmpty()

            if (isEmpty) {
                VaccinesEmptyState(modifier = Modifier.fillMaxSize(), onAdd = onAdd)
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item { Spacer(Modifier.height(4.dp)) }
                    vaccineSection("In ritardo", state.overdue, isOverdue = true, onOpen)
                    vaccineSection("Programmati", state.scheduled, onTap = onOpen)
                    vaccineSection("Somministrati", state.administered, onTap = onOpen)
                    vaccineSection("Non eseguiti", state.skipped, onTap = onOpen)
                    item { Spacer(Modifier.height(24.dp)) }
                }
            }
        }
    }
}

private fun LazyListScope.vaccineSection(
    title: String,
    items: List<KBVaccine>,
    isOverdue: Boolean = false,
    onTap: (String) -> Unit,
) {
    if (items.isEmpty()) return
    item {
        Text(
            title.uppercase(),
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            color = if (isOverdue) OVERDUE_RED else MaterialTheme.kidBoxColors.subtitle,
            letterSpacing = 0.8.sp,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
    }
    items(items, key = { it.id }) { vaccine ->
        VaccineRow(vaccine = vaccine, onTap = { onTap(vaccine.id) })
    }
}

@Composable
private fun VaccineRow(vaccine: KBVaccine, onTap: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val status = vaccine.computedStatus()
    val type = KBVaccineType.fromRaw(vaccine.vaccineTypeRaw)

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
                    .background(SALMON.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Default.Vaccines, contentDescription = null, tint = SALMON)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(vaccine.name, fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 15.sp)
                val subtitle = when (status) {
                    KBVaccineStatus.ADMINISTERED ->
                        vaccine.administeredDateEpochMillis?.let { "Somministrato il ${DATE_FMT_VACCINE.format(Date(it))}" }
                            ?: "Somministrato"
                    KBVaccineStatus.OVERDUE ->
                        vaccine.scheduledDateEpochMillis?.let { "Era programmato il ${DATE_FMT_VACCINE.format(Date(it))}" }
                            ?: "In ritardo"
                    KBVaccineStatus.SCHEDULED ->
                        vaccine.scheduledDateEpochMillis?.let { "Programmato il ${DATE_FMT_VACCINE.format(Date(it))}" }
                            ?: "Senza data"
                    KBVaccineStatus.SKIPPED -> "Non eseguito"
                }
                Text(
                    subtitle,
                    fontSize = 12.sp,
                    color = if (status == KBVaccineStatus.OVERDUE) OVERDUE_RED else kb.subtitle,
                )
                if (!vaccine.doctorName.isNullOrBlank()) {
                    Text(vaccine.doctorName, fontSize = 12.sp, color = kb.subtitle)
                }
            }
            Spacer(Modifier.width(8.dp))
            Column(horizontalAlignment = Alignment.End) {
                if (type != null) VaccineTypeChip(type)
                Spacer(Modifier.height(4.dp))
                VaccineStatusBadge(status)
            }
        }
    }
}

@Composable
fun VaccineTypeChip(type: KBVaccineType) {
    val (bg, fg) = when (type) {
        KBVaccineType.MANDATORY -> Color(0xFFBBDEFB) to Color(0xFF1565C0)
        KBVaccineType.RECOMMENDED -> Color(0xFFE0E0E0) to Color(0xFF616161)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .padding(horizontal = 7.dp, vertical = 2.dp),
    ) {
        Text(type.rawValue, fontSize = 10.sp, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun VaccineStatusBadge(status: KBVaccineStatus) {
    val (bg, fg) = when (status) {
        KBVaccineStatus.SCHEDULED -> Color(0xFFBBDEFB) to Color(0xFF1565C0)
        KBVaccineStatus.ADMINISTERED -> Color(0xFFC8E6C9) to Color(0xFF2E7D32)
        KBVaccineStatus.OVERDUE -> Color(0xFFFFCDD2) to Color(0xFFD32F2F)
        KBVaccineStatus.SKIPPED -> Color(0xFFE0E0E0) to Color(0xFF616161)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50.dp))
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(status.rawValue, fontSize = 11.sp, color = fg, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun VaccinesEmptyState(modifier: Modifier = Modifier, onAdd: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.Vaccines,
                contentDescription = null,
                tint = kb.subtitle.copy(alpha = 0.4f),
                modifier = Modifier.size(56.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Nessun vaccino registrato",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = kb.title,
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onAdd,
                colors = ButtonDefaults.buttonColors(containerColor = SALMON),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("Nuovo vaccino", color = Color.White, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
