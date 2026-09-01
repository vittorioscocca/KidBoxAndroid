package it.vittorioscocca.kidbox.feature.passwords.io

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PasswordsTxtIOTest {
    @Test
    fun parse_legacy_passbox_fixture() {
        val txt = fixture("fixtures/passbox_legacy_export.txt")
        val preview = PasswordsTxtParser.parseText(txt, currentUid = "u1")
        assertEquals(1, preview.total)
        assertTrue(preview.errors.isEmpty())
        assertEquals("Github", preview.records.first().title)
    }

    @Test
    fun parse_ios_fixture_interoperability() {
        val txt = fixture("fixtures/kidbox_ios_export.txt")
        val preview = PasswordsTxtParser.parseText(txt, currentUid = "u1")
        assertEquals(2, preview.total)
        assertEquals("Gmail", preview.records[0].title)
        assertEquals("mario@example.com", preview.records[0].username)
        assertEquals("riga1\nriga2", preview.records[0].note)
    }

    @Test
    fun parse_multiline_notes_and_private_skip() {
        // Nel file esportato l'a capo è la sequenza di due caratteri `\` + `n`:
        // dentro una raw string Kotlin si scrive esattamente così.
        val txt = """
            # KidBox Password Export v1
            ---
            Title: A
            Password: x
            Note: line1\nline2
            Visibility: family
            ---
            ---
            Title: B
            Password: y
            Visibility: private
            CreatedBy: other
            ---
        """.trimIndent()
        val preview = PasswordsTxtParser.parseText(txt, "u1")
        assertEquals(1, preview.total)
        assertEquals("line1\nline2", preview.records.first().note)
        assertEquals(1, preview.skippedOtherPrivate)
    }

    @Test
    fun parse_escaped_backslash_stays_literal() {
        // `\\n` è un backslash reale seguito da "n", non un a capo: una nota che
        // contiene un percorso Windows non deve spezzarsi in due righe.
        val txt = """
            # KidBox Password Export v1
            ---
            Title: A
            Password: x
            Note: C:\\nuovo\\cartella
            ---
        """.trimIndent()
        val preview = PasswordsTxtParser.parseText(txt, "u1")
        assertEquals(1, preview.total)
        assertEquals("C:\\nuovo\\cartella", preview.records.first().note)
    }

    @Test
    fun parse_missing_title_error() {
        val txt = """
            # KidBox Password Export v1
            ---
            Password: x
            ---
        """.trimIndent()
        val preview = PasswordsTxtParser.parseText(txt, "u1")
        assertEquals(0, preview.total)
        assertTrue(preview.errors.isNotEmpty())
    }

    @Test
    fun parse_legacy_multiple_records_single_text() {
        // Due record su una riga sola si separano senza incertezze: ogni
        // "Account:" apre davvero un record, quindi nessun avviso all'utente.
        val txt = "Account: A Group: G1 WebSite: https://a Username: u1 Password: p1 Note: n1 Account: B Group: G2 WebSite: https://b Username: u2 Password: p2 Note: n2"
        val preview = PasswordsTxtParser.parseText(txt, "u1")
        assertEquals(2, preview.total)
        assertEquals("A", preview.records[0].title)
        assertEquals("p2", preview.records[1].password)
        assertEquals(emptyList<Int>(), preview.legacyAmbiguousRecordIndices)
    }

    @Test
    fun parse_legacy_flags_account_hidden_in_note() {
        // Qui l'avviso serve davvero: "Account:" dentro una nota può far
        // tagliare il record nel punto sbagliato, e l'utente deve saperlo.
        val txt = "Account: A Group: G1 WebSite: https://a Username: u1 Password: p1 " +
            "Note: ricorda Account: vecchio del 2019"
        val preview = PasswordsTxtParser.parseText(txt, "u1")
        assertTrue(preview.legacyAmbiguousRecordIndices.isNotEmpty())
    }

    private fun fixture(path: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(path)) { "missing fixture: $path" }
            .bufferedReader()
            .use { it.readText() }
}
