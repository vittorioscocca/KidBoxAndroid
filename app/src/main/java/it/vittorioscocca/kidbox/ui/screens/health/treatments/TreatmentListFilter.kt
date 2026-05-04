package it.vittorioscocca.kidbox.ui.screens.health.treatments

import it.vittorioscocca.kidbox.domain.model.KBTreatment
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
/** Allineato a iOS [TreatmentTimeFilter] + [passesTimeFilter]. */
enum class TreatmentTimeFilter(val sheetLabel: String) {
    ALL("Tutte"),
    MONTHS_3("3 mesi"),
    MONTHS_6("6 mesi"),
    YEAR_LAST("Ultimo anno"),
    CUSTOM("Personalizzato"),
}

internal fun treatmentRefEpochMillis(t: KBTreatment): Long {
    val end = t.endDateEpochMillis
    return if (end != null) maxOf(t.startDateEpochMillis, end) else t.startDateEpochMillis
}

internal fun passesTreatmentTimeFilter(
    t: KBTreatment,
    filter: TreatmentTimeFilter,
    customStartMillis: Long,
    customEndMillis: Long,
): Boolean {
    val ref = treatmentRefEpochMillis(t)
    val zone = ZoneId.systemDefault()
    if (filter == TreatmentTimeFilter.ALL) return true

    if (filter == TreatmentTimeFilter.CUSTOM) {
        val startDay = Instant.ofEpochMilli(customStartMillis).atZone(zone).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()
        val endDay = Instant.ofEpochMilli(customEndMillis).atZone(zone).toLocalDate()
        val endExclusive = endDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return ref >= startDay && ref < endExclusive
    }

    val now = ZonedDateTime.now(zone)
    val cutoff = when (filter) {
        TreatmentTimeFilter.MONTHS_3 -> now.minusMonths(3)
        TreatmentTimeFilter.MONTHS_6 -> now.minusMonths(6)
        TreatmentTimeFilter.YEAR_LAST -> now.minusYears(1)
        else -> return true
    }.toInstant().toEpochMilli()
    return ref >= cutoff
}

internal fun defaultCustomFilterStartMillis(): Long =
    ZonedDateTime.now(ZoneId.systemDefault()).minusMonths(1).toInstant().toEpochMilli()
