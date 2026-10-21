package it.vittorioscocca.kidbox.ui.screens.ai.planning

import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.abs

/**
 * Link dell'assistente alle voci dell'app: «trovami la bolletta della luce»
 * → risposta con `[Bolletta luce](apri:spese)` → un tocco apre la spesa.
 *
 * Il modello non vede gli id (costerebbero token a ogni domanda e li
 * inventerebbe): scrive il titolo come compare nella scheda e il tipo, con la
 * data se più voci si chiamano uguale. La voce si cerca nello snapshot del
 * quaderno al momento del tocco; se non c'è più si apre la sezione.
 * Stesso formato e stesse regole su iOS (`AgentLinks.swift`).
 */
object AgentLinks {

    /** La regola del prompt, nelle regole stabili dell'assistente. Stesso testo su iOS. */
    const val PROMPT_RULE =
        "- Quando parli di una voce precisa delle schede (una spesa, un documento, una nota, un evento, un to-do, una visita…) " +
            "o l'utente cerca qualcosa («trovami…», «dov'è…», «la bolletta di…»), mettila come link, così si apre con un tocco: " +
            "[titolo esatto come nella scheda](apri:TIPO), oppure [titolo](apri:TIPO/AAAA-MM-GG) con la data della voce se più voci " +
            "hanno lo stesso titolo. TIPO: spese, documento, nota, evento, todo, listaspesa, visita, esame, cura, biglietto, casa, " +
            "veicolo, animale, viaggio. Solo voci che ci sono nelle schede, mai altri indirizzi; non dire «clicca qui»: il link è il titolo."

    enum class Kind(val raw: String) {
        EXPENSE("spese"), DOCUMENT("documento"), NOTE("nota"), EVENT("evento"), TODO("todo"),
        GROCERY("listaspesa"), VISIT("visita"), EXAM("esame"), TREATMENT("cura"), TICKET("biglietto"),
        HOME("casa"), VEHICLE("veicolo"), PET("animale"), TRIP("viaggio");

        companion object {
            /** Varianti che il modello scrive anche se la regola ne dà una sola. */
            fun loose(raw: String): Kind? {
                val key = AgentRelevance.fold(raw).replace(" ", "").replace("-", "")
                return when (key) {
                    "spesa", "spese", "expense" -> EXPENSE
                    "documenti", "doc" -> DOCUMENT
                    "note" -> NOTE
                    "eventi", "calendario" -> EVENT
                    "todos", "promemoria" -> TODO
                    "lista", "grocery" -> GROCERY
                    "visite" -> VISIT
                    "esami" -> EXAM
                    "cure", "terapia" -> TREATMENT
                    "wallet", "biglietti" -> TICKET
                    "veicoli", "auto" -> VEHICLE
                    "animali" -> PET
                    "viaggi" -> TRIP
                    else -> entries.firstOrNull { it.raw == key }
                }
            }
        }
    }

    data class Link(val kind: Kind, val title: String, val day: String?)

    /** Il destino di `apri:tipo[/data]`; `null` se non è un link dell'assistente. */
    fun parse(title: String, url: String): Link? {
        val m = Regex("""^\s*apri:([A-Za-zÀ-ÿ\- ]+?)(?:/(\d{4}-\d{2}-\d{2}))?\s*$""").find(url) ?: return null
        val kind = Kind.loose(m.groupValues[1]) ?: return null
        val cleanTitle = title.trim().takeIf { it.isNotEmpty() } ?: return null
        return Link(kind, cleanTitle, m.groupValues[2].takeIf { it.isNotEmpty() })
    }

    /**
     * Dove porta il link: le rotte da aprire una dopo l'altra sopra
     * l'assistente, così «Indietro» ci torna. `found` falso = aperta la sezione.
     */
    data class Destination(val routes: List<String>, val found: Boolean)

