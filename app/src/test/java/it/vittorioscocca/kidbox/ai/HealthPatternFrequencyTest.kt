package it.vittorioscocca.kidbox.ai

import it.vittorioscocca.kidbox.ui.screens.ai.planning.HealthPatternAnalyzerService
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Test

/** Chiavi di periodo e prossimo orario della notifica, uguali a iOS. */
class HealthPatternFrequencyTest {

    private val rome = ZoneId.of("Europe/Rome")

    private fun at(epochMillis: Long): LocalDateTime =
        java.time.Instant.ofEpochMilli(epochMillis).atZone(rome).toLocalDateTime()

    @Test
    fun monthlyKeyMatchesLegacyFormat() {
        assertEquals("2026-10", HealthPatternFrequency.MONTHLY.periodKey(LocalDate.of(2026, 10, 23)))
        assertEquals("2026-01", HealthPatternFrequency.MONTHLY.periodKey(LocalDate.of(2026, 1, 1)))
    }

    @Test
    fun weeklyKeyIsIsoWeek() {
        assertEquals("2026-W43", HealthPatternFrequency.WEEKLY.periodKey(LocalDate.of(2026, 10, 23)))
        assertEquals(
            HealthPatternFrequency.WEEKLY.periodKey(LocalDate.of(2026, 10, 19)),
            HealthPatternFrequency.WEEKLY.periodKey(LocalDate.of(2026, 10, 25)),
        )
        // 1 gennaio 2027 è venerdì: settimana 53 del 2026.
        assertEquals("2026-W53", HealthPatternFrequency.WEEKLY.periodKey(LocalDate.of(2027, 1, 1)))
    }

    @Test
    fun unknownRawFallsBackToMonthly() {
        assertEquals(HealthPatternFrequency.MONTHLY, HealthPatternFrequency.fromRaw(null))
        assertEquals(HealthPatternFrequency.MONTHLY, HealthPatternFrequency.fromRaw("?"))
        assertEquals(HealthPatternFrequency.WEEKLY, HealthPatternFrequency.fromRaw("weekly"))
    }

    @Test
    fun monthlyTriggerIsFirstOfNextMonthAtNine() {
        val now = LocalDateTime.of(2026, 10, 23, 12, 0)
        val next = at(HealthPatternAnalyzerService.nextTrigger9am(HealthPatternFrequency.MONTHLY, now, rome))
        assertEquals(LocalDateTime.of(2026, 11, 1, 9, 0), next)
    }

    @Test
    fun weeklyTriggerIsNextMondayAtNine() {
        val friday = LocalDateTime.of(2026, 10, 23, 12, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 26, 9, 0),
            at(HealthPatternAnalyzerService.nextTrigger9am(HealthPatternFrequency.WEEKLY, friday, rome)),
        )
        // Lunedì prima delle 9: oggi stesso.
        val mondayEarly = LocalDateTime.of(2026, 10, 26, 8, 0)
        assertEquals(
            LocalDateTime.of(2026, 10, 26, 9, 0),
            at(HealthPatternAnalyzerService.nextTrigger9am(HealthPatternFrequency.WEEKLY, mondayEarly, rome)),
        )
        // Lunedì dopo le 9: il lunedì dopo.
        val mondayLate = LocalDateTime.of(2026, 10, 26, 10, 0)
        assertEquals(
            LocalDateTime.of(2026, 11, 2, 9, 0),
            at(HealthPatternAnalyzerService.nextTrigger9am(HealthPatternFrequency.WEEKLY, mondayLate, rome)),
        )
    }
}
