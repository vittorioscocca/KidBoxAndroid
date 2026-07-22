package it.vittorioscocca.kidbox.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/**
 * Permette a una singola schermata di disegnare sotto le barre di sistema.
 *
 * Sostituisce il vecchio meccanismo — le schermate che chiamavano a mano
 * `WindowCompat.setDecorFitsSystemWindows(window, false)` all'ingresso e `true`
 * all'uscita. Quel modello non funziona più per due motivi:
 *
 * 1. Con `targetSdk 36` il ripristino a `true` non riporta la finestra al
 *    comportamento di prima: l'edge-to-edge è imposto e non si disattiva.
 * 2. Il padding ora lo mette la radice della UI (`MainActivity`), quindi una
 *    finestra che tornasse a essere rientrata dal sistema si sommerebbe a
 *    quello — ed è esattamente il difetto che faceva comparire due fasce su
 *    tutte le schermate DOPO essere passati dalla mappa.
 *
 * Qui il proprietario dell'impostazione è uno solo: la radice. Le schermate non
 * toccano la finestra, dichiarano un'intenzione e la radice la rispetta.
 *
 * È un contatore e non un booleano perché due schermate a tutto schermo possono
 * sovrapporsi durante una transizione di navigazione: con un booleano, la
 * `onDispose` di quella uscente rimetterebbe il padding mentre quella entrante
 * lo vuole ancora via, con un lampeggio visibile.
 */
object EdgeToEdgeController {

    private var requests by mutableIntStateOf(0)

    /** `true` quando almeno una schermata sta chiedendo il pieno schermo. */
    val isFullBleed: Boolean
        get() = requests > 0

    /**
     * Chiede il pieno schermo finché il composable resta in composizione.
     * Da usare nelle schermate che devono arrivare sotto le barre (mappa).
     */
    @Composable
    fun RequestFullBleed() {
        DisposableEffect(Unit) {
            requests++
            onDispose { requests-- }
        }
    }
}
