package it.vittorioscocca.kidbox.domain.save

import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SaveClassifierTest {

    private val zone = ZoneId.of("Europe/Rome")
    // Sabato 24 ottobre 2026, 15:00
    private val now = LocalDateTime.of(2026, 10, 24, 15, 0)

    private fun classify(text: String) = SaveClassifier.classify(text, zone, now)
    private fun first(text: String) = classify(text).actions.first().destination

    @Test
    fun shortImperativeIsATodo() {
        assertEquals(SaveDestination.TODO, first("Ricordati di chiamare il pediatra"))
    }

    @Test
    fun multilineShortLinesIsAGroceryList() {
        assertEquals(SaveDestination.GROCERY, first("latte\npane\nuova\nmozzarella"))
    }

    @Test
    fun dateAndEventWordIsAnEvent() {
        val r = classify("Visita dal dentista venerdì alle 17:30")
        assertEquals(SaveDestination.EVENT, r.actions.first().destination)
        val expected = LocalDateTime.of(2026, 10, 30, 17, 30).atZone(zone).toInstant().toEpochMilli()
        assertEquals(expected, r.detectedDateMillis)
    }

    @Test
    fun longMultilineTextFallsBackToANote() {
        val text = "Ricetta della nonna\nIngredienti e istruzioni\n" + (1..6).joinToString("\n") { "passaggio $it del procedimento con tanto testo" }
        assertEquals(SaveDestination.NOTE, first(text))
    }

    @Test
    fun noteIsAlwaysOffered() {
        assertTrue(classify("ok va bene").actions.any { it.destination == SaveDestination.NOTE })
    }

    @Test
    fun emptyTextGivesNothing() {
        assertTrue(classify("   ").actions.isEmpty())
    }

    @Test
    fun relativeDays() {
        assertEquals(LocalDateTime.of(2026, 10, 25, 12, 0), SaveDateDetector.detect("domani", now))
        assertEquals(LocalDateTime.of(2026, 10, 26, 18, 0), SaveDateDetector.detect("dopodomani alle 18", now))
        assertEquals(LocalDateTime.of(2026, 10, 24, 20, 30), SaveDateDetector.detect("stasera alle 20.30", now))
    }

    @Test
    fun weekdayDetectedOnTheSameWeekdayMeansNextWeek() {
        // oggi è sabato
        assertEquals(LocalDateTime.of(2026, 10, 31, 12, 0), SaveDateDetector.detect("sabato", now))
        assertEquals(LocalDateTime.of(2026, 10, 25, 12, 0), SaveDateDetector.detect("domenica", now))
    }

    @Test
    fun monthNameAndNumericDates() {
        assertEquals(LocalDateTime.of(2026, 11, 3, 12, 0), SaveDateDetector.detect("il 3 novembre", now))
        // già passato quest'anno: si prende il prossimo
        assertEquals(LocalDateTime.of(2027, 3, 3, 12, 0), SaveDateDetector.detect("3 marzo", now))
        assertEquals(LocalDateTime.of(2026, 12, 15, 12, 0), SaveDateDetector.detect("15/12", now))
        assertEquals(LocalDateTime.of(2027, 1, 9, 12, 0), SaveDateDetector.detect("9-1-27", now))
    }

    @Test
    fun noDateGivesNull() {
        assertNull(SaveDateDetector.detect("compra il latte", now))
        assertNull(SaveDateDetector.detect("32/13", now))
    }

    @Test
    fun modelOrderPutsItsChoicesFirstAndKeepsTheRest() {
        val rules = classify("Ricordati di chiamare il pediatra")
        val out = SaveModelAnswer.reorder(rules, listOf(SaveDestination.NOTE))
        assertEquals(SaveDestination.NOTE, out.actions.first().destination)
        assertEquals(rules.actions.size, out.actions.size)
        assertTrue(out.isModelClassified)
    }
}
