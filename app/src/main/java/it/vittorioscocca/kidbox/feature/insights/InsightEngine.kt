package it.vittorioscocca.kidbox.feature.insights

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Motore proattivo — gemello di `KBInsightEngine.swift`.
 *
 * Dalle voci di famiglia già in locale ricava le cose rimaste scoperte, senza
 * AI e senza rete. Funzione pura su una fotografia dei dati ([InsightSnapshot]),
 * così le regole si provano con dati finti.
 *
 * Ogni candidato ha il suo orario ideale di consegna, anche futuro o già
 * passato, e non dipende da «adesso»: due valutazioni sugli stessi dati danno
 * lo stesso risultato. Quando consegnare davvero lo decide [InsightDelivery].
 */

/** Il valore grezzo è lo stesso di iOS: finisce nella chiave e negli eventi analytics. */
enum class InsightType(val raw: String) {
    TODO_UNASSIGNED_DUE_SOON("todo_unassigned_due_soon"),
    TREATMENT_DOSE_MISSING("treatment_dose_missing"),
    EVENT_TOMORROW_NO_REMINDER("event_tomorrow_no_reminder"),
    VACCINE_DUE("vaccine_due"),
    GROCERY_STALE("grocery_stale"),
    /** Pagamento di casa in scadenza per cui nessuno ha preso il to-do «Pagare: …». */
    HOUSE_PAYMENT_DUE("house_payment_due"),
    ;

    companion object {
        fun fromRaw(raw: String?): InsightType? = entries.firstOrNull { it.raw == raw }
    }
}

/** Quanto conta: decide il canale, non l'ordine delle notifiche. */
enum class InsightPriority(val raw: Int) {
    LOW(0), NORMAL(1), HIGH(2);

    companion object {
        fun fromRaw(raw: Int): InsightPriority = entries.firstOrNull { it.raw == raw } ?: LOW
    }
}

enum class InsightState(val raw: String) {
    NEW("new"),
    SEEN("seen"),
    /** Risolto: dall'utente o dal motore quando la regola non scatta più. */
    DONE("done"),
    /** «Non mi interessa». */
    DISMISSED("dismissed"),
    ;

    val isOpen: Boolean get() = this == NEW || this == SEEN

    companion object {
        fun fromRaw(raw: String?): InsightState = entries.firstOrNull { it.raw == raw } ?: DISMISSED
    }
}

data class InsightCandidate(
    val type: InsightType,
    val entityId: String,
    val priority: InsightPriority,
    val fireAtMillis: Long,
)

data class InsightSnapshot(
    val todos: List<Todo> = emptyList(),
    val treatments: List<Treatment> = emptyList(),
    val doseLogs: List<DoseLog> = emptyList(),
    val housePayments: List<HousePayment> = emptyList(),
    /** Id dei to-do della famiglia, anche fatti: il to-do «Pagare: …» dice che qualcuno se n'è occupato. */
    val todoIds: Set<String> = emptySet(),
) {
    data class HousePayment(
        val id: String,
        val isDeleted: Boolean,
        /** Scadenza unica: ha la precedenza sul giorno del mese. */
        val dueDateMillis: Long?,
        /** Scadenza ricorrente: giorno del mese (1-31). */
        val monthlyDay: Int?,
    )

    data class Todo(
        val id: String,
        val dueAtMillis: Long?,
        val hasDueTime: Boolean,
        val isDone: Boolean,
        val isDeleted: Boolean,
        /** Uid di un membro oppure nome di chi l'ha presa da fuori dall'app. */
        val isTaken: Boolean,
        val isVisibleToUser: Boolean,
    )

    data class Treatment(
        val id: String,
        val isActive: Boolean,
        val isDeleted: Boolean,
        val isLongTerm: Boolean,
        val durationDays: Int,
        val startDateMillis: Long,
        val endDateMillis: Long?,
        val intervalBetweenDosesDays: Int,
        /** «08:00», «20:00»… */
        val scheduleTimes: List<String>,
    )

    data class DoseLog(
        val treatmentId: String,
        val dayNumber: Int,
        val slotIndex: Int,
        val taken: Boolean,
        val isDeleted: Boolean,
    )
}

