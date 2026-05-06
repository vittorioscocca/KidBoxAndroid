@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.ai.planning

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

private val AI_BLUE = Color(0xFF5C6BC0)

private val SUGGESTIONS = listOf(
    "Cosa ho in programma questa settimana?",
    "Cosa manca ancora alla lista della spesa?",
    "Aiutami a pianificare il weekend",
)

@Composable
fun AIChatScreen(
    familyId: String,
    familyName: String,
    onBack: () -> Unit,
    viewModel: PlanningAIChatViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    var showClearDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    BackHandler { onBack() }

    LaunchedEffect(familyId) { viewModel.bind(familyId, familyName) }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(0)
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { err ->
            val result = snackbarHostState.showSnackbar(message = err, actionLabel = "OK")
            if (result == SnackbarResult.ActionPerformed || result == SnackbarResult.Dismissed) {
                viewModel.consumeError()
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Nuova conversazione?") },
            text = { Text("La conversazione attuale verrà cancellata e non sarà recuperabile.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearConversation()
                    showClearDialog = false
                }) {
                    Text("Cancella", color = Color(0xFFD32F2F), fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Annulla") }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize().background(kb.background)) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
                title = {
                    Column {
                        Text(
                            "Assistente AI",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp,
                            color = kb.title,
                        )
                        if (state.dailyLimit > 0) {
                            val usageColor = if (state.isNearLimit) Color(0xFFEF4444) else kb.subtitle
                            Text(
                                "${state.usageToday}/${state.dailyLimit} messaggi oggi",
                                fontSize = 11.sp,
                                color = usageColor,
                            )
                        }
                    }
                },
                navigationIcon = {
                    KidBoxHeaderCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        onClick = onBack,
                    )
                },
                actions = {
                    if (state.messages.isNotEmpty()) {
                        Box {
                            KidBoxHeaderCircleButton(
                                icon = Icons.Default.MoreVert,
                                contentDescription = "Menu",
                                onClick = { showMenu = true },
                            )
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Nuova conversazione", color = Color(0xFFD32F2F)) },
                                    onClick = { showMenu = false; showClearDialog = true },
                                )
                            }
                        }
                    }
                },
            )

            when {
                state.isLoadingContext -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = AI_BLUE)
                            Spacer(Modifier.height(12.dp))
                            Text("Preparando il contesto...", fontSize = 14.sp, color = kb.subtitle)
                        }
                    }
                }

                state.messages.isEmpty() && !state.isLoading -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 32.dp),
                        ) {
                            Icon(
                                Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = AI_BLUE,
                                modifier = Modifier.size(48.dp),
                            )
                            Spacer(Modifier.height(12.dp))
                            val name = state.familyName.ifBlank { "la tua famiglia" }
                            Text(
                                "Ciao! Sono il tuo assistente AI.\nPosso aiutarti con la pianificazione di $name.",
                                fontSize = 15.sp,
                                color = kb.subtitle,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(20.dp))
                            SUGGESTIONS.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { viewModel.sendSuggestion(suggestion) },
                                    label = { Text(suggestion, fontSize = 13.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = AI_BLUE.copy(alpha = 0.10f),
                                        labelColor = AI_BLUE,
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = AI_BLUE.copy(alpha = 0.25f),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                        reverseLayout = true,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (state.isLoading) {
                            item { PlanningTypingIndicator() }
                        }
                        itemsIndexed(state.messages.reversed()) { _, message ->
                            PlanningChatBubble(message = message)
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.ime)
                    .navigationBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                OutlinedTextField(
                    value = state.inputText,
                    onValueChange = { viewModel.setInput(it) },
                    placeholder = { Text("Scrivi un messaggio...", color = kb.subtitle) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AI_BLUE,
                        unfocusedBorderColor = kb.subtitle.copy(alpha = 0.3f),
                        focusedContainerColor = kb.card,
                        unfocusedContainerColor = kb.card,
                    ),
                    maxLines = 5,
                    enabled = !state.isLoading && !state.isLoadingContext,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { if (state.canSend) viewModel.send() }),
                )
                Spacer(Modifier.size(8.dp))
                Surface(
                    shape = CircleShape,
                    color = if (state.canSend) AI_BLUE else kb.subtitle.copy(alpha = 0.2f),
                    modifier = Modifier.size(48.dp),
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        if (state.isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp,
                                color = Color.White,
                            )
                        } else {
                            androidx.compose.material3.IconButton(
                                onClick = { viewModel.send() },
                                enabled = state.canSend,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Invia",
                                    tint = if (state.canSend) Color.White else kb.subtitle,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun PlanningChatBubble(message: KBAIMessage) {
    val kb = MaterialTheme.kidBoxColors
    val isUser = message.isUser

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
    ) {
        if (!isUser) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(bottom = 4.dp, start = 4.dp),
            ) {
                Icon(
                    Icons.Default.AutoAwesome,
                    contentDescription = null,
                    tint = AI_BLUE,
                    modifier = Modifier.size(13.dp),
                )
                Text("Assistente AI", fontSize = 11.sp, color = AI_BLUE, fontWeight = FontWeight.Medium)
            }
        }

        Surface(
            color = if (isUser) AI_BLUE else kb.card,
            shape = RoundedCornerShape(
                topStart = if (isUser) 18.dp else 4.dp,
                topEnd = if (isUser) 4.dp else 18.dp,
                bottomStart = 18.dp,
                bottomEnd = 18.dp,
            ),
            modifier = Modifier.widthIn(max = 300.dp),
        ) {
            Text(
                text = message.content,
                color = if (isUser) Color.White else kb.title,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                lineHeight = 20.sp,
            )
        }
    }
}

@Composable
private fun PlanningTypingIndicator() {
    Row(
        modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(color = MaterialTheme.kidBoxColors.card, shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    val transition = rememberInfiniteTransition(label = "dot$index")
                    val alpha by transition.animateFloat(
                        initialValue = 0.3f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 1200
                                0.3f at index * 200 using LinearEasing
                                1f at index * 200 + 300 using LinearEasing
                                0.3f at index * 200 + 600 using LinearEasing
                                0.3f at 1200 using LinearEasing
                            },
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "alpha$index",
                    )
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(AI_BLUE.copy(alpha = alpha)),
                    )
                }
            }
        }
    }
}
