package it.vittorioscocca.kidbox.ui.screens.chat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.size.Scale

/**
 * Richieste Coil per le anteprime della chat, condivise fra il prefetch di ChatScreen e le
 * bolle.
 *
 * Devono essere identiche nei due punti: prima il prefetch chiedeva 400×400 mentre la bolla
 * lasciava a Coil la misura del layout (~780 px di lato). Stessa memoryCacheKey, taglie
 * diverse: Coil scartava la bitmap prefetchata perché più piccola del richiesto e
 * ridecodificava durante lo scroll, cioè esattamente quando il prefetch doveva evitarlo.
 */
internal object ChatMediaRequests {

    /** Anteprima singola (foto) in 4:3, larga quanto la bolla più larga. */
    fun single(context: Context, data: Any?, messageId: String, mediaWidthPx: Int): ImageRequest =
        build(context, data, singleKey(messageId), mediaWidthPx, mediaWidthPx * 3 / 4)

    /** Tessera quadrata di un MEDIA_GROUP: le righe hanno 2 o 3 celle, si dimensiona sulla più larga. */
    fun tile(context: Context, data: Any?, messageId: String, index: Int, mediaWidthPx: Int): ImageRequest {
        val side = mediaWidthPx / 2
        return build(context, data, tileKey(messageId, index), side, side)
    }

    fun singleKey(messageId: String) = "msg_$messageId"
    fun tileKey(messageId: String, index: Int) = "msg_${messageId}_$index"

    private fun build(context: Context, data: Any?, key: String, w: Int, h: Int): ImageRequest =
        ImageRequest.Builder(context)
            .data(data)
            .memoryCacheKey(key)
            .diskCacheKey(key)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .size(w.coerceAtLeast(1), h.coerceAtLeast(1))
            .scale(Scale.FILL)
            .build()
}

/**
 * Larghezza in px su cui si decodificano le anteprime: la bolla più larga che può
 * contenerle (80% dello schermo, max 360dp — `maxBubbleWidth` in ChatBubble).
 *
 * Non la bolla media (65%, 260dp): una foto con citazione sta dentro la bolla di
 * testo, più larga, e decodificata più piccola del riquadro verrebbe ingrandita e
 * sfocata. Coil prima misurava il layout e questo non succedeva.
 */
@Composable
internal fun chatMediaWidthPx(): Int {
    val widthDp = (LocalConfiguration.current.screenWidthDp * 0.80f).coerceAtMost(360f)
    return (widthDp * LocalDensity.current.density).toInt()
}