object InsightEngine {

    /** Quanto avanti si guarda per i to-do. */
    private const val TODO_HORIZON_MS = 7L * 24 * 3600 * 1000
    /** Margine dopo l'orario della dose prima di dire che manca. */
    const val DOSE_GRACE_MS = 3600L * 1000

    fun evaluate(snapshot: InsightSnapshot, nowMillis: Long, zone: ZoneId = ZoneId.systemDefault()): List<InsightCandidate> =
        unassignedTodos(snapshot.todos, nowMillis, zone) + missingDoses(snapshot, nowMillis, zone) +
            unclaimedHousePayments(snapshot, nowMillis, zone)

    private const val PAYMENT_LEAD_DAYS = 3L
    private const val PAYMENT_HORIZON_DAYS = 7L

    /** Id del to-do «Pagare: …» di una scadenza: lo stesso su ogni telefono e su iOS. */
    fun paymentTodoId(paymentId: String, due: LocalDate): String = "hp-$paymentId-$due"

    /** Prossima scadenza da oggi compreso: la data unica, o il giorno del mese (il 31 diventa l'ultimo). */
    fun nextDue(p: InsightSnapshot.HousePayment, today: LocalDate, zone: ZoneId): LocalDate? {
        p.dueDateMillis?.let { millis ->
            val day = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
            return if (day.isBefore(today)) null else day
        }
        val d = p.monthlyDay?.takeIf { it in 1..31 } ?: return null
        for (offset in 0L..1L) {
            val month = today.plusMonths(offset)
            val due = month.withDayOfMonth(minOf(d, month.lengthOfMonth()))
            if (!due.isBefore(today)) return due
        }
        return null
    }

