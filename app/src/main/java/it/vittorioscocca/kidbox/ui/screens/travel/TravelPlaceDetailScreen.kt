package it.vittorioscocca.kidbox.ui.screens.travel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import coil.compose.AsyncImage
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

private val PlaceAccent = Color(0xFFF2611A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelPlaceDetailScreen(
    context: TravelItineraryStopContext,
    familyId: String,
    onBack: () -> Unit,
    viewModel: TravelPlaceDetailViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val androidContext = LocalContext.current

    LaunchedEffect(context.placeName, context.locationContext, familyId) {
        viewModel.load(context, familyId)
    }

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text(context.placeName, color = kb.title, maxLines = 1) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null, tint = kb.title)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = PlaceAccent)
                }
            }
            uiState.details != null -> {
                PlaceDetailContent(
                    place = uiState.details!!,
                    context = context,
                    modifier = Modifier.padding(padding),
                    onDirections = { openDirections(androidContext, uiState.details, context) },
                )
            }
            else -> {
                PlaceDetailFallback(
                    context = context,
                    errorMessage = uiState.errorMessage,
                    modifier = Modifier.padding(padding),
                    onDirections = { openDirections(androidContext, uiState.details, context) },
                )
            }
        }
    }
}

@Composable
private fun PlaceDetailContent(
    place: TravelPlaceDetails,
    context: TravelItineraryStopContext,
    modifier: Modifier = Modifier,
    onDirections: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PhotoHero(place)
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(50), color = PlaceAccent.copy(alpha = 0.12f)) {
                Text(
                    place.category,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = PlaceAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Text(place.name, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = kb.title)
            place.rating?.let { rating ->
                if (place.reviewCount > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        StarRow(rating.roundToInt().coerceIn(0, 5))
                        Text(String.format("%.1f", rating), fontWeight = FontWeight.SemiBold, color = kb.title)
                        Text("· ${place.reviewCount} recensioni", color = kb.subtitle, fontSize = 14.sp)
                    }
                }
            }
        }
        ScheduleCard(context)
        DirectionsButton(onDirections)
        if (place.about.isNotBlank()) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.travel_info_up), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = kb.subtitle)
                Text(place.about, modifier = Modifier.padding(top = 8.dp), color = kb.title)
            }
        }
        if (place.address.isNotBlank()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Text(place.address, modifier = Modifier.weight(1f), color = kb.title, fontWeight = FontWeight.SemiBold)
                    Icon(Icons.Default.LocationOn, contentDescription = null, tint = kb.subtitle)
                }
            }
        }
        if (place.reviews.isNotEmpty()) {
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.travel_reviews_up), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = kb.subtitle)
                place.reviews.forEach { review ->
                    ReviewCard(review)
                }
                Text(
                    "powered by Google",
                    modifier = Modifier.fillMaxWidth(),
                    fontSize = 11.sp,
                    color = kb.subtitle,
                )
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun PlaceDetailFallback(
    context: TravelItineraryStopContext,
    errorMessage: String?,
    modifier: Modifier = Modifier,
    onDirections: () -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(context.placeName, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = kb.title)
        }
        ScheduleCard(context)
        errorMessage?.let {
            Text(it, modifier = Modifier.padding(horizontal = 16.dp), color = kb.subtitle)
        }
        DirectionsButton(onDirections)
    }
}

@Composable
private fun PhotoHero(place: TravelPlaceDetails) {
    val context = LocalContext.current
    if (place.photoUrls.isEmpty()) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp)
                .background(
                    Brush.linearGradient(
                        listOf(PlaceAccent.copy(alpha = 0.35f), PlaceAccent.copy(alpha = 0.12f)),
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("📷", fontSize = 48.sp)
        }
    } else {
        val pagerState = rememberPagerState(pageCount = { place.photoUrls.size })
        Box(Modifier.fillMaxWidth().height(260.dp)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                AsyncImage(
                    model = TravelTripCoil.imageRequest(context, place.photoUrls[page]),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            }
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(12.dp),
                shape = RoundedCornerShape(50),
                color = Color.Black.copy(alpha = 0.45f),
            ) {
                Text(
                    "${pagerState.currentPage + 1} / ${place.photoUrls.size}",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(context: TravelItineraryStopContext) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF2E2426),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(stringResource(R.string.travel_scheduled), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                Text(context.scheduleBadge, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.85f))
            }
            Row(Modifier.fillMaxWidth()) {
                ScheduleColumn(stringResource(R.string.travel_arrival_up), context.time.ifBlank { "—" }, Modifier.weight(1f))
                ScheduleColumn(stringResource(R.string.travel_stay_up), context.staySummary.ifBlank { "—" }, Modifier.weight(1f))
                ScheduleColumn(stringResource(R.string.travel_estimate_up), context.costSummary.ifBlank { "—" }, Modifier.weight(1f))
            }
            context.nextStopTitle?.takeIf { it.isNotBlank() }?.let { next ->
                Text("→ $next", fontSize = 12.sp, color = Color.White.copy(alpha = 0.9f), maxLines = 1)
            }
        }
    }
}

@Composable
private fun ScheduleColumn(title: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier.padding(horizontal = 4.dp)) {
        Text(title, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.55f))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun DirectionsButton(onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(50),
        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A1A1A)),
    ) {
        Icon(Icons.Default.LocationOn, contentDescription = null, tint = Color.White)
        Text(stringResource(R.string.travel_directions), modifier = Modifier.padding(start = 8.dp), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ReviewCard(review: TravelPlaceReview) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.12f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(review.text, color = kb.title, fontSize = 14.sp)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(review.authorName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = kb.title)
                    if (review.relativeTime.isNotBlank()) {
                        Text(review.relativeTime, fontSize = 11.sp, color = kb.subtitle)
                    }
                }
                StarRow(review.rating.coerceIn(0, 5))
            }
        }
    }
}

@Composable
private fun StarRow(filled: Int) {
    Row {
        repeat(5) { index ->
            Icon(
                imageVector = if (index < filled) Icons.Filled.Star else Icons.Outlined.Star,
                contentDescription = null,
                tint = PlaceAccent,
                modifier = Modifier.size(14.dp),
            )
        }
    }
}

private fun openDirections(
    context: android.content.Context,
    place: TravelPlaceDetails?,
    stopContext: TravelItineraryStopContext,
) {
    if (place != null && place.hasCoordinates) {
        val uri = Uri.parse("google.navigation:q=${place.latitude},${place.longitude}&mode=w")
        val intent = Intent(Intent.ACTION_VIEW, uri).setPackage("com.google.android.apps.maps")
        if (intent.resolveActivity(context.packageManager) != null) {
            context.startActivity(intent)
            return
        }
        val geo = Uri.parse("geo:${place.latitude},${place.longitude}?q=${Uri.encode(place.name)}")
        context.startActivity(Intent(Intent.ACTION_VIEW, geo))
        return
    }
    place?.googleMapsUri?.let { mapsUri ->
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(mapsUri)))
        return
    }
    val query = Uri.encode("${stopContext.placeName}, ${stopContext.locationContext}")
    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query")))
}