    fun destination(link: Link, s: AgentMemorySnapshot): Destination {
        val fid = s.familyId
        return when (link.kind) {
            Kind.EXPENSE -> best(s.expenses, link, { it.title }, { it.dateEpochMillis })
                ?.let { Destination(listOf(AppDestination.ExpensesHome.createRoute(fid, highlightExpenseId = it.id)), true) }
                ?: Destination(listOf(AppDestination.ExpensesHome.createRoute(fid)), false)
            Kind.DOCUMENT -> {
                val docs = s.documents + s.walletIdentity.map { it.first }.filter { w -> s.documents.none { it.id == w.id } }
                best(docs, link, { it.title }, { it.createdAtEpochMillis })
                    ?.let { documentDestination(it, s) }
                    ?: Destination(listOf(AppDestination.DocumentsHome.createRoute(fid)), false)
            }
            Kind.NOTE -> best(s.notes, link, { it.title }, { it.updatedAtEpochMillis })
                ?.let { Destination(listOf(AppDestination.NoteDetail.createRoute(fid, it.id)), true) }
                ?: Destination(listOf(AppDestination.NotesHome.createRoute(fid)), false)
            Kind.EVENT -> {
                val e = best(s.events, link, { it.title }, { it.startDateEpochMillis })
                Destination(listOf(AppDestination.Calendar.createRoute(fid, e?.id)), e != null)
            }
            Kind.TODO -> best(s.todos, link, { it.title }, { it.dueAtEpochMillis ?: it.createdAtEpochMillis })
                ?.takeIf { !it.listId.isNullOrBlank() }
                ?.let { Destination(listOf(AppDestination.TodoList.createRoute(fid, it.childId, it.listId!!, it.id)), true) }
                ?: Destination(listOf(AppDestination.Todo.route), false)
            Kind.GROCERY -> Destination(listOf(AppDestination.ShoppingList.createRoute(fid)), true)
            Kind.VISIT -> best(s.allVisits, link, { it.reason.ifBlank { it.doctorName.orEmpty() } }, { it.dateEpochMillis })
                ?.let { Destination(listOf(AppDestination.MedicalVisitDetail.route(fid, it.childId, it.id)), true) }
                ?: healthFallback(fid)
            Kind.EXAM -> best(s.healthPersons.flatMap { it.exams }, link, { it.name }, { it.deadlineEpochMillis ?: it.createdAtEpochMillis })
                ?.let { Destination(listOf(AppDestination.MedicalExamDetail.route(fid, it.childId, it.id)), true) }
                ?: healthFallback(fid)
            Kind.TREATMENT -> best(s.healthPersons.flatMap { it.treatments } + s.petTreatments, link, { it.drugName }, { it.startDateEpochMillis })
                ?.let { t ->
                    val route = if (t.petId.isNotBlank()) AppDestination.PetTreatmentDetail.route(fid, t.petId, t.id)
                    else AppDestination.TreatmentDetail.route(fid, t.childId, t.id)
                    Destination(listOf(route), true)
                }
                ?: healthFallback(fid)
            Kind.TICKET -> best(s.walletTickets, link, { it.title }, { it.eventDateEpochMillis ?: it.createdAtEpochMillis })
                ?.let { Destination(listOf(AppDestination.WalletDetail.createRoute(fid, it.id)), true) }
                ?: Destination(listOf(AppDestination.WalletHome.createRoute(fid)), false)
            Kind.HOME -> best(s.homeItems, link, { it.name }, { it.createdAt })
                ?.let { Destination(listOf(AppDestination.HomeItemDetail.createRoute(fid, it.id)), true) }
                ?: best(s.housePayments, link, { it.name }, { it.createdAt })
                    ?.let { Destination(listOf(AppDestination.HousePaymentDetail.createRoute(fid, it.id)), true) }
                ?: Destination(listOf(AppDestination.HomeItems.createRoute(fid)), false)
            Kind.VEHICLE -> best(s.vehicles, link, { it.name }, { it.createdAt })
                ?.let { Destination(listOf(AppDestination.VehicleDetail.createRoute(fid, it.id)), true) }
                ?: Destination(listOf(AppDestination.Vehicles.createRoute(fid)), false)
            Kind.PET -> best(s.pets, link, { it.name }, { it.createdAt })
                ?.let { Destination(listOf(AppDestination.PetDetail.createRoute(fid, it.id)), true) }
                ?: Destination(listOf(AppDestination.Pets.createRoute(fid)), false)
            Kind.TRIP -> best(s.trips, link, { it.name }, { it.startDateEpoch })
                ?.let { Destination(listOf(AppDestination.TravelDetail.createRoute(fid, it.id)), true) }
                ?: Destination(listOf(AppDestination.TravelList.createRoute(fid)), false)
        }
    }

