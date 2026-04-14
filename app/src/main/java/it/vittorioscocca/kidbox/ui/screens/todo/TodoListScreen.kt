package it.vittorioscocca.kidbox.ui.screens.todo

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.ui.theme.kidBoxColors
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar

@Composable
fun TodoListScreen(
    onBack: () -> Unit,
    viewModel: TodoListViewModel = hiltViewModel(),
) {
    val kb = MaterialTheme.kidBoxColors
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showEditor by remember { mutableStateOf(false) }
    var editingTodo by remember { mutableStateOf<KBTodoItemEntity?>(null) }
    var pendingSaveAfterPermission by remember { mutableStateOf<TodoEditForm?>(null) }
    var pendingSnackbarMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingSnackbarMessage) {
        val message = pendingSnackbarMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        pendingSnackbarMessage = null
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        val form = pendingSaveAfterPermission ?: return@rememberLauncherForActivityResult
        pendingSaveAfterPermission = null
        val effective = form.copy(reminderEnabled = granted && form.reminderEnabled)
        val editing = editingTodo
        if (editing == null) {
            viewModel.addTodo(
                title = effective.title,
                notes = effective.notes,
                dueAtEpochMillis = effective.dueAt,
                assignedTo = effective.assignedTo,
                priorityRaw = if (effective.urgent) 1 else 0,
                reminderEnabled = effective.reminderEnabled,
            )
        } else {
            viewModel.updateTodo(
                todoId = editing.id,
                title = effective.title,
                notes = effective.notes,
                dueAtEpochMillis = effective.dueAt,
                assignedTo = effective.assignedTo,
                priorityRaw = if (effective.urgent) 1 else 0,
                reminderEnabled = effective.reminderEnabled,
            )
        }
        if (effective.reminderEnabled && effective.dueAt != null) {
            pendingSnackbarMessage = "Promemoria programmato per ${formatDate(effective.dueAt)}"
        }
        showEditor = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(kb.background),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeaderCircleButton(icon = Icons.AutoMirrored.Filled.ArrowBack, onClick = onBack)
                if (state.smartKind == null) {
                    HeaderCircleButton(
                        icon = Icons.Filled.Add,
                        onClick = {
                            editingTodo = null
                            showEditor = true
                        },
                    )
                } else {
                    Spacer(modifier = Modifier.size(44.dp))
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(state.listName, fontSize = 38.sp, fontWeight = FontWeight.ExtraBold, color = kb.title)
            Spacer(Modifier.height(16.dp))

            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxSize()) {
                if (state.filteredTodos.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = kb.card),
                        ) {
                            Text(
                                "Nessun elemento",
                                modifier = Modifier.padding(16.dp),
                                color = kb.subtitle,
                            )
                        }
                    }
                } else {
                    items(state.filteredTodos, key = { it.id }) { todo ->
                        TodoRow(
                            todo = todo,
                            assigneeName = state.members.firstOrNull { it.uid == todo.assignedTo }?.displayName,
                            highlighted = state.highlightTodoId == todo.id,
                            onToggle = { viewModel.toggleDone(todo.id) },
                            onEdit = {
                                editingTodo = todo
                                showEditor = true
                            },
                            onDelete = { viewModel.deleteTodo(todo.id) },
                        )
                    }
                }
                item { Spacer(Modifier.height(24.dp)) }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        )
    }

    if (showEditor) {
        TodoEditDialog(
            initial = editingTodo,
            members = state.members,
            onDismiss = { showEditor = false },
            onSave = { form ->
                val mustAskPermission = form.reminderEnabled &&
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                if (mustAskPermission) {
                    pendingSaveAfterPermission = form
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    val editing = editingTodo
                    if (editing == null) {
                        viewModel.addTodo(
                            title = form.title,
                            notes = form.notes,
                            dueAtEpochMillis = form.dueAt,
                            assignedTo = form.assignedTo,
                            priorityRaw = if (form.urgent) 1 else 0,
                            reminderEnabled = form.reminderEnabled,
                        )
                    } else {
                        viewModel.updateTodo(
                            todoId = editing.id,
                            title = form.title,
                            notes = form.notes,
                            dueAtEpochMillis = form.dueAt,
                            assignedTo = form.assignedTo,
                            priorityRaw = if (form.urgent) 1 else 0,
                            reminderEnabled = form.reminderEnabled,
                        )
                    }
                    if (form.reminderEnabled && form.dueAt != null) {
                        pendingSnackbarMessage = "Promemoria programmato per ${formatDate(form.dueAt)}"
                    }
                    showEditor = false
                }
            },
        )
    }
}

