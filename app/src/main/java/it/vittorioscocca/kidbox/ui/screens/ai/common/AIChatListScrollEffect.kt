package it.vittorioscocca.kidbox.ui.screens.ai.common

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.yield

private const val STREAM_SCROLL_MIN_INTERVAL_MS = 120L

/**
 * Scroll stabile per chat AI: evita salti durante il typewriter (throttle ~120ms).
 *
 * @param reverseLayout se true, indice 0 = messaggio più recente (bottom visivo).
 */
@Composable
fun AIChatListScrollEffect(
    listState: LazyListState,
    messageCount: Int,
    isLoading: Boolean,
    streamingMessageId: String?,
    streamScrollTick: Int,
    reverseLayout: Boolean,
) {
    var lastStreamScrollAt by remember { mutableLongStateOf(0L) }

    suspend fun scrollToBottom(animated: Boolean) {
        val targetIndex = if (reverseLayout) 0 else {
            val total = listState.layoutInfo.totalItemsCount
            (total - 1).coerceAtLeast(0)
        }
        if (targetIndex < 0) return
        if (animated) {
            listState.animateScrollToItem(targetIndex)
        } else {
            listState.scrollToItem(targetIndex)
        }
    }

    LaunchedEffect(messageCount) {
        if (messageCount > 0) scrollToBottom(animated = false)
    }

    LaunchedEffect(isLoading) {
        if (isLoading) scrollToBottom(animated = true)
    }

    LaunchedEffect(streamingMessageId) {
        if (streamingMessageId != null) scrollToBottom(animated = false)
    }

    LaunchedEffect(streamScrollTick) {
        if (streamScrollTick == 0) return@LaunchedEffect
        val now = System.currentTimeMillis()
        if (now - lastStreamScrollAt < STREAM_SCROLL_MIN_INTERVAL_MS) return@LaunchedEffect
        lastStreamScrollAt = now
        yield()
        scrollToBottom(animated = false)
    }
}

/** Incrementato dal typewriter tick; passato a [AIChatListScrollEffect]. */
@Composable
fun rememberStreamScrollTick(): Pair<Int, () -> Unit> {
    var tick by remember { mutableIntStateOf(0) }
    return tick to { tick += 1 }
}
