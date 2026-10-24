package it.vittorioscocca.kidbox.domain.save

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

/**
 * «Salva in…» da un messaggio di testo: dove ha senso metterlo (to-do, evento,
 * lista della spesa, nota). Gemello dell'euristica di `KBSaveClassifier.swift`:
 * stesse parole chiave, stessi punteggi, stesse penalità per i testi lunghi.
 * Gira ovunque, senza modello e senza rete: è il ripiego quando il telefono
 * non ha Gemini Nano, e la prima risposta mostrata mentre Nano ragiona.
 */
enum class SaveDestination { TODO, EVENT, GROCERY, NOTE }

sealed interface SaveAction {
    val destination: SaveDestination

    data class Todo(val title: String) : SaveAction {
        override val destination get() = SaveDestination.TODO
    }

    /** `dateMillis` è l'inizio rilevato nel testo, se c'è. */
    data class Event(val title: String, val dateMillis: Long?) : SaveAction {
        override val destination get() = SaveDestination.EVENT
    }

    data class Grocery(val lines: List<String>) : SaveAction {
        override val destination get() = SaveDestination.GROCERY
    }

    data class Note(val title: String, val body: String) : SaveAction {
        override val destination get() = SaveDestination.NOTE
    }
}

data class SaveClassification(
    val actions: List<SaveAction>,
    val detectedDateMillis: Long?,
    /** true se a decidere è stato un modello sul telefono, non le regole. */
    val isModelClassified: Boolean,
)

object SaveClassifier {

    private val groceryKeywords = listOf(
        "spesa", "supermercato", "super", "mercato", "negozio",
        "comprare", "compra", "acquistare", "prendere", "prendi",
        "latte", "pane", "uova", "uovo", "pasta", "riso", "farina",
        "burro", "olio", "sale", "zucchero", "caffè", "caffe",
        "carne", "pollo", "pesce", "prosciutto", "formaggio", "mozzarella",
        "verdura", "frutta", "patate", "pomodori", "insalata", "carote",
        "cipolle", "aglio", "zucchine", "melanzane", "peperoni",
        "yogurt", "succo", "acqua", "bibite", "vino", "birra",
        "biscotti", "crackers", "cereali", "miele", "marmellata",
        "detersivo", "shampoo", "sapone", "carta igienica", "tovaglioli",
    )

    private val eventKeywords = listOf(
        "appuntamento", "visita", "riunione", "meeting", "incontro",
        "colloquio", "conferenza", "corso", "lezione", "allenamento",
        "partita", "spettacolo", "concerto", "cinema", "teatro",
        "compleanno", "anniversario", "festa", "cerimonia", "matrimonio",
        "vacanza", "viaggio", "volo", "prenotazione",
        "scadenza", "consegna", "deadline",
        "domani", "dopodomani", "stanotte", "stasera", "stamattina",
        "lunedì", "martedì", "mercoledì", "giovedì", "venerdì",
        "sabato", "domenica", "settimana", "mese", "prossimo", "prossima",
        "alle ore", "alle h", " ore ", " h ",
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )

    private val todoKeywords = listOf(
        "devo", "dobbiamo", "dovrei", "dovremmo", "bisogna",
        "ricordati", "ricordatevi", "ricorda", "non dimenticare",
        "non dimenticarti", "fai", "fate", "chiama", "chiamare",
        "scrivi", "scrivere", "manda", "mandare", "invia", "inviare",
        "prenota", "prenotare", "paga", "pagare", "ritira", "ritirare",
        "porta", "portare", "passa", "passare", "vai", "andate",
        "sistema", "sistemare", "aggiusta", "aggiustare", "prepara",
        "fare", "da fare", "to do", "todo", "task",
        "promemoria", "reminder", "nota bene", "nb:", "n.b.",
        "urgente", "importante", "priorità",
    )

