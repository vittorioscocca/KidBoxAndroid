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

    // Le altre tre lingue dell'app. Le parole italiane sopra si cercano come
    // sottostringa (come su iOS); queste a parola intera, perché le parole corte
    // di una lingua cadono dentro quelle di un'altra («sale» è sale in italiano
    // e saldi in inglese, «pan» sta in «pantaloni»).
    private val groceryWords = listOf(
        // en
        "shopping", "groceries", "supermarket", "store", "buy", "milk", "bread", "eggs", "pasta", "rice",
        "flour", "butter", "oil", "salt", "sugar", "coffee", "meat", "chicken", "fish", "ham", "cheese",
        "vegetables", "fruit", "potatoes", "tomatoes", "salad", "carrots", "onions", "garlic", "yogurt",
        "juice", "water", "wine", "beer", "cookies", "cereal", "honey", "jam", "detergent", "shampoo",
        "soap", "toilet paper", "napkins",
        // fr
        "courses", "supermarché", "magasin", "acheter", "achète", "lait", "pain", "oeufs", "œufs", "pâtes",
        "riz", "farine", "beurre", "huile", "sel", "sucre", "café", "viande", "poulet", "poisson", "jambon",
        "fromage", "légumes", "fruits", "pommes de terre", "tomates", "salade", "carottes", "oignons", "ail",
        "yaourt", "jus", "eau", "vin", "bière", "biscuits", "céréales", "miel", "confiture", "lessive",
        "shampooing", "savon", "papier toilette", "serviettes",
        // es
        "compra", "supermercado", "tienda", "comprar", "leche", "pan", "huevos", "arroz", "harina",
        "mantequilla", "aceite", "sal", "azúcar", "carne", "pollo", "pescado", "jamón", "queso", "verdura",
        "fruta", "patatas", "ensalada", "zanahorias", "cebollas", "ajo", "yogur", "zumo", "agua", "vino",
        "cerveza", "galletas", "cereales", "mermelada", "detergente", "champú", "jabón", "papel higiénico",
        "servilletas",
    )

    private val eventWords = listOf(
        // en
        "appointment", "visit", "meeting", "lesson", "class", "practice", "match", "game", "show", "concert",
        "cinema", "theater", "theatre", "birthday", "anniversary", "party", "ceremony", "wedding", "holiday",
        "trip", "flight", "booking", "reservation", "deadline", "due", "tomorrow", "tonight", "today",
        "monday", "tuesday", "wednesday", "thursday", "friday", "saturday", "sunday", "week", "month", "next",
        "o'clock", "january", "february", "march", "april", "may", "june", "july", "august", "september",
        "october", "november", "december",
        // fr
        "rendez-vous", "visite", "réunion", "rencontre", "cours", "entraînement", "spectacle", "cinéma",
        "théâtre", "anniversaire", "fête", "cérémonie", "mariage", "vacances", "voyage", "vol", "réservation",
        "échéance", "demain", "ce soir", "aujourd'hui", "lundi", "mardi", "mercredi", "jeudi", "vendredi",
        "samedi", "dimanche", "semaine", "mois", "prochain", "prochaine", "janvier", "février", "mars",
        "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre",
        // es
        "cita", "visita", "reunión", "encuentro", "clase", "entrenamiento", "partido", "espectáculo",
        "concierto", "cine", "teatro", "cumpleaños", "aniversario", "fiesta", "ceremonia", "boda",
        "vacaciones", "viaje", "vuelo", "reserva", "plazo", "mañana", "esta noche", "hoy", "lunes", "martes",
        "miércoles", "jueves", "viernes", "sábado", "domingo", "semana", "mes", "próximo", "próxima",
        "enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre",
        "noviembre", "diciembre",
    )

    private val todoWords = listOf(
        // en
        "i need to", "we need to", "have to", "must", "should", "remember", "don't forget", "call", "write",
        "send", "book", "pay", "pick up", "bring", "drop off", "fix", "prepare", "to do", "todo", "task",
        "reminder", "urgent", "important", "priority",
        // fr
        "je dois", "on doit", "il faut", "rappelle-toi", "n'oublie pas", "appelle", "appeler", "écris",
        "envoie", "envoyer", "réserve", "réserver", "paie", "payer", "récupère", "récupérer", "apporte",
        "apporter", "répare", "prépare", "à faire", "rappel", "priorité",
        // es
        "tengo que", "debo", "debemos", "hay que", "recuerda", "no olvides", "llama", "llamar", "escribe",
        "envía", "enviar", "reservar", "paga", "pagar", "recoge", "recoger", "trae", "traer", "arregla",
        "prepara", "por hacer", "recordatorio", "urgente", "importante", "prioridad",
    )

    private val noteWords = listOf(
        // en
        "note", "info", "information", "link", "article", "read", "see", "interesting", "keep", "address",
        "number", "code", "password", "pin", "recipe", "ingredients", "steps", "instructions",
        // fr
        "lien", "lis", "voir", "intéressant", "garde", "adresse", "numéro", "mot de passe", "recette",
        "ingrédients",
        // es
        "nota", "apunte", "información", "enlace", "artículo", "lee", "ver", "interesante", "guarda",
        "dirección", "número", "código", "contraseña", "receta", "ingredientes", "instrucciones",
    )

    private val wordRegexes = HashMap<String, Regex>()

    private fun hasWord(lower: String, word: String): Boolean =
        wordRegexes.getOrPut(word) { Regex("(?<!\\p{L})${Regex.escape(word)}(?!\\p{L})") }.containsMatchIn(lower)

    /** Italiano a sottostringa (come iOS), le altre lingue a parola intera. */
    private fun hits(lower: String, italian: List<String>, others: List<String>): Int =
        italian.count { lower.contains(it) } + others.count { it !in italian && hasWord(lower, it) }

    /**
     * Le destinazioni in ordine di pertinenza, la migliore per prima. Le note
     * ci sono sempre (catch-all con punteggio minimo 1), come su iOS.
     */
    fun classify(
        text: String,
        zone: ZoneId = ZoneId.systemDefault(),
        now: LocalDateTime = LocalDateTime.now(zone),
        /** Banner sotto la bolla: la nota catch-all solo se il testo la merita. */
        forChips: Boolean = false,
    ): SaveClassification {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return SaveClassification(emptyList(), null, false)
        val lower = trimmed.lowercase()
        val lines = trimmed.split("\n").map { it.trim() }.filter { it.isNotEmpty() }
        val date = SaveDateDetector.detect(trimmed, now)?.atZone(zone)?.toInstant()?.toEpochMilli()

        val scored = ArrayList<Pair<SaveAction, Int>>()

        var grocery = hits(lower, groceryKeywords, groceryWords)
        if (lines.size >= 2) grocery += 3
        if (lines.size >= 2 && lines.all { it.length < 30 }) grocery += 2
        if (grocery >= 2) scored += SaveAction.Grocery(lines) to grocery

        var event = hits(lower, eventKeywords, eventWords)
        if (date != null) event += 4
        if (event >= 2) scored += SaveAction.Event(trimmed, date) to event

        var todo = hits(lower, todoKeywords, todoWords)
        if (trimmed.length < 80 && lines.size == 1) todo += 2
        if (lines.size >= 3) todo -= 3
        if (lines.size >= 5) todo -= 5
        if (trimmed.length > 200) todo -= 3
        if (todo >= 1) scored += SaveAction.Todo(lines.first()) to (todo + 1)

        val note = hits(lower, noteKeywords, noteWords)
        val body = if (lines.size > 1) lines.drop(1).joinToString("\n") else trimmed
        if (!forChips || note >= 2 || lines.size >= 3 || trimmed.length > 160) {
            scored += SaveAction.Note(lines.first(), body) to maxOf(note + 1, 1)
        }

        // sortedByDescending è stabile: a parità resta l'ordine di inserimento, come su iOS.
        val actions = scored.sortedByDescending { it.second }.map { it.first }
        return SaveClassification(actions, date, isModelClassified = false)
    }
}

