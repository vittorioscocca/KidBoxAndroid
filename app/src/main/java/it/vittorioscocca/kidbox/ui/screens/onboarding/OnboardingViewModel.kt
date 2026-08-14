package it.vittorioscocca.kidbox.ui.screens.onboarding

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.remote.family.FamilyFirestoreCreationRepository
import it.vittorioscocca.kidbox.data.remote.family.isPermissionDenied
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
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
                // Nessun retry in questo percorso: un permission-denied qui è
                // firestore.rules che rifiuta la creazione per limite famiglie raggiunto
                // (vedi ownedFamilyCount() in firestore.rules) — non un problema transitorio.
                _createFamilyError.value = if (isPermissionDenied(e)) {
                    appContext.getString(R.string.settings_family_limit_reached)
                } else {
                    e.localizedMessage ?: "Errore durante la creazione"
                }
            } finally {
                _isCreatingFamily.value = false
            }
        }
    }
}
