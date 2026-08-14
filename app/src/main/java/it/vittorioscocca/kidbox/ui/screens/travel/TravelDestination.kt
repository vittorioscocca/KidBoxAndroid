package it.vittorioscocca.kidbox.ui.screens.travel

import it.vittorioscocca.kidbox.data.local.TravelPace
import it.vittorioscocca.kidbox.data.local.TravelProfile
import it.vittorioscocca.kidbox.data.local.TravelStyle
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

data class TravelDestination(
    val id: String,
    val name: String,
    val region: String,
    val tagline: String,
    val whyForYou: String,
    val aiHeadline: String,
    val estimatedCost: String,
    val durationDays: String,
    val bestTime: String,
    val bestTimeNote: String,
    val isTopMatch: Boolean,
    val previewPlanJson: String? = null,
) {
    fun previewPlanMap(): Map<String, Any>? =
        previewPlanJson?.let { json ->
            runCatching { jsonObjectToMap(org.json.JSONObject(json)) }.getOrNull()
        }

    val hasStructuredAiPreview: Boolean
        get() = previewPlanMap()?.isStructuredTravelPlan() == true

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(map: Map<String, Any?>): TravelDestination? {
            val id = map["id"] as? String ?: return null
            val name = map["name"] as? String ?: return null
            @Suppress("UNCHECKED_CAST")
            val previewJson = (map["previewPlan"] as? Map<String, Any?>)?.let { plan ->
                runCatching { org.json.JSONObject(plan as Map<*, *>).toString() }.getOrNull()
            }
            return TravelDestination(
                id = id,
                name = name,
                region = map["region"] as? String ?: "",
                tagline = map["tagline"] as? String ?: "",
                whyForYou = map["whyForYou"] as? String ?: "",
                aiHeadline = map["aiHeadline"] as? String ?: "",
                estimatedCost = map["estimatedCost"] as? String ?: "",
                durationDays = map["durationDays"] as? String ?: "",
                bestTime = map["bestTime"] as? String ?: "",
                bestTimeNote = map["bestTimeNote"] as? String ?: "",
                isTopMatch = map["isTopMatch"] as? Boolean ?: false,
                previewPlanJson = previewJson,
            )
        }

        private fun jsonObjectToMap(obj: org.json.JSONObject): Map<String, Any> {
            val result = mutableMapOf<String, Any>()
            obj.keys().forEach { key ->
                when (val value = obj.get(key)) {
                    is org.json.JSONObject -> result[key] = jsonObjectToMap(value)
                    is org.json.JSONArray -> result[key] = jsonArrayToList(value)
                    org.json.JSONObject.NULL -> result[key] = ""
                    else -> result[key] = value
                }
            }
            return result
        }

        private fun jsonArrayToList(arr: org.json.JSONArray): List<Any> {
            val list = mutableListOf<Any>()
            for (i in 0 until arr.length()) {
                when (val value = arr.get(i)) {
                    is org.json.JSONObject -> list.add(jsonObjectToMap(value))
                    is org.json.JSONArray -> list.add(jsonArrayToList(value))
                    org.json.JSONObject.NULL -> list.add("")
                    else -> list.add(value)
                }
            }
            return list
        }
    }
}

data class TravelSuggestionsResult(
    val destinations: List<TravelDestination>,
    val profileSummary: String,
    val usageToday: Int,
    val dailyLimit: Int,
    val period: it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod = it.vittorioscocca.kidbox.domain.model.ai.AIQuotaPeriod.DAILY,
)

fun TravelProfile.discoverSubtitle(context: android.content.Context): String {
    val locale = it.vittorioscocca.kidbox.util.KBLocale.current()
    val stylesPart = styles.take(2).joinToString(", ") { context.getString(it.titleRes) }
    return context.getString(
        it.vittorioscocca.kidbox.R.string.travel_discover_subtitle,
        if (stylesPart.isBlank()) "" else " ($stylesPart)",
        context.getString(pace.titleRes).lowercase(locale),
        ageGroup.raw,
    )
}

object TravelDiscoverTips {
    data class Tip(val title: String, val body: String)

    private val items = listOf(
        Tip("Lo sapevi?", "Il periodo migliore per Roma è aprile–maggio o settembre–ottobre. Meno folla, clima mite."),
        Tip("Lo sapevi?", "In primavera le città del Mediterraneo offrono sole e serate vivaci senza il caldo estivo."),
        Tip("Lo sapevi?", "Con bambini, 1–2 attività al giorno rendono il viaggio più piacevole per tutti."),
    )

    fun random(): Tip = items.random()
}
