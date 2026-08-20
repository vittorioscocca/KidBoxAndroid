package it.vittorioscocca.kidbox.ui.screens.wallet.loyaltycards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import androidx.compose.ui.platform.LocalContext
import it.vittorioscocca.kidbox.R

/**
 * Logo ufficiale di un brand di carte fedeltà, dentro un "chip" bianco
 * arrotondato — è così che i loghi appaiono sulle card reali, e il fondo bianco
 * li tiene leggibili sopra il gradiente colorato del brand.
 *
 * I loghi sorgente sono favicon ad alta risoluzione ma spesso piccole (48px o
 * meno): il chip resta volutamente compatto (default 44dp, immagine 28dp) per
 * non sgranarli.
 *
 * Se [logoURL] è `null` o il caricamento fallisce, il composable NON disegna
 * nulla e [onFallback] segnala al chiamante di mostrare il wordmark testuale.
 */
@Composable
fun LoyaltyCardLogo(
    logoURL: String?,
    brandName: String,
    modifier: Modifier = Modifier,
    chipSize: Dp = 44.dp,
    logoSize: Dp = 28.dp,
    cornerRadius: Dp = 10.dp,
    chipColor: Color = Color.White,
    onLoadFailed: (() -> Unit)? = null,
) {
    if (logoURL.isNullOrBlank()) return
    var failed by remember(logoURL) { mutableStateOf(false) }
    if (failed) return

    Box(
        modifier = modifier
            .size(chipSize)
            .clip(RoundedCornerShape(cornerRadius))
            .background(chipColor),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(logoURL)
                .crossfade(true)
                .build(),
            contentDescription = stringResource(R.string.wallet_loyalty_logo_content_description, brandName),
            contentScale = ContentScale.Fit,
            onError = {
                failed = true
                onLoadFailed?.invoke()
            },
            modifier = Modifier
                .size(logoSize)
                .padding(1.dp),
        )
    }
}
