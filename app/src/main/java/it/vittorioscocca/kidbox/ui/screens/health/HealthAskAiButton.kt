package it.vittorioscocca.kidbox.ui.screens.health

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import it.vittorioscocca.kidbox.ai.AskAiButton

/**
 * [AskAiButton] per la Home Salute: descrizione accessibile e abilitazione lato app
 * (es. [it.vittorioscocca.kidbox.ai.AiSettings.isEnabled] + dati presenti).
 */
@Composable
fun HealthAskAiButton(
    subjectName: String,
    isEnabled: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val display = subjectName.ifBlank { "Profilo" }
    AskAiButton(
        modifier = modifier,
        isEnabled = isEnabled,
        contentDescription = "Chiedi all'AI sulla salute di $display",
        onTap = onTap,
    )
}
