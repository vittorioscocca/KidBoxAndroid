package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AirplanemodeActive
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Train
import androidx.compose.ui.graphics.vector.ImageVector
import java.util.Locale

enum class TransportMode(val raw: String, val label: String, val icon: ImageVector) {
    FLIGHT("flight", "Aereo", Icons.Filled.AirplanemodeActive),
    TRAIN("train", "Treno", Icons.Filled.Train),
    SHIP("ship", "Nave", Icons.Filled.DirectionsBoat),
    CAR("car", "Auto", Icons.Filled.DirectionsCar),
    WALK("walk", "A piedi", Icons.Filled.DirectionsWalk),
    BIKE("bike", "Bici", Icons.Filled.DirectionsBike),
    ;

    companion object {
        fun fromRaw(raw: String): TransportMode =
            entries.firstOrNull { it.raw == raw } ?: CAR
    }
}

enum class WizardPrimaryTransport(val raw: String, val title: String, val subtitle: String, val emoji: String) {
    FLIGHT("flight", "Volo", "Prezzi voli in tempo reale", "✈️"),
    CAR("car", "In auto", "Viaggio su strada flessibile", "🚗"),
    OTHER("other", "Altro", "Treno, autobus, traghetto…", "🚆"),
    ;

    fun transportModeRaw(): String = when (this) {
        FLIGHT -> TransportMode.FLIGHT.raw
        CAR -> TransportMode.CAR.raw
        OTHER -> TransportMode.TRAIN.raw
    }
}

enum class TravelWizardBudgetPreset(val usdAmount: Int) {
    TWO(2000),
    THREE(3000),
    FOUR(4000),
    SIX(6000),
    TEN(10000),
    ;

    fun amount(currency: String): Double =
        if (currency == "EUR") usdAmount * 0.92 else usdAmount.toDouble()

    fun label(currency: String): String {
        val amount = amount(currency).toInt()
        val formatted = "%,d".format(Locale.ITALY, amount).replace(',', '.')
        val symbol = if (currency == "EUR") "€" else "$"
        return "$formatted $symbol"
    }
}

data class TravelWizardParticipantLine(
    val id: String,
    val name: String,
    val ageLabel: String,
    val emoji: String,
)

/** Converte la mappa `travelPlan` restituita da Firebase (HashMap annidate) in struttura leggibile dal wizard. */
fun Any?.toTravelPlanMap(): Map<String, Any>? {
    val raw = this ?: return null
    when (raw) {
        is Map<*, *> -> {
            return raw.entries.associate { (key, value) ->
                key.toString() to coerceTravelPlanValue(value)
            }
        }
        is String -> {
            val trimmed = raw.trim()
            if (trimmed.isBlank()) return null
            return TravelAIResponseParser.parseTravelPlan(trimmed)
                ?: runCatching {
                    org.json.JSONObject(trimmed).let { obj ->
                        val hasTrip = obj.has("trip")
                        val hasDays = (obj.optJSONArray("dayPlans")?.length() ?: 0) > 0
                        if (!hasTrip && !hasDays) return@runCatching null
                        obj.keys().asSequence().associate { key ->
                            key to coerceTravelPlanValue(obj.get(key))
                        }
                    }
                }.getOrNull()
        }
        else -> return null
    }
}

private fun coerceTravelPlanValue(value: Any?): Any = when (value) {
    null -> ""
    is Map<*, *> -> value.entries.associate { (k, v) ->
        k.toString() to coerceTravelPlanValue(v)
    }
    is List<*> -> value.map { item ->
        when (item) {
            is Map<*, *> -> item.entries.associate { (k, v) ->
                k.toString() to coerceTravelPlanValue(v)
            }
            else -> item ?: ""
        }
    }
    is Number, is Boolean, is String -> value
    else -> value.toString()
}

fun Any?.asMapList(): List<Map<String, Any>> {
    val list = this as? List<*> ?: return emptyList()
    return list.mapNotNull { entry ->
        when (entry) {
            is Map<*, *> -> entry.entries.associate { (k, v) ->
                k.toString() to coerceTravelPlanValue(v)
            }
            else -> null
        }
    }
}
