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

    @get:androidx.annotation.StringRes
    val titleRes: Int
        get() = when (this) {
            CULTURE -> it.vittorioscocca.kidbox.R.string.travel_style_culture
            FOOD -> it.vittorioscocca.kidbox.R.string.travel_style_food
            NIGHTLIFE -> it.vittorioscocca.kidbox.R.string.travel_style_nightlife
            ADVENTURE -> it.vittorioscocca.kidbox.R.string.travel_style_adventure
            RELAXATION -> it.vittorioscocca.kidbox.R.string.travel_style_relax
            SHOPPING -> it.vittorioscocca.kidbox.R.string.travel_style_shopping
        }

    /**
     * Etichetta italiana usata SOLO per comporre i prompt inviati al modello:
     * i prompt non vanno localizzati, altrimenti cambia il comportamento dell'AI.
     * Per la UI usare [titleRes].
     */
    val promptLabel: String
        get() = when (this) {
            CULTURE -> "Cultura e storia"
            FOOD -> "Cibo e gastronomia"
            NIGHTLIFE -> "Vita notturna"
            ADVENTURE -> "Avventura e outdoor"
            RELAXATION -> "Relax e spiaggia"
            SHOPPING -> "Shopping"
        }

    @get:androidx.annotation.StringRes
    val subtitleRes: Int
        get() = when (this) {
            CULTURE -> it.vittorioscocca.kidbox.R.string.travel_style_culture_sub
            FOOD -> it.vittorioscocca.kidbox.R.string.travel_style_food_sub
            NIGHTLIFE -> it.vittorioscocca.kidbox.R.string.travel_style_nightlife_sub
            ADVENTURE -> it.vittorioscocca.kidbox.R.string.travel_style_adventure_sub
            RELAXATION -> it.vittorioscocca.kidbox.R.string.travel_style_relax_sub
            SHOPPING -> it.vittorioscocca.kidbox.R.string.travel_style_shopping_sub
        }
}

enum class TravelPace(val raw: String) {
    CHILL("chill"),
    BALANCED("balanced"),
    PACKED("packed"),
    ;

    @get:androidx.annotation.StringRes
    val titleRes: Int
        get() = when (this) {
            CHILL -> it.vittorioscocca.kidbox.R.string.travel_pace_chill
            BALANCED -> it.vittorioscocca.kidbox.R.string.travel_pace_balanced
            PACKED -> it.vittorioscocca.kidbox.R.string.travel_pace_packed
        }

    /** Etichetta italiana per i prompt AI: vedi [TravelStyle.promptLabel]. */
    val promptLabel: String
        get() = when (this) {
            CHILL -> "Rilassato"
            BALANCED -> "Equilibrato"
            PACKED -> "Intenso"
        }

    @get:androidx.annotation.StringRes
    val line1Res: Int
        get() = when (this) {
            CHILL -> it.vittorioscocca.kidbox.R.string.travel_pace_chill_1
            BALANCED -> it.vittorioscocca.kidbox.R.string.travel_pace_balanced_1
            PACKED -> it.vittorioscocca.kidbox.R.string.travel_pace_packed_1
        }

    @get:androidx.annotation.StringRes
    val line2Res: Int
        get() = when (this) {
            CHILL -> it.vittorioscocca.kidbox.R.string.travel_pace_chill_2
            BALANCED -> it.vittorioscocca.kidbox.R.string.travel_pace_balanced_2
            PACKED -> it.vittorioscocca.kidbox.R.string.travel_pace_packed_2
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

    @get:androidx.annotation.StringRes
    val subtitleRes: Int
        get() = when (this) {
            YOUNG -> it.vittorioscocca.kidbox.R.string.travel_age_young
            MODERN -> it.vittorioscocca.kidbox.R.string.travel_age_modern
            SEASONED -> it.vittorioscocca.kidbox.R.string.travel_age_seasoned
            COMFORT -> it.vittorioscocca.kidbox.R.string.travel_age_comfort
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
