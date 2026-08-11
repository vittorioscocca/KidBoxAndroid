package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Locale
import it.vittorioscocca.kidbox.util.KBLocale
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import androidx.compose.ui.platform.LocalContext

private val ItineraryAccent = Color(0xFFF2611A)

@Composable
fun TravelItineraryDetailContent(
    overview: TravelItineraryOverview,
    legs: List<KBTripLegEntity>,
    modifier: Modifier = Modifier,
    introduction: String? = null,
    heroImageDestination: String? = null,
    heroImageContext: String? = null,
    hotelsCount: Int = 0,
    restaurantsCount: Int = 0,
    activitiesCount: Int = 0,
    onHotelsClick: (() -> Unit)? = null,
    onRestaurantsClick: (() -> Unit)? = null,
    onActivitiesClick: (() -> Unit)? = null,
    onPlaceInfoClick: (() -> Unit)? = null,
    onStopClick: ((TravelItineraryStopContext) -> Unit)? = null,
    onRegenerateDayClick: ((TravelItineraryDay) -> Unit)? = null,
    regeneratingDayIndex: Int? = null,
    contentHorizontalPadding: androidx.compose.ui.unit.Dp = 16.dp,
) {
    val kb = MaterialTheme.kidBoxColors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(0.dp)),
        ) {
            TravelTripHeroBackground(
                tripName = "Viaggio a ${overview.destinationTitle}",
                legs = legs,
                modifier = Modifier.fillMaxSize(),
                imageDestination = heroImageDestination ?: overview.destinationTitle,
                imageContext = heroImageContext,
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp),
            ) {
                Text(overview.destinationTitle, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 32.sp)
                Text(overview.subtitle, color = Color.White.copy(alpha = 0.92f), fontSize = 14.sp, modifier = Modifier.padding(top = 4.dp))
            }
        }

        Column(
            modifier = Modifier.padding(horizontal = contentHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BudgetSummaryCard(overview, kb)
            BudgetCategorySection(
                overview,
                kb,
                hotelsCount,
                restaurantsCount,
                activitiesCount,
                onHotelsClick,
                onRestaurantsClick,
                onActivitiesClick,
                onPlaceInfoClick,
            )
            introduction?.trim()?.takeIf { it.isNotEmpty() }?.let { text ->
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.travel_intro), fontWeight = FontWeight.Bold, fontSize = 20.sp, color = kb.title)
                    Text(text, color = kb.subtitle, fontSize = 15.sp)
                }
            }
            Text(
                "Il tuo itinerario di ${overview.dayCount} giorni",
                fontWeight = FontWeight.Bold,
                fontSize = 22.sp,
                color = kb.title,
            )
            overview.days.forEach { day ->
                DaySection(day, overview, kb, onStopClick, onRegenerateDayClick, regeneratingDayIndex)
            }
        }
    }
}

