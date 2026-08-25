package it.vittorioscocca.kidbox.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val ACCENT = Color(0xFF007AFF)

/**
 * Schermata di benvenuto di una sezione ancora vuota: icona, titolo, testo che
 * spiega a cosa serve la sezione, e il pulsante per creare il primo elemento.
 *
 * Nasce da `NotesEmptyState` / `TodoEmptyState`, che avevano lo stesso identico
 * corpo copiato due volte. Averne uno solo evita che le sezioni divergano una
 * per una e tiene allineato il rendering con `KBEmptyStateView` su iOS.
 *
 * [secondaryLabel] serve alle sezioni con due modi di iniziare — Casa, dove si
 * può aggiungere sia un elemento sia una scadenza.
 */
@Composable
fun KBEmptyState(
    icon: ImageVector,
    title: String,
    body: String,
    primaryIcon: ImageVector,
    primaryLabel: String,
    onPrimary: () -> Unit,
    modifier: Modifier = Modifier,
    /** Tinta della sezione: Animali e Garage usano l'arancio, il resto il blu KidBox. */
    accent: Color = ACCENT,
    secondaryIcon: ImageVector? = null,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = kb.subtitle,
            modifier = Modifier.size(52.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            text = title,
            color = kb.title,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = body,
            color = kb.subtitle,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        PillButton(icon = primaryIcon, label = primaryLabel, filled = true, accent = accent, onClick = onPrimary)
        if (secondaryLabel != null && onSecondary != null) {
            Spacer(Modifier.height(10.dp))
            PillButton(
                icon = secondaryIcon ?: primaryIcon,
                label = secondaryLabel,
                filled = false,
                accent = accent,
                onClick = onSecondary,
            )
        }
    }
}

@Composable
private fun PillButton(
    icon: ImageVector,
    label: String,
    filled: Boolean,
    accent: Color,
    onClick: () -> Unit,
) {
    val content = if (filled) Color.White else accent
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(999.dp),
        color = if (filled) accent else Color.Transparent,
        border = if (filled) null else androidx.compose.foundation.BorderStroke(1.dp, accent),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(text = label, color = content, fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
    }
}
