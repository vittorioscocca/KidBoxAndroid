package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.data.ai.AISettingsStore
import it.vittorioscocca.kidbox.data.health.ai.HealthContextSendPreference
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(
    aiSettingsStore: AISettingsStore,
    onBack: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    var preference by remember { mutableStateOf(aiSettingsStore.getHealthContextSendPreference()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Impostazioni AI") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = kb.background,
                    titleContentColor = kb.title,
                ),
            )
        },
        containerColor = kb.background,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            HealthContextSendPreferenceSection(
                selected = preference,
                onSelected = {
                    preference = it
                    aiSettingsStore.setHealthContextSendPreference(it)
                },
            )
        }
    }
}

@Composable
fun HealthContextSendPreferenceSection(
    selected: HealthContextSendPreference,
    onSelected: (HealthContextSendPreference) -> Unit,
    modifier: Modifier = Modifier,
) {
    val kb = MaterialTheme.kidBoxColors

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Chat Salute AI",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = kb.title,
        )
        Text(
            "Contesto inviato all'assistente quando il profilo sanitario è molto ampio.",
            style = MaterialTheme.typography.bodySmall,
            color = kb.subtitle,
            modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
        )
        HealthContextSendPreference.entries.forEach { pref ->
            RowPreference(
                title = pref.displayName,
                detail = pref.detail,
                selected = selected == pref,
                onClick = { onSelected(pref) },
            )
        }
        Text(
            "Puoi cambiare questa scelta in qualsiasi momento. Se scegli «Chiedi ogni volta», " +
                "vedrai il dialogo prima di ogni invio; le scelte nel dialogo aggiornano questa preferenza.",
            style = MaterialTheme.typography.bodySmall,
            color = kb.subtitle,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun RowPreference(
    title: String,
    detail: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    androidx.compose.foundation.layout.Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            Text(title, color = kb.title, fontWeight = FontWeight.Medium)
            Text(detail, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
        }
    }
}
