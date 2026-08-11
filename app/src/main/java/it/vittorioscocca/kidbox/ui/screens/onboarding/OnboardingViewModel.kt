package it.vittorioscocca.kidbox.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.remote.family.FamilyFirestoreCreationRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val familyCreation: FamilyFirestoreCreationRepository,
) : ViewModel() {

    private val _createdFamilyId = MutableStateFlow<String?>(null)
    val createdFamilyId: StateFlow<String?> = _createdFamilyId.asStateFlow()

    private val _isCreatingFamily = MutableStateFlow(false)
    val isCreatingFamily: StateFlow<Boolean> = _isCreatingFamily.asStateFlow()

    private val _createFamilyError = MutableStateFlow<String?>(null)
    val createFamilyError: StateFlow<String?> = _createFamilyError.asStateFlow()

    fun clearCreateError() {
        _createFamilyError.value = null
    }

    fun createFamily(
        familyName: String,
        childName: String,
        birthDateMillis: Long?,
    ) {
        val name = familyName.trim()
        val child = childName.trim()
        // Solo il nome della famiglia è obbligatorio: senza, non c'è niente da
        // creare. Il figlio è facoltativo — il repository scarta da sé i nomi
        // vuoti, quindi la famiglia nasce semplicemente senza figli e se ne
        // aggiungono dopo. Bloccare qui il wizard costringeva a inventare un
        // nome per superare la schermata.
        if (name.isEmpty()) return
        viewModelScope.launch {
            _isCreatingFamily.value = true
            _createFamilyError.value = null
            try {
                val id = familyCreation.createFamilyWithInitialChild(
                    familyName = name,
                    childName = child,
                    birthDateMillis = birthDateMillis,
                )
                _createdFamilyId.value = id
            } catch (e: Exception) {
                _createFamilyError.value = e.localizedMessage ?: "Errore durante la creazione"
            } finally {
                _isCreatingFamily.value = false
            }
        }
    }
}
