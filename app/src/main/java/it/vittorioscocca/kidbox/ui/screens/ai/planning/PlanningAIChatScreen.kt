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
import it.vittorioscocca.kidbox.ui.screens.ai.common.ClaudeMarkdownText
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun PlanningAIChatScreen(
    viewModel: PlanningAIChatViewModel = hiltViewModel(),
    onNavigateToCalendar: () -> Unit,
    onNavigateToTodo: () -> Unit,
    onNavigateToHealth: () -> Unit,
    onNavigateToUpgrade: () -> Unit,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val kb = MaterialTheme.kidBoxColors
    val listState = rememberLazyListState()
    val snackHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val hiddenActions = remember { mutableStateListOf<String>() }
    val doneActions = remember { mutableStateMapOf<String, Boolean>() }
    val reminderService = hiltViewModel<PlanningAIChatActionsViewModel>().reminderService
    var showMenu by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }

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
    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Assistente KidBox", color = kb.title, fontWeight = FontWeight.Bold)
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
                            Icon(Icons.Default.MoreVert, contentDescription = "Menu", tint = kb.title)
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
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val quick = listOf("Cosa ho questa settimana?", "Crea un evento", "Aggiungi to-do", "Cosa deve prendere mio figlio?", "Riepilogo spese")
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
                        placeholder = { Text("Chiedi qualcosa...") },
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
                        Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Invia", tint = Color(0xFF598FDB))
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
                                Icon(Icons.Default.Close, contentDescription = "Chiudi")
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
                        items(state.messages) { msg ->
                            AIChatBubbleView(msg)
                            if (msg.isAssistant) {
                                val actions = PlanningActionParser.parse(
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
                                                        snackHost.showSnackbar(result.toString())
                                                    }
                                                    PlanningActionKind.CREATE_EVENT -> {
                                                        onNavigateToCalendar()
                                                        snackHost.showSnackbar("Apri calendario per creare: ${it.prefilledEventTitle ?: it.subtitle}")
                                                    }
                                                    PlanningActionKind.CREATE_TODO -> {
                                                        onNavigateToTodo()
                                                        snackHost.showSnackbar("Apri to-do per aggiungere: ${it.prefilledTodoTitle ?: it.subtitle}")
                                                    }
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
                        Text("Assistente AI disponibile con il piano Pro", fontWeight = FontWeight.Bold, color = kb.title)
                        Spacer(Modifier.height(10.dp))
                        TextButton(onClick = onNavigateToUpgrade) { Text("Scopri i piani") }
                    }
                }
            }
        }
    }

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
fun AIChatBubbleView(message: KBAIMessage) {
    val isUser = message.isUser
    val maxUserWidth = LocalConfiguration.current.screenWidthDp.dp * 0.75f
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
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.widthIn(max = maxUserWidth),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Text(
                        message.content,
                        modifier = Modifier.padding(12.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
            } else {
                ClaudeMarkdownText(
                    message.content,
                    modifier = Modifier.fillMaxWidth(),
                )
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
                    if (done) "Fatto ✓" else when (action.kind) {
                        PlanningActionKind.CREATE_EVENT -> "Crea"
                        PlanningActionKind.CREATE_TODO -> "Aggiungi"
                        PlanningActionKind.SET_REMINDER -> "Attiva"
                        PlanningActionKind.NAVIGATE -> "Vai"
                    },
                )
            }
        }
    }
}
