package it.vittorioscocca.kidbox.ui.screens.chat

import it.vittorioscocca.kidbox.domain.save.SaveAction
import it.vittorioscocca.kidbox.domain.save.SaveClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SuggestedActionsTest {
    private val day = 24L * 3600 * 1000

    @Test fun scadenzaTrentaGiorni() {
        assertFalse(SuggestedActionsPrefs.isStale(0L, 30 * day))
        assertTrue(SuggestedActionsPrefs.isStale(0L, 30 * day + 1))
    }

    @Test fun nienteNotaSuUnSaluto() {
        assertTrue(SaveClassifier.classify("Ciao come stai?", forChips = true).actions.none { it is SaveAction.Note })
        // fuori dai banner la nota resta il catch-all
        assertTrue(SaveClassifier.classify("Ciao come stai?").actions.any { it is SaveAction.Note })
    }

    @Test fun indirizzoPerPrimo() {
        val found = SuggestedActionsFinder.find("m1", "Festa domani alle 17 in via Roma 12, 20121 Milano")
        assertEquals(SuggestedActionKind.MAP, found.first().kind)
        assertTrue(found.any { it.kind == SuggestedActionKind.CALENDAR })
        assertTrue(found.size <= 3)
    }
}
