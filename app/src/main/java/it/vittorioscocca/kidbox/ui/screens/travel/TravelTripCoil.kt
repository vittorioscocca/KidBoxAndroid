package it.vittorioscocca.kidbox.ui.screens.travel

import android.content.Context
import coil.request.ImageRequest
import it.vittorioscocca.kidbox.network.KidBoxHttpHeaders

object TravelTripCoil {
    fun imageRequest(context: Context, url: String): ImageRequest =
        ImageRequest.Builder(context)
            .data(url)
            .addHeader("User-Agent", KidBoxHttpHeaders.USER_AGENT)
            .crossfade(true)
            .build()
}
