package it.vittorioscocca.kidbox.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONArray
import org.json.JSONObject

enum class TravelStyle(val raw: String) {
    CULTURE("culture"),
    FOOD("food"),
    NIGHTLIFE("nightlife"),
    ADVENTURE("adventure"),
    RELAXATION("relaxation"),
    SHOPPING("shopping"),
    ;

    val emoji: String
        get() = when (this) {
            CULTURE -> "🏛️"
            FOOD -> "🍝"
            NIGHTLIFE -> "🍸"
            ADVENTURE -> "🏔️"
            RELAXATION -> "🏖️"
            SHOPPING -> "🛍️"
        }

    val title: String
        get() = when (this) {
            CULTURE -> "Cultura e storia"
            FOOD -> "Cibo e gastronomia"
            NIGHTLIFE -> "Vita notturna"
            ADVENTURE -> "Avventura e outdoor"
            RELAXATION -> "Relax e spiaggia"
            SHOPPING -> "Shopping"
        }

    val subtitle: String
        get() = when (this) {
            CULTURE -> "Musei, monumenti, storie"
            FOOD -> "Ristoranti, mercati, cucina locale"
            NIGHTLIFE -> "Bar, locali, serate"
            ADVENTURE -> "Trekking, sport, natura"
            RELAXATION -> "Spa, resort, giornate lente"
            SHOPPING -> "Boutique, mercati, design"
        }
}

enum class TravelPace(val raw: String) {
    CHILL("chill"),
    BALANCED("balanced"),
    PACKED("packed"),
    ;

    val title: String
        get() = when (this) {
            CHILL -> "Rilassato"
            BALANCED -> "Equilibrato"
            PACKED -> "Intenso"
        }

    val line1: String
        get() = when (this) {
            CHILL -> "1–2 attività al giorno"
            BALANCED -> "3–4 attività al giorno"
            PACKED -> "5–6 attività al giorno"
        }

    val line2: String
        get() = when (this) {
            CHILL -> "Mattine lente, pasti lunghi"
            BALANCED -> "Mix di visite e riposo"
            PACKED -> "Vedi tutto, senza perdere tempo"
        }
}

enum class TravelAgeGroup(val raw: String) {
    YOUNG("18-25"),
    MODERN("26-35"),
    SEASONED("36-50"),
    COMFORT("50+"),
    ;

    val emoji: String
        get() = when (this) {
            YOUNG -> "🎒"
            MODERN -> "✈️"
            SEASONED -> "🧭"
            COMFORT -> "☕"
        }

    val subtitle: String
        get() = when (this) {
            YOUNG -> "Giovane esploratore"
            MODERN -> "Viaggiatore moderno"
            SEASONED -> "Esperto"
            COMFORT -> "In cerca di comfort"
        }
}

data class TravelProfile(
    val styles: List<TravelStyle>,
    val pace: TravelPace,
    val ageGroup: TravelAgeGroup,
) {
    fun familyContextValue(): Map<String, Any> = mapOf(
        "styles" to styles.map { it.raw },
        "pace" to pace.raw,
        "ageGroup" to ageGroup.raw,
    )
}

@Singleton
class TravelProfilePreferences @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun hasCompletedOnboarding(userId: String): Boolean {
        if (userId.isBlank()) return true
        return prefs.getBoolean(completedKey(userId), false)
    }

    fun loadProfile(userId: String): TravelProfile? {
        if (userId.isBlank()) return null
        val json = prefs.getString(profileKey(userId), null) ?: return null
        return runCatching { decodeProfile(json) }.getOrNull()
    }

    fun saveProfile(userId: String, profile: TravelProfile) {
        if (userId.isBlank()) return
        prefs.edit()
            .putString(profileKey(userId), encodeProfile(profile))
            .putBoolean(completedKey(userId), true)
            .apply()
    }

    private fun encodeProfile(profile: TravelProfile): String {
        return JSONObject().apply {
            put("styles", JSONArray(profile.styles.map { it.raw }))
            put("pace", profile.pace.raw)
            put("ageGroup", profile.ageGroup.raw)
        }.toString()
    }

    private fun decodeProfile(json: String): TravelProfile {
        val obj = JSONObject(json)
        val stylesArray = obj.getJSONArray("styles")
        val styles = buildList {
            for (i in 0 until stylesArray.length()) {
                val raw = stylesArray.getString(i)
                TravelStyle.entries.firstOrNull { it.raw == raw }?.let { add(it) }
            }
        }
        val pace = TravelPace.entries.first { it.raw == obj.getString("pace") }
        val age = TravelAgeGroup.entries.first { it.raw == obj.getString("ageGroup") }
        return TravelProfile(styles, pace, age)
    }

    private fun completedKey(userId: String) = "travel_onboarding_completed_$userId"
    private fun profileKey(userId: String) = "travel_profile_$userId"

    private companion object {
        private const val PREFS_NAME = "kidbox_travel_profile"
    }
}
