package it.vittorioscocca.kidbox.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** La barra compatta scorrendo per leggere oltre, grande tornando indietro. */
class BottomBarScrollStateTest {

    private val threshold = 28f

    /** Uno scorrimento consumato da una lista: y negativo = dito verso l'alto, si legge oltre. */
    private fun BottomBarScrollState.scroll(dy: Float, steps: Int = 1, available: Float = 0f) {
        repeat(steps) {
            onPostScroll(Offset(0f, dy), Offset(0f, available), NestedScrollSource.UserInput)
        }
    }

    @Test
    fun `leggere oltre la rimpicciolisce, tornare indietro la riallarga`() {
        val state = BottomBarScrollState(threshold)
        state.scroll(-10f, steps = 3)
        assertTrue(state.isMinimized)
        state.scroll(10f, steps = 3)
        assertFalse(state.isMinimized)
    }

    @Test
    fun `un tremolio sotto la soglia non cambia niente`() {
        val state = BottomBarScrollState(threshold)
        repeat(10) {
            state.scroll(-20f)
            state.scroll(20f)
        }
        assertFalse(state.isMinimized)
    }

    @Test
    fun `una pagina che non scorre non la rimpicciolisce`() {
        val state = BottomBarScrollState(threshold)
        // Niente di consumato: tutto lo spostamento resta disponibile.
        state.scroll(0f, steps = 10, available = -15f)
        assertFalse(state.isMinimized)
    }

    @Test
    fun `un carosello orizzontale non conta`() {
        val state = BottomBarScrollState(threshold)
        repeat(10) { state.onPostScroll(Offset(-40f, 0f), Offset.Zero, NestedScrollSource.UserInput) }
        assertFalse(state.isMinimized)
    }

    @Test
    fun `in cima tirando ancora giù torna grande`() {
        val state = BottomBarScrollState(threshold)
        state.scroll(-40f)
        assertTrue(state.isMinimized)
        state.scroll(0f, available = 12f)
        assertFalse(state.isMinimized)
    }

    @Test
    fun `cambiare schermata la riporta grande`() {
        val state = BottomBarScrollState(threshold)
        state.scroll(-40f)
        state.reset()
        assertFalse(state.isMinimized)
    }
}
