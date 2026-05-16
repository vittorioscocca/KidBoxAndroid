package it.vittorioscocca.kidbox.ui.screens.ai.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Rivela il testo assistant con effetto macchina da scrivere (parity iOS `AIChatBubbleView`).
 */
@Composable
fun TypewriterClaudeMarkdownText(
    text: String,
    streamReveal: Boolean,
    modifier: Modifier = Modifier,
    onRevealTick: () -> Unit = {},
    onRevealComplete: () -> Unit = {},
) {
    var revealedCount by remember(text, streamReveal) {
        mutableIntStateOf(if (streamReveal && text.isNotEmpty()) 0 else text.length)
    }

    LaunchedEffect(text, streamReveal) {
        if (!streamReveal || text.isEmpty()) {
            revealedCount = text.length
            return@LaunchedEffect
        }
        revealedCount = 0
        val total = text.length
        val baseDelayMs = when {
            total > 1200 -> 6L
            total > 400 -> 10L
            else -> 14L
        }
        var index = 0
        while (index < total && isActive) {
            val step = revealStep(text, index)
            index = (index + step).coerceAtMost(total)
            revealedCount = index
            onRevealTick()
            if (index < total) delay(baseDelayMs * step)
        }
        onRevealComplete()
    }

    val displayText = if (streamReveal && revealedCount == 0) "" else text.take(revealedCount)
    ClaudeMarkdownText(text = displayText, modifier = modifier)
}

private fun revealStep(text: String, index: Int): Int {
    if (index >= text.length) return 1
    return when (text[index]) {
        '\n' -> 1
        ' ' -> 2
        else -> minOf(4, text.length - index)
    }
}
