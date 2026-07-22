package it.vittorioscocca.kidbox.ui.screens.travel

import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import java.net.URLDecoder
import java.net.URLEncoder

fun TravelItineraryStopContext.toPlaceDetailRoute(familyId: String): String =
    AppDestination.TravelPlaceDetail.createRoute(
        familyId = familyId,
        placeName = placeName,
        locationContext = locationContext,
        scheduleBadge = scheduleBadge,
        time = time,
        staySummary = staySummary,
        costSummary = costSummary,
        nextStopTitle = nextStopTitle,
    )

fun TravelPlaceResult.toPlaceContext(categoryTitle: String, destinationTitle: String): TravelItineraryStopContext {
    val location = locationContext.ifBlank { destinationTitle }
    return TravelItineraryStopContext(
        id = id,
        placeName = placeName.ifBlank { title },
        locationContext = location,
        scheduleBadge = categoryTitle.uppercase(),
        time = "",
        staySummary = subtitle,
        costSummary = meta,
        nextStopTitle = null,
    )
}

fun placeContextFromStop(
    context: android.content.Context,
    stop: TravelItineraryStop,
    day: TravelItineraryDay,
    block: TravelItineraryPeriodBlock,
    destinationTitle: String,
    nextStopTitle: String?,
): TravelItineraryStopContext {
    val metrics = parseStopMetrics(stop.detail)
    return TravelItineraryStopContext(
        id = "${day.id}-${block.period.name}-${stop.title}".hashCode().toString(),
        placeName = TravelItineraryBuilder.placeQueryName(stop.title, stop.detail),
        locationContext = TravelItineraryBuilder.placeSearchLocationContext(day.location, destinationTitle),
        scheduleBadge = context.getString(
            it.vittorioscocca.kidbox.R.string.travel_day_badge,
            day.dayIndex,
            context.getString(block.period.titleRes),
        ),
        time = stop.time,
        staySummary = metrics.first,
        costSummary = metrics.second,
        nextStopTitle = nextStopTitle?.let { TravelItineraryBuilder.placeQueryName(it) },
    )
}

private fun parseStopMetrics(detail: String): Pair<String, String> {
    val parts = detail.split("·").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return "" to ""
    if (parts.size == 1) {
        val single = parts[0]
        return if (single.contains("€", ignoreCase = true) || single.contains("gratis", ignoreCase = true)) {
            "" to single
        } else {
            single to ""
        }
    }
    return parts[0] to parts[1]
}

fun decodeNavArg(value: String?): String =
    value?.let { URLDecoder.decode(it, Charsets.UTF_8.name()) }.orEmpty()

fun encodeNavArg(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name())
