package it.vittorioscocca.kidbox.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

/**
 * Il marchio "KidBox" con "Box" arancione, come sulla landing e sulla web app
 * (`--text` / `--accent` di kidboxapp.com). Il nome non si traduce.
 */
@Composable
fun KidBoxWordmark(
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 34.sp,
) {
    val isDark = MaterialTheme.kidBoxColors.isDark
    val kidColor = if (isDark) Color(0xFFF6EFE6) else Color(0xFF1C1008)
    val boxColor = if (isDark) Color(0xFFF4995A) else Color(0xFFE8833A)
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(color = kidColor)) { append("Kid") }
            withStyle(SpanStyle(color = boxColor)) { append("Box") }
        },
        modifier = modifier.semantics { heading() },
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.5).sp,
    )
}
