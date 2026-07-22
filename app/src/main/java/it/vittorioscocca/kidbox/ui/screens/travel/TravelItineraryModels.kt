package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.ui.graphics.Color

enum class TravelItineraryPeriod(@androidx.annotation.StringRes val titleRes: Int, val dotColor: Color) {
    MORNING(it.vittorioscocca.kidbox.R.string.travel_morning_up, Color(0xFFF2BF1A)),
    AFTERNOON(it.vittorioscocca.kidbox.R.string.travel_afternoon_up, Color(0xFFF2611A)),
    EVENING(it.vittorioscocca.kidbox.R.string.travel_evening_up, Color(0xFF8C59D9)),
}

enum class TravelItineraryStopCategory(val raw: String, val emoji: String) {
    FLIGHT("flight", "✈️"),
    TRANSPORT("transport", "🚕"),
    FOOD("food", "🍝"),
    HOTEL("hotel", "🏨"),
    CULTURE("culture", "🏛️"),
    BEACH("beach", "🏖️"),
    SHOPPING("shopping", "🛍️"),
    OTHER("other", "📍"),
    ;

    companion object {
        fun from(raw: String?): TravelItineraryStopCategory =
            entries.firstOrNull { it.raw == raw?.lowercase() } ?: OTHER
    }
}

data class TravelItineraryStop(
    val time: String,
    val title: String,
    val detail: String,
    val emoji: String,
    val category: TravelItineraryStopCategory = TravelItineraryStopCategory.OTHER,
)

data class TravelItineraryPeriodBlock(
    val period: TravelItineraryPeriod,
    val stops: List<TravelItineraryStop>,
    val durationSummary: String,
    val costSummary: String,
)

data class TravelItineraryBudgetBreakdown(
    val hotels: Double,
    val flights: Double,
    val restaurants: Double,
    val activities: Double,
) {
    companion object {
        val Empty = TravelItineraryBudgetBreakdown(0.0, 0.0, 0.0, 0.0)
    }
}

data class TravelItineraryDay(
    val id: String,
    val dayIndex: Int,
    val dateString: String,
    val location: String,
    val headline: String,
    val dayCost: Double?,
    val blocks: List<TravelItineraryPeriodBlock>,
)

data class TravelItineraryOverview(
    val destinationTitle: String,
    val subtitle: String,
    val dayCount: Int,
    val estimatedTotal: Double,
    val budgetLimit: Double,
    val currency: String,
    val budget: TravelItineraryBudgetBreakdown,
    val days: List<TravelItineraryDay>,
)

data class TravelPlaceResult(
    val id: String = java.util.UUID.randomUUID().toString(),
    val title: String,
    val subtitle: String,
    val meta: String = "",
    val placeName: String = title,
    val locationContext: String = "",
) {
    val isBrowsableOnMap: Boolean
        get() {
            val query = placeName.trim()
            if (query.length !in 3..80) return false
            val lower = query.lowercase()
            if (lower.startsWith("locale consigliato")) return false
            val blocked = listOf(
                "escursione in barca",
                "passeggiata ",
                "spiaggia ",
                "chiesa di ",
                "visita alla",
                "visita al ",
            )
            return blocked.none { lower.contains(it) }
        }
}

/** Contesto per aprire il dettaglio luogo (itinerario, hotel, ristoranti). */
data class TravelItineraryStopContext(
    val id: String,
    val placeName: String,
    val locationContext: String,
    val scheduleBadge: String,
    val time: String = "",
    val staySummary: String = "",
    val costSummary: String = "",
    val nextStopTitle: String? = null,
)