@Composable
private fun BudgetSummaryCard(overview: TravelItineraryOverview, kb: KidBoxColorScheme) {
    Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Column(Modifier.padding(18.dp)) {
            Text(stringResource(R.string.travel_estimated_total), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = kb.subtitle)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatMoney(overview.estimatedTotal, overview.currency),
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = kb.title,
                )
                Text(
                    " / ${formatMoney(overview.budgetLimit, overview.currency)} budget",
                    fontSize = 14.sp,
                    color = kb.subtitle,
                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun BudgetCategorySection(
    overview: TravelItineraryOverview,
    kb: KidBoxColorScheme,
    hotelsCount: Int,
    restaurantsCount: Int,
    activitiesCount: Int,
    onHotelsClick: (() -> Unit)?,
    onRestaurantsClick: (() -> Unit)?,
    onActivitiesClick: (() -> Unit)?,
    onPlaceInfoClick: (() -> Unit)?,
) {
    val nights = maxOf(overview.dayCount - 1, 1)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        val hotelAction = if (hotelsCount > 0) "Vedi $hotelsCount" else null
        if (onHotelsClick != null && hotelsCount > 0) {
            Surface(
                onClick = onHotelsClick,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.15f)),
            ) {
                BudgetRowContent("🛏️", stringResource(R.string.travel_hotels), "~${formatMoney(overview.budget.hotels, overview.currency)} per $nights notti", hotelAction, kb, tappable = true)
            }
        } else {
            BudgetRow("🛏️", stringResource(R.string.travel_hotels), "~${formatMoney(overview.budget.hotels, overview.currency)} per $nights notti", hotelAction, kb, tappable = false)
        }
        BudgetRow("✈️", stringResource(R.string.travel_flights), formatMoney(overview.budget.flights, overview.currency), null, kb, tappable = false)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (onRestaurantsClick != null && restaurantsCount > 0) {
                Surface(
                    onClick = onRestaurantsClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.15f)),
                ) {
                    BudgetCompactContent(
                        "🍽️",
                        stringResource(R.string.travel_restaurants),
                        overview.budget.restaurants,
                        overview.currency,
                        kb,
                        tappable = true,
                        resultsCount = restaurantsCount,
                    )
                }
            } else {
                BudgetCompact("🍽️", stringResource(R.string.travel_restaurants), overview.budget.restaurants, overview.currency, Modifier.weight(1f), kb)
            }
            if (onActivitiesClick != null && activitiesCount > 0) {
                Surface(
                    onClick = onActivitiesClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.15f)),
                ) {
                    BudgetCompactContent(
                        "🎯",
                        stringResource(R.string.travel_activities),
                        overview.budget.activities,
                        overview.currency,
                        kb,
                        tappable = true,
                        resultsCount = activitiesCount,
                    )
                }
            } else {
                BudgetCompact("🎯", stringResource(R.string.travel_activities), overview.budget.activities, overview.currency, Modifier.weight(1f), kb)
            }
        }
        // Storia e territorio: riga intera e non compact card, perché non ha
        // un importo da mostrare — le compact card sono voci di budget.
        if (onPlaceInfoClick != null) {
            Surface(
                onClick = onPlaceInfoClick,
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.15f)),
            ) {
                BudgetRowContent(
                    "🏛️",
                    stringResource(R.string.travel_place_info),
                    stringResource(R.string.travel_place_info_subtitle, overview.destinationTitle),
                    stringResource(R.string.travel_place_info_discover),
                    kb,
                    tappable = true,
                )
            }
        }
    }
}

@Composable
private fun BudgetRow(
    emoji: String,
    title: String,
    subtitle: String,
    action: String?,
    kb: KidBoxColorScheme,
    tappable: Boolean,
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.15f)),
    ) {
        BudgetRowContent(emoji, title, subtitle, action, kb, tappable)
    }
}

@Composable
private fun BudgetRowContent(
    emoji: String,
    title: String,
    subtitle: String,
    action: String?,
    kb: KidBoxColorScheme,
    tappable: Boolean,
) {
    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 24.sp)
        Column(Modifier.padding(start = 12.dp).weight(1f)) {
            Text(title, fontWeight = FontWeight.Bold, color = kb.title)
            Text(subtitle, fontSize = 12.sp, color = kb.subtitle)
        }
        action?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    it,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tappable) ItineraryAccent else kb.title,
                )
                if (tappable) {
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = ItineraryAccent,
                        modifier = Modifier.padding(start = 2.dp).size(16.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun BudgetCompact(
    emoji: String,
    title: String,
    amount: Double,
    currency: String,
    modifier: Modifier,
    kb: KidBoxColorScheme,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.15f)),
    ) {
        BudgetCompactContent(emoji, title, amount, currency, kb, tappable = false, resultsCount = 0)
    }
}

@Composable
private fun BudgetCompactContent(
    emoji: String,
    title: String,
    amount: Double,
    currency: String,
    kb: KidBoxColorScheme,
    tappable: Boolean,
    resultsCount: Int,
) {
    Column(Modifier.padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 24.sp)
            if (tappable) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = ItineraryAccent)
            }
        }
        Text(title, fontWeight = FontWeight.Bold, color = kb.title, modifier = Modifier.padding(top = 8.dp))
        Text(formatMoney(amount, currency), fontSize = 12.sp, color = kb.subtitle, modifier = Modifier.padding(top = 4.dp))
        if (tappable && resultsCount > 0) {
            Text(
                "$resultsCount risultati",
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = ItineraryAccent,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun DaySection(
    day: TravelItineraryDay,
    overview: TravelItineraryOverview,
    kb: KidBoxColorScheme,
    onStopClick: ((TravelItineraryStopContext) -> Unit)?,
    onRegenerateDayClick: ((TravelItineraryDay) -> Unit)?,
    regeneratingDayIndex: Int?,
) {
    val currency = overview.currency
    val isRegenerating = regeneratingDayIndex == day.dayIndex
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (day.dayIndex == 1) {
            Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(
                    stringResource(R.string.travel_day1_preview),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = kb.subtitle,
                )
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(day.headline, fontWeight = FontWeight.Bold, color = kb.title)
                if (day.dateString.isNotBlank()) {
                    Text(formatDate(day.dateString), fontSize = 12.sp, color = kb.subtitle)
                }
            }
            day.dayCost?.let {
                Text(formatMoney(it, currency), fontWeight = FontWeight.SemiBold, color = kb.title)
            }
            if (onRegenerateDayClick != null) {
                Spacer(Modifier.width(4.dp))
                if (isRegenerating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp).padding(start = 4.dp),
                        strokeWidth = 2.dp,
                        color = ItineraryAccent,
                    )
                } else {
                    IconButton(
                        onClick = { onRegenerateDayClick(day) },
                        modifier = Modifier.size(32.dp),
                    ) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.travel_regenerate_day),
                            tint = ItineraryAccent,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        day.blocks.forEach { block ->
            PeriodBlockCard(block, day, overview, kb, onStopClick)
        }
    }
}

