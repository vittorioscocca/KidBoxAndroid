package it.vittorioscocca.kidbox.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.user.UserProfileRepository
import it.vittorioscocca.kidbox.domain.auth.LogoutUseCase
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val familyAddress: String = "",
    val email: String = "",
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val saveError: String? = null,
    val saveSucceeded: Boolean = false,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val userProfileRepository: UserProfileRepository,
    private val auth: FirebaseAuth,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun onScreenVisible() {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid ?: run {
                _uiState.update { it.copy(isLoading = false) }
                return@launch
            }
            userProfileRepository.ensureSeededFromAuth()
            val email = auth.currentUser?.email.orEmpty()
            val local = userProfileRepository.getByUid(uid)
            var first = local?.firstName.orEmpty()
            var last = local?.lastName.orEmpty()
            var addr = local?.familyAddress.orEmpty()
            userProfileRepository.fetchRemoteProfileFields(uid)?.let { r ->
                if (first.isBlank() && !r.firstName.isNullOrBlank()) first = r.firstName
                if (last.isBlank() && !r.lastName.isNullOrBlank()) last = r.lastName
                if (addr.isBlank() && !r.familyAddress.isNullOrBlank()) addr = r.familyAddress
            }
            _uiState.update {
                it.copy(
                    firstName = first,
                    lastName = last,
                    familyAddress = addr,
                    email = email,
                    isLoading = false,
                    saveSucceeded = false,
                    saveError = null,
                )
            }
        }
    }

    fun setFirstName(value: String) {
        _uiState.update { it.copy(firstName = value, saveSucceeded = false) }
    }

    fun setLastName(value: String) {
        _uiState.update { it.copy(lastName = value, saveSucceeded = false) }
    }

    fun setFamilyAddress(value: String) {
        _uiState.update { it.copy(familyAddress = value, saveSucceeded = false) }
    }

    fun save() {
        val s = _uiState.value
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, saveError = null, saveSucceeded = false) }
            try {
                userProfileRepository.saveLocalProfile(s.firstName, s.lastName, s.familyAddress)
                _uiState.update { it.copy(isSaving = false, saveSucceeded = true) }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saveError = e.localizedMessage ?: "Errore salvataggio",
                    )
                }
            }
        }
    }

    fun logout(onDone: () -> Unit) {
        viewModelScope.launch {
            logoutUseCase.logout()
            onDone()
        }
    }
}
