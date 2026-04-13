package it.vittorioscocca.kidbox.ui.screens.todo

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.TodoRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class TodoHomeViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
    private val todoRepository: TodoRepository,
    private val badgeManager: HomeBadgeManager,
    private val auth: FirebaseAuth,
) : ViewModel() {
    private val error = MutableStateFlow<String?>(null)
    private val familyFlow = familyDao.observeAll()
    private val stateBacking = MutableStateFlow(TodoHomeUiState())
    private var observeJob: Job? = null
    val uiState: StateFlow<TodoHomeUiState> = stateBacking

    init {
        viewModelScope.launch {
            familyFlow.collect { families ->
                val familyId = families.firstOrNull()?.id.orEmpty()
                if (familyId.isBlank()) {
                    stateBacking.value = TodoHomeUiState(isLoading = false, errorMessage = "Nessuna famiglia attiva")
                    return@collect
                }
                val child = childDao.getChildrenByFamilyId(familyId).firstOrNull()
                val childId = child?.id.orEmpty()
                if (childId.isBlank()) {
                    stateBacking.value = TodoHomeUiState(familyId = familyId, isLoading = false, errorMessage = "Nessun profilo bambino trovato")
                    return@collect
                }
                observeFamilyTodo(familyId, childId)
            }
        }
    }

    private fun observeFamilyTodo(familyId: String, childId: String) {
        todoRepository.startRealtime(
            familyId = familyId,
            childId = childId,
            onPermissionDenied = { error.value = "Accesso To-Do negato per questa famiglia" },
        )
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                todoRepository.observeLists(familyId, childId),
                todoRepository.observeTodos(familyId, childId),
                memberDao.observeActiveByFamilyId(familyId),
                error,
            ) { lists, todos, members, err ->
                val meUid = auth.currentUser?.uid.orEmpty()
                TodoHomeUiState(
                    familyId = familyId,
                    childId = childId,
                    currentUid = meUid,
                    lists = lists,
                    todos = todos,
                    members = members.map { m ->
                        val label = if (m.userId == meUid) {
                            "Me"
                        } else {
                            m.displayName?.trim()?.takeIf { it.isNotEmpty() }
                                ?: m.email?.trim()?.takeIf { it.isNotEmpty() }
                                ?: "Membro"
                        }
                        TodoMemberUi(uid = m.userId, displayName = label)
                    },
                    isLoading = false,
                    errorMessage = err,
                )
            }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodoHomeUiState())
                .collect { stateBacking.value = it }
        }
    }

    fun clearTodoBadge() {
        val state = stateBacking.value
        if (state.familyId.isBlank()) return
        badgeManager.clearLocal(CounterField.TODOS)
        viewModelScope.launch { badgeManager.resetRemote(state.familyId, CounterField.TODOS) }
    }

    fun createList(name: String) {
        val state = stateBacking.value
        if (state.familyId.isBlank() || state.childId.isBlank()) return
        viewModelScope.launch {
            runCatching { todoRepository.addList(state.familyId, state.childId, name.trim()) }
                .onFailure { error.value = it.message ?: "Errore durante la creazione lista" }
        }
    }

    fun renameList(listId: String, name: String) {
        viewModelScope.launch {
            runCatching { todoRepository.updateListName(listId, name.trim()) }
                .onFailure { error.value = it.message ?: "Errore durante il rename lista" }
        }
    }

    fun deleteList(listId: String) {
        viewModelScope.launch {
            runCatching { todoRepository.deleteList(listId) }
                .onFailure { error.value = it.message ?: "Errore durante l'eliminazione lista" }
        }
    }

    override fun onCleared() {
        todoRepository.stopRealtime()
        super.onCleared()
    }
}
