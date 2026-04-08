package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@Composable
fun EditChildScreen(
    childId: String,
    onBack: () -> Unit,
    viewModel: FamilySettingsViewModel = hiltViewModel(),
) {
    BackHandler { onBack() }
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var name by remember { mutableStateOf("") }
    var birth by remember { mutableStateOf<Long?>(null) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { viewModel.load() }
    LaunchedEffect(state.children, childId) {
        state.children.firstOrNull { it.id == childId }?.let {
            name = it.name
            birth = it.birthDateEpochMillis
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF2F0EB))
            .statusBarsPadding()
            .padding(16.dp),
    ) {
        Text("Figlio", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Dati", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(name, { name = it }, modifier = Modifier.fillMaxWidth(), label = { Text("Nome") })
        Spacer(modifier = Modifier.height(8.dp))
        OutlinedTextField(
            value = birth?.let { java.text.SimpleDateFormat("dd/MM/yyyy", java.util.Locale.getDefault()).format(java.util.Date(it)) } ?: "",
            onValueChange = {},
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Data di nascita") },
            enabled = false,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = { viewModel.saveChild(childId, name, birth, onBack) }) {
            Text("Salva")
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("Zona Pericolosa", color = Color(0xFFE53E3E), fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = { showDelete = true }) {
            Text("Elimina figlio", color = Color(0xFFE53E3E))
        }
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
}
