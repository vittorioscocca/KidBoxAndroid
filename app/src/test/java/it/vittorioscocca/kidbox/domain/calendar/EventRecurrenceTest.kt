package it.vittorioscocca.kidbox.domain.calendar

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Stessi casi limite provati su iOS per `KBEventOccurrence`. */
class EventRecurrenceTest {

    private val zone = ZoneId.of("Europe/Rome")
    private val hour = 60 * 60 * 1000L
    private val day = 24 * hour

    private fun ms(s: String): Long = LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli()
    private fun fmt(ms: Long): String =
        java.time.Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime().toString()

    @Test
    fun monthlyOn31stFallsOnLastDayAndComesBack() {
        val starts = EventRecurrence.occurrenceStarts(
            ms("2025-10-31T09:00"), hour, "monthly",
            ms("2026-01-01T00:00"), ms("2026-12-31T23:59"), zone,
        ).map(::fmt)
        assertEquals(12, starts.size)
        assertEquals("2026-02-28T09:00", starts[1])
        assertEquals("2026-03-31T09:00", starts[2])
        assertEquals("2026-04-30T09:00", starts[3])
    }

    @Test
    fun yearlyOnLeapDay() {
        val starts = EventRecurrence.occurrenceStarts(
            ms("2024-02-29T00:00"), 0, "yearly",
            ms("2024-01-01T00:00"), ms("2029-01-01T00:00"), zone,
        ).map(::fmt)
        assertEquals(
            listOf("2024-02-29T00:00", "2025-02-28T00:00", "2026-02-28T00:00", "2027-02-28T00:00", "2028-02-29T00:00"),
            starts,
        )
    }

    @Test
    fun weeklyKeepsWallClockAcrossDaylightSaving() {
        val starts = EventRecurrence.occurrenceStarts(
            ms("2026-03-16T18:00"), hour, "weekly",
            ms("2026-03-20T00:00"), ms("2026-04-10T00:00"), zone,
        ).map(::fmt)
        assertEquals(listOf("2026-03-23T18:00", "2026-03-30T18:00", "2026-04-06T18:00"), starts)
    }

    @Test
    fun oldDailySeriesJumpsToWindow() {
        val starts = EventRecurrence.occurrenceStarts(
            ms("2020-05-05T07:30"), 30 * 60 * 1000L, "daily",
            ms("2026-03-01T00:00"), ms("2026-03-04T00:00"), zone,
        ).map(::fmt)
        assertEquals(listOf("2026-03-01T07:30", "2026-03-02T07:30", "2026-03-03T07:30"), starts)
    }

    @Test
    fun longOccurrenceStartingBeforeWindowIsIncluded() {
        val starts = EventRecurrence.occurrenceStarts(
            ms("2026-01-02T10:00"), 3 * day, "weekly",
            ms("2026-01-11T00:00"), ms("2026-01-12T00:00"), zone,
        ).map(::fmt)
        assertEquals(listOf("2026-01-09T10:00"), starts)
    }

    @Test
    fun singleEventAndFutureSeries() {
        assertTrue(
            EventRecurrence.occurrenceStarts(
                ms("2027-06-01T10:00"), hour, "weekly",
                ms("2026-01-01T00:00"), ms("2026-12-31T00:00"), zone,
            ).isEmpty(),
        )
        assertEquals(
            listOf(ms("2026-05-01T10:00")),
            EventRecurrence.occurrenceStarts(
                ms("2026-05-01T10:00"), hour, "none",
                ms("2026-01-01T00:00"), ms("2026-12-31T00:00"), zone,
            ),
        )
    }

    @Test
    fun nextStartAfterSkipsPastOccurrences() {
        assertEquals(
            ms("2026-09-28T18:00"),
            EventRecurrence.nextStartAfter(ms("2025-01-06T18:00"), "weekly", ms("2026-09-26T12:00"), zone),
        )
        assertNull(EventRecurrence.nextStartAfter(ms("2025-01-06T18:00"), "none", ms("2026-09-26T12:00"), zone))
    }
}
