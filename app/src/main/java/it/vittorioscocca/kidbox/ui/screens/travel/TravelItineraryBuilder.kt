package it.vittorioscocca.kidbox.ui.screens.travel

import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripDayPlanEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

object TravelItineraryBuilder {

    fun build(
        trip: KBTripEntity,
        dayPlans: List<KBTripDayPlanEntity>,
        legs: List<KBTripLegEntity>,
        members: List<KBFamilyMemberEntity>,
        children: List<KBChildEntity>,
    ): TravelItineraryOverview {
        val proposal = parseProposalJson(trip.aiProposalJson)
        val tripMeta = proposal?.optJSONObject("trip")
        val estimated = tripMeta?.optDouble("estimatedTotalCost")?.takeIf { it > 0 }
            ?: dayPlans.mapNotNull { it.estimatedDailyCost }.sum().takeIf { it > 0 }
            ?: trip.budgetTotal
        val currency = tripMeta?.optString("currency")?.takeIf { it.isNotBlank() } ?: trip.currency
        val breakdown = budgetBreakdown(
            tripMeta?.optJSONObject("budgetBreakdown"),
            estimated,
            dayPlans,
            legs,
        )
        val destination = destinationTitle(trip.name)
        val dayCount = tripDayCount(trip)
        val subtitle = "Italia · $dayCount ${if (dayCount == 1) "giorno" else "giorni"} · ${
            travelerSummary(trip.participantIdsJson, members, children)
        }"

        val proposalDays = proposal?.optJSONArray("dayPlans")
        val alignedPlans = alignedDayPlans(trip, dayPlans, proposal)
        val days = alignedPlans.mapIndexed { index, plan ->
            val proposalDay = findProposalDay(proposalDays, plan.dateString, index)
            buildDay(plan, index + 1, proposalDay)
        }

