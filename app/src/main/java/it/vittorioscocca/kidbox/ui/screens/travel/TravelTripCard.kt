package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.SubcomposeAsyncImage
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TravelTripDateRangeFormatter {
    private val locale = Locale.forLanguageTag("it-IT")
    private val dayMonth = SimpleDateFormat("d MMM", locale)
    private val dayMonthYear = SimpleDateFormat("d MMM yyyy", locale)
    private val monthYear = SimpleDateFormat("MMM yyyy", locale)

    fun format(startEpoch: Long, endEpoch: Long): String {
        val start = Date(startEpoch)
        val end = Date(endEpoch)
        val calendar = Calendar.getInstance(locale)

        calendar.time = start
        val startDay = calendar.get(Calendar.DAY_OF_MONTH)
        val startMonth = calendar.get(Calendar.MONTH)
        val startYear = calendar.get(Calendar.YEAR)

        calendar.time = end
        val endDay = calendar.get(Calendar.DAY_OF_MONTH)
        val endMonth = calendar.get(Calendar.MONTH)
        val endYear = calendar.get(Calendar.YEAR)

        if (startYear == endYear && startMonth == endMonth && startDay == endDay) {
            return dayMonthYear.format(start)
        }
        if (startYear == endYear && startMonth == endMonth) {
            return "$startDay – $endDay ${monthYear.format(start)}"
        }
        if (startYear == endYear) {
            return "${dayMonth.format(start)} – ${dayMonthYear.format(end)}"
        }
        return "${dayMonthYear.format(start)} – ${dayMonthYear.format(end)}"
    }
}

@Composable
fun TravelTripHeroBackground(
    tripName: String,
    legs: List<KBTripLegEntity>,
    modifier: Modifier = Modifier,
    imageDestination: String? = null,
    imageContext: String? = null,
) {
    val resolvedDestination = remember(tripName, legs, imageDestination) {
        val explicit = imageDestination?.trim().orEmpty()
        if (explicit.isNotEmpty()) {
            explicit.substringBefore(',').trim()
        } else {
            TravelTripThumbnailResolver.primaryDestination(tripName, legs)
                .substringBefore(',').trim()
        }
    }
    val spec = remember(tripName, legs) {
        TravelTripThumbnailResolver.resolve(tripName, legs)
    }
    val context = LocalContext.current
    var imageUrl by remember(resolvedDestination, imageContext) { mutableStateOf<String?>(null) }

    LaunchedEffect(resolvedDestination, imageContext) {
        imageUrl = TravelTripPlaceImageLoader.imageUrl(resolvedDestination, imageContext)
    }

    Box(modifier = modifier) {
        val placeholder: @Composable () -> Unit = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.linearGradient(spec.colors)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = spec.icon,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.28f),
                    modifier = Modifier.size(72.dp),
                )
            }
        }

        val url = imageUrl
        if (url != null) {
            SubcomposeAsyncImage(
                model = TravelTripCoil.imageRequest(context, url),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                loading = { placeholder() },
                error = { placeholder() },
            )
        } else {
            placeholder()
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.05f),
                        0.45f to Color.Black.copy(alpha = 0.35f),
                        1f to Color.Black.copy(alpha = 0.78f),
                    ),
                ),
        )
    }
}

@Composable
fun TravelTripCard(
    trip: KBTripEntity,
    legs: List<KBTripLegEntity>,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    showsSelectionBadge: Boolean = false,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(212.dp)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        TravelTripHeroBackground(
            tripName = trip.name,
            legs = legs,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 16.dp)
                .padding(top = 12.dp, bottom = 22.dp),
        ) {
            Text(
                text = trip.name,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 20.sp,
                maxLines = 2,
            )
            Text(
                text = TravelTripDateRangeFormatter.format(trip.startDateEpoch, trip.endDateEpoch),
                color = Color.White.copy(alpha = 0.92f),
                fontSize = 14.sp,
                maxLines = 2,
                modifier = Modifier.padding(top = 6.dp),
            )
        }

        if (showsSelectionBadge) {
            Icon(
                imageVector = if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.RadioButtonUnchecked,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .size(28.dp),
            )
        }
    }
}
