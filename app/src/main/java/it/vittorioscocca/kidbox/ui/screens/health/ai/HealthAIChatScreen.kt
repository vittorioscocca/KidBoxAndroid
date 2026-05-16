package it.vittorioscocca.kidbox.ui.screens.health.ai

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatListScrollEffect
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatStandardMessageRow
import it.vittorioscocca.kidbox.ui.screens.ai.common.rememberStreamScrollTick
import it.vittorioscocca.kidbox.ui.components.KidBoxHeaderCircleButton
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch

private val ORANGE = Color(0xFFFF6B00)
private val DATE_SHORT = SimpleDateFormat("HH:mm", Locale.ITALIAN)

private val SUGGESTIONS = listOf(
    "Quali cure sta seguendo?",
    "Ci sono esami in scadenza?",
    "Riassumi la situazione vaccinale",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HealthAIChatScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
    viewModel: HealthAIChatViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showClearDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }

    LaunchedEffect(familyId, childId) { viewModel.bind(familyId, childId) }

    val (streamScrollTick, onStreamScrollTick) = rememberStreamScrollTick()
    AIChatListScrollEffect(
        listState = listState,
        messageCount = state.messages.size,
        isLoading = state.isLoading,
        streamingMessageId = state.streamingMessageId,
        streamScrollTick = streamScrollTick,
        reverseLayout = true,
    )

    LaunchedEffect(state.actionExecutionSummary) {
        val summary = state.actionExecutionSummary ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(summary)
        viewModel.clearActionExecutionSummary()
    }

    LaunchedEffect(state.errorMessage) {
        state.errorMessage?.let { err ->
            val result = snackbarHostState.showSnackbar(message = err, actionLabel = "Riprova")
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) viewModel.consumeError()
            else viewModel.consumeError()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top bar ──────────────────────────────────────────────────────
            TopAppBar(
                modifier = Modifier.statusBarsPadding(),
                colors = TopAppBarDefaults.topAppBarColors(containerColor = kb.background),
                title = {
                    Text(
                        "Salute",
                        fontWeight = FontWeight.Bold,
                        fontSize = 17.sp,
                        color = kb.title,
                    )
                },
                navigationIcon = {
                    KidBoxHeaderCircleButton(
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Indietro",
                        onClick = onBack,
                    )
                },
                actions = {
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
            // ── Content ──────────────────────────────────────────────────────
            when {
                state.isLoadingContext -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = ORANGE)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                "Preparando il contesto sanitario...",
                                fontSize = 14.sp,
                                color = kb.subtitle,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp),
                            )
                        }
                    }
                }

                state.messages.isEmpty() && !state.isLoading -> {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(horizontal = 24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = ORANGE,
                                modifier = Modifier.size(46.dp),
                            )
                            Spacer(Modifier.height(10.dp))
                            Text(
                                "Ciao! Sono il tuo assistente sanitario per ${state.subjectName.ifBlank { "questo profilo" }}.",
                                fontSize = 15.sp,
                                color = kb.subtitle,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "Ho accesso a: ${state.activeTreatmentsCount} cure attive, ${state.vaccinesCount} vaccini, ${state.visitsCount} visite, ${state.examsCount} esami.",
                                fontSize = 13.sp,
                                color = kb.subtitle,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Puoi chiedermi un riepilogo, farmaci in corso, vaccini, visite recenti o esami in attesa.",
                                fontSize = 13.sp,
                                color = kb.subtitle,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                            Spacer(Modifier.height(14.dp))
                            SUGGESTIONS.forEach { suggestion ->
                                SuggestionChip(
                                    onClick = { viewModel.sendSuggestion(suggestion) },
                                    label = { Text(suggestion, fontSize = 13.sp) },
                                    colors = SuggestionChipDefaults.suggestionChipColors(
                                        containerColor = ORANGE.copy(alpha = 0.10f),
                                        labelColor = ORANGE,
                                    ),
                                    border = SuggestionChipDefaults.suggestionChipBorder(
                                        enabled = true,
                                        borderColor = ORANGE.copy(alpha = 0.25f),
                                    ),
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Spacer(Modifier.height(8.dp))
                            }
                        }
                    }
                }

                else -> {
                    // ── Messages list ─────────────────────────────────────────
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 0.dp),
                            reverseLayout = true,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            if (state.isLoading) {
                                item { TypingIndicatorBubble(kb = kb) }
                            }
                            itemsIndexed(
                                state.messages.reversed(),
                                key = { _, message -> message.id },
                            ) { _, message ->
                                AIChatStandardMessageRow(
                                    message = message,
                                    kb = kb,
                                    userBubbleColor = ORANGE.copy(alpha = 0.12f),
                                    streamingMessageId = state.streamingMessageId,
                                    onStreamScrollTick = onStreamScrollTick,
                                    onStreamingComplete = viewModel::finishStreaming,
                                )
                            }
                        }
                        val showScrollToBottom = listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
                        androidx.compose.animation.AnimatedVisibility(
                            visible = showScrollToBottom,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(end = 8.dp, bottom = 12.dp),
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            IconButton(
                                onClick = { scope.launch { listState.animateScrollToItem(0) } },
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(ORANGE),
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
            }

            // ── Usage bar ─────────────────────────────────────────────────────
            if (state.dailyLimit > 0) {
                val usageColor = if (state.isNearLimit) ORANGE else kb.subtitle
                Text(
                    "${state.usageToday}/${state.dailyLimit}",
                    fontSize = 11.sp,
                    color = usageColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                )
            }

            // ── Input bar ─────────────────────────────────────────────────────
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
                        focusedBorderColor = ORANGE,
                        unfocusedBorderColor = kb.subtitle.copy(alpha = 0.3f),
                        focusedTextColor = kb.title,
                        unfocusedTextColor = kb.title,
                        cursorColor = ORANGE,
                    ),
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { viewModel.send() }),
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.send() },
                    enabled = state.canSend,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (state.canSend) ORANGE else kb.subtitle.copy(alpha = 0.2f)),
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

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("Nuova conversazione") },
            text = { Text("La cronologia verrà eliminata.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.clearConversation()
                    showClearDialog = false
                }) {
                    Text("Conferma", color = Color(0xFFD32F2F))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("Annulla") }
            },
        )
    }
}

