package it.vittorioscocca.kidbox.ai

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.BuildConfig
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics

@Composable
fun AskAiButton(
    modifier: Modifier = Modifier,
    upgradeSubtitle: String? = null,
    contentDescription: String = "Chiedi all'AI",
    analyticsContext: String,
    onTap: () -> Unit,
) {
    val context = LocalContext.current
    val aiSettings = remember(context) { context.getAiSettingsFromApp() }
    val consentGiven by aiSettings.consentGiven.collectAsStateWithLifecycle(initialValue = false)
    // Free ha accesso all'AI finché non esaurisce il bonus di 5 messaggi una tantum:
    // il blocco è reattivo (CurrentPlanStore.aiAccessBlocked), non più legato al piano.
    val isLocked by CurrentPlanStore.aiAccessBlocked.collectAsStateWithLifecycle()
    val upgradeAction = LocalUpgradeAction.current

    var showConsentDialog by remember { mutableStateOf(false) }

    FloatingActionButton(
        onClick = {
            if (isLocked || !BuildConfig.AI_ENABLED) {
                AppAnalytics.aiPaywallShown(context, analyticsContext)
                upgradeAction(upgradeSubtitle)
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
            .shadow(14.dp, CircleShape, clip = false),
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