@Composable
private fun PeriodBlockCard(
    block: TravelItineraryPeriodBlock,
    day: TravelItineraryDay,
    overview: TravelItineraryOverview,
    kb: KidBoxColorScheme,
    onStopClick: ((TravelItineraryStopContext) -> Unit)?,
) {
    val context = LocalContext.current
    val currency = overview.currency
    val stopLabel = if (block.stops.size == 1) "tappa" else "tappe"
    val meta = listOfNotNull(
        block.durationSummary.takeIf { it.isNotBlank() },
        block.costSummary.takeIf { it.isNotBlank() }?.let { "~$it ${currencySymbol(currency)}" },
    ).joinToString(" · ")

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(1.dp, kb.subtitle.copy(alpha = 0.12f)),
    ) {
        Column {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(block.period.dotColor),
                )
                Text(
                    "${stringResource(block.period.titleRes)} · ${block.stops.size} $stopLabel",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 8.dp),
                    color = kb.title,
                )
                Spacer(Modifier.weight(1f))
                if (meta.isNotBlank()) {
                    Text(meta, fontSize = 11.sp, color = kb.subtitle)
                }
            }
            block.stops.forEachIndexed { index, stop ->
                val nextTitle = block.stops.getOrNull(index + 1)?.title
                StopRow(
                    stop = stop,
                    isLast = index == block.stops.size - 1,
                    kb = kb,
                    tappable = onStopClick != null,
                    onClick = onStopClick?.let { handler ->
                        {
                            handler(
                                placeContextFromStop(
                                    context,
                                    stop = stop,
                                    day = day,
                                    block = block,
                                    destinationTitle = overview.destinationTitle,
                                    nextStopTitle = nextTitle,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun StopRow(
    stop: TravelItineraryStop,
    isLast: Boolean,
    kb: KidBoxColorScheme,
    tappable: Boolean,
    onClick: (() -> Unit)?,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (tappable && onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp)
            .padding(bottom = if (isLast) 12.dp else 0.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            stop.time.ifBlank { " " },
            fontSize = 11.sp,
            color = kb.subtitle,
            modifier = Modifier.width(44.dp),
            textAlign = TextAlign.End,
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(28.dp),
        ) {
            Text(stop.emoji, fontSize = 18.sp)
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(36.dp)
                        .background(kb.subtitle.copy(alpha = 0.2f)),
                )
            }
        }
        Column(Modifier.padding(start = 8.dp, bottom = if (isLast) 0.dp else 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stop.title, fontWeight = FontWeight.SemiBold, color = kb.title, modifier = Modifier.weight(1f))
                if (tappable) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = ItineraryAccent, modifier = Modifier.size(18.dp))
                }
            }
            if (stop.detail.isNotBlank()) {
                Text(stop.detail, fontSize = 12.sp, color = kb.subtitle, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}

private fun formatMoney(value: Double, currency: String): String =
    "${value.toInt()} ${currencySymbol(currency)}"

private fun currencySymbol(currency: String): String =
    if (currency.equals("EUR", ignoreCase = true)) "€" else currency

private fun formatDate(iso: String): String = runCatching {
    val inFmt = SimpleDateFormat("yyyy-MM-dd", KBLocale.current())
    val outFmt = SimpleDateFormat("d MMM yyyy", Locale("it", "IT"))
    inFmt.parse(iso)?.let { outFmt.format(it) }
}.getOrNull() ?: iso
