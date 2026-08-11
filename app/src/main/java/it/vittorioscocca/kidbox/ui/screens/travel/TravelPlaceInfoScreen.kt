package it.vittorioscocca.kidbox.ui.screens.travel

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val InfoAccent = Color(0xFFF2611A)

/** Scheda "Storia e territorio" della destinazione: foto grande + testo storico/descrittivo da Wikipedia. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TravelPlaceInfoScreen(
    placeName: String,
    familyId: String,
    onBack: () -> Unit,
    viewModel: TravelPlaceInfoViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(placeName, familyId) {
        viewModel.load(placeName, familyId)
    }

    Scaffold(
        containerColor = kb.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.travel_place_info_title), color = kb.title) },
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
            uiState.info != null -> PlaceInfoContent(info = uiState.info!!, modifier = Modifier.padding(padding))
            uiState.loadFailed -> Box(
                Modifier.fillMaxSize().padding(padding).padding(top = 60.dp),
                contentAlignment = Alignment.TopCenter,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.MenuBook, contentDescription = null, tint = kb.subtitle, modifier = Modifier.padding(bottom = 8.dp))
                    Text(
                        stringResource(R.string.travel_place_info_unavailable),
                        color = kb.subtitle,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 32.dp),
                    )
                }
            }
            else -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = InfoAccent)
            }
        }
    }
}

@Composable
private fun PlaceInfoContent(info: TravelPlaceInfo, modifier: Modifier = Modifier) {
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        // Galleria orizzontale: una sola foto raccontava poco di un posto, ed
        // è quello che rendeva la scheda povera.
        if (info.imageUrls.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(info.imageUrls) { url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(260.dp)
                            .height(190.dp)
                            .clip(RoundedCornerShape(14.dp)),
                    )
                }
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(info.title, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = kb.title)
            if (info.subtitle.isNotBlank()) {
                Text(
                    info.subtitle.replaceFirstChar { it.uppercase() },
                    fontSize = 14.sp,
                    color = kb.subtitle,
                )
            }
        }

        // ── Google: presenza, indirizzo e voto ──────────────────────────
        info.googleDetails?.let { g ->
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if ((g.rating ?: 0.0) > 0) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.height(16.dp))
                        Text(String.format(" %.1f", g.rating), fontWeight = FontWeight.SemiBold, color = kb.title, fontSize = 14.sp)
                        if (g.reviewCount > 0) {
                            Text(
                                " · ${g.reviewCount} " + stringResource(R.string.travel_place_info_google_reviews),
                                fontSize = 12.sp,
                                color = kb.subtitle,
                            )
                        }
                    }
                }
                if (g.address.isNotBlank()) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = kb.subtitle, modifier = Modifier.height(16.dp))
                        Text(g.address, fontSize = 12.sp, color = kb.subtitle, modifier = Modifier.padding(start = 4.dp))
                    }
                }
                if (g.about.isNotBlank()) {
                    Text(g.about, fontSize = 14.sp, color = kb.title)
                }
                g.googleMapsUri?.let { uri ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 4.dp).clickableOpen(context, uri),
                    ) {
                        Icon(Icons.Filled.Map, contentDescription = null, tint = InfoAccent, modifier = Modifier.height(16.dp))
                        Text(
                            " " + stringResource(R.string.travel_place_info_open_maps),
                            color = InfoAccent,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 13.sp,
                        )
                    }
                }
            }
        }

        // ── Storia e territorio ──────────────────────────────────────────
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            info.paragraphs.forEach { paragraph ->
                Text(paragraph, fontSize = 15.sp, color = kb.title)
            }
        }

        // ── Cosa dicono i visitatori ──────────────────────────────────────
        val reviews = info.googleDetails?.reviews.orEmpty()
        if (reviews.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(stringResource(R.string.travel_place_info_visitors_say), fontWeight = FontWeight.Bold, color = kb.title)
                reviews.take(3).forEach { review ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(review.authorName, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = kb.title)
                            if (review.rating > 0) {
                                Icon(
                                    Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color(0xFFFFC107),
                                    modifier = Modifier.padding(start = 6.dp).height(12.dp),
                                )
                                Text(" ${review.rating}", fontSize = 11.sp, color = kb.subtitle)
                            }
                            Spacer(Modifier.weight(1f))
                            Text(review.relativeTime, fontSize = 11.sp, color = kb.subtitle)
                        }
                        Text(review.text, fontSize = 13.sp, color = kb.subtitle, maxLines = 5)
                    }
                }
            }
        }

        info.wikipediaUrl?.let { url ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickableOpen(context, url)) {
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = InfoAccent, modifier = Modifier.height(16.dp))
                Text(
                    " " + stringResource(R.string.travel_place_info_continue_wikipedia),
                    color = InfoAccent,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                )
            }
        }

        Text(
            stringResource(R.string.travel_place_info_sources),
            fontSize = 11.sp,
            color = kb.subtitle,
        )
    }
}

@Composable
private fun Modifier.clickableOpen(context: android.content.Context, url: String): Modifier =
    this.then(
        Modifier.clickable {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        },
    )
