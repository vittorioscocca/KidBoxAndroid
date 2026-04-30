package it.vittorioscocca.kidbox.ui.screens.health

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class HealthSubject(
    val id: String,
    val name: String,
    val isChild: Boolean,
)

data class HealthSubjectSelectorState(
    val subjects: List<HealthSubject> = emptyList(),
    val isLoading: Boolean = true,
)

@HiltViewModel
class HealthSubjectSelectorViewModel @Inject constructor(
    private val childDao: KBChildDao,
    private val memberDao: KBFamilyMemberDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HealthSubjectSelectorState())
    val uiState: StateFlow<HealthSubjectSelectorState> = _uiState.asStateFlow()

    fun load(familyId: String) {
        viewModelScope.launch {
            combine(
                childDao.observeByFamilyId(familyId),
                memberDao.observeActiveByFamilyId(familyId),
            ) { children, members ->
                val mapped = mutableListOf<HealthSubject>()
                // Children first
                children.forEach { c ->
                    mapped += HealthSubject(id = c.id, name = c.name, isChild = true)
                }
                // Adult members (exclude those already covered as children)
                val childUserIds = children.map { it.id }.toSet()
                members.forEach { m ->
                    if (m.userId in childUserIds) return@forEach
                    val name = m.displayName?.takeIf { it.isNotBlank() } ?: "Membro"
                    mapped += HealthSubject(id = m.userId, name = name, isChild = false)
                }
                HealthSubjectSelectorState(subjects = mapped, isLoading = false)
            }.collect { _uiState.value = it }
        }
    }
}
