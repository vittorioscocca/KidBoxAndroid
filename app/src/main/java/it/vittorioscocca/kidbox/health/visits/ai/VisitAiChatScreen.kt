@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.health.visits.ai

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatListScrollEffect
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatStandardMessageRow
import it.vittorioscocca.kidbox.ui.screens.ai.common.rememberStreamScrollTick
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.layout.imePadding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import it.vittorioscocca.kidbox.util.KBLocale

private val VISIT_EMPTY_SUGGESTIONS = listOf(
    "Riassumi le visite recenti",
    "Quali prescrizioni sono attive?",
    "Mostrami eventuali controlli da fare",
)
private fun VISIT_TIME() = SimpleDateFormat("HH:mm", KBLocale.current())

@Composable
fun VisitAiChatScreen(
    visitId: String,
    subjectName: String,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
    viewModel: VisitAiChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val (streamScrollTick, onStreamScrollTick) = rememberStreamScrollTick()
    AIChatListScrollEffect(
        listState = listState,
        messageCount = uiState.messages.size,
        isLoading = uiState.isLoading,
        streamingMessageId = uiState.streamingMessageId,
        streamScrollTick = streamScrollTick,
        reverseLayout = false,
    )
    var input by remember { mutableStateOf("") }
    var showClearDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.actionExecutionSummary) {
        val summary = uiState.actionExecutionSummary ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(summary)
        viewModel.clearActionExecutionSummary()
    }

    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        TopAppBar(
            modifier = Modifier.statusBarsPadding(),
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
            ),
            title = {
                Text(
                    if (uiState.isListMode) {
                        "Chiedi all'AI · Visite"
                    } else {
                        "Chiedi all'AI"
                    },
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                    )
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { showMenu = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text("Nuova conversazione", color = Color(0xFFD32F2F)) },
                            onClick = {
                                showMenu = false
                                showClearDialog = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Impostazioni AI") },
                            onClick = {
                                showMenu = false
                                onOpenAiSettings()
                            },
                        )
                    }
                }
            },
        )

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) {
            if (uiState.messages.isEmpty() && !uiState.isLoading) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = Color(0xFFFF6B00),
                        modifier = Modifier.size(46.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (uiState.isListMode) {
                            "Ciao! Sono il tuo assistente sanitario per $subjectName."
                        } else {
                            "Ciao! Sono il tuo assistente sanitario per ${subjectName.ifBlank { "questo profilo" }}."
                        },
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (uiState.isListMode) {
                            "Ho accesso a: ${uiState.listVisitCount} visite, prescrizioni collegate e referti."
                        } else {
                            "Ho accesso a: dettagli visita, esami/cure collegati e referti."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (uiState.isListMode) {
                            "Puoi chiedermi riepiloghi, terapie attive o controlli da programmare."
                        } else {
                            "Puoi chiedermi un riepilogo clinico della visita e dei referti collegati."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    VISIT_EMPTY_SUGGESTIONS.forEach { suggestion ->
                        SuggestionChip(
                            onClick = { viewModel.sendMessage(suggestion) },
                            label = { Text(suggestion) },
                            modifier = Modifier.fillMaxWidth(),
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = Color(0xFFFF6B00).copy(alpha = 0.10f),
                                labelColor = Color(0xFFFF6B00),
                            ),
                            border = SuggestionChipDefaults.suggestionChipBorder(
                                enabled = true,
                                borderColor = Color(0xFFFF6B00).copy(alpha = 0.25f),
                            ),
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 0.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.messages, key = { it.id }) { msg ->
                        AIChatStandardMessageRow(
                            messageId = msg.id,
                            content = msg.content,
                            isUser = msg.role == "user",
                            createdAtEpochMillis = msg.createdAt,
                            kb = kb,
                            userBubbleColor = MaterialTheme.colorScheme.primaryContainer,
                            userTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            isStreaming = uiState.streamingMessageId == msg.id && msg.role != "user",
                            onStreamScrollTick = onStreamScrollTick,
                            onStreamingComplete = { viewModel.finishStreaming(msg.id) },
                        )
                    }
                    if (uiState.isLoading) {
                        item(key = "typing_indicator") {
                            TypingIndicatorBubble()
                        }
                    }
                }
                val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
                val total = listState.layoutInfo.totalItemsCount
                val showScrollToBottom = total > 0 && lastVisible < total - 1
                androidx.compose.animation.AnimatedVisibility(
                    visible = showScrollToBottom,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 8.dp, bottom = 12.dp),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(
                        onClick = { scope.launch { listState.animateScrollToItem((total - 1).coerceAtLeast(0)) } },
                        modifier = Modifier
                            .size(42.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFFFF6B00)),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Scorri in basso",
                            tint = Color.White,
                        )
                    }
                }
            }
        }

        if (uiState.dailyLimit > 0) {
            Text(
                text = "${uiState.usageToday}/${uiState.dailyLimit}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Scrivi un messaggio...") },
                enabled = !uiState.isLoading,
                maxLines = 4,
                shape = RoundedCornerShape(24.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    focusedBorderColor = Color.Transparent,
                    unfocusedBorderColor = Color.Transparent,
                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                    cursorColor = Color(0xFFFF6B00),
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        val text = input.trim()
                        if (text.isNotEmpty()) {
                            viewModel.sendMessage(text)
                            input = ""
                        }
                    },
                ),
            )
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = {
                    val text = input.trim()
                    if (text.isNotEmpty()) {
                        viewModel.sendMessage(text)
                        input = ""
                    }
                },
                enabled = !uiState.isLoading && input.isNotBlank(),
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape)
                    .background(
                        if (!uiState.isLoading && input.isNotBlank()) Color(0xFFFF6B00)
                        else MaterialTheme.colorScheme.surfaceVariant,
                    ),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Invia",
                    tint = if (!uiState.isLoading && input.isNotBlank()) {
                        Color.White
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
        }
    }

    SnackbarHost(hostState = snackbarHostState)

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Nuova conversazione") },
            text = { Text("La cronologia verrà eliminata e il contesto verrà ricostruito.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearConversation()
                        showClearDialog = false
                    },
                ) {
                    Text("Conferma", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("Annulla")
                }
            },
        )
    }
}

@Composable
private fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "visitAiTyping")
    val alpha1 = transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val alpha2 = transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, delayMillis = 120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val alpha3 = transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 700, delayMillis = 240, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha1.value)),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha2.value)),
        )
        Spacer(Modifier.width(6.dp))
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha3.value)),
        )
    }
}
