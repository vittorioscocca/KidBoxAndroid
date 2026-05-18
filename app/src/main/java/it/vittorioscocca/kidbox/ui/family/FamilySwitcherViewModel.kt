package it.vittorioscocca.kidbox.ui.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.repository.AuthRepository
import it.vittorioscocca.kidbox.data.repository.FamilyRepository
import it.vittorioscocca.kidbox.data.sync.FamilySyncCenter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class FamilySwitcherViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familySyncCenter: FamilySyncCenter,
    private val familySessionPreferences: FamilySessionPreferences,
    private val familyRepository: FamilyRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val families: StateFlow<List<KBFamilyEntity>> = familyDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _activeFamilyId = MutableStateFlow(familySessionPreferences.getActiveFamilyId())
    val activeFamilyId: StateFlow<String?> = _activeFamilyId.asStateFlow()

    sealed class Event {
        data object SwitchComplete : Event()
        data class Error(val message: String) : Event()
        data object FamilyCreated : Event()
    }

    private val _events = MutableSharedFlow<Event>()
    val events: SharedFlow<Event> = _events.asSharedFlow()

    private val _isCreating = MutableStateFlow(false)
    val isCreating: StateFlow<Boolean> = _isCreating.asStateFlow()

    fun switchToFamily(familyId: String) {
        viewModelScope.launch {
            familySyncCenter.stopSync()
            familySessionPreferences.setActiveFamilyId(familyId)
            familySyncCenter.startSync(familyId)
            _activeFamilyId.value = familyId
            _events.emit(Event.SwitchComplete)
        }
    }

    fun createNewFamily(name: String) {
        viewModelScope.launch {
            _isCreating.value = true
            try {
                val uid = authRepository.currentUid
                if (uid.isNullOrBlank()) {
                    _events.emit(Event.Error("Utente non autenticato"))
                    return@launch
                }
                familyRepository.createNewFamily(name.trim(), uid)
                    .onSuccess { _events.emit(Event.FamilyCreated) }
                    .onFailure { err ->
                        _events.emit(Event.Error(err.message ?: "Errore durante la creazione"))
                    }
            } finally {
                _isCreating.value = false
            }
        }
    }
}
