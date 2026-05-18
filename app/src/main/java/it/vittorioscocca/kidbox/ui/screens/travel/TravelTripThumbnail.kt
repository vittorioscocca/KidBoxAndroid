package it.vittorioscocca.kidbox.ui.screens.travel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BeachAccess
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsBoat
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.Flight
import androidx.compose.material.icons.filled.LocationCity
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Park
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material.icons.filled.Train
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import java.text.Normalizer
import java.util.Locale
import kotlin.math.abs

data class TravelTripThumbnailSpec(
    val icon: ImageVector,
    val colors: List<Color>,
)

object TravelTripThumbnailResolver {

    fun resolve(tripName: String, legs: List<KBTripLegEntity>): TravelTripThumbnailSpec {
        val sortedLegs = legs.sortedBy { it.order }
        val destination = primaryDestination(tripName, sortedLegs)
        val haystack = normalized(
            listOf(tripName, destination) + sortedLegs.flatMap { listOf(it.fromLocation, it.toLocation) },
        )

        theme(haystack)?.let { return it }
        sortedLegs.lastOrNull()?.let { leg ->
            return specForTransport(leg.transportModeRaw, destination.ifBlank { tripName })
        }
        return defaultSpec(destination.ifBlank { tripName })
    }

    fun primaryDestination(tripName: String, legs: List<KBTripLegEntity>): String {
        legs.lastOrNull()?.toLocation?.trim()?.takeIf { it.length >= 2 }?.let { return it }
        val name = tripName.trim()
        val separators = listOf(" – ", " - ", " — ", " a ", " in ", " per ", " verso ", ", ")
        separators.forEach { separator ->
            val index = name.lastIndexOf(separator, ignoreCase = true)
            if (index >= 0) {
                val tail = name.substring(index + separator.length).trim()
                if (tail.length >= 2) return tail
            }
        }
        return name
    }

    private fun normalized(parts: List<String>): String {
        val joined = parts.joinToString(" ")
        val folded = Normalizer.normalize(joined, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        return folded.lowercase(Locale.getDefault())
    }

    private fun theme(haystack: String): TravelTripThumbnailSpec? {
        val rules = listOf(
            Triple(
                listOf("mare", "spiaggia", "costa", "isola", "lido", "baia", "cala", "beach", "sea", "coast", "island"),
                Icons.Filled.BeachAccess,
                listOf(Color(0xFF1F8CD1), Color(0xFF33C6E6)),
            ),
            Triple(
                listOf("montagna", "alpi", "dolomiti", "trekking", "rifugio", "mountain", "hike", "trail"),
                Icons.Filled.Terrain,
                listOf(Color(0xFF387A57), Color(0xFF739E6B)),
            ),
            Triple(
                listOf("neve", "sci", "snow", "ski"),
                Icons.Filled.AcUnit,
                listOf(Color(0xFF739EDB), Color(0xFFC7E0FA)),
            ),
            Triple(
                listOf("parco", "natura", "foresta", "camping", "lake", "lago"),
                Icons.Filled.Park,
                listOf(Color(0xFF2E8547), Color(0xFF6BB861)),
            ),
            Triple(
                listOf("roma", "milano", "napoli", "paris", "london", "barcelona", "city", "citta", "centro"),
                Icons.Filled.LocationCity,
                listOf(Color(0xFF5C47B8), Color(0xFF856BD8)),
            ),
        )
        rules.forEach { (keywords, icon, colors) ->
            if (keywords.any { haystack.contains(it) }) {
                return TravelTripThumbnailSpec(icon, colors)
            }
        }
        return null
    }

    private fun specForTransport(modeRaw: String, seed: String): TravelTripThumbnailSpec {
        val icon = when (modeRaw.lowercase(Locale.US)) {
            "flight" -> Icons.Filled.Flight
            "train" -> Icons.Filled.Train
            "ship" -> Icons.Filled.DirectionsBoat
            "walk" -> Icons.Filled.DirectionsWalk
            "bike" -> Icons.Filled.DirectionsBike
            else -> Icons.Filled.DirectionsCar
        }
        return TravelTripThumbnailSpec(icon, gradientPalette(seed))
    }

    private fun defaultSpec(seed: String): TravelTripThumbnailSpec {
        return TravelTripThumbnailSpec(Icons.Filled.Map, gradientPalette(seed))
    }

    private fun gradientPalette(seed: String): List<Color> {
        val hash = stableHash(seed.ifBlank { "trip" })
        val hue = (hash % 360) / 360f
        val start = Color.hsv(hue * 360f, 0.55f, 0.72f)
        val end = Color.hsv(((hue + 0.12f) % 1f) * 360f, 0.48f, 0.88f)
        return listOf(start, end)
    }

    private fun stableHash(value: String): Int {
        var hash = 0
        value.forEach { char ->
            hash = (hash * 31 + char.code) and 0x7FFFFFFF
        }
        return abs(hash)
    }
}

@Composable
fun TravelTripThumbnail(
    tripName: String,
    legs: List<KBTripLegEntity>,
    modifier: Modifier = Modifier,
) {
    val spec = TravelTripThumbnailResolver.resolve(tripName, legs)
    Box(
        modifier = modifier
            .size(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                Brush.linearGradient(spec.colors),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = spec.icon,
            contentDescription = null,
            tint = Color.White,
        )
    }
}
