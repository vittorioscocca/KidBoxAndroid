package it.vittorioscocca.kidbox.ui.screens.todo

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

/**
 * Ricava da un `todoId` la lista che lo contiene, per il deep link da notifica.
 *
 * Serve perché il payload del server manda `childId: after.childId || ""` e
 * `listId: after.listId || ""`, e in questa app `childId` è vuoto per
 * costruzione (i to-do e le liste di famiglia hanno childId = ""): la rotta
 * `TodoList` non è quindi ricostruibile dalla sola notifica.
 *
 * Si ASPETTA il to-do invece di leggerlo una volta sola: ad app killata la
 * notifica viene toccata molto prima che la sincronizzazione abbia portato il
 * to-do in Room — il login da solo richiede una ventina di secondi — quindi
 * una lettura immediata fallirebbe sempre e si resterebbe sulla panoramica
 * To-Do. Il `Flow` di Room emette appena la sync inserisce il record.
 */
@HiltViewModel
class TodoDeepLinkResolverViewModel @Inject constructor(
    private val todoItemDao: KBTodoItemDao,
) : ViewModel() {

    data class TodoLocation(
        val familyId: String,
        val childId: String,
        val listId: String,
    )

    /**
     * @return la lista che contiene il to-do, oppure `null` se entro
     *     [RESOLVE_TIMEOUT_MS] non è arrivato (to-do cancellato, non visibile,
     *     o sincronizzazione mai completata).
     */
    suspend fun resolveLocation(todoId: String): TodoLocation? =
        withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            // Polling e non un Flow di Room: `observeById` in questa app non
            // emetteva nemmeno per righe già presenti (verificato sul device,
            // causa non chiarita), mentre la lettura singola funziona. Una
            // query su chiave primaria ogni mezzo secondo costa poco, e questo
            // percorso vive solo per la manciata di secondi dopo il tap.
            var found: TodoLocation? = null
            while (found == null) {
                val todo = todoItemDao.getById(todoId)
                // `childId` vuoto va benissimo — è il valore normale, ed è quello
                // su cui filtrano le query della lista. Serve solo `listId`.
                val listId = todo?.listId
                if (todo != null && !listId.isNullOrBlank()) {
                    found = TodoLocation(
                        familyId = todo.familyId,
                        childId = todo.childId,
                        listId = listId,
                    )
                } else {
                    delay(POLL_INTERVAL_MS)
                }
            }
            found
        }

    private companion object {
        /**
         * A freddo il grosso del tempo se ne va nel login e nel primo giro di
         * sincronizzazione, non nell'attesa del singolo to-do. Oltre questo
         * limite si smette di aspettare e si resta sulla panoramica To-Do:
         * meglio lasciare l'utente libero che tenerlo appeso a un'attesa che
         * probabilmente non si sbloccherà.
         */
        const val RESOLVE_TIMEOUT_MS = 25_000L
        const val POLL_INTERVAL_MS = 500L
    }
}
