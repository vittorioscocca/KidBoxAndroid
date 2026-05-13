package it.vittorioscocca.kidbox.ui.screens.passwords

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PasswordsSettingsScreen(
    onBack: () -> Unit,
    onOpenAutoFillSettings: () -> Unit,
    viewModel: PasswordsSettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
            )
        },
    ) { pad ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(pad)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Controlli avanzati (password deboli, duplicate, leak noti) saranno disponibili anche su Android, in linea con la schermata iOS.",
                style = MaterialTheme.typography.bodyMedium,
                color = kb.title,
            )
            Text(
                "Privacy check HIBP: la password in chiaro non lascia mai il dispositivo; " +
                    "KidBox invia solo i primi 5 caratteri dell'hash SHA-1 con modello k-anonymity.",
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
            Button(
                onClick = onOpenAutoFillSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Apri impostazioni compilazione automatica")
            }
                RowToggle(
                    label = "Scansione settimanale automatica",
                    checked = state.weeklyEnabled,
                    onCheckedChange = viewModel::setWeeklyEnabled,
                )
                RowToggle(
                    label = "Avvisi push per nuove password compromesse",
                    checked = state.pushEnabled,
                    onCheckedChange = viewModel::setPushEnabled,
                )
                Button(
                    onClick = viewModel::runScanNow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Ultima scansione: ${state.lastScanLabel}")
                }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun RowToggle(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
