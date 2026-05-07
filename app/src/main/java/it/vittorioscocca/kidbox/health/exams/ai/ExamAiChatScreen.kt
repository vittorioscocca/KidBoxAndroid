@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.health.exams.ai

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import kotlinx.coroutines.launch

private val EXAM_EMPTY_SUGGESTIONS = listOf(
    "Quali esami richiedono attenzione?",
    "Ci sono esami in scadenza?",
    "Riassumi i referti disponibili",
)

@Composable
fun ExamAiChatScreen(
    examId: String,
    examName: String,
    subjectName: String,
    onBack: () -> Unit,
    viewModel: ExamAiChatViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var input by remember { mutableStateOf("") }

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
                        "Chiedi all'AI · Esami"
                    } else {
                        "Chiedi all'AI · Esame"
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
                            "Ho accesso a: ${uiState.listExamCount} esami e relativi referti."
                        } else {
                            "Ho accesso a: dettagli e referti dell'esame selezionato."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (uiState.isListMode) {
                            "Puoi chiedermi riepiloghi, priorita cliniche o esami in scadenza."
                        } else {
                            "Puoi chiedermi un riepilogo o un aiuto nell'interpretazione del contesto disponibile."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(14.dp))
                    EXAM_EMPTY_SUGGESTIONS.forEach { suggestion ->
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
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(uiState.messages, key = { it.id }) { msg ->
                        val isUser = msg.role == "user"
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = 300.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 14.dp,
                                            topEnd = 14.dp,
                                            bottomStart = if (isUser) 14.dp else 4.dp,
                                            bottomEnd = if (isUser) 4.dp else 14.dp,
                                        ),
                                    )
                                    .background(
                                        if (isUser) {
                                            MaterialTheme.colorScheme.primaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        },
                                    )
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = msg.content,
                                    color = if (isUser) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
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
}

@Composable
private fun TypingIndicatorBubble() {
    val transition = rememberInfiniteTransition(label = "examAiTyping")
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha1.value)),
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha2.value)),
            )
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha3.value)),
            )
        }
    }
}
