package it.vittorioscocca.kidbox.domain.calendar

import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Le ripetizioni di un evento ricorrente. Gemello di `KBEventOccurrence.swift`.
 *
 * `recurrenceRaw` si scriveva dalla prima versione del calendario ma nessun
 * client lo espandeva: un evento «settimanale» compariva solo il primo giorno.
 * Le regole dei casi limite sono quelle di iOS:
 *
 * - ogni occorrenza si calcola dall'inizio originale (`inizio + n × passo`),
 *   mai dalla precedente, così il 31 non scivola al 30 e poi al 28 per sempre;
 * - l'orario è quello «da orologio» (`ZonedDateTime` gestisce l'ora legale);
 * - un mensile del 31 cade l'ultimo giorno dei mesi più corti, un annuale del
 *   29 febbraio il 28 negli anni non bisestili.
 *
 * La serie non ha fine né eccezioni: si modifica e si cancella tutta insieme.
 */
object EventRecurrence {

    /** Tetto di sicurezza: un giornaliero su due anni sono ~730 occorrenze. */
    private const val MAX_OCCURRENCES = 2_000

    fun isRecurring(recurrenceRaw: String?): Boolean = unitOf(recurrenceRaw) != null

    private fun unitOf(recurrenceRaw: String?): Pair<ChronoUnit, Long>? = when (recurrenceRaw) {
        "daily" -> ChronoUnit.DAYS to 1L
        "weekly" -> ChronoUnit.DAYS to 7L
        "monthly" -> ChronoUnit.MONTHS to 1L
        "yearly" -> ChronoUnit.YEARS to 1L
        else -> null
    }

    /**
     * Gli inizi delle occorrenze che toccano `[windowStart, windowEnd]`, in
     * millisecondi. Un evento singolo ne ha al massimo uno.
     */
    fun occurrenceStarts(
        startMillis: Long,
        durationMillis: Long,
        recurrenceRaw: String?,
        windowStart: Long,
        windowEnd: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): List<Long> {
        val duration = durationMillis.coerceAtLeast(0)
        val step = unitOf(recurrenceRaw)
        if (step == null) {
            return if (startMillis <= windowEnd && startMillis + duration >= windowStart) {
                listOf(startMillis)
            } else {
                emptyList()
            }
        }
        if (startMillis > windowEnd) return emptyList()

        val (unit, amount) = step
        val start = Instant.ofEpochMilli(startMillis).atZone(zone)
        // Salta direttamente vicino alla finestra invece di contare da un
        // evento magari di tre anni fa. Un passo di margine per le occorrenze
        // lunghe che iniziano prima e finiscono dentro.
        val from = Instant.ofEpochMilli(windowStart - duration).atZone(zone)
        val elapsed = unit.between(start, from).coerceAtLeast(0)
        val skipped = (elapsed / amount - 1).coerceAtLeast(0)

        val result = mutableListOf<Long>()
        var n = skipped
        while (n < skipped + MAX_OCCURRENCES) {
            val occStart = start.plus(n * amount, unit).toInstant().toEpochMilli()
            if (occStart > windowEnd) break
            if (occStart + duration >= windowStart) result += occStart
            n++
        }
        return result
    }

    /** Il primo inizio della serie strettamente dopo `afterMillis`, o `null`. */
    fun nextStartAfter(
        startMillis: Long,
        recurrenceRaw: String?,
        afterMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long? {
        if (!isRecurring(recurrenceRaw)) return startMillis.takeIf { it > afterMillis }
        // Due anni bastano a trovare la prossima di qualsiasi passo.
        val windowEnd = afterMillis + 800L * 24 * 60 * 60 * 1000
        return occurrenceStarts(startMillis, 0, recurrenceRaw, afterMillis, windowEnd, zone)
            .firstOrNull { it > afterMillis }
    }

    /**
     * La finestra in cui il calendario espande le ricorrenze: l'anno del
     * giorno guardato, allargato di qualche mese perché sfogliare dicembre
     * mostri già gennaio. Stessa finestra di iOS.
     */
    fun windowAround(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Pair<Long, Long> {
        val yearStart = date.withDayOfYear(1)
        val start = minOf(yearStart, date.minusMonths(3))
        val end = maxOf(yearStart.plusYears(1), date.plusMonths(4))
        return start.atStartOfDay(zone).toInstant().toEpochMilli() to
            end.atStartOfDay(zone).toInstant().toEpochMilli()
    }
}

/**
 * L'evento espanso nelle sue ripetizioni dentro la finestra: ogni occorrenza è
 * una **copia** con le date spostate e lo **stesso id** della serie. Serve
 * solo a disegnare: per modificare o cancellare si torna all'originale
 * (`seriesOf`), altrimenti si salverebbero le date di una ripetizione sulla
 * serie intera.
 */
fun KBCalendarEventEntity.occurrencesIn(
    windowStart: Long,
    windowEnd: Long,
    zone: ZoneId = ZoneId.systemDefault(),
): List<KBCalendarEventEntity> {
    val duration = (endDateEpochMillis - startDateEpochMillis).coerceAtLeast(0)
    val starts = EventRecurrence.occurrenceStarts(
        startMillis = startDateEpochMillis,
        durationMillis = duration,
        recurrenceRaw = recurrenceRaw,
        windowStart = windowStart,
        windowEnd = windowEnd,
        zone = zone,
    )
    return starts.map { s ->
        if (s == startDateEpochMillis) this
        else copy(startDateEpochMillis = s, endDateEpochMillis = s + duration)
    }
}

/** L'evento originale di un'occorrenza disegnata. */
fun List<KBCalendarEventEntity>.seriesOf(occurrence: KBCalendarEventEntity): KBCalendarEventEntity =
    firstOrNull { it.id == occurrence.id } ?: occurrence
