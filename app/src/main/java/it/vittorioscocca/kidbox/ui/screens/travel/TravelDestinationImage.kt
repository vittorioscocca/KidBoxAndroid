package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
fun TravelDestinationImage(
    destinationName: String,
    locationContext: String? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var imageUrl by remember(destinationName, locationContext) { mutableStateOf<String?>(null) }

    LaunchedEffect(destinationName, locationContext) {
        imageUrl = TravelTripPlaceImageLoader.imageUrl(destinationName, locationContext)
    }

    Box(modifier = modifier) {
        val url = imageUrl
        if (url != null) {
            AsyncImage(
                model = TravelTripCoil.imageRequest(context, url),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF59739E), Color(0xFF38527A)),
                        ),
                    ),
            )
        }
    }
}