    private val noteKeywords = listOf(
        "nota", "appunto", "info", "informazione", "link", "url",
        "http", "https", "www", "articolo", "leggi", "vedi",
        "interessante", "salva", "tieni", "conserva",
        "indirizzo", "numero", "codice", "password", "pin",
        "ricetta", "ingredienti", "procedimento", "istruzioni",
    )

    /**
     * Le destinazioni in ordine di pertinenza, la migliore per prima. Le note
     * ci sono sempre (catch-all con punteggio minimo 1), come su iOS.
     */
    fun classify(
        text: String,
        zone: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = LocalDateTime.now(zone),
    ): SaveClassification {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SaveClassification(emptyList(), null, false)
        val lower = trimmed.lowercase()
        val lines = trimmed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val date = SaveDateDetector.detect(trimmed, now)?.atZone(zone)?.toInstant()?.toEpochMilli()

        val scored = ArrayList<Pair<SaveAction, Int>>()

        var grocery = groceryKeywords.count { lower.contains(it) }
        if (lines.size >= 2) grocery += 3
        if (lines.size >= 2 && lines.all { it.length < 30 }) grocery += 2
        if (grocery >= 2) scored += SaveAction.Grocery(lines) to grocery

        var event = eventKeywords.count { lower.contains(it) }
        if (date != null) event += 4
        if (event >= 2) scored += SaveAction.Event(trimmed, date) to event

        var todo = todoKeywords.count { lower.contains(it) }
        if (trimmed.length < 80 && lines.size == 1) todo += 2
        if (lines.size >= 3) todo -= 3
        if (lines.size >= 5) todo -= 5
        if (trimmed.length > 200) todo -= 3
        if (todo >= 1) scored += SaveAction.Todo(lines.first()) to (todo + 1)

        val note = noteKeywords.count { lower.contains(it) }
        val body = if (lines.size > 1) lines.drop(1).joinToString("\n") else trimmed
        scored += SaveAction.Note(lines.first(), body) to maxOf(note + 1, 1)

        // sortedByDescending è stabile: a parità resta l'ordine di inserimento, come su iOS.
        val actions = scored.sortedByDescending { it.second }.map { it.first }
        return SaveClassification(actions, date, isModelClassified = false)
    }
}

/**
 * Date scritte in italiano dentro un messaggio: «domani alle 18», «venerdì»,
 * «3 marzo», «15/10», «tra due settimane» no (fuori misura). Gemello povero
 * di `NSDataDetector`: copre quello che si scrive davvero in una chat di
 * famiglia. Senza orario il momento è mezzogiorno, come fa iOS.
 */
object SaveDateDetector {

    private val weekdays = mapOf(
        "lunedì" to DayOfWeek.MONDAY, "lunedi" to DayOfWeek.MONDAY,
        "martedì" to DayOfWeek.TUESDAY, "martedi" to DayOfWeek.TUESDAY,
        "mercoledì" to DayOfWeek.WEDNESDAY, "mercoledi" to DayOfWeek.WEDNESDAY,
        "giovedì" to DayOfWeek.THURSDAY, "giovedi" to DayOfWeek.THURSDAY,
        "venerdì" to DayOfWeek.FRIDAY, "venerdi" to DayOfWeek.FRIDAY,
        "sabato" to DayOfWeek.SATURDAY,
        "domenica" to DayOfWeek.SUNDAY,
    )

    private val months = listOf(
        "gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno",
        "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre",
    )

    /** `\b` di Java non vede le lettere accentate come parola: «venerdì» non combacerebbe. */
    private fun word(pattern: String) = Regex("(?<!\\p{L})$pattern(?!\\p{L})")

    private val timeRegex = Regex("""(?:\balle(?:\s+ore)?|\bore|\bh)\s*(\d{1,2})(?:[:.](\d{2}))?\b""")
    private val numericDate = Regex("""\b(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{2,4}))?\b""")
    private val monthDate = Regex("""\b(\d{1,2})\s+(${months.joinToString("|")})(?:\s+(\d{4}))?\b""")

