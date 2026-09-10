package it.vittorioscocca.kidbox.ui.screens.todo

import androidx.lifecycle.ViewModel
import com.google.firebase.auth.FirebaseAuth
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
    private val auth: FirebaseAuth,
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
    suspend fun resolveLocation(todoId: String): TodoLocation? {
        // ⚠️ L'attesa è in DUE fasi, e non è pignoleria.
        //
        // Prima era una sola: 25 secondi a partire dal tap sulla notifica. Ma
        // a freddo il ripristino della sessione Firebase da solo ne prende una
        // ventina, e quel tempo veniva scalato dallo stesso budget: alla
        // sincronizzazione ne restavano una manciata. Di qui il «a volte il
        // to-do c'è, a volte la lista è vuota» — non un caso, una corsa persa
        // per pochi secondi.
        //
        // Ora il budget del to-do parte da quando la sync PUÒ consegnare,
        // cioè a sessione ripristinata. Il timeout misura la cosa giusta.
        val authed = withTimeoutOrNull(AUTH_TIMEOUT_MS) {
            while (auth.currentUser == null) delay(POLL_INTERVAL_MS)
            true
        }
        if (authed != true) return null

        return withTimeoutOrNull(RESOLVE_TIMEOUT_MS) {
            // Polling e non un Flow di Room: `observeById` in questa app non
            // emetteva nemmeno per righe già presenti (verificato sul device,
            // causa non chiarita), mentre la lettura singola funziona. Una
            // query su chiave primaria ogni mezzo secondo costa poco, e questo
            // percorso vive solo per la manciata di secondi dopo il tap.
            var found: TodoLocation? = null
            var seenWithoutListAt: Long? = null
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
                    // Il to-do c'è ma non ha lista: è un orfano (la sua lista è
                    // stata cancellata senza cancellare i to-do). Aspettare i 25
                    // secondi pieni non lo farà comparire — l'unica cosa che
                    // produce è uno spinner lungo e poi niente. Si concede solo
                    // una breve grazia perché la riga può essere una copia
                    // vecchia, che lo snapshot in arrivo sta per rilegare alla
                    // lista (vedi `resolveReferencedListId` in TodoRepository).
                    if (todo != null) {
                        val since = seenWithoutListAt ?: System.currentTimeMillis()
                            .also { seenWithoutListAt = it }
                        if (System.currentTimeMillis() - since >= ORPHAN_GRACE_MS) return@withTimeoutOrNull null
                    } else {
                        seenWithoutListAt = null
                    }
                    delay(POLL_INTERVAL_MS)
                }
            }
            found
        }
    }

    private companion object {
        /**
         * Budget per il ripristino della sessione Firebase, che a freddo può
         * prendere una ventina di secondi. Non è tempo sprecato dall'attesa del
         * to-do: è il tempo prima che l'attesa possa anche solo cominciare.
         */
        const val AUTH_TIMEOUT_MS = 45_000L

        /**
         * Budget per l'arrivo del singolo to-do, contato DA SESSIONE PRONTA.
         * Oltre questo limite si smette di aspettare e si resta sulla panoramica
         * To-Do: meglio lasciare l'utente libero che tenerlo appeso a un'attesa
         * che probabilmente non si sbloccherà.
         */
        const val RESOLVE_TIMEOUT_MS = 25_000L
        const val POLL_INTERVAL_MS = 500L

        /**
         * Quanto si aspetta un `listId` per un to-do che in Room c'è già.
         * Oltre questa soglia il to-do è orfano e si smette: l'attesa lunga
         * serve a chi non è ancora arrivato, non a chi è arrivato monco.
         */
        const val ORPHAN_GRACE_MS = 3_000L
    }
}
