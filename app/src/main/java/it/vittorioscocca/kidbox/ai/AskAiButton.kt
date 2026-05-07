package it.vittorioscocca.kidbox.ai

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.BuildConfig

@Composable
fun AskAiButton(
    modifier: Modifier = Modifier,
    isEnabled: Boolean = true,
    contentDescription: String = "Chiedi all'AI",
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val aiSettings = remember(context) { context.getAiSettingsFromApp() }
    val consentGiven by aiSettings.consentGiven.collectAsStateWithLifecycle(initialValue = false)

    var showUpgradeDialog by remember { mutableStateOf(false) }
    var showConsentDialog by remember { mutableStateOf(false) }

    FloatingActionButton(
        onClick = {
            if (!isEnabled) return@FloatingActionButton
            if (!BuildConfig.AI_ENABLED) {
                showUpgradeDialog = true
                return@FloatingActionButton
            }
            if (!consentGiven) {
                showConsentDialog = true
                return@FloatingActionButton
            }
            onTap()
        },
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 4.dp, bottom = 4.dp)
            .size(56.dp)
            .shadow(14.dp, CircleShape, clip = false)
            .graphicsLayer(alpha = if (isEnabled) 1f else 0.4f),
        shape = CircleShape,
        containerColor = Color(0xFFFF6B00),
        contentColor = Color.White,
    ) {
        Icon(
            imageVector = Icons.Default.AutoAwesome,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(26.dp),
        )
    }

    if (showUpgradeDialog) {
        UpgradeDialogStub(
            onDismiss = { showUpgradeDialog = false },
        )
    }

    if (showConsentDialog) {
        AiConsentDialog(
            onAccept = {
                showConsentDialog = false
                onTap()
            },
            onDismiss = { showConsentDialog = false },
        )
    }
}

@Composable
private fun UpgradeDialogStub(
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Funzione AI premium") },
        text = { Text("Per usare l'assistente AI è richiesto un piano con AI abilitata.") },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("OK")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Annulla")
            }
        },
    )
}