/**
 * Date scritte in un messaggio, nelle quattro lingue dell'app: «domani alle 18»,
 * «tomorrow at 6pm», «demain à 18h», «el viernes a las 18», «3 marzo», «15/10».
 * Gemello povero di `NSDataDetector`: copre quello che si scrive davvero in una
 * chat di famiglia. Senza orario il momento è mezzogiorno, come fa iOS. Le date
 * numeriche si leggono giorno/mese.
 */
object SaveDateDetector {

    private val weekdays = mapOf(
        // it
        "lunedì" to DayOfWeek.MONDAY, "lunedi" to DayOfWeek.MONDAY,
        "martedì" to DayOfWeek.TUESDAY, "martedi" to DayOfWeek.TUESDAY,
        "mercoledì" to DayOfWeek.WEDNESDAY, "mercoledi" to DayOfWeek.WEDNESDAY,
        "giovedì" to DayOfWeek.THURSDAY, "giovedi" to DayOfWeek.THURSDAY,
        "venerdì" to DayOfWeek.FRIDAY, "venerdi" to DayOfWeek.FRIDAY,
        "sabato" to DayOfWeek.SATURDAY,
        "domenica" to DayOfWeek.SUNDAY,
        // en
        "monday" to DayOfWeek.MONDAY, "tuesday" to DayOfWeek.TUESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thursday" to DayOfWeek.THURSDAY, "friday" to DayOfWeek.FRIDAY, "saturday" to DayOfWeek.SATURDAY,
        "sunday" to DayOfWeek.SUNDAY,
        // fr
        "lundi" to DayOfWeek.MONDAY, "mardi" to DayOfWeek.TUESDAY, "mercredi" to DayOfWeek.WEDNESDAY,
        "jeudi" to DayOfWeek.THURSDAY, "vendredi" to DayOfWeek.FRIDAY, "samedi" to DayOfWeek.SATURDAY,
        "dimanche" to DayOfWeek.SUNDAY,
        // es
        "lunes" to DayOfWeek.MONDAY, "martes" to DayOfWeek.TUESDAY, "miércoles" to DayOfWeek.WEDNESDAY,
        "miercoles" to DayOfWeek.WEDNESDAY, "jueves" to DayOfWeek.THURSDAY, "viernes" to DayOfWeek.FRIDAY,
        "sábado" to DayOfWeek.SATURDAY, "sabado" to DayOfWeek.SATURDAY, "domingo" to DayOfWeek.SUNDAY,
    )

