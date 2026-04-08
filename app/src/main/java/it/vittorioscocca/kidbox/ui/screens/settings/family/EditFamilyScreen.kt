package it.vittorioscocca.kidbox.ui.screens.settings.family

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ChildCare
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SentimentSatisfied
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class ChildEntry(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "",
    var birthDate: LocalDate? = null,
)

@Composable
fun EditFamilyScreen(
    onBack: () -> Unit,
    onEditChild: (String) -> Unit,
    viewModel: FamilySettingsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var familyName by remember { mutableStateOf("") }
    var children by remember { mutableStateOf(listOf<ChildEntry>()) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("d/M/yyyy") }
    val cardShape = RoundedCornerShape(16.dp)
    val cardBorder = Color(0x552E86FF)
    val sectionSubtitle = Color(0xFF777777)
    val dividerColor = Color(0xFFEAEAEA)

    // Lifecycle-aware: calls startObserving() every time screen resumes
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                viewModel.startObserving()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Always sync UI from latest Room state
    LaunchedEffect(state.family?.id, state.children) {
        familyName = state.family?.name.orEmpty()
        if (state.children.isNotEmpty()) {
            children = state.children.map {
                ChildEntry(
                    id = it.id,
                    name = it.name,
                    birthDate = it.birthDateEpochMillis?.let { millis ->
                        java.time.Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                    },
                )
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text(
            "Modifica famiglia",
            fontSize = 34.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1A1A1A),
        )
        Spacer(modifier = Modifier.height(14.dp))

        // ── Famiglia card ────────────────────────────────────────────────────
        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().border(1.5.dp, cardBorder, cardShape),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Groups, null, tint = Color(0xFF2E86FF))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Famiglia", fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                        Text("Aggiorna nome famiglia e gestisci i figli.", color = sectionSubtitle, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = dividerColor)
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = familyName,
                    onValueChange = { familyName = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1A1A1A),
                        unfocusedTextColor = Color(0xFF1A1A1A),
                        focusedContainerColor = Color.White,
                        unfocusedContainerColor = Color.White,
                        focusedBorderColor = Color(0xFF2E86FF),
                        unfocusedBorderColor = Color(0xFFCCCCCC),
                    ),
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Figli card ───────────────────────────────────────────────────────
        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth().border(1.5.dp, cardBorder, cardShape),
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.ChildCare, null, tint = Color(0xFF777777))
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text("Figli", fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                        Text("${children.count { it.id.isNotBlank() }} figlio/i configurato/i.", color = sectionSubtitle, fontSize = 12.sp)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = dividerColor)

                children.forEachIndexed { index, child ->
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.SentimentSatisfied, null, tint = Color(0xFF999999))
                        Spacer(modifier = Modifier.width(10.dp))

                        if (child.id.isNotBlank()) {
                            // Existing saved child — tap chevron to edit
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (child.name.isBlank()) "Senza nome" else child.name,
                                    fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1A1A1A),
                                )
                                Text(
                                    child.birthDate?.format(dateFormat) ?: "Nessuna data",
                                    fontSize = 12.sp, color = Color(0xFF999999),
                                )
                            }
                            IconButton(onClick = { onEditChild(child.id) }) {
                                Icon(Icons.Filled.ChevronRight, null, tint = Color(0xFF999999))
                            }
                        } else {
                            // New unsaved child — inline text field
                            OutlinedTextField(
                                value = child.name,
                                onValueChange = { value ->
                                    children = children.toMutableList().also { it[index] = it[index].copy(name = value) }
                                },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("Nome figlio") },
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color(0xFF1A1A1A),
                                    unfocusedTextColor = Color(0xFF1A1A1A),
                                    focusedContainerColor = Color.White,
                                    unfocusedContainerColor = Color.White,
                                    focusedBorderColor = Color(0xFF2E86FF),
                                    unfocusedBorderColor = Color(0xFFCCCCCC),
                                ),
                            )
                            IconButton(onClick = {
                                children = children.toMutableList().also { it.removeAt(index) }
                            }) {
                                Icon(Icons.Filled.Remove, null, tint = Color(0xFF999999))
                            }
                        }
                    }
                    if (index < children.lastIndex) {
                        Spacer(modifier = Modifier.height(10.dp))
                        HorizontalDivider(color = dividerColor)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .clickable { children = children + ChildEntry(id = "") }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AddCircle, null, tint = Color(0xFF2E86FF))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aggiungi figlio", color = Color(0xFF2E86FF), fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // ── Salva card ───────────────────────────────────────────────────────
        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.5.dp, cardBorder, cardShape)
                .clickable {
                    viewModel.saveFamilyWithChildren(
                        newName = familyName,
                        childrenInputs = children.map { entry ->
                            ChildInput(
                                id = if (entry.id.isBlank()) UUID.randomUUID().toString() else entry.id,
                                name = entry.name,
                                birthDateEpochMillis = entry.birthDate
                                    ?.atStartOfDay(ZoneId.systemDefault())
                                    ?.toInstant()?.toEpochMilli(),
                            )
                        },
                        onDone = onBack,
                    )
                },
        ) {
            Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF2E86FF), modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text("Salva modifiche", fontWeight = FontWeight.Bold, color = Color(0xFF1A1A1A))
                    Text("Le modifiche verranno sincronizzate.", color = sectionSubtitle, fontSize = 12.sp)
                }
            }
        }

        state.error?.takeIf { it.isNotBlank() }?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = Color(0xFFE53E3E), fontSize = 12.sp)
        }
    }
}