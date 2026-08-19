package it.vittorioscocca.kidbox.ui.screens.health

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import it.vittorioscocca.kidbox.ai.AskAiButton
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

@Composable
fun HealthAskAiButton(
    subjectName: String,
    upgradeSubtitle: String,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = subjectName.ifBlank { stringResource(R.string.health_profile) }
    AskAiButton(
        modifier = modifier,
        upgradeSubtitle = upgradeSubtitle,
        contentDescription = "Chiedi all'AI sulla salute di $display",
        analyticsContext = "health_agent",
        onTap = onTap,
    )
}
