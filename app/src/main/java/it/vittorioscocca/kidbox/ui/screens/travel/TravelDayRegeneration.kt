package it.vittorioscocca.kidbox.ui.screens.travel

import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import it.vittorioscocca.kidbox.data.remote.ai.TravelPlanResponse
import org.json.JSONObject
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

object TravelDayRegeneration {

    fun legsPayload(
        wizardLegs: List<TravelPlanningViewModel.LegDraft>,
        tripLegs: List<KBTripLegEntity>,
        fallbackLocation: String,
    ): List<Map<String, Any>> {
        if (wizardLegs.isNotEmpty()) {
            return wizardLegs.mapIndexed { index, leg ->
                mapOf(
                    "order" to (index + 1),
                    "fromLocation" to leg.fromLocation,
                    "toLocation" to leg.toLocation,
                    "transportMode" to leg.transportMode,
                    "days" to leg.days,
                )
            }
        }
        if (tripLegs.isNotEmpty()) {
            return tripLegs.sortedBy { it.order }.map { leg ->
                mapOf(
                    "order" to leg.order,
                    "fromLocation" to leg.fromLocation,
                    "toLocation" to leg.toLocation,
                    "transportMode" to leg.transportModeRaw,
                    "days" to 1,
                )
            }
        }
        val place = fallbackLocation.trim().ifBlank { "" }
        return listOf(
            mapOf(
                "order" to 1,
                "fromLocation" to place,
                "toLocation" to place,
                "transportMode" to "car",
                "days" to 1,
            ),
        )
    }

    fun wizardData(
        tripName: String,
        day: TravelItineraryDay,
        budgetPerDay: Double,
        currency: String,
        legs: List<Map<String, Any>>,
    ): Map<String, Any> = mapOf(
        "tripName" to tripName,
        "startDate" to day.dateString,
        "endDate" to day.dateString,
        "budgetTotal" to budgetPerDay,
        "currency" to currency,
        "legs" to legs,
    )

    fun regenerationPrompt(day: TravelItineraryDay, otherPlaces: String): String =
        "Rigenera SOLO il giorno ${day.dayIndex} (data: ${day.dateString}, location: ${day.location}). " +
            "NON riproporre questi luoghi già inclusi negli altri giorni: ${otherPlaces.ifBlank { "nessuno" }}. " +
            "La risposta JSON deve contenere ESATTAMENTE 1 elemento in dayPlans con la stessa data ${day.dateString}, " +
            "con morningStops, afternoonStops e eveningStops (minimo 2 tappe per fascia quando possibile) e nomi reali dei locali."

    fun collectOtherDaysPlaces(proposal: Map<String, Any>?, excludeDate: String): String =
        proposal?.get("dayPlans").asMapList()
            ?.filter { (it["date"] as? String) != excludeDate }
            ?.flatMap { day ->
                listOf("morningStops", "afternoonStops", "eveningStops").flatMap { period ->
                    @Suppress("UNCHECKED_CAST")
                    (day[period] as? List<Map<String, Any?>>).orEmpty()
                        .mapNotNull { stop -> stop["title"] as? String ?: stop["name"] as? String }
                }
            }
            ?.distinct()
            ?.take(25)
            ?.joinToString(", ")
            .orEmpty()

    fun collectOtherDaysPlaces(aiProposalJson: String?, excludeDate: String): String {
        if (aiProposalJson.isNullOrBlank()) return ""
        return runCatching {
            val root = JSONObject(aiProposalJson)
            val days = root.optJSONArray("dayPlans") ?: return ""
            val places = mutableListOf<String>()
            for (i in 0 until days.length()) {
                val d = days.optJSONObject(i) ?: continue
                if (d.optString("date") == excludeDate) continue
                for (period in listOf("morningStops", "afternoonStops", "eveningStops")) {
                    val stops = d.optJSONArray(period) ?: continue
                    for (j in 0 until stops.length()) {
                        val stop = stops.optJSONObject(j) ?: continue
                        val name = stop.optString("title").takeIf { it.isNotBlank() }
                            ?: stop.optString("name").takeIf { it.isNotBlank() }
                        if (name != null) places.add(name)
                    }
                }
            }
            places.distinct().take(25).joinToString(", ")
        }.getOrDefault("")
    }

    fun extractRegeneratedDay(response: TravelPlanResponse, dateString: String): Map<String, Any>? {
        response.travelPlan?.let { plan ->
            dayMatching(plan, dateString)?.let { return it }
        }
        TravelAIResponseParser.parseTravelPlan(response.narrativeText)?.let { plan ->
            dayMatching(plan, dateString)?.let { return it }
        }
        return null
    }

    fun mergeDay(proposal: Map<String, Any>, newDay: Map<String, Any>, dateString: String): Map<String, Any> {
        val merged = proposal.toMutableMap()
        val days = merged["dayPlans"].asMapList().toMutableList()
        val normalized = newDay.toMutableMap()
        if ((normalized["date"] as? String).isNullOrBlank()) {
            normalized["date"] = dateString
        }
        val idx = days.indexOfFirst { (it["date"] as? String) == dateString }
        if (idx >= 0) days[idx] = normalized else days.add(normalized)
        merged["dayPlans"] = days
        return merged
    }

    private fun dayMatching(plan: Map<String, Any>, dateString: String): Map<String, Any>? {
        val days = plan["dayPlans"].asMapList()
        if (days.isEmpty()) return null
        return days.firstOrNull { (it["date"] as? String) == dateString } ?: days.firstOrNull()
    }
}
