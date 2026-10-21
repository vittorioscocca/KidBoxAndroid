package it.vittorioscocca.kidbox.ui.screens.search

import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.ui.screens.ai.planning.AgentLinks
import it.vittorioscocca.kidbox.ui.screens.ai.planning.AgentMemorySnapshot
import it.vittorioscocca.kidbox.ui.screens.ai.planning.AgentRelevance
import it.vittorioscocca.kidbox.ui.screens.notes.htmlToPlainText
import java.text.DateFormat
import java.text.NumberFormat
import java.util.Currency
import java.util.Date
import java.util.Locale

/**
 * La ricerca in Home: tutta sul telefono, sui dati del quaderno
 * dell'assistente ([AgentMemorySnapshot]), quindi con le stesse esclusioni —
 * niente password, niente voci «solo per me» degli altri, niente testo dei
 * documenti d'identità. Cerca nei titoli e nel testo letto dei documenti; una
 * voce trovata si apre con la destinazione dei link dell'assistente
 * ([AgentLinks.destination]). Stessa logica su iOS (`KBSearchIndex.swift`).
 */
/** `untitled` e `visit`: etichette nella lingua dell'app per voci senza titolo. */
class SearchIndex(val snapshot: AgentMemorySnapshot, untitled: String, visit: String) {

    data class Entry(
        val id: String,
        val kind: AgentLinks.Kind,
        val item: AgentLinks.Item,
        val title: String,
        val subtitle: String,
        val date: Long,
        /** Testo oltre al titolo (note, testo letto…), per l'estratto. */
        val text: String,
        val foldedTitle: String,
        val foldedText: String,
    )

    /** `snippet`: riga di testo intorno alla parola trovata, se non è nel titolo. */
    data class Hit(val entry: Entry, val snippet: String?, val inTitle: Boolean)

    data class Section(val kind: AgentLinks.Kind, val hits: List<Hit>)

    private val entries: List<Entry> = build(snapshot, untitled, visit)

