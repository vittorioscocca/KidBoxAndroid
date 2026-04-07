package it.vittorioscocca.kidbox.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import it.vittorioscocca.kidbox.R

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
fun KidBoxTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        typography = KidBoxTypography,
        content = content,
    )
}