@Composable
private fun TypingIndicatorBubble(kb: it.vittorioscocca.kidbox.ui.theme.KidBoxColorScheme) {
    val infiniteTransition = rememberInfiniteTransition(label = "typing")
    val delays = listOf(0, 150, 300)
    val alphas = delays.map { delay ->
        infiniteTransition.animateFloat(
            initialValue = 0.3f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = keyframes {
                    durationMillis = 900
                    0.3f at delay with LinearEasing
                    1f at delay + 300 with LinearEasing
                    0.3f at delay + 600 with LinearEasing
                },
                repeatMode = RepeatMode.Restart,
            ),
            label = "dot_$delay",
        ).value
    }

    Row(
        modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        alphas.forEach { alpha ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(kb.subtitle.copy(alpha = alpha)),
            )
        }
    }
}

private fun parseMarkdownBasic(text: String) = buildAnnotatedString {
    val boldRegex = Regex("""\*\*(.+?)\*\*""")
    val italicRegex = Regex("""\*(.+?)\*""")
    var cursor = 0
    val allMatches = (boldRegex.findAll(text) + italicRegex.findAll(text))
        .sortedBy { it.range.first }
        .distinctBy { it.range.first }

    allMatches.forEach { match ->
        if (match.range.first < cursor) return@forEach
        if (match.range.first > cursor) append(text.substring(cursor, match.range.first))
        val isBold = match.value.startsWith("**")
        val inner = match.groupValues[1]
        if (isBold) {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(inner) }
        } else {
            withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(inner) }
        }
        cursor = match.range.last + 1
    }
    if (cursor < text.length) append(text.substring(cursor))
}

/** Richiesta naming: stessa schermata di [HealthAIChatScreen]. */
@Composable
fun HealthAiChatScreen(
    familyId: String,
    childId: String,
    onBack: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
) {
    HealthAIChatScreen(
        familyId = familyId,
        childId = childId,
        onBack = onBack,
        onOpenAiSettings = onOpenAiSettings,
    )
}