    /**
     * Tutte le parole della ricerca, nel titolo o nel testo: prima le voci col
     * titolo che corrisponde, poi le più recenti.
     */
    fun search(query: String): List<Section> {
        val words = AgentRelevance.fold(query).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty() || words.joinToString("").length < 2) return emptyList()
        val hits = entries.mapNotNull { e ->
            val inTitle = words.all { e.foldedTitle.contains(it) }
            if (!inTitle && !words.all { e.foldedTitle.contains(it) || e.foldedText.contains(it) }) return@mapNotNull null
            Hit(e, if (inTitle) null else snippet(e.text, words), inTitle)
        }
        val grouped = hits.groupBy { it.entry.kind }
        return ORDER.mapNotNull { kind ->
            grouped[kind]?.takeIf { it.isNotEmpty() }?.let { list ->
                Section(
                    kind,
                    list.sortedWith(compareByDescending<Hit> { it.inTitle }.thenByDescending { it.entry.date }).take(PER_SECTION),
                )
            }
        }
    }

    companion object {
        /** Ordine delle sezioni nei risultati. */
        val ORDER = listOf(
            AgentLinks.Kind.DOCUMENT, AgentLinks.Kind.EXPENSE, AgentLinks.Kind.NOTE, AgentLinks.Kind.EVENT,
            AgentLinks.Kind.TODO, AgentLinks.Kind.VISIT, AgentLinks.Kind.EXAM, AgentLinks.Kind.TREATMENT,
            AgentLinks.Kind.TICKET, AgentLinks.Kind.HOME, AgentLinks.Kind.VEHICLE, AgentLinks.Kind.PET,
            AgentLinks.Kind.TRIP, AgentLinks.Kind.GROCERY,
        )
        const val PER_SECTION = 8

        private fun build(s: AgentMemorySnapshot, untitled: String, visit: String): List<Entry> {
            val locale = Locale.getDefault()
            val dateFmt = DateFormat.getDateInstance(DateFormat.MEDIUM, locale)
            fun day(millis: Long) = dateFmt.format(Date(millis))
            val euro = NumberFormat.getCurrencyInstance(locale).apply { currency = Currency.getInstance("EUR") }
            val out = mutableListOf<Entry>()
            fun add(
                id: String, kind: AgentLinks.Kind, item: AgentLinks.Item, title: String,
                subtitle: String, date: Long, vararg text: String?,
            ) {
                val clean = title.trim()
                if (clean.isEmpty()) return
                val body = text.mapNotNull { it?.trim()?.takeIf { t -> t.isNotEmpty() } }.joinToString("\n")
                out += Entry(
                    "${kind.raw}:$id", kind, item, clean, subtitle, date, body,
                    AgentRelevance.fold(clean), AgentRelevance.fold(body),
                )
            }
            fun join(vararg parts: String?) = parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

            s.expenses.forEach { e ->
                add(e.id, AgentLinks.Kind.EXPENSE, AgentLinks.Item.Expense(e), e.title,
                    join(euro.format(e.amount), day(e.dateEpochMillis)), e.dateEpochMillis, e.notes)
            }
            s.documents.forEach { d ->
                val text = d.extractedText?.let { HealthAiDocumentText.sanitizeExtractedText(it) }
                add(d.id, AgentLinks.Kind.DOCUMENT, AgentLinks.Item.Document(d), d.title, day(d.createdAtEpochMillis), d.createdAtEpochMillis, text)
            }
            // Documenti d'identità: solo il titolo, il loro testo non entra mai.
            s.walletIdentity.map { it.first }.filter { w -> s.documents.none { it.id == w.id } }.forEach { d ->
                add(d.id, AgentLinks.Kind.DOCUMENT, AgentLinks.Item.Document(d), d.title, day(d.createdAtEpochMillis), d.createdAtEpochMillis)
            }
            s.notes.forEach { n ->
                add(n.id, AgentLinks.Kind.NOTE, AgentLinks.Item.Note(n), n.title.ifBlank { untitled },
                    day(n.updatedAtEpochMillis), n.updatedAtEpochMillis, n.body.htmlToPlainText())
            }
            s.events.forEach { e ->
                add(e.id, AgentLinks.Kind.EVENT, AgentLinks.Item.Event(e), e.title, day(e.startDateEpochMillis), e.startDateEpochMillis, e.location, e.notes)
            }
            s.todos.filter { !it.listId.isNullOrBlank() }.forEach { t ->
                add(t.id, AgentLinks.Kind.TODO, AgentLinks.Item.Todo(t), t.title, t.dueAtEpochMillis?.let(::day).orEmpty(),
                    t.dueAtEpochMillis ?: t.createdAtEpochMillis, t.notes)
            }
            s.pendingGrocery.forEach { g ->
                add(g.id, AgentLinks.Kind.GROCERY, AgentLinks.Item.Grocery, g.name, g.category.orEmpty(), g.createdAtEpochMillis)
            }
            s.allVisits.forEach { v ->
                add(v.id, AgentLinks.Kind.VISIT, AgentLinks.Item.Visit(v), v.reason.ifBlank { visit },
                    join(s.personName(v.childId), day(v.dateEpochMillis)), v.dateEpochMillis,
                    v.doctorName, v.diagnosis, v.recommendations, v.notes)
            }
            s.healthPersons.flatMap { it.exams }.forEach { e ->
                val whenMs = e.resultDateEpochMillis ?: e.deadlineEpochMillis ?: e.createdAtEpochMillis
                add(e.id, AgentLinks.Kind.EXAM, AgentLinks.Item.Exam(e), e.name, join(s.personName(e.childId), day(whenMs)), whenMs, e.notes)
            }
            (s.healthPersons.flatMap { it.treatments } + s.petTreatments).forEach { t ->
                val who = if (t.petId.isBlank()) s.personName(t.childId) else s.pets.firstOrNull { it.id == t.petId }?.name
                add(t.id, AgentLinks.Kind.TREATMENT, AgentLinks.Item.Treatment(t), t.drugName, join(who, day(t.startDateEpochMillis)), t.startDateEpochMillis)
            }
            s.walletTickets.forEach { t ->
                add(t.id, AgentLinks.Kind.TICKET, AgentLinks.Item.Ticket(t), t.title, t.eventDateEpochMillis?.let(::day).orEmpty(),
                    t.eventDateEpochMillis ?: t.createdAtEpochMillis, t.location, t.arrivalLocation)
            }
            s.homeItems.forEach { add(it.id, AgentLinks.Kind.HOME, AgentLinks.Item.HomeItem(it), it.name, "", it.createdAt, it.notes) }
            s.housePayments.forEach { add(it.id, AgentLinks.Kind.HOME, AgentLinks.Item.HousePayment(it), it.name, "", it.createdAt) }
            s.vehicles.forEach { add(it.id, AgentLinks.Kind.VEHICLE, AgentLinks.Item.Vehicle(it), it.name, "", it.createdAt) }
            s.pets.forEach { add(it.id, AgentLinks.Kind.PET, AgentLinks.Item.Pet(it), it.name, "", it.createdAt) }
            s.trips.forEach { t ->
                add(t.id, AgentLinks.Kind.TRIP, AgentLinks.Item.Trip(t), t.name, "${day(t.startDateEpoch)} – ${day(t.endDateEpoch)}", t.startDateEpoch)
            }
            return out
        }

        /** Una riga intorno alla prima parola trovata nel testo. */
        private fun snippet(text: String, words: List<String>): String? {
            val folded = AgentRelevance.fold(text)
            // `fold` toglie gli accenti senza cambiare la lunghezza dei caratteri
            // composti già normalizzati: per l'estratto basta la posizione approssimata.
            for (w in words) {
                val at = folded.indexOf(w).takeIf { it >= 0 } ?: continue
                val start = (at - 40).coerceAtLeast(0).coerceAtMost(text.length)
                val end = (at + w.length + 80).coerceAtMost(text.length)
                if (start >= end) continue
                val line = text.substring(start, end).replace('\n', ' ').trim()
                return (if (start > 0) "…" else "") + line + (if (end < text.length) "…" else "")
            }
            return null
        }
    }
}
