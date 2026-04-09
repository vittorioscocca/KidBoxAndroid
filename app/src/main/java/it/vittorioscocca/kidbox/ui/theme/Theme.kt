package it.vittorioscocca.kidbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import it.vittorioscocca.kidbox.R

data class KidBoxColorScheme(
    val background: Color,
    val card: Color,
    val title: Color,
    val subtitle: Color,
    val divider: Color,
    val rowBackground: Color,
)

val KidBoxLightColorScheme = KidBoxColorScheme(
    background = Color(0xFFF5F3EE),
    card = Color(0xFFFFFFFF),
    title = Color(0xFF1A1A1A),
    subtitle = Color(0xFF666666),
    divider = Color(0xFFE8E8E8),
    rowBackground = Color(0xFFFFFFFF),
)

val KidBoxDarkColorScheme = KidBoxColorScheme(
    background = Color(0xFF1C1C1E),
    card = Color(0xFF2C2C2E),
    title = Color(0xFFFFFFFF),
    subtitle = Color(0xFFAAAAAA),
    divider = Color(0xFF3A3A3C),
    rowBackground = Color(0xFF2C2C2E),
)

val LocalKidBoxColors = staticCompositionLocalOf { KidBoxLightColorScheme }

val MaterialTheme.kidBoxColors: KidBoxColorScheme
    @Composable
    get() = LocalKidBoxColors.current

val NunitoFontFamily = FontFamily(
    Font(R.font.nunito_regular, FontWeight.Normal),
    Font(R.font.nunito_semibold, FontWeight.SemiBold),
    Font(R.font.nunito_bold, FontWeight.Bold),
    Font(R.font.nunito_extrabold, FontWeight.ExtraBold),
)

private val KidBoxTypography = Typography(
    displayLarge = TextStyle(fontFamily = NunitoFontFamily),
    displayMedium = TextStyle(fontFamily = NunitoFontFamily),
    displaySmall = TextStyle(fontFamily = NunitoFontFamily),
    headlineLarge = TextStyle(fontFamily = NunitoFontFamily),
    headlineMedium = TextStyle(fontFamily = NunitoFontFamily),
    headlineSmall = TextStyle(fontFamily = NunitoFontFamily),
    titleLarge = TextStyle(fontFamily = NunitoFontFamily),
    titleMedium = TextStyle(fontFamily = NunitoFontFamily),
    titleSmall = TextStyle(fontFamily = NunitoFontFamily),
    bodyLarge = TextStyle(fontFamily = NunitoFontFamily),
    bodyMedium = TextStyle(fontFamily = NunitoFontFamily),
    bodySmall = TextStyle(fontFamily = NunitoFontFamily),
    labelLarge = TextStyle(fontFamily = NunitoFontFamily),
    labelMedium = TextStyle(fontFamily = NunitoFontFamily),
    labelSmall = TextStyle(fontFamily = NunitoFontFamily),
)

@Composable
fun KidBoxTheme(
    darkTheme: Boolean,
    content: @Composable () -> Unit,
) {
    val kidBoxColors = if (darkTheme) KidBoxDarkColorScheme else KidBoxLightColorScheme
    CompositionLocalProvider(LocalKidBoxColors provides kidBoxColors) {
        MaterialTheme(
            colorScheme = if (darkTheme) darkColorScheme() else lightColorScheme(),
            typography = KidBoxTypography,
            content = content,
        )
    }
}
