package it.vittorioscocca.kidbox.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Segnaposto delle push con il testo cifrato (cifratura delle sezioni §5). */
class SecurePushTextTest {

    private val decrypt: (String, String) -> String? = { blob, familyId ->
        if (familyId == "fam" && blob.startsWith("ENC:")) blob.removePrefix("ENC:") else null
    }

    private fun push(vararg extra: Pair<String, String>) = mapOf(
        "type" to "new_calendar_event",
        "familyId" to "fam",
        "title" to "Nuovo evento",
        "body" to "Anna ha aggiunto: Impegno",
        "secure" to "ENC:{\"title\":\"Recita\",\"notes\":\"porta i fiori\"}",
        "secureColl" to "calendarEvents",
        "secureFamilyId" to "fam",
        "titleTpl" to "Nuovo evento",
        "bodyTpl" to "Anna ha aggiunto: {title} — 12 novembre",
    ) + extra

    @Test
    fun riempieISegnaposto() {
        assertEquals("Pane · 3,00 €", SecurePushText.fill("{title} · 3,00 €", mapOf("title" to "Pane")))
        assertEquals("Pane — Spesa", SecurePushText.fill("{title} — {list}", mapOf("title" to "Pane", "list" to "Spesa")))
        assertEquals("Senza segnaposto", SecurePushText.fill("Senza segnaposto", emptyMap()))
    }

    @Test
    fun segnapostoSenzaValoreDaNull() {
        assertNull(SecurePushText.fill("{title} — {list}", mapOf("title" to "Pane")))
        assertNull(SecurePushText.fill("{title}", mapOf("title" to "  ")))
    }

    @Test
    fun pushDecifrata() {
        val out = SecurePushText.resolve(push(), decrypt)
        assertEquals("Nuovo evento" to "Anna ha aggiunto: Recita — 12 novembre", out)
    }

    @Test
    fun chiaveMancanteOBlobAssenteUsaIlGenerico() {
        assertNull(SecurePushText.resolve(push("secureFamilyId" to "altra"), decrypt))
        assertNull(SecurePushText.resolve(push() - "secure", decrypt))
        assertNull(SecurePushText.resolve(push("secure" to "ENC:non json"), decrypt))
        assertNull(SecurePushText.resolve(push("secure" to "rotto"), { _, _ -> error("boom") }))
    }

    @Test
    fun segnapostoSenzaValoreUsaIlGenerico() {
        assertNull(SecurePushText.resolve(push("bodyTpl" to "{title} — {list}"), decrypt))
    }

    @Test
    fun pushNormaleNonToccata() {
        assertNull(SecurePushText.resolve(mapOf("type" to "new_grocery_item", "title" to "x", "body" to "y"), decrypt))
    }
}