    fun detect(text: String, now: LocalDateTime): LocalDateTime? {
        val lower = text.lowercase()
        val today = now.toLocalDate()
        val day: LocalDate = dayFrom(lower, today) ?: return null
        val time = timeFrom(lower) ?: LocalTime.NOON
        return LocalDateTime.of(day, time)
    }

    private fun timeFrom(lower: String): LocalTime? {
        val m = timeRegex.find(lower) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        if (h !in 0..23 || min !in 0..59) return null
        return LocalTime.of(h, min)
    }

    private fun dayFrom(lower: String, today: LocalDate): LocalDate? {
        // Le parole prima dei numeri: «domani» batte «15/10» se compaiono insieme.
        if (word("dopodomani").containsMatchIn(lower)) return today.plusDays(2)
        if (word("domani").containsMatchIn(lower)) return today.plusDays(1)
        if (word("(?:oggi|stasera|stamattina|stanotte)").containsMatchIn(lower)) return today

        monthDate.find(lower)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = months.indexOf(m.groupValues[2]) + 1
            val explicitYear = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            return resolve(d, mo, explicitYear, today)
        }
        numericDate.find(lower)?.let { m ->
            val d = m.groupValues[1].toInt()
            val mo = m.groupValues[2].toInt()
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()?.let { if (it < 100) 2000 + it else it }
            resolve(d, mo, y, today)?.let { return it }
        }
        for ((name, dow) in weekdays) {
            if (word(name).containsMatchIn(lower)) {
                var diff = (dow.value - today.dayOfWeek.value + 7) % 7
                if (diff == 0) diff = 7 // «venerdì» detto di venerdì è la settimana dopo
                return today.plusDays(diff.toLong())
            }
        }
        return null
    }

    /** Senza anno si prende la prossima occorrenza: «3 marzo» detto a ottobre è l'anno dopo. */
    private fun resolve(day: Int, month: Int, year: Int?, today: LocalDate): LocalDate? {
        if (month !in 1..12 || day !in 1..31) return null
        return runCatching {
            if (year != null) {
                LocalDate.of(year, month, day)
            } else {
                val thisYear = LocalDate.of(today.year, month, day)
                if (thisYear.isBefore(today)) LocalDate.of(today.year + 1, month, day) else thisYear
            }
        }.getOrNull()
    }
}

/**
 * La risposta JSON del modello sul telefono, ridotta alle sole destinazioni
 * (in ordine di pertinenza). Tollerante come su iOS: toglie i recinti
 * markdown, ignora le etichette sconosciute e rifiuta una risposta vuota.
 */
object SaveModelAnswer {

    fun parseDestinations(raw: String): List<SaveDestination>? {
        val clean = raw.replace("```json", "").replace("```", "").trim()
        val obj = runCatching { org.json.JSONObject(clean) }.getOrNull() ?: return null
        val arr = obj.optJSONArray("destinations") ?: return null
        val out = ArrayList<SaveDestination>()
        for (i in 0 until arr.length()) {
            val d = when (arr.optString(i)) {
                "todo" -> SaveDestination.TODO
                "event" -> SaveDestination.EVENT
                "grocery" -> SaveDestination.GROCERY
                "note" -> SaveDestination.NOTE
                else -> null
            }
            if (d != null && d !in out) out += d
        }
        return out.ifEmpty { null }
    }

    /**
     * Rimette in ordine le azioni delle regole secondo il modello: quelle che il
     * modello cita vengono prima, nel suo ordine; il resto resta in coda. Il
     * contenuto delle azioni (titolo, righe, data) lo producono sempre le
     * regole, così non dipende da cosa scrive il modello.
     */
    fun reorder(rules: SaveClassification, modelOrder: List<SaveDestination>): SaveClassification {
        val byDest = rules.actions.associateBy { it.destination }
        val listed = modelOrder.mapNotNull { byDest[it] }
        val rest = rules.actions.filter { it !in listed }
        return rules.copy(actions = listed + rest, isModelClassified = true)
    }
}
