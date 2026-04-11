package it.vittorioscocca.kidbox.ui.screens.settings.family

import android.app.DatePickerDialog
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun EditChildScreen(
    childId: String,
    onBack: () -> Unit,
    viewModel: FamilySettingsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf<LocalDate?>(null) }
    var showDelete by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val dateFormat = remember { DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ITALIAN) }
    val cardShape = RoundedCornerShape(16.dp)

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

    // Populate fields from Room state
    LaunchedEffect(state.children, childId) {
        state.children.firstOrNull { it.id == childId }?.let {
            name = it.name
            birth = it.birthDateEpochMillis?.let { millis ->
                Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.kidBoxColors.background)
            .statusBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
    ) {
        Text("Figlio", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.kidBoxColors.title)
        Spacer(modifier = Modifier.height(16.dp))

        Text("Dati", fontSize = 13.sp, color = MaterialTheme.kidBoxColors.subtitle)
        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.kidBoxColors.title,
                        unfocusedTextColor = MaterialTheme.kidBoxColors.title,
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                )
                HorizontalDivider(color = MaterialTheme.kidBoxColors.divider, modifier = Modifier.padding(start = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Text("Data di nascita", color = MaterialTheme.kidBoxColors.title, modifier = Modifier.weight(1f))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.kidBoxColors.divider,
                        modifier = Modifier.clickable { showDatePicker = true },
                    ) {
                        Text(
                            text = birth?.format(dateFormat) ?: "Aggiungi",
                            color = if (birth != null) MaterialTheme.kidBoxColors.title else Color(0xFF2E86FF),
                            fontSize = 15.sp,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Salva ────────────────────────────────────────────────────────────
        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth().clickable(enabled = !state.isLoading) {
                viewModel.saveChild(
                    childId, name,
                    birth?.atStartOfDay(ZoneId.systemDefault())?.toInstant()?.toEpochMilli(),
                    onBack,
                )
            },
        ) {
            Row(modifier = Modifier.padding(16.dp)) {
                if (state.isLoading) {
                    CircularProgressIndicator(
                        color = Color(0xFF2E86FF),
                        strokeWidth = 2.dp,
                        modifier = Modifier.width(18.dp).height(18.dp),
                    )
                } else {
                    Text("Salva", color = Color(0xFF2E86FF), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }

        Spacer(modifier = Modifier.height(28.dp))
        Text("Zona Pericolosa", fontSize = 13.sp, color = MaterialTheme.kidBoxColors.subtitle)
        Spacer(modifier = Modifier.height(8.dp))

        // ── Elimina figlio ───────────────────────────────────────────────────
        Card(
            shape = cardShape,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier.fillMaxWidth().clickable { showDelete = true },
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Icon(Icons.Filled.Delete, null, tint = Color(0xFFE53E3E),
                    modifier = Modifier.width(20.dp).height(20.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text("Elimina figlio", color = Color(0xFFE53E3E), fontSize = 17.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
        Text(
            "Questa azione non può essere annullata. Il figlio verrà eliminato definitivamente.",
            fontSize = 13.sp, color = MaterialTheme.kidBoxColors.subtitle,
        )
    }

    if (showDelete) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("Eliminare figlio?") },
            text = { Text("Confermi eliminazione?") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    viewModel.deleteChild(childId, onBack)
                }) { Text("Elimina", color = Color(0xFFE53E3E)) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("Annulla") }
            },
        )
    }

    // Date picker
    LaunchedEffect(showDatePicker) {
        if (!showDatePicker) return@LaunchedEffect
        val initial = birth ?: LocalDate.now()
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                birth = LocalDate.of(year, month + 1, dayOfMonth)
                showDatePicker = false
            },
            initial.year, initial.monthValue - 1, initial.dayOfMonth,
        ).apply {
            setOnDismissListener { showDatePicker = false }
        }.show()
    }
}