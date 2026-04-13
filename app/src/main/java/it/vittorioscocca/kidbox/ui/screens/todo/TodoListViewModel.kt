package it.vittorioscocca.kidbox.ui.screens.todo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.repository.TodoRepository
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TodoListUiState(
    val familyId: String = "",
    val childId: String = "",
    val listId: String = "",
    val listName: String = "Lista",
    val currentUid: String = "",
    val todos: List<KBTodoItemEntity> = emptyList(),
    val filteredTodos: List<KBTodoItemEntity> = emptyList(),
    val members: List<TodoMemberUi> = emptyList(),
    val highlightTodoId: String? = null,
    val smartKind: TodoSmartKind? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class TodoListViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val todoRepository: TodoRepository,
    private val memberDao: KBFamilyMemberDao,
    private val auth: FirebaseAuth,
) : ViewModel() {
    private val familyId = savedStateHandle.get<String>("familyId").orEmpty()
    private val childId = savedStateHandle.get<String>("childId").orEmpty()
    private val listId = savedStateHandle.get<String>("listId").orEmpty()
    private val kind = savedStateHandle.get<String>("kind")?.let(TodoSmartKind::fromRaw)
    private val highlightTodoIdArg = savedStateHandle.get<String>("highlightTodoId")
    private val error = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TodoListUiState> = combine(
        todoRepository.observeLists(familyId, childId),
        todoRepository.observeTodos(familyId, childId),
        memberDao.observeActiveByFamilyId(familyId),
        error,
    ) { lists, todos, members, err ->
        val meUid = auth.currentUser?.uid.orEmpty()
        val memberItems = members.map { member ->
            TodoMemberUi(
                uid = member.userId,
                displayName = if (member.userId == meUid) {
                    "Me"
                } else {
                    member.displayName?.trim()?.takeIf { it.isNotEmpty() }
                        ?: member.email?.trim()?.takeIf { it.isNotEmpty() }
                        ?: "Membro"
                },
            )
        }.toMutableList()
        if (meUid.isNotBlank() && memberItems.none { it.uid == meUid }) {
            memberItems.add(0, TodoMemberUi(uid = meUid, displayName = "Me"))
        }
        val listName = lists.firstOrNull { it.id == listId }?.name ?: titleForKind(kind)
        val listScoped = if (kind == null) todos.filter { it.listId == listId } else todos
        val filtered = when (kind) {
            TodoSmartKind.TODAY -> {
                val dayStart = java.time.LocalDate.now().atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                val dayEnd = dayStart + 24L * 60L * 60L * 1000L
                listScoped.filter { !it.isDone && (it.dueAtEpochMillis ?: 0L) in dayStart until dayEnd }
            }

            TodoSmartKind.ALL -> listScoped.filter { !it.isDone }
            TodoSmartKind.ASSIGNED_TO_ME -> listScoped.filter { !it.isDone && it.assignedTo == meUid }
            TodoSmartKind.COMPLETED -> listScoped.filter { it.isDone }
            TodoSmartKind.NOT_ASSIGNED_TO_ME -> listScoped.filter { !it.isDone && it.assignedTo != meUid }
            TodoSmartKind.NOT_COMPLETED -> listScoped.filter { !it.isDone }
            null -> listScoped
        }
        TodoListUiState(
            familyId = familyId,
            childId = childId,
            listId = listId,
            listName = listName,
            currentUid = meUid,
            todos = listScoped,
            filteredTodos = filtered,
            members = memberItems,
            highlightTodoId = highlightTodoIdArg,
            smartKind = kind,
            isLoading = false,
            errorMessage = err,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoListUiState(isLoading = true))

    init {
        if (familyId.isNotBlank() && childId.isNotBlank()) {
            todoRepository.startRealtime(
                familyId = familyId,
                childId = childId,
                onPermissionDenied = { error.value = "Non hai accesso a questa lista To-Do" },
            )
        }
    }

    fun addTodo(
        title: String,
        notes: String?,
        dueAtEpochMillis: Long?,
        assignedTo: String?,
        priorityRaw: Int,
        reminderEnabled: Boolean,
    ) {
        if (familyId.isBlank() || childId.isBlank() || listId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                todoRepository.addTodo(
                    familyId = familyId,
                    childId = childId,
                    listId = listId,
                    title = title.trim(),
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    dueAtEpochMillis = dueAtEpochMillis,
                    assignedTo = assignedTo,
                    priorityRaw = priorityRaw,
                    reminderEnabled = reminderEnabled,
                )
            }.onFailure { error.value = it.message ?: "Errore durante il salvataggio To-Do" }
        }
    }

    fun updateTodo(
        todoId: String,
        title: String,
        notes: String?,
        dueAtEpochMillis: Long?,
        assignedTo: String?,
        priorityRaw: Int,
        reminderEnabled: Boolean,
    ) {
        viewModelScope.launch {
            runCatching {
                todoRepository.updateTodo(
                    todoId = todoId,
                    title = title.trim(),
                    notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                    dueAtEpochMillis = dueAtEpochMillis,
                    assignedTo = assignedTo,
                    priorityRaw = priorityRaw,
                    reminderEnabled = reminderEnabled,
                )
            }.onFailure { error.value = it.message ?: "Errore durante aggiornamento To-Do" }
        }
    }

    fun toggleDone(todoId: String) {
        viewModelScope.launch {
            runCatching { todoRepository.toggleTodoDone(todoId) }
                .onFailure { error.value = it.message ?: "Errore durante toggle completato" }
        }
    }

    fun deleteTodo(todoId: String) {
        viewModelScope.launch {
            runCatching { todoRepository.deleteTodo(todoId) }
                .onFailure { error.value = it.message ?: "Errore durante eliminazione To-Do" }
        }
    }

    override fun onCleared() {
        todoRepository.stopRealtime()
        super.onCleared()
    }

    private fun titleForKind(kind: TodoSmartKind?): String = when (kind) {
        TodoSmartKind.TODAY -> "Oggi"
        TodoSmartKind.ALL -> "Tutti"
        TodoSmartKind.ASSIGNED_TO_ME -> "Assegnati a me"
        TodoSmartKind.COMPLETED -> "Completati"
        TodoSmartKind.NOT_ASSIGNED_TO_ME -> "Non assegnati a me"
        TodoSmartKind.NOT_COMPLETED -> "Non completati"
        null -> "Lista"
    }
}
