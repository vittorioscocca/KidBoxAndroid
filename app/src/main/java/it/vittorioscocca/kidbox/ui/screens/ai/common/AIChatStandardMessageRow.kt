package it.vittorioscocca.kidbox.ui.screens.ai.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import it.vittorioscocca.kidbox.util.KBLocale

private fun TIME_FMT() = SimpleDateFormat("HH:mm", KBLocale.current())

@Composable
fun AIChatStandardMessageRow(
    message: KBAIMessage,
    kb: KidBoxColorScheme,
    userBubbleColor: Color,
    userTextColor: Color? = null,
    streamingMessageId: String?,
    onStreamScrollTick: () -> Unit,
    onStreamingComplete: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    AIChatStandardMessageRow(
        messageId = message.id,
        content = message.content,
        isUser = message.isUser,
        createdAtEpochMillis = message.createdAtEpochMillis,
        kb = kb,
        userBubbleColor = userBubbleColor,
        userTextColor = userTextColor,
        isStreaming = streamingMessageId == message.id && message.isAssistant,
        onStreamScrollTick = onStreamScrollTick,
        onStreamingComplete = { onStreamingComplete(message.id) },
        modifier = modifier,
    )
}

@Composable
fun AIChatStandardMessageRow(
    messageId: String,
    content: String,
    isUser: Boolean,
    createdAtEpochMillis: Long,
    kb: KidBoxColorScheme,
    userBubbleColor: Color,
    userTextColor: Color? = null,
    isStreaming: Boolean,
    onStreamScrollTick: () -> Unit,
    onStreamingComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val alignment = if (isUser) Alignment.End else Alignment.Start
    val maxUserWidth = LocalConfiguration.current.screenWidthDp.dp * 0.75f

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalAlignment = alignment,
    ) {
        if (isUser) {
            AIChatCopyableMessageContainer(
                copyText = content,
                modifier = Modifier.widthIn(max = maxUserWidth),
            ) {
                Box(
                    modifier = Modifier
                        .clip(
                            RoundedCornerShape(
                                topStart = 16.dp,
                                topEnd = 16.dp,
                                bottomStart = 16.dp,
                                bottomEnd = 4.dp,
                            ),
                        )
                        .background(userBubbleColor)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(content, fontSize = 14.sp, color = userTextColor ?: kb.title)
                }
            }
        } else {
            AIChatCopyableMessageContainer(
                copyText = content,
                modifier = Modifier.fillMaxWidth(),
            ) {
                TypewriterClaudeMarkdownText(
                    text = content,
                    streamReveal = isStreaming,
                    modifier = Modifier.fillMaxWidth(),
                    onRevealTick = onStreamScrollTick,
                    onRevealComplete = onStreamingComplete,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            TIME_FMT().format(Date(createdAtEpochMillis)),
            fontSize = 10.sp,
            color = kb.subtitle,
        )
    }
}
