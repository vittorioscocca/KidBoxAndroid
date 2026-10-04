package it.vittorioscocca.kidbox.ui.screens.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Le notizie salvate: id e formato condivisi con iOS, scadenza passata. */
class NewsSavedTest {

    private val item = NewsItem(
        category = "bonus",
        level = "country",
        title = "Bonus nido 2026",
        summary = "Fino a 3.600 euro.",
        action = null,
        keyDate = "2026-12-31",
        keyDateKind = "deadline",
        publishedAt = "2026-10-02",
        source = "INPS",
        url = "https://www.inps.it/bonus-nido",
    )

    @Test
    fun `l'id e lo SHA-256 dell'URL, lo stesso che calcola iOS`() {
        // Calcolato con CryptoKit (iOS) e con hashlib: stesso documento da tutti e due i telefoni.
        assertEquals("a24b84f5e98e83ece0cc7c768d0f84dc1d4cfd89a95d6afda757ee7bd9b15905", newsSavedId(item.url))
    }

    @Test
    fun `il documento torna uguale, senza i campi vuoti`() {
        val saved = NewsSavedItem(newsSavedId(item.url), item, "Italia", 1_791_100_000_000L)
        val encoded = NewsSavedFormat.encode(saved)
        assertFalse("action" in encoded)
        assertEquals(1_791_100_000_000L, encoded["savedAtMs"])
        assertEquals(saved, NewsSavedFormat.decode(saved.id, encoded))
    }

    @Test
    fun `senza URL o titolo il documento si scarta`() {
        assertNull(NewsSavedFormat.decode("x", mapOf("title" to "Solo titolo")))
        assertNull(NewsSavedFormat.decode("x", mapOf("url" to "https://a.it", "title" to "")))
        assertNull(NewsSavedFormat.decode("x", null))
    }

    @Test
    fun `una scadenza e passata solo dal giorno dopo`() {
        val today = LocalDate.parse("2026-10-04")
        assertTrue(NewsDates.isPast("2026-10-03", today))
        assertFalse(NewsDates.isPast("2026-10-04", today))
        assertFalse(NewsDates.isPast(null, today))
        assertFalse(NewsDates.isPast("non-una-data", today))
    }
}
