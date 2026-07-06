package it.vittorioscocca.kidbox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/**
 * Rounded pill for visibility tags, feature tags and filters — neutral or category-tinted.
 * Mirrors the KidBox design system's `Chip` component (design-system/components/core/Chip.jsx)
 * and the iOS `KBChip`.
 */
@Composable
fun KidBoxChip(
    label: String,
    modifier: Modifier = Modifier,
    tint: Color? = null,
    onClick: (() -> Unit)? = null,
) {
    val kb = MaterialTheme.kidBoxColors
    val background = tint?.copy(alpha = 0.14f) ?: kb.visibilityChipBackground
    val foreground = tint ?: kb.visibilityChipForeground
    val border = tint?.copy(alpha = 0.26f)

    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(100.dp),
        colors = CardDefaults.cardColors(containerColor = background),
        border = border?.let { BorderStroke(1.dp, it) },
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            fontSize = 14.sp,
            color = foreground,
        )
    }
}
