package it.vittorioscocca.kidbox.ui.screens.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.remote.family.InviteRemoteStore
import it.vittorioscocca.kidbox.data.remote.family.InviteWrapService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class InviteCodeViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
) : ViewModel() {
    private val remote = InviteRemoteStore()
    private val wrapService = InviteWrapService()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _qrPayload = MutableStateFlow<String?>(null)
    val qrPayload: StateFlow<String?> = _qrPayload.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()

    fun generateInviteCode() {
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                val firstFamily = familyDao.observeAll().first().firstOrNull()
                    ?: error("Nessuna family trovata.")

                val familyId = firstFamily.id
                val newCode = remote.createInviteCode(familyId = familyId)
                _code.value = newCode

                val invite = wrapService.createInvite(familyId = familyId, ttlSeconds = 86400)
                _qrPayload.value = invite.qrPayload + "&code=$newCode"
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Errore generazione invito"
            } finally {
                _isBusy.value = false
            }
        }
    }
}
