package it.vittorioscocca.kidbox.ai

import java.time.LocalDate
import java.time.temporal.IsoFields
import java.util.Locale

/**
 * Ogni quanto gira l'analisi dei pattern salute. Preferenza del dispositivo,
 * come l'interruttore (parity iOS `HealthPatternFrequency`).
 */
enum class HealthPatternFrequency(val raw: String) {
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    ;

    /** Chiave del periodo che contiene [date]: «2026-10» o «2026-W43» (settimana ISO). */
    fun periodKey(date: LocalDate): String = when (this) {
        MONTHLY -> String.format(Locale.US, "%04d-%02d", date.year, date.monthValue)
        WEEKLY -> String.format(
            Locale.US,
            "%04d-W%02d",
            date.get(IsoFields.WEEK_BASED_YEAR),
            date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
        )
    }

    companion object {
        fun fromRaw(raw: String?): HealthPatternFrequency =
            entries.firstOrNull { it.raw == raw } ?: MONTHLY
    }
}
