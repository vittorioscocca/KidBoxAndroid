package it.vittorioscocca.kidbox.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** La parte pura dell'analisi dei log con Gemini Nano: righe, prompt, risposta. */
class CrashLogAnalysisTest {

    private val logs = """
        [2026-10-23 10:00:00] [INFO] [sync] [SyncCenter.kt:12] flushGlobal requested
        [2026-10-23 10:00:01] [DEBUG] [sync] [SyncCenter.kt:20] ops=0
        [2026-10-23 10:00:02] [WARNING] [auth] [Auth.kt:5] token in scadenza
        [2026-10-23 10:00:03] [ERROR] [data] [TodoRepository.kt:88] upsert fallito: PERMISSION_DENIED
        [2026-10-23 10:00:04] [CRASH] [ui] [NotesHomeScreen.kt:40] IllegalStateException
        [2026-10-23 10:00:05] [WARNING] [app] [CrashAnalyzer.kt:120] analisi on-device non riuscita
        [2026-10-23 10:00:06] [ERROR] [app] [CrashAnalyzer.kt:130] upload Firestore fallito
    """.trimIndent()

    @Test
    fun keepsOnlyErrorsAndCrashesButNotItsOwnLines() {
        val s = CrashLogAnalysis.significantLines(logs).lines()
        assertEquals(2, s.size)
        assertTrue(s[0].contains("TodoRepository.kt"))
        assertTrue(s[1].contains("[CRASH]"))
    }

    @Test
    fun onlyInfoLogsGiveNothing() {
        assertEquals("", CrashLogAnalysis.significantLines("[x] [INFO] [a] [B.kt:1] ok\n[x] [DEBUG] [a] [B.kt:2] ok"))
    }

    @Test
    fun promptKeepsTheMostRecentLinesWithinBudget() {
        val many = (1..500).joinToString("\n") { "[t] [ERROR] [data] [X.kt:$it] errore numero $it" }
        val prompt = CrashLogAnalysis.buildPrompt(many, 1024)
        assertTrue(prompt.contains("errore numero 500"))
        assertFalse(prompt.contains("errore numero 1\n"))
        assertTrue(CrashLogAnalysis.tail(many, 1024).toByteArray().size <= 1024)
    }

    @Test
    fun parsesJsonEvenWithTextAround() {
        val reply = """
            Ecco l'analisi:
            {"hasIssues": true, "issues": [{"type": "error", "severity": "high", "category": "data",
             "affectedModule": "TodoRepository.kt", "summary": "Salvataggio del to-do rifiutato",
             "detail": "PERMISSION_DENIED da Firestore", "firstOccurrence": "2026-10-23 10:00:03", "occurrences": 2}]}
            Fine.
        """.trimIndent()
        val r = CrashLogAnalysis.parse(reply)!!
        assertTrue(r.hasIssues)
        assertEquals("TodoRepository.kt", r.issues.single().affectedModule)
        assertEquals(2, r.issues.single().occurrences)
    }

    @Test
    fun noIssuesOrUnreadableReplies() {
        assertFalse(CrashLogAnalysis.parse("""{"hasIssues": false, "issues": []}""")!!.hasIssues)
        // hasIssues senza nessun problema descritto non conta come problema.
        assertFalse(CrashLogAnalysis.parse("""{"hasIssues": true, "issues": []}""")!!.hasIssues)
        assertNull(CrashLogAnalysis.parse("nessun JSON qui"))
        assertNull(CrashLogAnalysis.parse("{non è json}"))
    }
}
