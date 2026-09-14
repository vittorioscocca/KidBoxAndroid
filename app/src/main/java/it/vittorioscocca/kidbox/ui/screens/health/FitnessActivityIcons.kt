package it.vittorioscocca.kidbox.ui.screens.health

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.DownhillSkiing
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Hiking
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.Rowing
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.SportsBasketball
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material.icons.filled.SportsMartialArts
import androidx.compose.material.icons.filled.SportsMma
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.SportsTennis
import androidx.compose.material.icons.filled.SportsVolleyball
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.ui.graphics.vector.ImageVector
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSession
import it.vittorioscocca.kidbox.data.health.fitness.FitnessSessionStatus
import java.text.Normalizer

/**
 * Sceglie l'icona di un'attività dal tipo e dal titolo scritti dall'AI.
 *
 * Tre regole, perché le icone uscivano incoerenti per la stessa disciplina:
 * - le parole si confrontano **per inizio di parola**, non come sottostringa
 *   («percorso» non è una corsa, «veloce» non è un vélo);
 * - il **tipo** vince sul titolo, tranne quando è generico («cardio», «riposo
 *   attivo»): una seduta di forza con riscaldamento di corsa resta forza;
 * - le chiavi coprono **italiano, inglese, spagnolo e francese**, perché l'AI
 *   risponde nella lingua dell'app.
 * Parity con `FitnessActivityIcon` su iOS (stesse regole, nello stesso ordine).
 */
object FitnessActivityIcons {

    private class Rule(
        val icon: ImageVector,
        val prefixes: List<String>,
        val exact: List<String> = emptyList(),
        val phrases: List<String> = emptyList(),
        /** Regola generica: il tipo che la attiva cede il passo al titolo. */
        val generic: Boolean = false,
    )

    private val rules = listOf(
        Rule(Icons.Default.Pool, listOf("nuot", "swim", "piscin", "natac", "natat", "nage")),
        Rule(Icons.Default.DirectionsBike, listOf("bici", "ciclism", "cycling", "cyclist", "cyclette", "cyclisme", "spinning", "bike"), exact = listOf("velo")),
        Rule(Icons.Default.Rowing, listOf("vogat", "canott", "rowing", "rower", "remo", "rameur", "aviron")),
        Rule(Icons.Default.MusicNote, listOf("danz", "dance", "danse", "ballo", "balli", "zumba", "baile")),
        Rule(Icons.Default.SelfImprovement, listOf("yoga")),
        Rule(Icons.Default.SelfImprovement, listOf("pilates")),
        Rule(Icons.Default.Hiking, listOf("trekking", "escursion", "hike", "hiking", "randonn", "senderis")),
        Rule(Icons.Default.DirectionsRun, listOf("cors", "run", "jog", "footing", "fartlek", "ripetut", "carrer", "correr", "trote", "course", "sprint")),
        Rule(Icons.Default.DirectionsWalk, listOf("camm", "walk", "passeg", "marcia", "camina", "paseo", "marche")),
        Rule(Icons.Default.SportsGymnastics, listOf("hiit", "tabata", "circuit", "crossfit", "calisten", "intervall", "interval"), phrases = listOf("corpo libero", "peso corporeo", "bodyweight")),
        Rule(Icons.Default.SportsGymnastics, listOf("addomin", "abdomin", "abdos", "plank"), exact = listOf("core")),
        Rule(Icons.Default.FitnessCenter, listOf("funzional", "functional", "funcional", "fonctionnel")),
        Rule(Icons.Default.FitnessCenter, listOf("forz", "pesi", "strength", "tonific", "fuerza", "pesas", "muscul", "force", "renforc", "palestr", "weight", "tonif"), exact = listOf("gym")),
        Rule(Icons.Default.Accessibility, listOf("mobil", "stretch", "allungament", "flessib", "flexib", "estiram", "etirement", "souplesse")),
        Rule(Icons.Default.MonitorHeart, listOf("ellittic", "elliptic", "eliptic")),
        Rule(Icons.Default.SportsSoccer, listOf("calcio", "calcett", "soccer", "futbol", "football")),
        Rule(Icons.Default.SportsTennis, listOf("tennis", "tenis", "padel")),
        Rule(Icons.Default.SportsBasketball, listOf("basket", "balonces")),
        Rule(Icons.Default.SportsVolleyball, listOf("pallavol", "volley", "voley", "voleib")),
        Rule(Icons.Default.SportsMma, listOf("box", "pugil", "kickbox")),
        Rule(Icons.Default.SportsMartialArts, listOf("karate", "judo", "taekwondo", "marzial", "martial", "marcial", "martiaux")),
        Rule(Icons.Default.Terrain, listOf("arramp", "climb", "escalad", "boulder")),
        Rule(Icons.Default.DownhillSkiing, listOf("skiing", "esqui"), exact = listOf("sci", "ski")),
        Rule(Icons.Default.Bedtime, listOf("ripos", "recuper", "descans", "repos", "recover"), exact = listOf("rest"), generic = true),
        Rule(Icons.Default.MonitorHeart, listOf("cardio", "aerob"), generic = true),
    )

    private val fallback: ImageVector get() = Icons.Default.MonitorHeart

    fun forSession(activityType: String, title: String): ImageVector {
        val fromType = ruleFor(activityType)
        if (fromType != null && !fromType.generic) return fromType.icon
        ruleFor(title)?.let { return it.icon }
        return fromType?.icon ?: fallback
    }

    /**
     * Icona dell'attività svolta: se l'allenamento registrato era un altro
     * («Corsa» al posto della bici prevista), l'icona segue quello, come il
     * titolo mostrato accanto.
     */
    fun forPerformed(session: FitnessSession): ImageVector {
        val actual = session.actualActivityTitle?.takeIf { it.isNotBlank() }
        return if (session.status == FitnessSessionStatus.DONE && actual != null) {
            forWorkout(actual)
        } else {
            forSession(session.activityType, session.title)
        }
    }

    /** Icona per un allenamento letto da Health Connect, che ha solo un titolo. */
    fun forWorkout(title: String): ImageVector = ruleFor(title)?.icon ?: fallback

    private fun ruleFor(text: String): Rule? {
        val normalized = Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
        val words = normalized.split(Regex("[^\\p{L}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return null
        val joined = words.joinToString(" ")
        return rules.firstOrNull { rule ->
            rule.phrases.any { joined.contains(it) } ||
                words.any { word -> word in rule.exact || rule.prefixes.any { word.startsWith(it) } }
        }
    }
}
