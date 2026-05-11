package it.vittorioscocca.kidbox.data.life

import it.vittorioscocca.kidbox.data.local.entity.HousePaymentEntity
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit

/**
 * Scadenze e prossimo promemoria (3 giorni prima, ore 9 Europe/Rome), allineato alla logica iOS.
 */
object HousePaymentDeadlineCalculator {

    private val zone: ZoneId = ZoneId.of("Europe/Rome")

    fun earliestDisplayDeadlineMillis(entity: HousePaymentEntity, fromMillis: Long = System.currentTimeMillis()): Long? {
        val candidates = mutableListOf<Long>()
        entity.giornoDiScadenzaMensile?.let { day ->
            nextMonthlyDeadlineStartMillis(day, fromMillis)?.let { candidates.add(it) }
        }
        entity.dataScadenza?.let { ref ->
            nextAnnualDeadlineStartMillis(ref, fromMillis)?.let { candidates.add(it) }
        }
        entity.dataScadenzaContratto?.let { candidates.add(startOfDayMillis(it)) }
        return candidates.minOrNull()
    }

    fun nextReminderFireMillis(
        entity: HousePaymentEntity,
        strictlyAfterMillis: Long = System.currentTimeMillis(),
    ): Long? {
        val candidates = mutableListOf<Long>()
        val anchorMillis = startOfDayMillis(strictlyAfterMillis)
        entity.giornoDiScadenzaMensile?.let { day ->
            for (off in 0 until 48) {
                val deadline = nextMonthlyDeadlineStartMillis(day, anchorMillis, monthOffset = off) ?: continue
                reminderFireMillis(deadline)?.let { if (it > strictlyAfterMillis) candidates.add(it) }
                if (candidates.isNotEmpty()) break
            }
        }
        entity.dataScadenza?.let { ref ->
            var searchFrom = anchorMillis
            repeat(6) {
                val deadline = nextAnnualDeadlineStartMillis(ref, searchFrom) ?: return@let
                val fire = reminderFireMillis(deadline) ?: return@let
                if (fire > strictlyAfterMillis) {
                    candidates.add(fire)
                    return@let
                }
                searchFrom = deadline + 86_400_000L
            }
        }
        entity.dataScadenzaContratto?.let { end ->
            val deadline = startOfDayMillis(end)
            reminderFireMillis(deadline)?.let { if (it > strictlyAfterMillis) candidates.add(it) }
        }
        return candidates.minOrNull()
    }

    fun daysRemainingTo(deadlineMillis: Long?, fromMillis: Long = System.currentTimeMillis()): Int? {
        if (deadlineMillis == null) return null
        val today = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val d = Instant.ofEpochMilli(deadlineMillis).atZone(zone).toLocalDate()
        return ChronoUnit.DAYS.between(today, d).toInt()
    }

    fun nextMonthlyDeadlineOnly(entity: HousePaymentEntity, fromMillis: Long = System.currentTimeMillis()): Long? {
        val day = entity.giornoDiScadenzaMensile ?: return null
        val anchor = startOfDayMillis(fromMillis)
        for (off in 0 until 48) {
            nextMonthlyDeadlineStartMillis(day, anchor, off)?.let { return it }
        }
        return null
    }

    fun nextAnnualDeadlineOnly(entity: HousePaymentEntity, fromMillis: Long = System.currentTimeMillis()): Long? {
        val ref = entity.dataScadenza ?: return null
        return nextAnnualDeadlineStartMillis(ref, startOfDayMillis(fromMillis))
    }

    fun urgencyRank(entity: HousePaymentEntity): Int {
        val days = daysRemainingTo(earliestDisplayDeadlineMillis(entity)) ?: return 3
        return when {
            days < 30 -> 0
            days < 60 -> 1
            else -> 2
        }
    }

    private fun reminderFireMillis(deadlineStartOfDayMillis: Long): Long? {
        val day = Instant.ofEpochMilli(deadlineStartOfDayMillis).atZone(zone).toLocalDate()
        val threeBefore = day.minusDays(3)
        val zdt: ZonedDateTime = threeBefore.atTime(9, 0).atZone(zone)
        return zdt.toInstant().toEpochMilli()
    }

    internal fun startOfDayMillis(epochMillis: Long): Long {
        val d = Instant.ofEpochMilli(epochMillis).atZone(zone).toLocalDate()
        return d.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    /** Prossimo giorno di scadenza mensile (inizio giorno, Roma) da [fromMillis] in poi. */
    private fun nextMonthlyDeadlineStartMillis(day: Int, fromMillis: Long, monthOffset: Int = 0): Long? {
        val fromDay = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val monthStart = fromDay.withDayOfMonth(1).plusMonths(monthOffset.toLong())
        val dom = day.coerceIn(1, monthStart.lengthOfMonth())
        val candidate = monthStart.withDayOfMonth(dom)
        val candidateStart = candidate.atStartOfDay(zone).toInstant().toEpochMilli()
        val fromStart = fromDay.atStartOfDay(zone).toInstant().toEpochMilli()
        return if (candidateStart >= fromStart) candidateStart else null
    }

    private fun nextMonthlyDeadlineStartMillis(day: Int, fromMillis: Long): Long? {
        for (off in 0 until 48) {
            val d = nextMonthlyDeadlineStartMillis(day, fromMillis, off) ?: continue
            return d
        }
        return null
    }

    private fun nextAnnualDeadlineStartMillis(referenceMillis: Long, fromMillis: Long): Long? {
        val ref = Instant.ofEpochMilli(referenceMillis).atZone(zone).toLocalDate()
        val fromDay = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
        val fromStart = fromDay.atStartOfDay(zone).toInstant().toEpochMilli()
        var y = fromDay.year
        repeat(6) {
            val month = LocalDate.of(y, ref.monthValue, 1)
            val dom = ref.dayOfMonth.coerceIn(1, month.lengthOfMonth())
            val candidate = month.withDayOfMonth(dom)
            val candidateStart = candidate.atStartOfDay(zone).toInstant().toEpochMilli()
            if (candidateStart >= fromStart) return candidateStart
            y++
        }
        return null
    }
}
