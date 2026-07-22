@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package it.vittorioscocca.kidbox.ui.screens.ai.common

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import it.vittorioscocca.kidbox.R

@Composable
fun AIChatCopyableMessageContainer(
    copyText: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    var showCopyMenu by remember { mutableStateOf(false) }
    val canCopy = copyText.isNotBlank()

    Box(modifier = modifier) {
        Box(
            modifier = if (canCopy) {
                Modifier.combinedClickable(
                    onClick = {},
                    onLongClick = { showCopyMenu = true },
                )
            } else {
                Modifier
            },
        ) {
            content()
        }
        if (canCopy) {
            DropdownMenu(
                expanded = showCopyMenu,
                onDismissRequest = { showCopyMenu = false },
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.ai_copy_message)) },
                    onClick = {
                        clipboard.setText(AnnotatedString(AIChatMarkdownPlainText.forClipboard(copyText)))
                        showCopyMenu = false
                    },
                )
            }
        }
    }
}
