@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package it.vittorioscocca.kidbox.ui.screens.ai.planning

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import it.vittorioscocca.kidbox.notifications.NotificationDeepLinkRouter
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatCopyableMessageContainer
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatListScrollEffect
import it.vittorioscocca.kidbox.ui.screens.ai.common.AIChatStandardMessageRow
import it.vittorioscocca.kidbox.ui.screens.ai.common.TypewriterClaudeMarkdownText
import it.vittorioscocca.kidbox.ui.screens.ai.common.rememberStreamScrollTick
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import androidx.compose.ui.platform.LocalContext

@Composable
fun PlanningAIChatScreen(
    viewModel: PlanningAIChatViewModel = hiltViewModel(),
    onNavigateToCalendar: () -> Unit,
    onNavigateToTodo: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val recapTick by NotificationDeepLinkRouter.recapTick.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val listState = rememberLazyListState()
    val snackHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hiddenActions = remember { mutableStateListOf<String>() }
    val doneActions = remember { mutableStateMapOf<String, Boolean>() }
    val actionsViewModel = hiltViewModel<PlanningAIChatActionsViewModel>()
    val reminderService = actionsViewModel.reminderService
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.actionExecutionSummary) {
        val summary = state.actionExecutionSummary ?: return@LaunchedEffect
        snackHost.showSnackbar(summary)
        viewModel.clearActionExecutionSummary()
    }

    val contextInput = remember {
        PlanningContextInput(
            familyName = "",
            memberNames = emptyList(),
            calendarEvents = emptyList(),
            openTodos = emptyList(),
            activeRoutines = emptyList(),
            todayChecks = emptyList(),
            childNames = emptyList(),
            activeTreatments = emptyList(),
            visitsWithNextDate = emptyList(),
            visitsWithPendingExams = emptyList(),
            upcomingVaccines = emptyList(),
            recentNotes = emptyList(),
            recentExpenses = emptyList(),
            expenseCategoryNames = emptyList(),
            pendingGroceryItems = emptyList(),
            recentChatMessages = emptyList(),
            recentDocuments = emptyList(),
            recentWalletTickets = emptyList(),
            children = emptyList(),
            pediatricProfiles = emptyList(),
            allVisits = emptyList(),
            allExams = emptyList(),
            allVaccines = emptyList(),
        )
    }

    LaunchedEffect(Unit) { viewModel.loadOrCreateConversation(contextInput) }
    LaunchedEffect(state.conversationReady, recapTick) {
        if (state.conversationReady && recapTick > 0) {
            viewModel.injectPendingRecapFromStores()
        }
    }
    val (streamScrollTick, onStreamScrollTick) = rememberStreamScrollTick()
    AIChatListScrollEffect(
        listState = listState,
        messageCount = state.messages.size,
        isLoading = state.isLoading,
        streamingMessageId = state.streamingMessageId,
        streamScrollTick = streamScrollTick,
        reverseLayout = false,
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.ai_kidbox_assistant), color = kb.title, fontWeight = FontWeight.Bold)
                        AssistChip(
                            onClick = {},
                            enabled = false,
                            label = { Text("Claude", fontSize = 11.sp) },
                            colors = AssistChipDefaults.assistChipColors(
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.ai_menu), tint = kb.title)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ai_new_conversation), color = Color(0xFFD32F2F)) },
                                onClick = {
                                    showMenu = false
                                    showClearDialog = true
                                },
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.ai_settings)) },
                                onClick = {
                                    showMenu = false
                                    onNavigateToUpgrade()
                                },
                            )
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackHost) },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(kb.background)
                    .windowInsetsPadding(WindowInsets.ime)
                    .navigationBarsPadding()
                    .padding(10.dp),
            ) {
                val quick = listOf(
                    stringResource(R.string.ai_q_this_week),
                    stringResource(R.string.ai_q_create_event),
                    stringResource(R.string.ai_q_add_todo),
                    stringResource(R.string.ai_q_child_meds),
                    stringResource(R.string.ai_q_expenses),
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(quick) { chip ->
                        AssistChip(onClick = { viewModel.onInputChanged(chip) }, label = { Text(chip) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    OutlinedTextField(
                        value = state.inputText,
                        onValueChange = viewModel::onInputChanged,
                        modifier = Modifier.weight(1f),
                        placeholder = { Text(stringResource(R.string.ai_ask_something)) },
                        shape = RoundedCornerShape(24.dp),
                        maxLines = 4,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(onSend = { if (state.inputText.isNotBlank() && !state.isLoading) viewModel.send(contextInput) }),
                    )
                    IconButton(
                        onClick = { viewModel.send(contextInput) },
                        enabled = state.inputText.isNotBlank() && !state.isLoading,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = stringResource(R.string.chat_send), tint = Color(0xFF598FDB))
                    }
                }
            }
        },
        containerColor = kb.background,
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            Column(Modifier.fillMaxSize()) {
                if (state.conversationReady) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            listOf(
                                "Oggi ${PlanningContextBuilder.formatDateShort(System.currentTimeMillis())}",
                                "${state.todayEventsCount} eventi oggi",
                                "${state.urgentTodosCount} todo urgenti",
                                "${state.todayDosesCount} dosi oggi",
                            ),
                        ) { label ->
                            AssistChip(
                                onClick = {},
                                enabled = false,
                                label = { Text(label, fontSize = 12.sp) },
                                leadingIcon = { Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(14.dp)) },
                            )
                        }
                    }
                }

                AnimatedVisibility(visible = state.errorMessage != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(state.errorMessage.orEmpty(), color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                            IconButton(onClick = viewModel::clearError) {
                                Icon(Icons.Default.Close, contentDescription = stringResource(R.string.chat_close))
                            }
                        }
                    }
                }

                if (state.isSubscribed) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 0.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            AIChatBubbleView(
                                message = msg,
                                streamingMessageId = state.streamingMessageId,
                                onStreamScrollTick = onStreamScrollTick,
                                onStreamingComplete = viewModel::finishStreaming,
                            )
                            if (msg.isAssistant && state.streamingMessageId != msg.id &&
                                msg.id !in state.autoExecutedMessageIds
                            ) {
                                val actions = PlanningActionParser.parse(
                                    context,
                                    text = msg.content,
                                    openTodos = state.parserOpenTodos,
                                    visits = state.parserVisits,
                                    treatments = state.parserTreatments,
                                    familyId = state.parserFamilyId,
                                ).filterNot { it.id in hiddenActions }
                                actions.forEach { action ->
                                    PlanningActionCard(
                                        action = action,
                                        onConfirm = {
                                            scope.launch {
                                                when (it.kind) {
                                                    PlanningActionKind.SET_REMINDER -> {
                                                        val result = reminderService.schedule(it.reminderContext)
                                                        snackHost.showSnackbar(result)
                                                    }
                                                    PlanningActionKind.CREATE_EVENT,
                                                    PlanningActionKind.CREATE_TODO,
                                                    PlanningActionKind.CREATE_GROCERY,
                                                    PlanningActionKind.CREATE_NOTE,
                                                    -> viewModel.executeCardAction(it)
                                                    PlanningActionKind.NAVIGATE -> when (it.navigationTarget) {
                                                        PlanningNavigationTarget.CALENDAR -> onNavigateToCalendar()
                                                        PlanningNavigationTarget.TODO -> onNavigateToTodo()
                                                        PlanningNavigationTarget.HEALTH -> onNavigateToHealth()
                                                        PlanningNavigationTarget.NONE -> Unit
                                                    }
                                                }
                                                doneActions[it.id] = true
                                                delay(2000)
                                                hiddenActions.add(it.id)
                                            }
                                        },
                                        onNavigate = { target ->
                                            when (target) {
                                                PlanningNavigationTarget.CALENDAR -> onNavigateToCalendar()
                                                PlanningNavigationTarget.TODO -> onNavigateToTodo()
                                                PlanningNavigationTarget.HEALTH -> onNavigateToHealth()
                                                PlanningNavigationTarget.NONE -> Unit
                                            }
                                        },
                                        done = doneActions[action.id] == true,
                                    )
                                }
                            }
                        }
                        if (state.isLoading) item { AIChatTypingIndicator() }
                    }
                }
            }

            if (state.isLoadingContext) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CircularProgressIndicator()
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.ai_preparing_context_dots), fontSize = 14.sp, color = kb.subtitle)
                }
            }

            if (!state.isLoadingContext && !state.isSubscribed) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background.copy(alpha = 0.96f),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text("\uD83E\uDD16", fontSize = 32.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(stringResource(R.string.ai_available_pro), fontWeight = FontWeight.Bold, color = kb.title)
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = onNavigateToUpgrade) { Text(stringResource(R.string.ai_discover_plans)) }
                    }
                }
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.ai_new_conversation)) },
            text = { Text(stringResource(R.string.ai_history_reset)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearConversation()
                        showClearDialog = false
                    },
                ) {
                    Text(stringResource(R.string.ai_confirm), color = Color(0xFFD32F2F))
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
fun AIChatBubbleView(
    message: KBAIMessage,
    streamingMessageId: String? = null,
    onStreamScrollTick: () -> Unit = {},
    onStreamingComplete: (String) -> Unit = {},
) {
    val isUser = message.isUser
    val maxUserWidth = LocalConfiguration.current.screenWidthDp.dp * 0.75f
    val isStreaming = streamingMessageId == message.id && message.isAssistant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start,
        ) {
            if (isUser) {
                AIChatCopyableMessageContainer(
                    copyText = message.content,
                    modifier = Modifier.widthIn(max = maxUserWidth),
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Text(
                            message.content,
                            modifier = Modifier.padding(12.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            } else {
                AIChatCopyableMessageContainer(
                    copyText = message.content,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    TypewriterClaudeMarkdownText(
                        text = message.content,
                        streamReveal = isStreaming,
                        modifier = Modifier.fillMaxWidth(),
                        onRevealTick = onStreamScrollTick,
                        onRevealComplete = { onStreamingComplete(message.id) },
                    )
                }
            }
            Text(
                PlanningContextBuilder.formatTime(message.createdAtEpochMillis),
                fontSize = 11.sp,
                color = MaterialTheme.kidBoxColors.subtitle,
                modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

@Composable
fun AIChatTypingIndicator() {
    val transition = rememberInfiniteTransition(label = "typing")
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(start = 16.dp, top = 4.dp, bottom = 6.dp)) {
        repeat(3) { idx ->
            val scale by transition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(600, delayMillis = idx * 200, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "dot$idx",
            )
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(Color(0xFF598FDB), CircleShape),
            ) {
                Spacer(Modifier.fillMaxSize().background(Color.Transparent))
            }
        }
    }
}

@Composable
fun PlanningActionCard(
    action: PlanningAction,
    onConfirm: (PlanningAction) -> Unit,
    onNavigate: (PlanningNavigationTarget) -> Unit,
    done: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val icon = when (action.kind) {
                PlanningActionKind.CREATE_EVENT -> Icons.Default.CalendarMonth
                PlanningActionKind.CREATE_TODO -> Icons.Default.TaskAlt
                PlanningActionKind.CREATE_GROCERY -> Icons.Default.CheckCircle
                PlanningActionKind.CREATE_NOTE -> Icons.Default.CheckCircle
                PlanningActionKind.SET_REMINDER -> Icons.Default.Notifications
                PlanningActionKind.NAVIGATE -> Icons.Default.ArrowForward
            }
            Icon(icon, null, tint = Color(0xFF598FDB))
            Column(Modifier.weight(1f)) {
                Text(action.title, fontWeight = FontWeight.SemiBold)
                Text(action.subtitle, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.kidBoxColors.subtitle)
            }
            TextButton(
                onClick = {
                    if (action.kind == PlanningActionKind.NAVIGATE) onNavigate(action.navigationTarget) else onConfirm(action)
                },
                modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), RoundedCornerShape(16.dp)),
            ) {
                Text(
                    if (done) stringResource(R.string.ai_done_check) else when (action.kind) {
                        PlanningActionKind.CREATE_EVENT -> stringResource(R.string.ai_create)
                        PlanningActionKind.CREATE_TODO -> stringResource(R.string.ai_add)
                        PlanningActionKind.CREATE_GROCERY -> stringResource(R.string.ai_add)
                        PlanningActionKind.CREATE_NOTE -> stringResource(R.string.chat_save)
                        PlanningActionKind.SET_REMINDER -> stringResource(R.string.ai_activate)
                        PlanningActionKind.NAVIGATE -> stringResource(R.string.ai_go)
                    },
                )
            }
        }
    }
}
