package it.vittorioscocca.kidbox.ui.screens.todo

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.ui.navigation.AppDestination
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors

@Composable
fun TodoHomeScreen(
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    viewModel: TodoHomeViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showNewList by remember { mutableStateOf(false) }
    var listNameDraft by remember { mutableStateOf("") }
    var editingListId by remember { mutableStateOf<String?>(null) }
    var longPressListId by remember { mutableStateOf<String?>(null) }
    var longPressListName by remember { mutableStateOf("") }

    androidx.compose.runtime.LaunchedEffect(Unit) { viewModel.clearTodoBadge() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderCircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                HeaderCircleButton(
                    icon = Icons.Filled.Add,
                    onClick = {
                        editingListId = null
                        listNameDraft = ""
                        showNewList = true
                    },
                )
            }
            Spacer(Modifier.height(12.dp))
            Text("To-Do", fontSize = 40.sp, fontWeight = FontWeight.ExtraBold, color = kb.title)
            Spacer(Modifier.height(18.dp))
            Text("Panoramica", fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = kb.title)
            Spacer(Modifier.height(12.dp))

            val cards = listOf(
                TodoOverviewCard("Oggi", state.todayCount, TodoSmartKind.TODAY, Icons.Filled.CalendarMonth, Color(0xFFF59E0B)),
                TodoOverviewCard("Tutti", state.allCount, TodoSmartKind.ALL, Icons.Filled.List, Color(0xFF3B82F6)),
                TodoOverviewCard("Assegnati a me", state.activeTodos.count { it.assignedTo == state.currentUid }, TodoSmartKind.ASSIGNED_TO_ME, Icons.Filled.PersonAdd, Color(0xFF06B6D4)),
                TodoOverviewCard("Completati", state.completedCount, TodoSmartKind.COMPLETED, Icons.Filled.DoneAll, Color(0xFF22C55E)),
                TodoOverviewCard("Non assegnati a me", state.activeTodos.count { it.assignedTo != state.currentUid }, TodoSmartKind.NOT_ASSIGNED_TO_ME, Icons.Filled.PersonOff, Color(0xFFA855F7)),
                TodoOverviewCard("Non completati", state.notCompletedCount, TodoSmartKind.NOT_COMPLETED, Icons.Filled.RadioButtonUnchecked, Color(0xFFEF4444)),
            )
            cards.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                    row.forEach { card ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .height(112.dp)
                                .clickable {
                                    onNavigate(
                                        AppDestination.TodoSmart.createRoute(
                                            familyId = state.familyId,
                                            childId = state.childId,
                                            kind = card.kind,
                                        ),
                                    )
                                },
                            shape = RoundedCornerShape(18.dp),
                            colors = CardDefaults.cardColors(containerColor = kb.card),
                            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(14.dp),
                            ) {
                                Column(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.SpaceBetween,
                                ) {
                                    Icon(
                                        imageVector = card.icon,
                                        contentDescription = null,
                                        tint = card.iconColor,
                                        modifier = Modifier.size(18.dp),
                                    )
                                    Text(card.title, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = kb.title)
                                }
                                if (card.count > 0) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(24.dp)
                                            .background(card.iconColor, CircleShape),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            card.count.coerceAtMost(99).toString(),
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }

            Spacer(Modifier.height(8.dp))
            Text("Le mie liste", fontWeight = FontWeight.SemiBold, fontSize = 24.sp, color = kb.title)
            Spacer(Modifier.height(12.dp))
            if (state.lists.isEmpty()) {
                EmptyCard("Nessuna lista")
            } else {
                state.lists.forEach { list ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .todoListClickable(
                                onClick = {
                                    onNavigate(
                                        AppDestination.TodoList.createRoute(
                                            familyId = list.familyId,
                                            childId = list.childId,
                                            listId = list.id,
                                        ),
                                    )
                                },
                                onLongClick = {
                                    longPressListId = list.id
                                    longPressListName = list.name
                                },
                            ),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = kb.card),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(list.name, fontSize = 16.sp, color = kb.title, modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }

    if (showNewList) {
        AlertDialog(
            onDismissRequest = { showNewList = false },
            title = { Text(if (editingListId == null) "Nuova lista" else "Modifica lista") },
            text = {
                TextField(
                    value = listNameDraft,
                    onValueChange = { listNameDraft = it },
                    placeholder = { Text("Nome lista") },
                    singleLine = true,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = listNameDraft.trim()
                        if (value.isBlank()) return@TextButton
                        if (editingListId == null) viewModel.createList(value) else viewModel.renameList(editingListId!!, value)
                        showNewList = false
                    },
                ) { Text("Salva") }
            },
            dismissButton = {
                TextButton(onClick = { showNewList = false }) { Text("Annulla") }
            },
        )
    }

    if (longPressListId != null) {
        AlertDialog(
            onDismissRequest = {
                longPressListId = null
                longPressListName = ""
            },
            title = { Text(longPressListName) },
            text = { Text("Scegli azione per questa lista") },
            confirmButton = {
                TextButton(
                    onClick = {
                        editingListId = longPressListId
                        listNameDraft = longPressListName
                        showNewList = true
                        longPressListId = null
                        longPressListName = ""
                    },
                ) { Text("Modifica") }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            longPressListId?.let(viewModel::deleteList)
                            longPressListId = null
                            longPressListName = ""
                        },
                    ) { Text("Elimina", color = Color(0xFFD92323)) }
                    TextButton(
                        onClick = {
                            longPressListId = null
                            longPressListName = ""
                        },
                    ) { Text("Annulla") }
                }
            },
        )
    }
}

private data class TodoOverviewCard(
    val title: String,
    val count: Int,
    val kind: TodoSmartKind,
    val icon: ImageVector,
    val iconColor: Color,
)

@OptIn(ExperimentalFoundationApi::class)
private fun Modifier.todoListClickable(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
): Modifier = combinedClickable(
    onClick = onClick,
    onLongClick = onLongClick,
)

@Composable
private fun HeaderCircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Card(
        modifier = Modifier.size(44.dp).clickable(onClick = onClick),
        shape = CircleShape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.kidBoxColors.title)
        }
    }
}

@Composable
private fun EmptyCard(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.card),
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(16.dp),
            color = MaterialTheme.kidBoxColors.subtitle,
        )
    }
}