    /** Pagamento che scade entro una settimana senza il suo to-do: avviso 3 giorni prima alle 9. */
    fun unclaimedHousePayments(snapshot: InsightSnapshot, nowMillis: Long, zone: ZoneId): List<InsightCandidate> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        return snapshot.housePayments.mapNotNull { p ->
            if (p.isDeleted) return@mapNotNull null
            val due = nextDue(p, today, zone) ?: return@mapNotNull null
            val days = ChronoUnit.DAYS.between(today, due)
            if (days > PAYMENT_HORIZON_DAYS) return@mapNotNull null
            if (paymentTodoId(p.id, due) in snapshot.todoIds) return@mapNotNull null
            InsightCandidate(
                type = InsightType.HOUSE_PAYMENT_DUE,
                entityId = "${p.id}@$due",
                priority = if (days <= 1) InsightPriority.HIGH else InsightPriority.NORMAL,
                fireAtMillis = due.minusDays(PAYMENT_LEAD_DAYS).atTime(LocalTime.of(9, 0))
                    .atZone(zone).toInstant().toEpochMilli(),
            )
        }
    }

    /**
     * To-do da fare, visibile, che nessuno ha preso e scade nella prossima
     * settimana: avviso il giorno prima alla stessa ora, urgente sotto le 6 ore.
     * Senza orario la scadenza è la fine di quel giorno.
     */
    fun unassignedTodos(todos: List<InsightSnapshot.Todo>, nowMillis: Long, zone: ZoneId): List<InsightCandidate> =
        todos.mapNotNull { t ->
            if (t.isDone || t.isDeleted || t.isTaken || !t.isVisibleToUser) return@mapNotNull null
            val due = t.dueAtMillis ?: return@mapNotNull null
            val deadline = if (t.hasDueTime) {
                Instant.ofEpochMilli(due).atZone(zone)
            } else {
                Instant.ofEpochMilli(due).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone)
            }
            val left = deadline.toInstant().toEpochMilli() - nowMillis
            if (left <= 0 || left > TODO_HORIZON_MS) return@mapNotNull null
            InsightCandidate(
                type = InsightType.TODO_UNASSIGNED_DUE_SOON,
                entityId = t.id,
                priority = if (left <= 6 * 3600 * 1000L) InsightPriority.HIGH else InsightPriority.NORMAL,
                // Il giorno prima alla stessa ora, anche a cavallo del cambio d'ora.
                fireAtMillis = deadline.minusDays(1).toInstant().toEpochMilli(),
            )
        }

    /**
     * Cura attiva con una dose di oggi non segnata come presa un'ora dopo
     * l'orario previsto. Un insight per cura e per giorno.
     */
    fun missingDoses(snapshot: InsightSnapshot, nowMillis: Long, zone: ZoneId): List<InsightCandidate> {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val taken = takenKeys(snapshot)
        return snapshot.treatments.mapNotNull { t ->
            val firstMissing = uncoveredSlots(t, taken, today, zone).minOfOrNull { it.atMillis } ?: return@mapNotNull null
            InsightCandidate(
                type = InsightType.TREATMENT_DOSE_MISSING,
                entityId = "${t.id}@$today",
                priority = InsightPriority.HIGH,
                fireAtMillis = firstMissing + DOSE_GRACE_MS,
            )
        }
    }

    /** Chiavi `cura|giorno|dose` delle dosi segnate come prese. */
    fun takenKeys(snapshot: InsightSnapshot): Set<String> = snapshot.doseLogs
        .filter { it.taken && !it.isDeleted }
        .map { "${it.treatmentId}|${it.dayNumber}|${it.slotIndex}" }
        .toSet()

    /** Una dose di oggi non ancora segnata come presa, con l'orario previsto. */
    data class UncoveredSlot(val slot: Int, val time: String, val atMillis: Long)

    /**
     * Le dosi di oggi di una cura non ancora segnate come prese. Vuoto se la cura
     * oggi non ha dosi (finita, non ancora iniziata, giorno senza dose in una
     * cura a intervalli) o se sono tutte prese. Gemella di
     * `KBInsightEngine.uncoveredSlots` su iOS.
     */
    fun uncoveredSlots(
        t: InsightSnapshot.Treatment,
        taken: Set<String>,
        today: LocalDate,
        zone: ZoneId,
    ): List<UncoveredSlot> {
        if (!t.isActive || t.isDeleted) return emptyList()
        val start = Instant.ofEpochMilli(t.startDateMillis).atZone(zone).toLocalDate()
        val offset = ChronoUnit.DAYS.between(start, today).toInt()
        if (offset < 0) return emptyList()
        if (!t.isLongTerm && offset >= t.durationDays) return emptyList()
        t.endDateMillis?.let { end ->
            if (Instant.ofEpochMilli(end).atZone(zone).toLocalDate().isBefore(today)) return emptyList()
        }
        if (t.intervalBetweenDosesDays > 0 && offset % t.intervalBetweenDosesDays != 0) return emptyList()

        val dayNumber = offset + 1
        // Una cura a intervalli ha una sola dose nel giorno, al primo orario: gli
        // altri orari in elenco non sono dosi da registrare.
        val times = if (t.intervalBetweenDosesDays > 0) t.scheduleTimes.take(1) else t.scheduleTimes
        return times.mapIndexedNotNull { slot, hhmm ->
            if ("${t.id}|$dayNumber|$slot" in taken) null
            else time(hhmm, today, zone)?.let { UncoveredSlot(slot, hhmm, it) }
        }
    }

    private fun time(hhmm: String, day: LocalDate, zone: ZoneId): Long? {
        val parts = hhmm.split(":").mapNotNull { it.trim().toIntOrNull() }
        if (parts.size != 2 || parts[0] !in 0..23 || parts[1] !in 0..59) return null
        return day.atTime(LocalTime.of(parts[0], parts[1])).atZone(zone).toInstant().toEpochMilli()
    }
}