    /** Nome del mese (qualunque lingua) → numero. */
    private val monthNumbers: Map<String, Int> = buildMap {
        val tables = listOf(
            listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre"),
            listOf("january", "february", "march", "april", "may", "june", "july", "august", "september", "october", "november", "december"),
            listOf("janvier", "février", "mars", "avril", "mai", "juin", "juillet", "août", "septembre", "octobre", "novembre", "décembre"),
            listOf("enero", "febrero", "marzo", "abril", "mayo", "junio", "julio", "agosto", "septiembre", "octubre", "noviembre", "diciembre"),
        )
        for (t in tables) t.forEachIndexed { i, name -> put(name, i + 1) }
        put("fevrier", 2); put("aout", 8); put("decembre", 12)
    }

    /** `\b` di Java non vede le lettere accentate come parola: «venerdì» non combacerebbe. */
    private fun word(pattern: String) = Regex("(?<!\\p{L})$pattern(?!\\p{L})")

    private val monthAlt = monthNumbers.keys.sortedByDescending { it.length }.joinToString("|")

    // «alle 18», «alle ore 18.30», «h 18» · «at 6pm», «6 pm», «at 18:30» · «à 18h», «18h30» · «a las 18»
    private val timeAmPm = Regex("""\b(\d{1,2})(?:[:.](\d{2}))?\s*(am|pm)\b""")
    private val timeIt = Regex("""(?:\balle(?:\s+ore)?|\bore|\bh)\s*(\d{1,2})(?:[:.](\d{2}))?\b""")
    private val timeEn = Regex("""\bat\s+(\d{1,2})(?::(\d{2}))?\b""")
    private val timeFr = Regex("""\b(\d{1,2})\s*h\s*(\d{2})?\b""")
    private val timeEs = Regex("""\ba\s+las?\s+(\d{1,2})(?:[:.](\d{2}))?\b""")

    private val numericDate = Regex("""\b(\d{1,2})[/\-.](\d{1,2})(?:[/\-.](\d{2,4}))?\b""")
    // «3 novembre», «3 de noviembre», «3rd of november»
    private val dayMonth = Regex("""\b(\d{1,2})(?:st|nd|rd|th)?\s+(?:de\s+|of\s+)?($monthAlt)(?:\s+(?:de\s+)?(\d{4}))?\b""")
    // «november 3», «november 3rd»
    private val monthDay = Regex("""\b($monthAlt)\s+(\d{1,2})(?:st|nd|rd|th)?(?:,?\s+(\d{4}))?\b""")

    fun detect(text: String, now: LocalDateTime): LocalDateTime? {
        val lower = text.lowercase()
        val today = now.toLocalDate()
        val day: LocalDate = dayFrom(lower, today) ?: return null
        val time = timeFrom(lower) ?: LocalTime.NOON
        return LocalDateTime.of(day, time)
    }

    private fun timeFrom(lower: String): LocalTime? {
        timeAmPm.find(lower)?.let { m ->
            var h = m.groupValues[1].toInt()
            val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.toInt() ?: 0
            if (h !in 1..12 || min !in 0..59) return@let
            val pm = m.groupValues[3] == "pm"
            h = when { pm && h < 12 -> h + 12; !pm && h == 12 -> 0; else -> h }
            return LocalTime.of(h, min)
        }
        for (r in listOf(timeIt, timeEs, timeEn, timeFr)) {
            val m = r.find(lower) ?: continue
            val h = m.groupValues[1].toIntOrNull() ?: continue
            val min = m.groupValues[2].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
            if (h in 0..23 && min in 0..59) return LocalTime.of(h, min)
        }
        return null
    }

    private fun dayFrom(lower: String, today: LocalDate): LocalDate? {
        // Le parole prima dei numeri: «domani» batte «15/10» se compaiono insieme.
        if (word("(?:dopodomani|day after tomorrow|après-demain|apres-demain|pasado mañana|pasado manana)").containsMatchIn(lower)) {
            return today.plusDays(2)
        }
        if (word("(?:domani|tomorrow|demain|mañana|manana)").containsMatchIn(lower)) return today.plusDays(1)
        if (word("(?:oggi|stasera|stamattina|stanotte|today|tonight|aujourd'hui|ce soir|hoy|esta noche)").containsMatchIn(lower)) {
            return today
        }

        dayMonth.find(lower)?.let { m ->
            val mo = monthNumbers[m.groupValues[2]] ?: return@let
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            resolve(m.groupValues[1].toInt(), mo, y, today)?.let { return it }
        }
        monthDay.find(lower)?.let { m ->
            val mo = monthNumbers[m.groupValues[1]] ?: return@let
            val y = m.groupValues[3].takeIf { it.isNotEmpty() }?.toInt()
            resolve(m.groupValues[2].toInt(), mo, y, today)?.let { return it }
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
