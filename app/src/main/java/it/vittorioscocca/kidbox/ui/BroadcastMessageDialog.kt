package it.vittorioscocca.kidbox.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.notifications.BroadcastMessage

/**
 * View unica per gli annunci inviati dalla console admin (`sendBroadcast`).
 *
 * Gemella di `BroadcastMessageView` su iOS: è deliberatamente la STESSA view per
 * ogni annuncio. Il testo arriva già dentro il payload della notifica, quindi
 * qui non si legge nulla dalla rete — si apre anche offline e anche se
 * l'annuncio nel frattempo è stato cancellato.
 *
 * È un dialog e non una destinazione di navigazione perché un annuncio non
 * appartiene a una famiglia e non ha un posto nello stack: non deve comparire
 * nel back stack né sopravvivere a un cambio famiglia.
 */
@Composable
fun BroadcastMessageDialog(
    message: BroadcastMessage?,
    onDismiss: () -> Unit,
    /** Invocata al tap sul pulsante primario dei nudge (quelli con destinazione). */
    onAction: (BroadcastMessage) -> Unit = {},
) {
    if (message == null) return

    val hasAction = message.destination != null

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            if (message.title.isNotBlank()) Text(message.title)
        },
        text = {
            // Il testo è scritto a mano in console: niente markdown, niente
            // HTML. Va mostrato per intero — la notifica di sistema lo tronca,
            // questo dialog è il posto dove leggerlo. Lo scroll serve perché il
            // limite lato server è 300 caratteri, che su schermi piccoli e con
            // font di sistema ingranditi non ci stanno.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { if (hasAction) onAction(message) else onDismiss() }) {
                Text(if (hasAction) "Vai" else "Ho capito")
            }
        },
        dismissButton = if (hasAction) {
            {
                // "Non ora" chiude e basta. Non ripianifica e non penalizza:
                // il tetto della campagna vale già di suo.
                TextButton(onClick = onDismiss) { Text("Non ora") }
            }
        } else {
            null
        },
    )
}