@Composable
private fun TodoRow(
    todo: KBTodoItemEntity,
    assigneeName: String?,
    highlighted: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val isUrgent = (todo.priorityRaw ?: 0) == 1
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(if (todo.isDone) 0.8f else 1f)
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = if (highlighted) Color(0xFFFFF8D8) else MaterialTheme.kidBoxColors.card),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            IconButton(onClick = onToggle) {
                Icon(
                    imageVector = if (todo.isDone) Icons.Filled.CheckCircle else Icons.Filled.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (todo.isDone) MaterialTheme.kidBoxColors.title else Color(0xFFB9BDC6),
                    modifier = Modifier.size(20.dp),
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = todo.title,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                    color = if (todo.isDone) MaterialTheme.kidBoxColors.subtitle else MaterialTheme.kidBoxColors.title,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (todo.reminderEnabled) {
                        Icon(
                            imageVector = Icons.Filled.Alarm,
                            contentDescription = "Promemoria attivo",
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                val info = buildString {
                    if (!assigneeName.isNullOrBlank()) append(assigneeName)
                    if (todo.dueAtEpochMillis != null) {
                        if (isNotEmpty()) append(" • ")
                        append(formatDate(todo.dueAtEpochMillis))
                    }
                }
                if (info.isNotBlank()) {
                    Text(info, color = MaterialTheme.kidBoxColors.subtitle, fontSize = 13.sp)
                }
                if (isUrgent) {
                    Text(
                        "Urgente",
                        color = Color(0xFFE5484D),
                        fontSize = 11.sp,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .background(Color(0xFFFFE8EA), RoundedCornerShape(100.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            TextButton(onClick = onDelete) { Text("Elimina", color = Color(0xFFD92323)) }
        }
    }
}

private data class TodoEditForm(
    val title: String,
    val notes: String?,
    val dueAt: Long?,
    val assignedTo: String?,
    val urgent: Boolean,
    val reminderEnabled: Boolean,
)

@Composable
private fun TodoEditDialog(
    initial: KBTodoItemEntity?,
    members: List<TodoMemberUi>,
    onDismiss: () -> Unit,
    onSave: (TodoEditForm) -> Unit,
) {
    val context = LocalContext.current
    var title by remember(initial?.id) { mutableStateOf(initial?.title.orEmpty()) }
    var notes by remember(initial?.id) { mutableStateOf(initial?.notes.orEmpty()) }
    var dueEnabled by remember(initial?.id) { mutableStateOf(initial?.dueAtEpochMillis != null) }
    var dueAt by remember(initial?.id) { mutableLongStateOf(initial?.dueAtEpochMillis ?: System.currentTimeMillis()) }
    var reminderEnabled by remember(initial?.id) { mutableStateOf(initial?.reminderEnabled == true && initial.dueAtEpochMillis != null) }
    var showReminderConfirm by remember { mutableStateOf(false) }
    var urgent by remember(initial?.id) { mutableStateOf((initial?.priorityRaw ?: 0) == 1) }
    var assignedTo by remember(initial?.id) { mutableStateOf(initial?.assignedTo) }
    var showAssigneePicker by remember { mutableStateOf(false) }

    fun pickDate() {
        val cal = Calendar.getInstance().apply { timeInMillis = dueAt }
        DatePickerDialog(
            context,
            { _, year, month, dayOfMonth ->
                cal.set(Calendar.YEAR, year)
                cal.set(Calendar.MONTH, month)
                cal.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                dueAt = cal.timeInMillis
            },
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH),
            cal.get(Calendar.DAY_OF_MONTH),
        ).show()
    }

    fun pickTime() {
        val cal = Calendar.getInstance().apply { timeInMillis = dueAt }
        TimePickerDialog(
            context,
            { _, hourOfDay, minute ->
                cal.set(Calendar.HOUR_OF_DAY, hourOfDay)
                cal.set(Calendar.MINUTE, minute)
                cal.set(Calendar.SECOND, 0)
                cal.set(Calendar.MILLISECOND, 0)
                dueAt = cal.timeInMillis
            },
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            true,
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Nuovo To-Do" else "Modifica To-Do") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextField(value = title, onValueChange = { title = it }, placeholder = { Text("Titolo") })
                TextField(value = notes, onValueChange = { notes = it }, placeholder = { Text("Note") })
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Scadenza")
                    Switch(
                        checked = dueEnabled,
                        onCheckedChange = {
                            dueEnabled = it
                            if (!it) reminderEnabled = false
                        },
                    )
                }
                if (dueEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { pickDate() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.rowBackground),
                        ) {
                            Text(
                                text = formatDateOnly(dueAt),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                fontSize = 14.sp,
                            )
                        }
                        Card(
                            modifier = Modifier
                                .clickable { pickTime() },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.rowBackground),
                        ) {
                            Text(
                                text = formatTimeOnly(dueAt),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Promemoria")
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { enabled ->
                            if (!dueEnabled) {
                                reminderEnabled = false
                            } else if (enabled) {
                                showReminderConfirm = true
                            } else {
                                reminderEnabled = false
                            }
                        },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Text("Urgente")
                    Switch(checked = urgent, onCheckedChange = { urgent = it })
                }
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showAssigneePicker = true },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.kidBoxColors.rowBackground),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Assegnato a", fontSize = 13.sp, color = MaterialTheme.kidBoxColors.subtitle)
                        Text(
                            members.firstOrNull { it.uid == assignedTo }?.displayName ?: "Nessuno",
                            fontSize = 15.sp,
                            color = MaterialTheme.kidBoxColors.title,
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val cleanTitle = title.trim()
                    if (cleanTitle.isBlank()) return@TextButton
                    onSave(
                        TodoEditForm(
                            title = cleanTitle,
                            notes = notes.trim().takeIf { it.isNotEmpty() },
                            dueAt = if (dueEnabled) dueAt else null,
                            assignedTo = assignedTo,
                            urgent = urgent,
                            reminderEnabled = dueEnabled && reminderEnabled,
                        ),
                    )
                },
            ) { Text("Salva") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annulla") } },
    )

    if (showReminderConfirm) {
        AlertDialog(
            onDismissRequest = { showReminderConfirm = false },
            title = { Text("Creare un promemoria?") },
            text = { Text("Vuoi ricevere una notifica locale il ${formatDate(dueAt)}?") },
            dismissButton = {
                TextButton(onClick = {
                    reminderEnabled = false
                    showReminderConfirm = false
                }) { Text("No") }
            },
            confirmButton = {
                TextButton(onClick = {
                    reminderEnabled = true
                    showReminderConfirm = false
                }) { Text("Sì") }
            },
        )
    }

    if (showAssigneePicker) {
        AlertDialog(
            onDismissRequest = { showAssigneePicker = false },
            title = { Text("Assegna a") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Nessuno",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                assignedTo = null
                                showAssigneePicker = false
                            }
                            .padding(vertical = 8.dp),
                    )
                    members.forEach { member ->
                        Text(
                            text = member.displayName,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    assignedTo = member.uid
                                    showAssigneePicker = false
                                }
                                .padding(vertical = 8.dp),
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAssigneePicker = false }) { Text("Chiudi") } },
        )
    }
}

private fun formatDate(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM 'at' HH:mm")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

private fun formatDateOnly(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

private fun formatTimeOnly(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofPattern("HH:mm")
    return Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(formatter)
}

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
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}