    private fun healthFallback(fid: String) =
        Destination(listOf(AppDestination.PediatricChildSelector.createRoute(fid)), false)

    /**
     * Un allegato si apre dove vive (la visita, l'oggetto di casa…), un
     * documento d'identità nel Wallet, gli altri nella loro cartella.
     */
    private fun documentDestination(d: it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity, s: AgentMemorySnapshot): Destination {
        val fid = s.familyId
        if (s.walletIdentity.any { it.first.id == d.id }) {
            return Destination(listOf(AppDestination.WalletDocumentDetail.createRoute(fid, d.id)), true)
        }
        val tag = d.notes.orEmpty()
        val colon = tag.indexOf(':')
        if (colon > 0) {
            val id = tag.substring(colon + 1)
            val route = when (tag.substring(0, colon)) {
                "visit" -> s.allVisits.firstOrNull { it.id == id }?.let { AppDestination.MedicalVisitDetail.route(fid, it.childId, it.id) }
                "exam" -> s.healthPersons.flatMap { it.exams }.firstOrNull { it.id == id }
                    ?.let { AppDestination.MedicalExamDetail.route(fid, it.childId, it.id) }
                "treatment" -> s.healthPersons.flatMap { it.treatments }.firstOrNull { it.id == id }
                    ?.let { AppDestination.TreatmentDetail.route(fid, it.childId, it.id) }
                "homeItem" -> AppDestination.HomeItemDetail.createRoute(fid, id)
                "housePayment" -> AppDestination.HousePaymentDetail.createRoute(fid, id)
                "vehicle" -> AppDestination.VehicleDetail.createRoute(fid, id)
                "vehicleEvent" -> s.vehicleEvents.firstOrNull { it.id == id }?.let { AppDestination.VehicleDetail.createRoute(fid, it.vehicleId) }
                "pet" -> AppDestination.PetDetail.createRoute(fid, id)
                "expense" -> AppDestination.ExpensesHome.createRoute(fid, highlightExpenseId = id)
                else -> null
            }
            if (route != null) return Destination(listOf(route), true)
        }
        return Destination(
            listOf(AppDestination.DocumentsHome.createRoute(fid, highlightDocumentId = d.id, folderId = d.categoryId ?: "root")),
            true,
        )
    }

    private fun words(text: String): Set<String> =
        text.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length > 2 }.toSet()

    /**
     * La voce più simile al titolo: uguale, poi contenuta, poi parole in
     * comune; a parità vince il giorno indicato, poi la più recente.
     */
    fun <T> best(items: List<T>, link: Link, title: (T) -> String, date: (T) -> Long): T? {
        val wanted = AgentRelevance.fold(link.title).trim()
        if (wanted.isEmpty()) return null
        val wantedWords = words(wanted)
        val day = link.day?.let {
            runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(it)?.time }.getOrNull()
        }
        var bestItem: T? = null
        var bestScore = 0
        var bestDistance = Double.POSITIVE_INFINITY
        for (item in items) {
            val t = AgentRelevance.fold(title(item)).trim()
            if (t.isEmpty()) continue
            val score = when {
                t == wanted -> 1000
                t.contains(wanted) || wanted.contains(t) -> 500
                else -> {
                    val common = words(t).intersect(wantedWords).size
                    // Più di metà delle parole chieste: una parola sola in comune
                    // («Bolletta gas» per «Bolletta luce») non è la voce cercata.
                    if (common == 0 || common * 2 <= wantedWords.size) continue
                    100 * common
                }
            }
            val d = date(item)
            val distance = day?.let { abs(d - it).toDouble() } ?: -d.toDouble()
            if (score > bestScore || (score == bestScore && distance < bestDistance)) {
                bestItem = item
                bestScore = score
                bestDistance = distance
            }
        }
        return bestItem
    }
}
