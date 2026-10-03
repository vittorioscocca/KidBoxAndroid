package it.vittorioscocca.kidbox.ui.screens.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Il «+» degli eventi delle Notizie: cosa arriva al modulo «Nuovo evento». */
class NewsCalendarPrefillTest {

    private val zone = ZoneId.systemDefault()
    private fun midnight(day: String) = LocalDate.parse(day).atStartOfDay(zone).toInstant().toEpochMilli()

    private fun event(start: String, end: String? = null, summary: String? = "Stand e musica", place: String? = "Dugenta") =
        NewsEvent(
            title = "Sagra del Cinghiale",
            summary = summary,
            place = place,
            distanceKm = 25,
            startDate = start,
            endDate = end,
            free = null,
            source = "Virgilio Eventi",
            url = "https://www.virgilio.it/eventi",
        )

    @Test
    fun `piu giorni - tutto il giorno dal primo all'ultimo, fine inclusa`() {
        val p = event("2026-10-02", "2026-10-04").toCalendarPrefill()!!
        assertTrue(p.isAllDay)
        assertEquals(midnight("2026-10-02"), p.startMillis)
        assertEquals(midnight("2026-10-05") - 1, p.endMillis)
        assertEquals("leisure", p.category)
        assertEquals("Dugenta", p.location)
        assertEquals("Stand e musica\n\nhttps://www.virgilio.it/eventi", p.notes)
    }

    @Test
    fun `un giorno solo, anche con la fine mancante o prima dell'inizio`() {
        for (end in listOf(null, "2026-10-04", "2026-10-01", "non-una-data")) {
            val p = event("2026-10-04", end).toCalendarPrefill()!!
            assertEquals(midnight("2026-10-04"), p.startMillis)
            assertEquals(midnight("2026-10-05") - 1, p.endMillis)
        }
    }

    @Test
    fun `senza riassunto e luogo restano il link e nessun luogo`() {
        val p = event("2026-10-04", summary = "  ", place = " ").toCalendarPrefill()!!
        assertEquals("https://www.virgilio.it/eventi", p.notes)
        assertNull(p.location)
    }

    @Test
    fun `data d'inizio illeggibile - nessun modulo da aprire`() {
        assertNull(event("4 ottobre").toCalendarPrefill())
    }

    @Test
    fun `la spunta ignora maiuscole, accenti e spazi ma non il giorno`() {
        assertEquals(newsCalendarKey("Sagra del Fagiolo", "2026-10-03"), newsCalendarKey("  sagra del fagiolò ", "2026-10-03"))
        assertNotEquals(newsCalendarKey("Sagra del Fagiolo", "2026-10-03"), newsCalendarKey("Sagra del Fagiolo", "2026-10-04"))
    }
}
