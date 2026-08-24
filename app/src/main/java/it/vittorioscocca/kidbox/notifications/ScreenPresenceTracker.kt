package it.vittorioscocca.kidbox.notifications

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

/**
 * Sezioni dell'app che possono "assorbire" una notifica.
 *
 * Ogni voce corrisponde alla schermata in cui l'utente vedrebbe comunque il
 * contenuto appena creato, e per cui quindi la notifica sarebbe rumore.
 */
enum class AppSection {
    CHAT,
    TODO_LIST,
    SHOPPING_LIST,
    CALENDAR,
    NOTES,
    EXPENSES,
    DOCUMENTS,
    WALLET,
    FAMILY_LOCATION,
}

/**
 * Tiene traccia della sezione che l'utente sta guardando in questo momento.
 *
 * Regola generale dell'app: **se sei già dentro la sezione in cui è appena
 * stato creato qualcosa, quella notifica non deve comparire** — la vedresti
 * arrivare da sola nella lista sotto gli occhi. Vale per la chat come per i
 * to-do, la spesa, le note e tutto il resto.
 *
 * Un `object` statico basta perché il messaging service gira nello stesso
 * processo della UI. È `@Volatile` perché a scrivere è il main thread (la
 * composizione) e a leggere è il thread del servizio FCM.
 *
 * La presenza è legata al ciclo di vita e non alla sola composizione: con la
 * schermata aperta ma l'app in background (schermo spento, altra app davanti)
 * la notifica DEVE tornare a comparire.
 */
object ScreenPresenceTracker {

    /**
     * @property scopeId identifica *quale* elemento della sezione si sta
     *     guardando, quando la sezione ha più contenitori: per i to-do è il
     *     `listId`, perché essere in una lista non deve zittire le notifiche
     *     di un'altra. `null` quando la sezione è unica per famiglia.
     */
    data class Presence(
        val section: AppSection,
        val familyId: String,
        val scopeId: String? = null,
    )

    @Volatile
    private var current: Presence? = null

    fun enter(presence: Presence) {
        if (presence.familyId.isNotBlank()) current = presence
    }

    /**
     * Il confronto evita che una schermata smontata in ritardo cancelli la
     * presenza di un'altra aperta nel frattempo.
     */
    fun leave(presence: Presence) {
        if (current == presence) current = null
    }

    /**
     * True se la notifica descritta dai parametri riguarda proprio ciò che
     * l'utente sta già guardando.
     *
     * @param scopeId lo scope indicato dalla notifica (es. il `listId` del
     *     to-do). Se la notifica non lo porta, si preferisce MOSTRARE la
     *     notifica: sopprimere senza sapere quale contenitore sia rischierebbe
     *     di nascondere un avviso relativo a un'altra lista.
     */
    fun isViewing(
        section: AppSection,
        familyId: String?,
        scopeId: String? = null,
        scoped: Boolean = false,
    ): Boolean {
        val presence = current ?: return false
        if (familyId.isNullOrBlank() || familyId != presence.familyId) return false
        if (presence.section != section) return false
        if (!scoped) return true
        if (scopeId.isNullOrBlank() || presence.scopeId.isNullOrBlank()) return false
        return scopeId == presence.scopeId
    }
}

/**
 * Dichiara che questa schermata è la sezione [section] della famiglia
 * [familyId], finché resta aperta e in primo piano.
 *
 * Da chiamare una volta nel corpo della schermata: registra e deregistra da
 * sé, seguendo il ciclo di vita.
 */
@Composable
fun TrackSectionPresence(
    section: AppSection,
    familyId: String,
    scopeId: String? = null,
) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, section, familyId, scopeId) {
        val presence = ScreenPresenceTracker.Presence(section, familyId, scopeId)
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> ScreenPresenceTracker.enter(presence)
                Lifecycle.Event.ON_PAUSE -> ScreenPresenceTracker.leave(presence)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ScreenPresenceTracker.leave(presence)
        }
    }
}
