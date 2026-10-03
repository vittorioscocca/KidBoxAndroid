package it.vittorioscocca.kidbox.ui.screens.news

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ConfirmationNumber
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import it.vittorioscocca.kidbox.ai.getAiSettingsFromApp
import it.vittorioscocca.kidbox.ai.AiConsentDialog
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.ai.LocalUpgradeAction
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import it.vittorioscocca.kidbox.util.KBLocale
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import java.util.Date

private val Orange = Color(0xFFFF6B00)
private val Green = Color(0xFF4DA673)

/**
 * La scheda Notizie (seconda radice della barra in basso). Tre stati prima del
 * contenuto, come su iOS: Free → invito a Pro; mai attivata → presentazione con
 * il costo in messaggi; attiva → l'edizione del giorno.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewsScreen(
    onOpenSettings: () -> Unit,
    viewModel: NewsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val prefs by viewModel.prefsStore.state.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val context = LocalContext.current
    val upgradeAction = LocalUpgradeAction.current
    // Le offerte su misura mandano ad Anthropic dati della famiglia (bollette,
    // spesa): passano dallo stesso consenso dell'assistente.
    val aiSettings = remember(context) { context.getAiSettingsFromApp() }
    val consentGiven by aiSettings.consentGiven.collectAsStateWithLifecycle(initialValue = false)
    var showOffersConsent by remember { mutableStateOf(false) }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.news_title),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = kb.title,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onOpenSettings) {
                Icon(Icons.Filled.Tune, contentDescription = stringResource(R.string.news_settings_desc), tint = kb.title)
            }
        }

        when {
            !state.isPaid -> NewsLocked {
                AppAnalytics.aiPaywallShown(context, "news_tab")
                upgradeAction(context.getString(R.string.news_upgrade_subtitle))
            }
            !prefs.enabled -> NewsIntro(
                place = prefs.effectivePlace,
                maxUnits = state.feed?.maxUnitsPerEdition ?: 6,
                onEditPlace = onOpenSettings,
                onActivate = viewModel::activate,
            )
            else -> PullToRefreshBox(
                isRefreshing = false,
                onRefresh = { viewModel.load(force = true) },
                modifier = Modifier.fillMaxSize(),
            ) {
                NewsContent(
                    state = state,
                    prefs = prefs,
                    onFilter = viewModel::setFilter,
                    onOpenSettings = onOpenSettings,
                    onRetry = { viewModel.load(force = true) },
                    onSearchOffers = { if (consentGiven) viewModel.searchOffers() else showOffersConsent = true },
                    onOpen = { url, kind, category, level ->
                        open(url)
                        viewModel.itemOpened(kind, category, level)
                    },
                )
            }
        }
    }

    if (showOffersConsent) {
        AiConsentDialog(
            onAccept = {
                showOffersConsent = false
                viewModel.searchOffers()
            },
            onDismiss = { showOffersConsent = false },
        )
    }
}

@Composable
private fun NewsContent(
    state: NewsUiState,
    prefs: NewsPrefs,
    onFilter: (NewsCategory?) -> Unit,
    onOpenSettings: () -> Unit,
    onRetry: () -> Unit,
    onSearchOffers: () -> Unit,
    onOpen: (url: String, kind: String, category: String, level: String) -> Unit,
) {
    val kb = MaterialTheme.kidBoxColors
    val feed = state.feed
    val items = feed?.items.orEmpty().filter { state.filter == null || it.category == state.filter.id }
    val events = if (state.filter == null || state.filter == NewsCategory.LEISURE) feed?.events.orEmpty() else emptyList()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "header") {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = java.text.SimpleDateFormat("EEEE d MMMM", KBLocale.current()).format(Date())
                        .replaceFirstChar { it.titlecase(KBLocale.current()) },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = kb.subtitle,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable(onClick = onOpenSettings),
                ) {
                    Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Orange, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(prefs.effectivePlace.label, color = Orange, style = MaterialTheme.typography.bodyMedium)
                }
                if ((feed?.totalUnits ?: 0) > 0) {
                    Text(
                        stringResource(R.string.news_edition_units, feed!!.totalUnits),
                        style = MaterialTheme.typography.bodySmall,
                        color = kb.subtitle,
                    )
                }
            }
        }

        if (feed == null) {
            item(key = "waiting") {
                if (state.error != null) ErrorCard(state.error, onRetry) else PreparingCard(localOnly = false)
            }
            return@LazyColumn
        }

        item(key = "chips") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    Chip(stringResource(R.string.news_filter_all), Icons.Filled.GridView, Orange, state.filter == null) { onFilter(null) }
                }
                items(prefs.categories, key = { it.id }) { cat ->
                    Chip(stringResource(cat.title), cat.icon, cat.tint, state.filter == cat) {
                        onFilter(if (state.filter == cat) null else cat)
                    }
                }
            }
        }

        if (feed.isPreparing) {
            item(key = "preparing") { PreparingCard(localOnly = "local" in feed.pending && "country" !in feed.pending) }
        }

        if (!prefs.effectivePlace.hasCity) {
            item(key = "addCity") { AddCityCard(onOpenSettings) }
        }

        if (prefs.personalOffers && (state.filter == null || state.filter == NewsCategory.ECONOMY)) {
            item(key = "offersTitle") { SectionTitle(stringResource(R.string.news_offers_title), Icons.Filled.AutoAwesome) }
            state.offers?.offers.orEmpty().forEach { offer ->
                item(key = "offer-${offer.url}-${offer.title}") {
                    OfferCard(offer) { onOpen(offer.url, "offer", offer.kind, "personal") }
                }
            }
            item(key = "offersFooter") {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    val offers = state.offers
                    if (offers?.generatedAtMs != null && offers.offers.isNotEmpty()) {
                        Text(
                            stringResource(
                                R.string.news_offers_searched,
                                DateUtils.getRelativeTimeSpanString(offers.generatedAtMs, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString(),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = kb.subtitle,
                        )
                    } else if (offers?.status == "fresh") {
                        Text(stringResource(R.string.news_offers_none), style = MaterialTheme.typography.bodyMedium, color = kb.subtitle)
                    }
                    Button(
                        onClick = onSearchOffers,
                        enabled = !state.searchingOffers,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
                    ) {
                        if (state.searchingOffers) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(stringResource(R.string.news_offers_searching))
                        } else {
                            Icon(Icons.Filled.Search, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (offers?.offers.isNullOrEmpty()) R.string.news_offers_search else R.string.news_offers_search_again,
                                ),
                            )
                        }
                    }
                    if (!state.searchingOffers) {
                        Text(
                            stringResource(R.string.news_offers_cost, offers?.estimateUnits ?: 8),
                            style = MaterialTheme.typography.labelSmall,
                            color = kb.subtitle,
                        )
                    }
                    state.offersError?.let {
                        Text(it, style = MaterialTheme.typography.bodySmall, color = Orange)
                    }
                }
            }
        }

        val groups = listOf(
            Triple("country", feed.placeCountry, Icons.Filled.Flag),
            Triple("region", feed.placeRegion, Icons.Filled.Map),
            Triple("city", feed.placeCity, Icons.Filled.LocationCity),
        )
        groups.forEach { (level, title, icon) ->
            val levelItems = items.filter { it.level == level }
            if (levelItems.isNotEmpty()) {
                item(key = "title-$level") { SectionTitle(title ?: "", icon) }
                levelItems.forEach { news ->
                    item(key = "news-${news.url}") {
                        NewsItemCard(news) { onOpen(news.url, "news", news.category, news.level) }
                    }
                }
            }
        }

        if (events.isNotEmpty()) {
            item(key = "eventsTitle") { SectionTitle(stringResource(R.string.news_events_title), Icons.Filled.ConfirmationNumber) }
            events.forEach { event ->
                item(key = "event-${event.url}-${event.startDate}-${event.title}") {
                    EventCard(event) { onOpen(event.url, "event", "leisure", "city") }
                }
            }
        }

        if (items.isEmpty() && events.isEmpty() && !feed.isPreparing) {
            item(key = "empty") { EmptyState() }
        }

        item(key = "disclaimer") {
            Text(
                stringResource(R.string.news_disclaimer),
                style = MaterialTheme.typography.labelSmall,
                color = kb.subtitle,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

// ── Pezzi ────────────────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String, icon: ImageVector) {
    val kb = MaterialTheme.kidBoxColors
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
        Icon(icon, contentDescription = null, tint = kb.title, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = kb.title)
    }
}

@Composable
private fun Chip(label: String, icon: ImageVector, tint: Color, selected: Boolean, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) tint else tint.copy(alpha = 0.12f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Icon(icon, contentDescription = null, tint = if (selected) Color.White else tint, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = if (selected) Color.White else tint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
    }
}

@Composable
private fun CardSurface(onClick: (() -> Unit)?, border: BorderStroke? = null, content: @Composable () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Surface(
        color = kb.card,
        shape = RoundedCornerShape(16.dp),
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick) else Modifier),
    ) {
        Box(Modifier.padding(14.dp)) { content() }
    }
}

@Composable
private fun NewsItemCard(item: NewsItem, onOpen: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    CardSurface(onClick = onOpen) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                item.newsCategory?.let { cat ->
                    Icon(cat.icon, contentDescription = null, tint = cat.tint, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(cat.title), color = cat.tint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                keyDateBadge(item)?.let { badge ->
                    Text(
                        badge,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (item.keyDateKind == "deadline") Color(0xFFD93A3A) else Orange)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Text(item.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = kb.title)
            Text(item.summary, style = MaterialTheme.typography.bodyMedium, color = kb.subtitle)
            item.action?.takeIf { it.isNotBlank() }?.let { action ->
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Green, modifier = Modifier.size(16.dp).padding(top = 2.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(action, style = MaterialTheme.typography.bodySmall, color = kb.title)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                val date = NewsDates.dayMonth(item.publishedAt)
                Text(
                    listOfNotNull(item.source.takeIf { it.isNotBlank() }, date).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = kb.subtitle,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
private fun keyDateBadge(item: NewsItem): String? {
    val day = NewsDates.dayMonth(item.keyDate) ?: return null
    return when (item.keyDateKind) {
        "deadline" -> stringResource(R.string.news_badge_deadline, day)
        "start" -> stringResource(R.string.news_badge_start, day)
        "payment" -> stringResource(R.string.news_badge_payment, day)
        else -> null
    }
}

@Composable
private fun EventCard(event: NewsEvent, onOpen: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val tint = NewsCategory.LEISURE.tint
    val start = NewsDates.parse(event.startDate)
    CardSurface(onClick = onOpen) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.12f))
                    .width(48.dp)
                    .padding(vertical = 6.dp),
            ) {
                Text(
                    start?.let { java.text.SimpleDateFormat("d", KBLocale.current()).format(it) } ?: "",
                    color = tint, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                )
                Text(
                    start?.let { java.text.SimpleDateFormat("MMM", KBLocale.current()).format(it).uppercase(KBLocale.current()) } ?: "",
                    color = tint, fontWeight = FontWeight.SemiBold, fontSize = 11.sp,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp), modifier = Modifier.weight(1f)) {
                Text(event.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = kb.title)
                val where = listOfNotNull(
                    event.place?.takeIf { it.isNotBlank() },
                    event.distanceKm?.takeIf { it > 0 }?.let { "$it km" },
                ).joinToString(" · ")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (where.isNotEmpty()) {
                        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(13.dp))
                        Spacer(Modifier.width(3.dp))
                        Text(where, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
                    }
                    if (event.free == true) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.news_event_free),
                            color = Green, fontWeight = FontWeight.Bold, fontSize = 11.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(Green.copy(alpha = 0.18f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                val end = event.endDate
                if (end != null && end != event.startDate) {
                    val a = NewsDates.short(event.startDate)
                    val b = NewsDates.short(end)
                    if (a != null && b != null) Text("$a → $b", style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
                }
                event.summary?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = kb.subtitle, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun OfferCard(offer: NewsOffer, onOpen: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    val tint = NewsCategory.ECONOMY.tint
    val icon = when (offer.kind) {
        "electricity" -> Icons.Filled.Bolt
        "gas" -> Icons.Filled.LocalFireDepartment
        "water" -> Icons.Filled.WaterDrop
        "internet" -> Icons.Filled.Wifi
        "phone" -> Icons.Filled.Phone
        else -> Icons.Filled.ShoppingCart
    }
    CardSurface(onClick = onOpen, border = BorderStroke(1.dp, tint.copy(alpha = 0.35f))) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp).padding(top = 2.dp))
                Spacer(Modifier.width(8.dp))
                Text(offer.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, color = kb.title)
            }
            Text(offer.summary, style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
            Row(verticalAlignment = Alignment.CenterVertically) {
                offer.saving?.takeIf { it.isNotBlank() }?.let {
                    Text(it, color = tint, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Text(offer.source, style = MaterialTheme.typography.labelSmall, color = kb.subtitle)
            }
        }
    }
}

@Composable
private fun PreparingCard(localOnly: Boolean) {
    val kb = MaterialTheme.kidBoxColors
    CardSurface(onClick = null) {
        Row(verticalAlignment = Alignment.Top) {
            CircularProgressIndicator(strokeWidth = 2.dp, color = Orange, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    stringResource(if (localOnly) R.string.news_preparing_local else R.string.news_preparing),
                    fontWeight = FontWeight.SemiBold, color = kb.title,
                )
                Text(stringResource(R.string.news_preparing_hint), style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    CardSurface(onClick = null) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Filled.Warning, contentDescription = null, tint = Orange, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(message, color = kb.title, style = MaterialTheme.typography.bodyMedium)
            }
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
            ) { Text(stringResource(R.string.news_retry)) }
        }
    }
}

@Composable
private fun AddCityCard(onClick: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Orange.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Orange, modifier = Modifier.size(26.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.news_add_city), fontWeight = FontWeight.SemiBold, color = kb.title)
            Text(stringResource(R.string.news_add_city_sub), style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
        }
    }
}

@Composable
private fun EmptyState() {
    val kb = MaterialTheme.kidBoxColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Newspaper, contentDescription = null, tint = kb.subtitle, modifier = Modifier.size(40.dp))
        Text(stringResource(R.string.news_empty_title), fontWeight = FontWeight.SemiBold, color = kb.title)
        Text(stringResource(R.string.news_empty_body), style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
    }
}

// ── Presentazione e invito ───────────────────────────────────────────────────

@Composable
private fun NewsIntro(place: NewsPlace, maxUnits: Int, onEditPlace: () -> Unit, onActivate: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Icon(Icons.Filled.Newspaper, contentDescription = null, tint = Orange, modifier = Modifier.size(44.dp))
        Text(stringResource(R.string.news_intro_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = kb.title)
        IntroBullet(NewsCategory.BONUS.icon, stringResource(R.string.news_intro_bullet_daily))
        IntroBullet(NewsCategory.LEISURE.icon, stringResource(R.string.news_intro_bullet_events))
        IntroBullet(Icons.Filled.AutoAwesome, stringResource(R.string.news_intro_bullet_offers))
        IntroBullet(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.news_intro_bullet_sources))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(kb.card)
                .clickable(onClick = onEditPlace)
                .padding(14.dp),
        ) {
            Icon(Icons.Filled.LocationOn, contentDescription = null, tint = Orange)
            Spacer(Modifier.width(8.dp))
            Text(place.label, color = kb.title, modifier = Modifier.weight(1f))
            Text(stringResource(R.string.news_change), color = Orange, fontWeight = FontWeight.SemiBold)
        }
        Text(stringResource(R.string.news_intro_cost, maxUnits), style = MaterialTheme.typography.bodySmall, color = kb.subtitle)
        Button(
            onClick = onActivate,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
        ) { Text(stringResource(R.string.news_activate), fontWeight = FontWeight.Bold) }
    }
}

@Composable
private fun IntroBullet(icon: ImageVector, text: String) {
    val kb = MaterialTheme.kidBoxColors
    Row(verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = Orange, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = kb.title)
    }
}

@Composable
private fun NewsLocked(onUpgrade: () -> Unit) {
    val kb = MaterialTheme.kidBoxColors
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(84.dp).clip(CircleShape).background(Orange.copy(alpha = 0.12f)),
        ) {
            Icon(Icons.Filled.Newspaper, contentDescription = null, tint = Orange, modifier = Modifier.size(44.dp))
        }
        Text(stringResource(R.string.news_locked_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = kb.title)
        Text(stringResource(R.string.news_locked_body), style = MaterialTheme.typography.bodyMedium, color = kb.subtitle)
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            NewsCategory.entries.forEach { cat ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(cat.icon, contentDescription = null, tint = cat.tint, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(10.dp))
                    Text(stringResource(cat.title), color = cat.tint, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        Button(
            onClick = onUpgrade,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Orange, contentColor = Color.White),
        ) { Text(stringResource(R.string.news_discover_pro), fontWeight = FontWeight.Bold) }
    }
}
