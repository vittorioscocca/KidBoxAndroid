package it.vittorioscocca.kidbox.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.feature.passwords.PasswordStrength
import it.vittorioscocca.kidbox.feature.passwords.PasswordStrengthLevel

/** Barra forza password (allineata a KBTheme iOS: viola KidBox + verde). */
@Composable
fun PasswordStrengthMeter(
    password: String,
    modifier: Modifier = Modifier,
) {
    val result = PasswordStrength.evaluate(password)
    val light = !isSystemInDarkTheme()
    val track = MaterialTheme.colorScheme.surfaceVariant
    val bar = barColorForLevel(result.level, light)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(track),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(result.fillFraction.toFloat().coerceIn(0.04f, 1f))
                    .clip(CircleShape)
                    .background(bar),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = result.level.labelIt(),
                style = MaterialTheme.typography.labelMedium,
                color = bar,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (password.isNotEmpty()) {
                Text(
                    text = "~${result.estimatedBits.toInt()} bit",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun barColorForLevel(level: PasswordStrengthLevel, light: Boolean): Color = when (level) {
    PasswordStrengthLevel.VERY_WEAK -> Color(0xFFD93636)
    PasswordStrengthLevel.WEAK -> Color(0xFFF27318)
    PasswordStrengthLevel.FAIR ->
        if (light) Color(0xFFBF900D) else Color(0xFFF2D140)
    PasswordStrengthLevel.STRONG -> Color(0xFF4DA676)
    PasswordStrengthLevel.VERY_STRONG -> Color(0xFF9973D9)
}
