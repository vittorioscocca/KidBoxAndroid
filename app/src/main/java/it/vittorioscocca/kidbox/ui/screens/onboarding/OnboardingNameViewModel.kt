package it.vittorioscocca.kidbox.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.user.UserProfileRepository
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "OnboardingNameVM"

data class OnboardingNameUiState(
    val firstName: String = "",
    val lastName: String = "",
    val isSaving: Boolean = false,
    val error: String? = null,
) {
    /**
     * Entrambi obbligatori: un `displayName` a metà sembra completo pur non
     * essendolo, ed è peggio del segnaposto.
     */
    val canSubmit: Boolean
        get() = firstName.isNotBlank() && lastName.isNotBlank() && !isSaving
}

/**
 * Raccoglie nome e cognome nel wizard, prima della pagina con i dati della
 * famiglia, e li salva con la stessa pipeline della sezione Profilo
 * ([UserProfileRepository.saveLocalProfile]): Room + `users/{uid}` + membro.
 *
 * Gemello di `NameOnboardingCard` + `UserProfileWriter` su iOS.
 */
@HiltViewModel
class OnboardingNameViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingNameUiState())
    val uiState: StateFlow<OnboardingNameUiState> = _uiState.asStateFlow()

    init {
        prefillFromExistingProfile()
    }

    fun setFirstName(value: String) = _uiState.update { it.copy(firstName = value, error = null) }

    fun setLastName(value: String) = _uiState.update { it.copy(lastName = value, error = null) }

    /**
     * Precompila da ciò che si sa già: prima il profilo locale, poi il
     * `displayName` di Firebase Auth, che con Google e Facebook arriva
     * valorizzato. Senza, chi entra con un social riscriverebbe un nome che
     * l'app conosce già.
     */
    private fun prefillFromExistingProfile() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: return@launch
            val local = runCatching { userProfileRepository.getByUid(uid) }.getOrNull()
            val fn = local?.firstName?.trim().orEmpty()
            val ln = local?.lastName?.trim().orEmpty()
            if (fn.isNotEmpty() || ln.isNotEmpty()) {
                _uiState.update { it.copy(firstName = fn, lastName = ln) }
                return@launch
            }

            val display = auth.currentUser?.displayName?.trim().orEmpty()
            if (display.isEmpty() || display == "Utente") return@launch
            val parts = display.split(" ").filter { it.isNotBlank() }
            if (parts.isEmpty()) return@launch
            _uiState.update {
                it.copy(
                    firstName = parts.first(),
                    lastName = parts.drop(1).joinToString(" "),
                )
            }
        }
    }

    /**
     * Salva e, solo a salvataggio riuscito, invoca [onSaved].
     *
     * Avanzare dopo un errore lascerebbe l'utente convinto di aver messo il
     * nome, e la famiglia nascerebbe comunque con un membro anonimo.
     */
    /**
     * Porta il nome sul documento membro, una volta che la famiglia esiste.
     *
     * Va chiamata dopo creazione o join: al momento del salvataggio (pagina 4)
     * la famiglia non c'è ancora, quindi il ramo di `saveLocalProfile` che
     * propaga al membro non può scattare.
     */
    fun propagateNameToMember(familyId: String) {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching { userProfileRepository.propagateDisplayNameToMember(familyId) }
                .onFailure {
                    KBLog.ui.error("Onboarding: member name propagation failed: ${it.message}", TAG, it)
                }
        }
    }

    fun save(onSaved: () -> Unit) {
        val s = _uiState.value
        if (!s.canSubmit) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            try {
                // L'indirizzo esistente va riletto e ripassato: `saveLocalProfile`
                // azzera `familyAddress` quando riceve stringa vuota, e qui non
                // abbiamo un campo indirizzo da cui prenderlo.
                val uid = auth.currentUser?.uid
                val currentAddress = uid
                    ?.let { runCatching { userProfileRepository.getByUid(it) }.getOrNull() }
                    ?.familyAddress
                    .orEmpty()

                userProfileRepository.saveLocalProfile(
                    firstName = s.firstName,
                    lastName = s.lastName,
                    familyAddress = currentAddress,
                )
                KBLog.ui.info("Onboarding: profile names saved", TAG)
                _uiState.update { it.copy(isSaving = false) }
                onSaved()
            } catch (e: Exception) {
                KBLog.ui.error("Onboarding: profile names save failed: ${e.message}", TAG, e)
                _uiState.update { it.copy(isSaving = false, error = e.localizedMessage) }
            }
        }
    }
}
