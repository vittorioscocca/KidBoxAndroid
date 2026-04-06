package it.vittorioscocca.kidbox.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.domain.auth.LogoutUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    fun logout(onComplete: () -> Unit) {
        viewModelScope.launch {
            logoutUseCase.logout()
            onComplete()
        }
    }
}
