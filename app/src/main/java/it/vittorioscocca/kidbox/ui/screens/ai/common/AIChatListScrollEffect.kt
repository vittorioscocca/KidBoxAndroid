package it.vittorioscocca.kidbox.ui.screens.ai.common

import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.IntState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.yield

private const val STREAM_SCROLL_MIN_INTERVAL_MS = 120L

/** Tolleranza in px per considerare la lista "in fondo". */
private const val BOTTOM_TOLERANCE_PX = 48

/**
 * Scroll stabile per chat AI: evita salti durante il typewriter (throttle ~120ms).
 *
 * La lista segue il fondo solo se l'utente ci sta già. Prima ogni tick del typewriter
 * (ogni 120 ms per tutta la durata della risposta) e ogni variazione del numero di
 * messaggi riportavano la lista in fondo in modo incondizionato: chi risaliva per
 * rileggere, o scorreva mentre scriveva la domanda successiva, veniva strappato giù.
 * Ora il "segui il fondo" si spegne quando l'utente si ferma lontano dal fondo e si
 * riaccende quando ci torna, o quando invia un messaggio.
 *
 * @param reverseLayout se true, indice 0 = messaggio più recente (bottom visivo).
 */
@Composable
fun AIChatListScrollEffect(
    listState: LazyListState,
    messageCount: Int,
    isLoading: Boolean,
    streamingMessageId: String?,
    streamScrollTick: IntState,
    reverseLayout: Boolean,
) {
    var lastStreamScrollAt by remember { mutableLongStateOf(0L) }
    var followBottom by remember { mutableStateOf(true) }
    var lastMessageCount by remember { mutableIntStateOf(0) }

    fun isAtBottom(): Boolean = if (reverseLayout) {
        listState.firstVisibleItemIndex == 0 &&
            listState.firstVisibleItemScrollOffset <= BOTTOM_TOLERANCE_PX
    } else {
        !listState.canScrollForward
    }

    suspend fun scrollToBottom(animated: Boolean) {
        // Mai contro un gesto in corso: vincerebbe comunque il dito, ma a scatti.
        if (listState.isScrollInProgress && !animated) return
        val targetIndex = if (reverseLayout) 0 else {
            val total = listState.layoutInfo.totalItemsCount
            (total - 1).coerceAtLeast(0)
        }
        if (animated) {
            listState.animateScrollToItem(targetIndex)
        } else {
            listState.scrollToItem(targetIndex)
            // In lista normale l'ultimo item può essere più alto del viewport: allineato
            // il suo inizio, si scorre di quanto sporge sotto. NON scrollToItem(i,
            // Int.MAX_VALUE): LazyListMeasure calcola `maxOffset - (-offset)` e trabocca.
            if (!reverseLayout) {
                val info = listState.layoutInfo
                val last = info.visibleItemsInfo.lastOrNull()
                if (last != null && last.index == targetIndex) {
                    val overflow = last.offset + last.size - info.viewportEndOffset
                    if (overflow > 0) listState.scrollBy(overflow.toFloat())
                }
            }
        }
    }

    // Quando lo scroll si ferma (dito o programmatico) si decide se continuare a seguire.
    LaunchedEffect(listState, reverseLayout) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling) followBottom = isAtBottom()
        }
    }

    LaunchedEffect(messageCount) {
        val previous = lastMessageCount
        lastMessageCount = messageCount
        if (messageCount == 0) return@LaunchedEffect
        // Primo caricamento: sempre in fondo. Dopo, solo se si stava seguendo.
        if (previous == 0 || followBottom) scrollToBottom(animated = false)
    }

    LaunchedEffect(isLoading) {
        // isLoading parte quando l'utente invia: è l'unico momento in cui riportarlo giù
        // anche se stava leggendo più in alto.
        if (isLoading) {
            followBottom = true
            scrollToBottom(animated = true)
        }
    }

    LaunchedEffect(streamingMessageId) {
        if (streamingMessageId != null && followBottom) scrollToBottom(animated = false)
    }

    // Il tick si osserva qui dentro e non nel corpo della schermata: letto lì, ogni passo
    // del typewriter ricomponeva l'intera schermata (lista e markdown di ogni messaggio).
    LaunchedEffect(streamScrollTick) {
        snapshotFlow { streamScrollTick.intValue }.collect { tick ->
            if (tick == 0 || !followBottom) return@collect
            val now = System.currentTimeMillis()
            if (now - lastStreamScrollAt < STREAM_SCROLL_MIN_INTERVAL_MS) return@collect
            lastStreamScrollAt = now
            yield()
            scrollToBottom(animated = false)
        }
    }
}

/** Incrementato dal typewriter tick; passato a [AIChatListScrollEffect]. */
@Composable
fun rememberStreamScrollTick(): Pair<IntState, () -> Unit> {
    val tick = remember { mutableIntStateOf(0) }
    val onTick = remember(tick) { { tick.intValue += 1 } }
    return tick to onTick
}
