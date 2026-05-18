package it.vittorioscocca.kidbox.ui.screens.travel

import org.json.JSONArray
import org.json.JSONObject

object TravelAIResponseParser {

    private val fencedJsonRegex =
        Regex("""```(?:json)?\s*\n?([\s\S]*?)\n?```""", RegexOption.IGNORE_CASE)
    private val unclosedFenceRegex =
        Regex("""```(?:json)?\s*([\s\S]+)""", RegexOption.IGNORE_CASE)
    private val tripJsonStartRegex = Regex("""\{\s*"trip"\s*:""")
    private val dayPlansJsonStartRegex = Regex("""\{\s*"dayPlans"\s*:""")

    /** Introduzione senza blocco JSON o marcatori markdown. */
    fun sanitizedNarrative(raw: String): String {
        var text = raw.trim()
        text = text.replace(fencedJsonRegex, "").trim()
        text = text.replace(unclosedFenceRegex, "").trim()
        tripJsonStartRegex.find(text)?.let { match ->
            text = text.substring(0, match.range.first).trim()
        }
        text = text.replace(Regex("```(?:json)?\\s*", RegexOption.IGNORE_CASE), "").trim()
        while (text.contains("\n\n\n")) {
            text = text.replace("\n\n\n", "\n\n")
        }
        return text.trim()
    }

    /** Estrae il piano strutturato dal testo grezzo (come `parseTravelPlanResponse` su Cloud Functions). */
    fun parseTravelPlan(raw: String): Map<String, Any>? {
        val text = raw.trim()
        if (text.isBlank()) return null

        fencedJsonRegex.find(text)?.let { match ->
            parsePlanJson(match.groupValues[1])?.let { return it }
        }

        unclosedFenceRegex.find(text)?.let { match ->
            parsePlanJson(match.groupValues[1])?.let { return it }
        }

        tripJsonStartRegex.find(text)?.let { match ->
            val candidate = extractJsonObject(text.substring(match.range.first)) ?: return@let
            parsePlanJson(candidate)?.let { return it }
        }

        dayPlansJsonStartRegex.find(text)?.let { match ->
            val candidate = extractJsonObject(text.substring(match.range.first)) ?: return@let
            parsePlanJson(candidate)?.let { return it }
        }

        return null
    }

    private fun parsePlanJson(json: String): Map<String, Any>? {
        val trimmed = json.trim()
        if (trimmed.isBlank()) return null

        fun accept(obj: JSONObject): Map<String, Any>? {
            if (obj.has("trip")) return jsonObjectToMap(obj)
            if (obj.optJSONArray("dayPlans")?.length().orZero() > 0) return jsonObjectToMap(obj)
            return null
        }

        runCatching { JSONObject(trimmed) }.getOrNull()?.let { obj ->
            accept(obj)?.let { return it }
        }

        extractJsonObject(trimmed)?.let { repaired ->
            runCatching { JSONObject(repaired) }.getOrNull()?.let { obj ->
                accept(obj)?.let { return it }
            }
        }

        return null
    }

    private fun Int?.orZero(): Int = this ?: 0

    private fun extractJsonObject(source: String): String? {
        var depth = 0
        var started = false
        val builder = StringBuilder()
        for (ch in source) {
            when (ch) {
                '{' -> {
                    if (!started) started = true
                    depth++
                }
                '}' -> if (started) depth--
            }
            if (started) builder.append(ch)
            if (started && depth == 0) return builder.toString()
        }
        if (started && depth > 0) {
            repeat(depth) { builder.append('}') }
            return builder.toString()
        }
        return null
    }

    private fun jsonObjectToMap(obj: JSONObject): Map<String, Any> =
        obj.keys().asSequence().associateWith { key -> jsonValueToKotlin(obj.get(key)) }

    private fun jsonValueToKotlin(value: Any?): Any = when (value) {
        null, JSONObject.NULL -> ""
        is JSONObject -> jsonObjectToMap(value)
        is JSONArray -> (0 until value.length()).map { index ->
            jsonValueToKotlin(value.get(index))
        }
        is Number, is Boolean, is String -> value
        else -> value.toString()
    }
}

fun Map<String, Any>?.isStructuredTravelPlan(): Boolean {
    if (this == null || isEmpty()) return false
    val trip = this["trip"]
    if (trip is Map<*, *> && trip.isNotEmpty()) return true
    return this["dayPlans"].asMapList().isNotEmpty()
}
