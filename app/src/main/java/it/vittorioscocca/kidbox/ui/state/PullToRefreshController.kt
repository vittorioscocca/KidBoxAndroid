package it.vittorioscocca.kidbox.ui.state

import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "PullToRefresh"

/**
 * Stato del pull-to-refresh, condiviso da tutte le sezioni con una lista che
 * arriva dal remoto.
 *
 * Non esiste una "fetch" separata da invocare: la sorgente di verità sono i
 * listener Firestore, quindi il force refresh è staccarli e riagganciarli
 * (`awaitForceRestartRealtime` sui repository, `restart` sui sync center di
 * Salute). Il resto è la meccanica dello spinner, che è identica ovunque e
 * quindi vive qui invece che copiata in venti ViewModel.
 */
class PullToRefreshController(private val scope: CoroutineScope) {

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    /**
     * Esegue [block] mostrando lo spinner. Un secondo pull mentre il primo è in
     * corso viene ignorato.
     *
     * Lo spinner resta visibile almeno [MIN_SPINNER_MILLIS]: riagganciare un
     * listener ritorna quasi subito, mentre lo snapshot arriva un attimo dopo,
     * e senza questa attesa il gesto sembrerebbe non aver fatto nulla.
     */
    fun refresh(block: suspend () -> Unit) {
        if (_isRefreshing.value) return
        scope.launch {
            _isRefreshing.value = true
            val startedAt = System.currentTimeMillis()
            try {
                block()
            } catch (e: Exception) {
                KBLog.ui.warning("refresh fallito: ${e.message}", TAG)
            } finally {
                val elapsed = System.currentTimeMillis() - startedAt
                if (elapsed < MIN_SPINNER_MILLIS) delay(MIN_SPINNER_MILLIS - elapsed)
                _isRefreshing.value = false
            }
        }
    }

    private companion object {
        const val MIN_SPINNER_MILLIS = 900L
    }
}