        return TravelItineraryOverview(
            destinationTitle = destination,
            subtitle = subtitle,
            dayCount = dayCount,
            estimatedTotal = estimated,
            budgetLimit = trip.budgetTotal,
            currency = currency,
            budget = breakdown,
            days = days,
        )
    }

    fun buildFromProposal(
        proposal: Map<String, Any>,
        tripName: String,
        budgetLimit: Double,
        currency: String,
        participantIdsJson: String,
        members: List<KBFamilyMemberEntity>,
        children: List<KBChildEntity>,
        plannedDayCount: Int,
    ): TravelItineraryOverview {
        @Suppress("UNCHECKED_CAST")
        val tripMeta = proposal["trip"] as? Map<String, Any>
        val estimated = (tripMeta?.get("estimatedTotalCost") as? Number)?.toDouble()?.takeIf { it > 0 }
            ?: budgetLimit
        val resolvedCurrency = (tripMeta?.get("currency") as? String)?.takeIf { it.isNotBlank() } ?: currency
        val breakdown = budgetBreakdown(
            tripMeta?.get("budgetBreakdown")?.let { anyToJsonObject(it) },
            estimated,
            emptyList(),
            emptyList(),
        )
        val destination = destinationTitle(tripName)
        val proposalDays = proposal["dayPlans"].asMapList()
        val dayCount = maxOf(proposalDays.size, plannedDayCount, 1)
        val subtitle = "Italia · $dayCount ${if (dayCount == 1) "giorno" else "giorni"} · ${
            travelerSummary(participantIdsJson, members, children)
        }"
        val days = proposalDays.mapIndexed { index, day ->
            buildDayFromProposal(index + 1, day)
        }
        return TravelItineraryOverview(
            destinationTitle = destination,
            subtitle = subtitle,
            dayCount = dayCount,
            estimatedTotal = estimated,
            budgetLimit = budgetLimit,
            currency = resolvedCurrency,
            budget = breakdown,
            days = days,
        )
    }

    fun buildFromSuggestion(destination: TravelDestination): TravelItineraryOverview {
        val budget = parseEstimatedCost(destination.estimatedCost) ?: 1200.0
        val dayCount = maxOf(parseDurationDays(destination.durationDays), 1)
        val proposal = destination.previewPlanMap()?.takeIf { it.isNotEmpty() }
            ?: syntheticPreviewPlan(destination, minOf(dayCount, 4), budget)
        val base = buildFromProposal(
            proposal = proposal,
            tripName = "Viaggio a ${destination.name}",
            budgetLimit = budget,
            currency = "EUR",
            participantIdsJson = "[]",
            members = emptyList(),
            children = emptyList(),
            plannedDayCount = dayCount,
        )
        val regionLine = destination.region.ifBlank { destination.name }
        return base.copy(
            destinationTitle = destination.name,
            subtitle = "$regionLine · ${base.dayCount} ${if (base.dayCount == 1) "giorno" else "giorni"} · Anteprima AI",
        )
    }

    fun parseEstimatedCost(text: String): Double? {
        val match = Regex("""(\d+(?:[.,]\d+)?)""").find(text) ?: return null
        return match.groupValues[1].replace(',', '.').toDoubleOrNull()
    }

    fun parseDurationDays(text: String): Int {
        val numbers = Regex("""\d+""").findAll(text).map { it.value.toInt() }.toList()
        return numbers.maxOrNull() ?: 3
    }

    private fun syntheticPreviewPlan(
        destination: TravelDestination,
        dayCount: Int,
        budget: Double,
    ): Map<String, Any> {
        val hotels = budget * 0.36
        val flights = budget * 0.22
        val restaurants = budget * 0.22
        val activities = max(budget - hotels - flights - restaurants, budget * 0.12)
        val location = destination.region.ifBlank { destination.name }
        val dayPlans = (1..dayCount).map { index ->
            val isFirst = index == 1
            val isLast = index == dayCount
            mapOf(
                "date" to "2026-06-${index.toString().padStart(2, '0')}",
                "location" to location,
                "estimatedDailyCost" to (budget / dayCount),
                "morningStops" to listOf(
                    stop(
                        "09:30",
                        if (isFirst) "Arrivo e check-in a ${destination.name}" else "Colazione e passeggiata",
                        90,
                        if (isFirst) "~${(budget * 0.08).toInt()}" else "Gratis",
                        if (isFirst) "hotel" else "culture",
                    ),
                ),
                "afternoonStops" to listOf(
                    stop(
                        "13:00",
                        "Osteria ${destination.name} — pranzo tipico",
                        75,
                        "~${(budget * 0.04).toInt()}",
                        "food",
                    ),
                    stop(
                        "14:30",
                        destination.tagline.ifBlank { "Esplora il centro storico" },
                        120,
                        "~${(budget * 0.06).toInt()}",
                        "culture",
                    ),
                ),
                "eveningStops" to listOf(
                    stop(
                        "19:30",
                        if (isLast) {
                            "Ristorante ${destination.name} — cena di arrivederci"
                        } else {
                            "Trattoria del Porto — cucina di pesce"
                        },
                        90,
                        "~${(budget * 0.05).toInt()}",
                        "food",
                    ),
                ),
            )
        }
        return mapOf(
            "trip" to mapOf(
                "estimatedTotalCost" to budget,
                "currency" to "EUR",
                "summary" to destination.aiHeadline.ifBlank { destination.tagline },
                "budgetBreakdown" to mapOf(
                    "hotels" to hotels,
                    "flights" to flights,
                    "restaurants" to restaurants,
                    "activities" to activities,
                ),
            ),
            "dayPlans" to dayPlans,
        )
    }

    private fun stop(
        time: String,
        title: String,
        minutes: Int,
        cost: String,
        category: String,
    ) = mapOf(
        "time" to time,
        "title" to title,
        "durationMinutes" to minutes,
        "costLabel" to cost,
        "category" to category,
    )

    private fun buildDayFromProposal(dayIndex: Int, proposalDay: Map<String, Any>): TravelItineraryDay {
        val location = proposalDay["location"]?.toString().orEmpty()
        val dateString = proposalDay["date"]?.toString().orEmpty()
        val blocks = listOf(
            buildBlock(
                TravelItineraryPeriod.MORNING,
                proposalDay["morningPlan"]?.toString().orEmpty(),
                structuredStops(proposalDay["morningStops"]),
            ),
            buildBlock(
                TravelItineraryPeriod.AFTERNOON,
                proposalDay["afternoonPlan"]?.toString().orEmpty(),
                structuredStops(proposalDay["afternoonStops"]),
            ),
            buildBlock(
                TravelItineraryPeriod.EVENING,
                proposalDay["eveningPlan"]?.toString().orEmpty(),
                structuredStops(proposalDay["eveningStops"]),
            ),
        ).filter { it.stops.isNotEmpty() }

        val headline = when {
            location.isBlank() -> "Giorno $dayIndex"
            dayIndex == 1 -> "Arrivo a $location"
            else -> location
        }
        val dayCost = (proposalDay["estimatedDailyCost"] as? Number)?.toDouble()
        return TravelItineraryDay(
            id = "$dayIndex-$dateString",
            dayIndex = dayIndex,
            dateString = dateString,
            location = location,
            headline = headline,
            dayCost = dayCost,
            blocks = blocks,
        )
    }

    private fun structuredStops(value: Any?): JSONArray? {
        val maps = value.asMapList()
        if (maps.isEmpty()) return null
        return JSONArray().apply {
            maps.forEach { put(anyToJsonObject(it)) }
        }
    }

    fun destinationTitle(tripName: String): String {
        val prefix = "Viaggio a "
        return if (tripName.startsWith(prefix)) tripName.removePrefix(prefix) else tripName
    }

    fun normalizeDayPlansForTrip(
        trip: KBTripEntity,
        parsedPlans: List<KBTripDayPlanEntity>,
        plan: Map<String, Any>,
    ): List<KBTripDayPlanEntity> {
        val proposal = anyToJsonObject(plan)
        return alignedDayPlans(trip, parsedPlans, proposal)
    }

    fun travelerSummary(
        participantIdsJson: String,
        members: List<KBFamilyMemberEntity>,
        children: List<KBChildEntity>,
    ): String {
        val ids = runCatching {
            val arr = JSONArray(participantIdsJson)
            buildList { for (i in 0 until arr.length()) add(arr.optString(i)) }
        }.getOrDefault(emptyList())
        if (ids.isEmpty()) return "Famiglia"
        val lines = ids.mapNotNull { id ->
            children.firstOrNull { it.id == id }?.let { child ->
                val age = child.birthDateEpochMillis?.let { birth ->
                    val years = ((System.currentTimeMillis() - birth) / (365.25 * 24 * 3600 * 1000)).toInt()
                    if (years > 0) "$years anni" else null
                }
                if (age != null) "${child.name} ($age)" else child.name
            } ?: members.firstOrNull { it.userId == id }?.displayName
        }
        return lines.filter { !it.isNullOrBlank() }.joinToString(", ").ifBlank { "Famiglia" }
    }

    private fun tripDayCount(trip: KBTripEntity): Int {
        val days = TimeUnit.MILLISECONDS.toDays(trip.endDateEpoch - trip.startDateEpoch).coerceAtLeast(0)
        return (days + 1).toInt()
    }

    private fun alignedDayPlans(
        trip: KBTripEntity,
        dayPlans: List<KBTripDayPlanEntity>,
        proposal: JSONObject?,
    ): List<KBTripDayPlanEntity> {
        val plannedCount = tripDayCount(trip)
        val dateStrings = tripDateStrings(trip.startDateEpoch, plannedCount)
        val proposalDays = proposal?.optJSONArray("dayPlans")
        val destination = destinationTitle(trip.name)

        return dateStrings.mapIndexed { index, dateString ->
            dayPlans.firstOrNull { it.dateString == dateString }
                ?: proposalDays?.let { days ->
                    findProposalDay(days, dateString, index)?.let { dayPlanFromProposal(it, trip, dateString, destination) }
                }
                ?: syntheticDayPlan(trip, dateString, index + 1, destination)
        }
    }

    private fun tripDateStrings(startEpoch: Long, dayCount: Int): List<String> {
        val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = startEpoch
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
        cal.set(java.util.Calendar.MINUTE, 0)
        cal.set(java.util.Calendar.SECOND, 0)
        cal.set(java.util.Calendar.MILLISECOND, 0)
        return (0 until dayCount).map { offset ->
            val day = cal.clone() as java.util.Calendar
            day.add(java.util.Calendar.DAY_OF_YEAR, offset)
            fmt.format(day.time)
        }
    }

    private fun dayPlanFromProposal(
        dict: JSONObject,
        trip: KBTripEntity,
        dateString: String,
        fallbackLocation: String,
    ): KBTripDayPlanEntity {
        val location = dict.optString("location").trim().ifBlank { fallbackLocation }
        return KBTripDayPlanEntity(
            id = java.util.UUID.randomUUID().toString(),
            familyId = trip.familyId,
            tripId = trip.id,
            dateString = dateString,
            location = location,
            morningPlan = dict.optString("morningPlan"),
            afternoonPlan = dict.optString("afternoonPlan"),
            eveningPlan = dict.optString("eveningPlan"),
            accommodationName = dict.optString("accommodationName").takeIf { it.isNotBlank() },
            accommodationType = dict.optString("accommodationType").takeIf { it.isNotBlank() },
            accommodationCostPerNight = dict.optDouble("accommodationCostPerNight").takeIf { !it.isNaN() },
            weatherBackupPlan = dict.optString("weatherBackupPlan").takeIf { it.isNotBlank() },
            estimatedDailyCost = dict.optDouble("estimatedDailyCost").takeIf { !it.isNaN() },
            updatedAtEpoch = System.currentTimeMillis(),
        )
    }

    private fun syntheticDayPlan(
        trip: KBTripEntity,
        dateString: String,
        dayIndex: Int,
        location: String,
    ): KBTripDayPlanEntity = KBTripDayPlanEntity(
        id = java.util.UUID.randomUUID().toString(),
        familyId = trip.familyId,
        tripId = trip.id,
        dateString = dateString,
        location = location,
        morningPlan = if (dayIndex == 1) "Mattina di arrivo e primo giro in $location" else "Mattina libera a $location",
        afternoonPlan = "Pomeriggio per scoprire $location",
        eveningPlan = "Sera a $location",
        accommodationName = null,
        accommodationType = null,
        accommodationCostPerNight = null,
        weatherBackupPlan = null,
        estimatedDailyCost = null,
        updatedAtEpoch = System.currentTimeMillis(),
    )

    private fun parseProposalJson(json: String?): JSONObject? =
        runCatching { JSONObject(json.orEmpty()) }.getOrNull()?.takeIf { json?.isNotBlank() == true }

    private fun findProposalDay(days: JSONArray?, date: String, index: Int): JSONObject? {
        if (days == null) return null
        for (i in 0 until days.length()) {
            val day = days.optJSONObject(i) ?: continue
            if (day.optString("date") == date) return day
        }
        return days.optJSONObject(index)
    }

    private fun buildDay(
        plan: KBTripDayPlanEntity,
        dayIndex: Int,
        proposalDay: JSONObject?,
    ): TravelItineraryDay {
        val blocks = listOf(
            buildBlock(TravelItineraryPeriod.MORNING, plan.morningPlan, proposalDay?.optJSONArray("morningStops")),
            buildBlock(TravelItineraryPeriod.AFTERNOON, plan.afternoonPlan, proposalDay?.optJSONArray("afternoonStops")),
            buildBlock(TravelItineraryPeriod.EVENING, plan.eveningPlan, proposalDay?.optJSONArray("eveningStops")),
        ).filter { it.stops.isNotEmpty() }

        val headline = when {
            plan.location.isBlank() -> "Giorno $dayIndex"
            dayIndex == 1 -> "Arrivo a ${plan.location}"
            else -> plan.location
        }

        return TravelItineraryDay(
            id = plan.id,
            dayIndex = dayIndex,
            dateString = plan.dateString,
            location = plan.location,
            headline = headline,
            dayCost = plan.estimatedDailyCost,
            blocks = blocks,
        )
    }

    private fun buildBlock(
        period: TravelItineraryPeriod,
        text: String,
        structured: JSONArray?,
    ): TravelItineraryPeriodBlock {
        val stops = if (structured != null && structured.length() > 0) {
            buildList {
                for (i in 0 until structured.length()) {
                    structured.optJSONObject(i)?.let { parseStructuredStop(it) }?.let { add(it) }
                }
            }
        } else {
            parseTextStops(text)
        }
        return TravelItineraryPeriodBlock(
            period = period,
            stops = stops,
            durationSummary = summarizeDuration(stops),
            costSummary = summarizeCost(stops),
        )
    }

    private fun parseStructuredStop(obj: JSONObject): TravelItineraryStop? {
        val title = resolveStopTitle(obj)
        if (title.isEmpty()) return null
        val time = obj.optString("time").ifBlank {
            obj.optString("startTime").ifBlank { obj.optString("hour") }
        }
        val duration = obj.optInt("durationMinutes").takeIf { it > 0 }
            ?: obj.optInt("duration").takeIf { it > 0 }
        val costLabel = obj.optString("costLabel").takeIf { it.isNotBlank() }
            ?: obj.optString("price").takeIf { it.isNotBlank() }
        val cost = obj.optDouble("cost").takeIf { !it.isNaN() }
            ?: obj.optDouble("estimatedCost").takeIf { !it.isNaN() }
        val category = TravelItineraryStopCategory.from(obj.optString("category"))
        return TravelItineraryStop(
            time = time,
            title = title,
            detail = formatDetail(duration, cost, costLabel),
            emoji = category.emoji,
            category = category,
        )
    }

    private fun resolveStopTitle(obj: JSONObject): String {
        val keys = listOf("title", "name", "place", "location", "label", "activity", "description")
        for (k in keys) {
            val v = obj.optString(k).trim()
            if (v.isNotEmpty()) return v
        }
        return ""
    }

    private fun parseTextStops(text: String): List<TravelItineraryStop> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return emptyList()
        val lines = trimmed.split("\n", " · ").map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.size <= 1) {
            val category = categoryForTitle(trimmed)
            return listOf(TravelItineraryStop("", trimmed, "", category.emoji, category))
        }
        return lines.mapNotNull { parseTextLine(it) }
    }

    private fun parseTextLine(line: String): TravelItineraryStop? {
        val trimmed = line.trim()
        if (trimmed.isEmpty()) return null
        val match = Regex("""^(\d{1,2}:\d{2})\s*[-–—]?\s*(.+)$""").find(trimmed)
        if (match != null) {
            val time = match.groupValues[1]
            val (title, detail) = splitTitleDetail(match.groupValues[2])
            val category = categoryForTitle(title)
            return TravelItineraryStop(time, title, detail, category.emoji, category)
        }
        val category = categoryForTitle(trimmed)
        return TravelItineraryStop("", trimmed, "", category.emoji, category)
    }

    private fun splitTitleDetail(rest: String): Pair<String, String> {
        val open = rest.lastIndexOf('(')
        val close = rest.lastIndexOf(')')
        if (open in 0..<close) {
            return rest.substring(0, open).trim() to rest.substring(open + 1, close).replace('•', '·')
        }
        val sep = rest.indexOf(" · ")
        return if (sep > 0) rest.substring(0, sep) to rest.substring(sep + 3) else rest to ""
    }

    private fun formatDetail(durationMinutes: Int?, cost: Double?, costLabel: String?): String {
        val parts = mutableListOf<String>()
        durationMinutes?.let { m ->
            if (m < 60) parts += "${m}m"
            else {
                val h = m / 60
                val rem = m % 60
                parts += if (rem == 0) "${h}h" else "${h}h ${rem}m"
            }
        }
        when {
            !costLabel.isNullOrBlank() -> parts += costLabel
            cost != null -> parts += if (cost <= 0) "Gratis" else "~${cost.roundToInt()}"
        }
        return parts.joinToString(" · ")
    }

    private fun summarizeDuration(stops: List<TravelItineraryStop>): String {
        val minutes = stops.mapNotNull { stop ->
            Regex("""(\d+)\s*m""").find(stop.detail)?.groupValues?.getOrNull(1)?.toIntOrNull()
        }.sum()
        if (minutes <= 0) return ""
        val h = minutes / 60
        val m = minutes % 60
        return if (h == 0) "${m}m" else if (m == 0) "${h}h" else "${h}h ${m}m"
    }

    private fun summarizeCost(stops: List<TravelItineraryStop>): String {
        val sum = stops.mapNotNull { stop ->
            if (stop.detail.contains("gratis", ignoreCase = true)) return@mapNotNull null
            Regex("""~?(\d+(?:[.,]\d+)?)""").find(stop.detail)?.groupValues?.getOrNull(1)
                ?.replace(',', '.')?.toDoubleOrNull()
        }.sum()
        return if (sum > 0) "~${sum.roundToInt()}" else ""
    }

    private val foodNeedles = listOf(
        "ristor", "trattoria", "osteria", "pizzeria", "enoteca", "taverna", "locanda",
        "bistrot", "bacaro", "friggitoria", "gastronomia", "degustazione", "cucina",
        "pranzo", "cena", "colazione", "aperitivo", "brunch", "gelateria", "pasticceria",
    )

    private val genericMealTitles = setOf(
        "cena", "pranzo", "colazione", "aperitivo", "cena tipica", "cena tipica locale",
        "cena di arrivederci", "pranzo veloce", "colazione e passeggiata",
    )

    private fun categoryForTitle(title: String): TravelItineraryStopCategory {
        val lower = title.lowercase()
        return when {
            "aeroport" in lower || "volo" in lower -> TravelItineraryStopCategory.FLIGHT
            "taxi" in lower || "bus" in lower || "traghetto" in lower -> TravelItineraryStopCategory.TRANSPORT
            foodNeedles.any { lower.contains(it) } -> TravelItineraryStopCategory.FOOD
            "hotel" in lower || "suite" in lower -> TravelItineraryStopCategory.HOTEL
            "muse" in lower || "castell" in lower -> TravelItineraryStopCategory.CULTURE
            "spiagg" in lower || "marina" in lower -> TravelItineraryStopCategory.BEACH
            else -> TravelItineraryStopCategory.OTHER
        }
    }

    private fun emojiForTitle(title: String): String = categoryForTitle(title).emoji

    private fun budgetBreakdown(
        dict: JSONObject?,
        estimatedTotal: Double,
        dayPlans: List<KBTripDayPlanEntity>,
        legs: List<KBTripLegEntity>,
    ): TravelItineraryBudgetBreakdown {
        if (dict != null) {
            return TravelItineraryBudgetBreakdown(
                hotels = dict.optDouble("hotels"),
                flights = dict.optDouble("flights"),
                restaurants = dict.optDouble("restaurants"),
                activities = dict.optDouble("activities"),
            )
        }
        val hotelNights = dayPlans.mapNotNull { it.accommodationCostPerNight }.sum()
        val nights = max(dayPlans.size - 1, 1)
        val hotels = if (hotelNights > 0) hotelNights * nights else estimatedTotal * 0.35
        val hasFlight = legs.any { it.transportModeRaw == "flight" }
        val flights = if (hasFlight) estimatedTotal * 0.28 else estimatedTotal * 0.1
        val restaurants = estimatedTotal * 0.18
        val activities = max(estimatedTotal - hotels - flights - restaurants, estimatedTotal * 0.12)
        return TravelItineraryBudgetBreakdown(hotels, flights, restaurants, activities)
    }

    fun collectHotels(
        dayPlans: List<KBTripDayPlanEntity>,
        overview: TravelItineraryOverview,
    ): List<TravelPlaceResult> {
        val results = mutableListOf<TravelPlaceResult>()
        val seen = mutableSetOf<String>()

        dayPlans.forEach { plan ->
            val name = plan.accommodationName?.trim().orEmpty()
            if (name.isEmpty()) return@forEach
            val key = name.lowercase()
            if (!seen.add(key)) return@forEach
            val parts = buildList {
                plan.accommodationType?.takeIf { it.isNotBlank() }?.let { add(typeLabel(it)) }
                plan.accommodationCostPerNight?.takeIf { it > 0 }?.let { add("~${it.toInt()} €/notte") }
                if (plan.location.isNotBlank()) add(plan.location)
            }
            val locationContext = placeSearchLocationContext(plan.location, overview.destinationTitle)
            results += TravelPlaceResult(
                title = name,
                subtitle = parts.joinToString(" · "),
                meta = formattedDayLabel(plan.dateString),
                placeName = placeQueryName(name),
                locationContext = locationContext,
            )
        }

        appendStops(overview, ::isHotelStop, results, seen)
        return results
    }

    fun collectRestaurants(
        dayPlans: List<KBTripDayPlanEntity>,
        overview: TravelItineraryOverview,
        proposalJson: String? = null,
    ): List<TravelPlaceResult> {
        val results = mutableListOf<TravelPlaceResult>()
        val seen = mutableSetOf<String>()

        parseProposalJson(proposalJson)?.let { proposal ->
            appendDiningPlaces(proposal, results, seen, overview.destinationTitle)
            appendFoodStopsFromProposal(proposal, results, seen, overview.destinationTitle)
        }

        overview.days.forEach { day ->
            val locationContext = placeSearchLocationContext(day.location, overview.destinationTitle)
            day.blocks.forEach { block ->
                block.stops.filter(::isRestaurantStop).forEach { stop ->
                    appendRestaurant(
                        name = displayRestaurantName(stop.title, stop.detail, day.location),
                        subtitle = restaurantSubtitle(stop.detail, day.location, null),
                        meta = restaurantMeta(day.dateString, stop.time, null),
                        results = results,
                        seen = seen,
                        placeName = placeQueryName(stop.title, stop.detail),
                        locationContext = locationContext,
                    )
                }
            }
        }

        dayPlans.forEach { plan ->
            val location = plan.location
            val locationContext = placeSearchLocationContext(location, overview.destinationTitle)
            listOf(plan.morningPlan, plan.afternoonPlan, plan.eveningPlan).forEach { text ->
                val trimmed = text.trim()
                if (trimmed.isEmpty()) return@forEach
                parseTextStops(trimmed).filter(::isRestaurantStop).forEach { stop ->
                    appendRestaurant(
                        name = displayRestaurantName(stop.title, stop.detail, location),
                        subtitle = restaurantSubtitle(stop.detail, location, null),
                        meta = restaurantMeta(plan.dateString, stop.time, null),
                        results = results,
                        seen = seen,
                        placeName = placeQueryName(stop.title, stop.detail),
                        locationContext = locationContext,
                    )
                }
                extractVenueNames(trimmed).forEach { name ->
                    appendRestaurant(
                        name = name,
                        subtitle = location,
                        meta = formattedDayLabel(plan.dateString),
                        results = results,
                        seen = seen,
                        locationContext = locationContext,
                    )
                }
            }
        }

        return results.sortedBy { it.meta }
    }

    fun collectActivities(
        dayPlans: List<KBTripDayPlanEntity>,
        overview: TravelItineraryOverview,
        proposalJson: String? = null,
    ): List<TravelPlaceResult> {
        val results = mutableListOf<TravelPlaceResult>()
        val seen = mutableSetOf<String>()

        parseProposalJson(proposalJson)?.let { proposal ->
            appendActivityStopsFromProposal(proposal, results, seen, overview.destinationTitle)
        }

        appendStops(overview, ::isActivityStop, results, seen)

        dayPlans.forEach { plan ->
            val location = plan.location
            val locationContext = placeSearchLocationContext(location, overview.destinationTitle)
            listOf(plan.morningPlan, plan.afternoonPlan, plan.eveningPlan).forEach { text ->
                val trimmed = text.trim()
                if (trimmed.isEmpty()) return@forEach
                parseTextStops(trimmed).filter(::isActivityStop).forEach { stop ->
                    appendActivity(
                        name = stop.title,
                        subtitle = activitySubtitle(stop.detail, location),
                        meta = activityMeta(plan.dateString, stop.time),
                        results = results,
                        seen = seen,
                        placeName = placeQueryName(stop.title, stop.detail),
                        locationContext = locationContext,
                    )
                }
            }
        }

        return results.sortedBy { it.meta }
    }

    private fun appendDiningPlaces(
        proposal: JSONObject,
        results: MutableList<TravelPlaceResult>,
        seen: MutableSet<String>,
        destinationTitle: String,
    ) {
        val places = proposal.optJSONArray("diningPlaces") ?: return
        for (i in 0 until places.length()) {
            val place = places.optJSONObject(i) ?: continue
            val name = place.optString("name").trim()
            if (name.isEmpty()) continue
            val location = place.optString("location").trim()
            val cuisine = place.optString("cuisine").trim()
            val meal = place.optString("meal").trim()
            val notes = place.optString("notes").trim()
            val day = place.optString("day")
            val detailParts = buildList {
                if (cuisine.isNotBlank()) add(cuisine)
                if (notes.isNotBlank()) add(notes)
                val cost = place.optDouble("estimatedCost")
                if (cost > 0) add("~${cost.toInt()} €")
            }
            val locationContext = placeSearchLocationContext(location, destinationTitle)
            appendRestaurant(
                name = name,
                subtitle = restaurantSubtitle(detailParts.joinToString(" · "), location, null),
                meta = restaurantMeta(day, "", meal),
                results = results,
                seen = seen,
                placeName = placeQueryName(name),
                locationContext = locationContext,
            )
        }
    }

    private fun appendActivityStopsFromProposal(
        proposal: JSONObject,
        results: MutableList<TravelPlaceResult>,
        seen: MutableSet<String>,
        destinationTitle: String,
    ) {
        val dayPlans = proposal.optJSONArray("dayPlans") ?: return
        for (d in 0 until dayPlans.length()) {
            val day = dayPlans.optJSONObject(d) ?: continue
            val location = day.optString("location")
            val dateString = day.optString("date")
            listOf("morningStops", "afternoonStops", "eveningStops").forEach { key ->
                val stops = day.optJSONArray(key) ?: return@forEach
                for (s in 0 until stops.length()) {
                    val stopDict = stops.optJSONObject(s) ?: continue
                    val stop = parseStructuredStop(stopDict) ?: continue
                    if (!isActivityStop(stop)) continue
                    val locationContext = placeSearchLocationContext(location, destinationTitle)
                    appendActivity(
                        name = stop.title,
                        subtitle = activitySubtitle(stop.detail, location),
                        meta = activityMeta(dateString, stop.time),
                        results = results,
                        seen = seen,
                        placeName = placeQueryName(stop.title, stop.detail),
                        locationContext = locationContext,
                    )
                }
            }
        }
    }

    private fun appendFoodStopsFromProposal(
        proposal: JSONObject,
        results: MutableList<TravelPlaceResult>,
        seen: MutableSet<String>,
        destinationTitle: String,
    ) {
        val dayPlans = proposal.optJSONArray("dayPlans") ?: return
        for (d in 0 until dayPlans.length()) {
            val day = dayPlans.optJSONObject(d) ?: continue
            val location = day.optString("location")
            val dateString = day.optString("date")
            listOf("morningStops" to "Colazione", "afternoonStops" to "Pranzo", "eveningStops" to "Cena").forEach { (key, meal) ->
                val stops = day.optJSONArray(key) ?: return@forEach
                for (s in 0 until stops.length()) {
                    val stopDict = stops.optJSONObject(s) ?: continue
                    val category = TravelItineraryStopCategory.from(stopDict.optString("category"))
                    if (category != TravelItineraryStopCategory.FOOD) continue
                    val title = stopDict.optString("title").trim()
                    if (title.isEmpty()) continue
                    val time = stopDict.optString("time")
                    val detail = formatDetail(
                        stopDict.optInt("durationMinutes").takeIf { it > 0 },
                        stopDict.optDouble("cost").takeIf { !it.isNaN() },
                        stopDict.optString("costLabel").takeIf { it.isNotBlank() },
                    )
                    val locationContext = placeSearchLocationContext(location, destinationTitle)
                    appendRestaurant(
                        name = displayRestaurantName(title, detail, location),
                        subtitle = restaurantSubtitle(detail, location, null),
                        meta = restaurantMeta(dateString, time, meal),
                        results = results,
                        seen = seen,
                        placeName = placeQueryName(title, detail),
                        locationContext = locationContext,
                    )
                }
            }
        }
    }

    private fun appendActivity(
        name: String,
        subtitle: String,
        meta: String,
        results: MutableList<TravelPlaceResult>,
        seen: MutableSet<String>,
        placeName: String? = null,
        locationContext: String = "",
    ) {
        val cleaned = name.trim()
        if (cleaned.isEmpty() || isGenericMealTitle(cleaned) || isGenericActivityTitle(cleaned)) return
        if (!seen.add(cleaned.lowercase())) return
        results += TravelPlaceResult(
            title = cleaned,
            subtitle = subtitle,
            meta = meta,
            placeName = placeName ?: placeQueryName(cleaned, subtitle),
            locationContext = locationContext,
        )
    }

    private fun activitySubtitle(detail: String, location: String): String {
        return buildList {
            detail.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            location.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")
    }

    private fun activityMeta(dateString: String, time: String): String {
        return buildList {
            formattedDayLabel(dateString).takeIf { it.isNotBlank() && it != dateString }?.let { add(it) }
            time.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")
    }

    private fun appendRestaurant(
        name: String,
        subtitle: String,
        meta: String,
        results: MutableList<TravelPlaceResult>,
        seen: MutableSet<String>,
        placeName: String? = null,
        locationContext: String = "",
    ) {
        val cleaned = name.trim()
        if (cleaned.isEmpty() || isGenericMealTitle(cleaned)) return
        if (!seen.add(cleaned.lowercase())) return
        val queryName = placeName ?: placeQueryName(cleaned, subtitle)
        results += TravelPlaceResult(
            title = cleaned,
            subtitle = subtitle,
            meta = meta,
            placeName = queryName,
            locationContext = locationContext,
        )
    }

    private fun displayRestaurantName(title: String, detail: String, location: String): String {
        val trimmed = title.trim()
        if (!isGenericMealTitle(trimmed)) return cleanVenueTitle(trimmed)
        extractVenueNames("$title $detail").firstOrNull()?.let { return it }
        if (location.isNotBlank()) return "Locale consigliato · $location"
        return trimmed
    }

    private fun cleanVenueTitle(title: String): String {
        val dash = title.indexOf(" — ").takeIf { it > 0 } ?: title.indexOf(" - ").takeIf { it > 0 }
        if (dash != null && dash > 0) {
            val left = title.substring(0, dash).trim()
            if (!isGenericMealTitle(left)) return left
        }
        return title.trim()
    }

    private fun restaurantSubtitle(detail: String, location: String, cuisine: String?): String {
        return buildList {
            cuisine?.takeIf { it.isNotBlank() }?.let { add(it) }
            detail.trim().takeIf { it.isNotEmpty() }?.let { add(it) }
            location.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")
    }

    private fun restaurantMeta(dateString: String, time: String, meal: String?): String {
        return buildList {
            formattedDayLabel(dateString).takeIf { it.isNotBlank() && it != dateString }?.let { add(it) }
            meal?.takeIf { it.isNotBlank() }?.let { add(it.replaceFirstChar { c -> c.uppercase() }) }
            time.takeIf { it.isNotBlank() }?.let { add(it) }
        }.joinToString(" · ")
    }

    private fun isGenericMealTitle(title: String): Boolean {
        val normalized = title.lowercase().trim()
        if (normalized in genericMealTitles) return true
        return normalized.length < 12 && listOf("cena", "pranzo", "colazione", "aperitivo").any { normalized.contains(it) }
    }

    private fun extractVenueNames(text: String): List<String> {
        val names = mutableListOf<String>()
        val patterns = listOf(
            Regex("""(?i)(ristorante|trattoria|osteria|pizzeria|enoteca|taverna|locanda|bistrot|bacaro)\s+([^·(,.\n]+)"""),
            Regex("""(?i)\b(da|al|alla|dal|dalla)\s+([A-ZÀ-Ÿ][\w\s']+)"""),
            Regex("""«([^»]+)»"""),
            Regex("""\"([^\"]+)\""""),
        )
        patterns.forEach { regex ->
            regex.findAll(text).forEach { match ->
                val index = if (match.groupValues.size >= 3) 2 else 1
                val name = match.groupValues.getOrNull(index)?.trim().orEmpty()
                if (name.length >= 3 && !isGenericMealTitle(name)) {
                    names += cleanVenueTitle(name)
                }
            }
        }
        return names
    }

    private fun appendStops(
        overview: TravelItineraryOverview,
        predicate: (TravelItineraryStop) -> Boolean,
        results: MutableList<TravelPlaceResult>,
        seen: MutableSet<String>,
    ) {
        overview.days.forEach { day ->
            day.blocks.forEach { block ->
                block.stops.filter(predicate).forEach { stop ->
                    val key = stop.title.lowercase()
                    if (!seen.add(key)) return@forEach
                    val meta = buildList {
                        formattedDayLabel(day.dateString).takeIf { it.isNotBlank() }?.let { add(it) }
                        if (stop.time.isNotBlank()) add(stop.time)
                    }.joinToString(" · ")
                    val locationContext = placeSearchLocationContext(day.location, overview.destinationTitle)
                    results += TravelPlaceResult(
                        title = stop.title,
                        subtitle = stop.detail,
                        meta = meta,
                        placeName = placeQueryName(stop.title, stop.detail),
                        locationContext = locationContext,
                    )
                }
            }
        }
    }

    fun placeQueryName(title: String, subtitle: String = ""): String {
        val cleaned = cleanVenueTitle(title)
        if (!isGenericMealTitle(cleaned) && cleaned.length <= 72) return cleaned
        extractVenueNames("$title $subtitle").firstOrNull()?.let { return it }
        return cleaned
    }

    fun placeSearchLocationContext(dayLocation: String, destinationTitle: String): String {
        val day = dayLocation.trim()
        val dest = destinationTitle.trim()
        if (day.isEmpty()) return dest
        if (dest.isEmpty()) return day
        if (day.equals(dest, ignoreCase = true)) return day
        if (day.contains(dest, ignoreCase = true)) return day
        return "$day, $dest"
    }

    private fun isHotelStop(stop: TravelItineraryStop): Boolean {
        val lower = stop.title.lowercase()
        return stop.emoji == "🏨" || listOf("hotel", "bb", "suite", "alloggio", "resort").any { lower.contains(it) }
    }

    private fun isRestaurantStop(stop: TravelItineraryStop): Boolean {
        if (stop.category == TravelItineraryStopCategory.FOOD) return true
        val blob = "${stop.title} ${stop.detail}".lowercase()
        return stop.emoji == "🍝" || foodNeedles.any { blob.contains(it) }
    }

    private fun isActivityStop(stop: TravelItineraryStop): Boolean {
        if (isHotelStop(stop) || isRestaurantStop(stop)) return false
        when (stop.category) {
            TravelItineraryStopCategory.FLIGHT,
            TravelItineraryStopCategory.TRANSPORT,
            TravelItineraryStopCategory.HOTEL,
            TravelItineraryStopCategory.FOOD,
            -> return false
            TravelItineraryStopCategory.CULTURE,
            TravelItineraryStopCategory.BEACH,
            TravelItineraryStopCategory.SHOPPING,
            -> return !isGenericActivityTitle(stop.title)
            TravelItineraryStopCategory.OTHER -> {
                val blob = "${stop.title} ${stop.detail}".lowercase()
                if (activityExclusionNeedles.any { blob.contains(it) }) return false
                return stop.title.trim().length >= 3 && !isGenericMealTitle(stop.title)
            }
        }
    }

    private fun isGenericActivityTitle(title: String): Boolean {
        val normalized = title.lowercase().trim()
        if (normalized.isEmpty()) return true
        return activityExclusionNeedles.any { normalized.contains(it) }
    }

    private val activityExclusionNeedles = listOf(
        "tempo libero",
        "giornata libera",
        "riposo",
        "trasferimento",
        "check-in",
        "check-out",
        "volo",
        "aeroporto",
        "imbarco",
        "sbarco",
        "taxi",
        "bus",
        "treno",
        "navetta",
        "traghetto",
    )

    private fun typeLabel(raw: String): String = when (raw.lowercase()) {
        "hotel" -> "Hotel"
        "bb" -> "B&B"
        "camping" -> "Campeggio"
        "airbnb" -> "Appartamento"
        else -> raw.replaceFirstChar { it.uppercase() }
    }

    private fun formattedDayLabel(iso: String): String {
        return runCatching {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
            val out = java.text.SimpleDateFormat("d MMM yyyy", java.util.Locale("it", "IT"))
            fmt.parse(iso)?.let { out.format(it) }.orEmpty()
        }.getOrDefault(iso)
    }

    fun proposalToJson(plan: Map<String, Any>): String = anyToJsonObject(plan).toString()

    private fun anyToJsonObject(value: Any?): JSONObject {
        val obj = JSONObject()
        (value as? Map<*, *>)?.forEach { (k, v) ->
            obj.put(k.toString(), anyToJsonValue(v))
        }
        return obj
    }

    private fun anyToJsonValue(value: Any?): Any = when (value) {
        null -> JSONObject.NULL
        is Map<*, *> -> anyToJsonObject(value)
        is List<*> -> JSONArray().apply { value.forEach { put(anyToJsonValue(it)) } }
        is Number, is Boolean, is String -> value
        else -> value.toString()
    }
}
