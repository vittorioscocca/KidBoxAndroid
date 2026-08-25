package it.vittorioscocca.kidbox.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import it.vittorioscocca.kidbox.data.repository.PhotoVideoRepository
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/**
 * Alimenta il selettore dei media KidBox da allegare in chat.
 *
 * Il `familyId` arriva dalla chat invece di essere ridedotto qui: la chat lo ha
 * già risolto, e ricalcolarlo per conto proprio significava avere una seconda
 * fonte di verità che poteva non combaciare — con la griglia che restava vuota
 * pur essendoci foto in KidBox.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class ChatKidBoxPickerViewModel @Inject constructor(
    private val photoVideoRepository: PhotoVideoRepository,
) : ViewModel() {

    private val familyIdFlow = MutableStateFlow<String?>(null)

    /**
     * `observeByFamilyId` filtra già gli eliminati e ordina dal più recente,
     * quindi qui non serve altro.
     */
    val photos: StateFlow<List<KBFamilyPhotoEntity>> = familyIdFlow
        .filterNotNull()
        .flatMapLatest { fid -> photoVideoRepository.observePhotos(fid) }
        .onEach { KBLog.app.debug("KidBox picker: ${it.size} media disponibili", "Chat") }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun bind(familyId: String) {
        if (familyId.isBlank() || familyIdFlow.value == familyId) return
        familyIdFlow.value = familyId
        // Il listener delle foto lo avviava solo la sezione Foto e Video:
        // aprendo il selettore dalla chat senza esserci mai passati, Room poteva
        // essere vuota. È idempotente, quindi si può invocare a ogni apertura.
        photoVideoRepository.startRealtime(familyId)
    }
}
