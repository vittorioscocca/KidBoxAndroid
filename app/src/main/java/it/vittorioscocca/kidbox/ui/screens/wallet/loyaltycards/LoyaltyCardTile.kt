package it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.data.local.entity.KBLoyaltyCardEntity

/** Parsing best-effort di un colore esadecimale, con fallback grigio neutro. */
fun parseLoyaltyCardColor(hex: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(hex))
}.getOrElse { Color(0xFF9E9E9E) }

/**
 * Tile colorata per una carta fedeltà: gradient primario/secondario + logo
 * ufficiale del brand dentro un chip bianco, con fallback al wordmark testuale
 * quando la carta non ha `logoURL` o il download fallisce. Mirror Compose di
 * `LoyaltyCardTileView` (iOS).
 */
@Composable
fun LoyaltyCardTile(
    card: KBLoyaltyCardEntity,
    modifier: Modifier = Modifier,
    height: androidx.compose.ui.unit.Dp = 130.dp,
    // Default a false: il dettaglio carta riusa la tile senza selezione e resta
    // identico a prima.
    isSelecting: Boolean = false,
    isSelected: Boolean = false,
) {
    val primary = parseLoyaltyCardColor(card.primaryColorHex)
    val secondary = parseLoyaltyCardColor(card.secondaryColorHex)

    // Il logo è la firma visiva della card: quando c'è, il wordmark testuale
    // sparisce; torna se l'URL manca o il download fallisce.
    var showLogo by remember(card.logoURL) { mutableStateOf(!card.logoURL.isNullOrBlank()) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .shadow(elevation = 6.dp, shape = RoundedCornerShape(18.dp), ambientColor = primary, spotColor = primary)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(primary, secondary))),
    ) {
        if (isSelecting) {
            // Solo il cerchio, come la tile iOS: i glifi ☑/☐ dentro un riquadro
            // scuro leggevano come una spunta quadrata ed erano l'unico punto
            // in cui le due piattaforme divergevano.
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(10.dp)
                    .size(26.dp),
            )
        }
        LoyaltyCardLogo(
            logoURL = card.logoURL,
            brandName = card.brandName,
            onLoadFailed = { showLogo = false },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(14.dp),
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(14.dp),
        ) {
            // Il nome del negozio va SEMPRE mostrato, anche quando il logo
            // carica: il logo lo affianca, non lo sostituisce (stesso
            // comportamento della tile iOS).
            Text(
                card.brandName,
                color = Color.White,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
            )
            if (card.cardNumber.isNotBlank()) {
                Text(
                    maskedCardNumber(card.cardNumber),
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
                )
            }
        }
    }
}

private fun maskedCardNumber(number: String): String {
    val trimmed = number.trim()
    if (trimmed.length <= 4) return trimmed
    return "•••• ${trimmed.takeLast(4)}"
}
