package it.vittorioscocca.kidbox.ui.screens.passwords

import it.vittorioscocca.kidbox.R
import androidx.compose.ui.res.stringResource
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
                title = { Text(stringResource(R.string.passwords_settings_menu_label)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.location_back_content_description))
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
                stringResource(R.string.passwords_advanced_controls_info),
                style = MaterialTheme.typography.bodyMedium,
                color = kb.title,
            )
            Text(
                stringResource(R.string.passwords_hibp_privacy_info),
                style = MaterialTheme.typography.bodySmall,
                color = kb.subtitle,
            )
            Button(
                onClick = onOpenAutoFillSettings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.passwords_open_autofill_settings_button))
            }
                RowToggle(
                    label = stringResource(R.string.passwords_weekly_scan_label),
                    checked = state.weeklyEnabled,
                    onCheckedChange = viewModel::setWeeklyEnabled,
                )
                RowToggle(
                    label = stringResource(R.string.passwords_push_alerts_label),
                    checked = state.pushEnabled,
                    onCheckedChange = viewModel::setPushEnabled,
                )
                Button(
                    onClick = viewModel::runScanNow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.passwords_last_scan_button, state.lastScanLabel))
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
