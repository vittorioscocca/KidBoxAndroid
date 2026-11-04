package it.vittorioscocca.kidbox.ui.screens.news

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NewsFeedCacheTest {

    private val ready = mapOf("status" to "ready", "stale" to false)
    private val feed = mapOf(
        "status" to "ready",
        "pending" to emptyList<String>(),
        "dateKey" to "2026-11-04",
        "place" to mapOf("city" to "Benevento", "region" to null),
        "items" to listOf(mapOf("title" to "Bonus", "url" to "https://a.it", "keyDate" to null)),
        "events" to emptyList<Any>(),
        "editions" to mapOf("country" to ready, "local" to ready),
        "charge" to mapOf("units" to 6, "totalUnits" to 12),
    )

    private fun stored() = NewsFeedCache.encode("u", "f", "k", "2026-11-04", feed)

    @Test
    fun `dura il giorno solare e poi non vale piu`() {
        assertNotNull(NewsFeedCache.decode(stored(), "u", "f", "k", "2026-11-04"))
        assertNull(NewsFeedCache.decode(stored(), "u", "f", "k", "2026-11-05"))
    }

    @Test
    fun `non vale per un altro account, famiglia o scelta`() {
        assertNull(NewsFeedCache.decode(stored(), "altro", "f", "k", "2026-11-04"))
        assertNull(NewsFeedCache.decode(stored(), "u", "altra", "k", "2026-11-04"))
        assertNull(NewsFeedCache.decode(stored(), "u", "f", "altra", "2026-11-04"))
    }

    @Test
    fun `il contenuto torna leggibile dal parser`() {
        val back = NewsFeedCache.decode(stored(), "u", "f", "k", "2026-11-04")!!
        val parsed = NewsParser.feed(back)
        assertEquals("2026-11-04", parsed.dateKey)
        assertEquals("Benevento", parsed.placeCity)
        assertEquals(12, parsed.totalUnits)
    }

    @Test
    fun `si salva solo l'edizione definitiva`() {
        assertTrue(NewsFeedCache.isComplete(feed))
        assertFalse(NewsFeedCache.isComplete(feed + ("status" to "preparing")))
        assertFalse(NewsFeedCache.isComplete(feed + ("pending" to listOf("local"))))
        val stale = mapOf("country" to mapOf("status" to "ready", "stale" to true))
        assertFalse(NewsFeedCache.isComplete(feed + ("editions" to stale)))
        val noCity = mapOf("country" to ready, "local" to null)
        assertTrue(NewsFeedCache.isComplete(feed + ("editions" to noCity)))
    }

    @Test
    fun `un file rovinato non blocca niente`() {
        assertNull(NewsFeedCache.decode("non e' json", "u", "f", "k", "2026-11-04"))
    }
}
