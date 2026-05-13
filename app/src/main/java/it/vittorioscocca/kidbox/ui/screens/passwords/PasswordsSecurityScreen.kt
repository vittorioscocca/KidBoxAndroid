package it.vittorioscocca.kidbox.ui.screens.passwords

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Refresh

private val PasswordsIosBackgroundLight = Color(0xFFF2F2F7)

@Composable
fun PasswordsSecurityScreen(
    familyId: String,
    onBack: () -> Unit,
    onOpenPassword: (String) -> Unit = {},
    viewModel: PasswordsSecurityViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scanState by viewModel.scanState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors

    LaunchedEffect(familyId) {
        viewModel.bind(familyId)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PasswordsIosBackgroundLight)
            .statusBarsPadding()
            .padding(horizontal = 16.dp),
    ) {
            Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = Color.White, shadowElevation = 1.dp) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Indietro")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = viewModel::rescan,
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = kb.title,
                ),
            ) {
                       Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                       Spacer(modifier = Modifier.size(6.dp))
                       Text("Scansiona ora", fontWeight = FontWeight.SemiBold)
            }
        }

        Text(
            "Sicurezza password",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = kb.title,
            modifier = Modifier.padding(bottom = 12.dp),
        )
            if (state.isOffline) {
                AssistChip(
                    onClick = {},
                    label = { Text("Offline - verdetti potrebbero essere obsoleti") },
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
            when (val s = scanState) {
                is ScanState.Running -> Text("Scansione in corso...", color = kb.subtitle, modifier = Modifier.padding(bottom = 6.dp))
                is ScanState.Done -> Text(
                    "Scansione completata: ${s.compromised} compromesse, ${s.duplicates} cluster duplicati, ${s.weak} deboli.",
                    color = kb.subtitle,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
                is ScanState.Error -> Text(s.message, color = Color(0xFFE53935), modifier = Modifier.padding(bottom = 6.dp))
                ScanState.Idle -> Unit
            }

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                SectionTitle("🔴 Compromesse")
                if (state.compromised.isEmpty()) {
                    EmptySectionCard("Nessuna password compromessa.")
                } else {
                           SecurityCardList(rows = state.compromised, showWarningIcon = true, onOpenPassword = onOpenPassword)
                }
            }
            item {
                SectionTitle("🟡 Duplicate")
            }
            if (state.duplicateClusters.isEmpty()) {
                item { EmptySectionCard("Nessuna password duplicata.") }
            } else {
                items(state.duplicateClusters, key = { it.id }) { cluster ->
                    DuplicateClusterCard(cluster = cluster)
                }
            }
                item {
                    SectionTitle("🟠 Deboli")
                    if (state.weak.isEmpty()) {
                        EmptySectionCard("Nessuna password debole.")
                    } else {
                        SecurityCardList(rows = state.weak, onOpenPassword = onOpenPassword)
                    }
                }
            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    val kb = MaterialTheme.kidBoxColors
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = kb.subtitle,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
    )
}

@Composable
private fun SecurityCardList(
    rows: List<PasswordCompromisedUi>,
    showWarningIcon: Boolean = false,
    onOpenPassword: (String) -> Unit = {},
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                   SecurityRow(row = row, showWarningIcon = showWarningIcon, onOpenPassword = onOpenPassword)
                if (index < rows.lastIndex) {
                    HorizontalDivider(
                        color = Color(0xFFEDEDED),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SecurityRow(
    row: PasswordCompromisedUi,
    showWarningIcon: Boolean = false,
    onOpenPassword: (String) -> Unit = {},
) {
    val kb = MaterialTheme.kidBoxColors
   Column(
       modifier = Modifier
           .fillMaxWidth()
           .clickable { onOpenPassword(row.id) }
           .padding(horizontal = 14.dp, vertical = 12.dp),
   ) {
       Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
           if (showWarningIcon) {
               Icon(Icons.Filled.Warning, contentDescription = null, tint = Color(0xFFE53935), modifier = Modifier.size(16.dp))
           }
           Text(
               row.title,
               style = MaterialTheme.typography.bodyLarge,
               fontWeight = FontWeight.SemiBold,
               color = kb.title,
           )
       }
        Text(
            row.subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = kb.subtitle,
        )
    }
}

@Composable
private fun DuplicateClusterCard(cluster: PasswordDuplicateClusterUi) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
        ) {
            Text(
                cluster.header,
                style = MaterialTheme.typography.titleSmall,
                color = kb.subtitle,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
            )
            cluster.items.forEachIndexed { index, item ->
                SecurityRow(row = item)
                if (index < cluster.items.lastIndex) {
                    HorizontalDivider(
                        color = Color(0xFFEDEDED),
                        thickness = 0.5.dp,
                        modifier = Modifier.padding(horizontal = 14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySectionCard(message: String) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = Color.White,
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 16.dp),
        ) {
            Text(message, color = kb.subtitle)
        }
    }
}
