package it.vittorioscocca.kidbox.ui.screens.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
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

/**
 * Genera il QR invite code per la famiglia corrente.
 * Usa AndroidViewModel per accedere al Context (necessario per FamilyKeyStore).
 */
@HiltViewModel
class InviteCodeViewModel @Inject constructor(
    application: Application,
    private val familyDao: KBFamilyDao,
) : AndroidViewModel(application) {

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

                // 1) Membership invite code (classico)
                val newCode = remote.createInviteCode(familyId = familyId)
                _code.value = newCode

                // 2) Crypto-wrapped invite (include family key cifrata)
                val invite = wrapService.createInvite(
                    context = getApplication(),
                    familyId = familyId,
                    ttlSeconds = 86400,
                )

                // QR payload = crypto invite + membership code
                _qrPayload.value = invite.qrPayload + "&code=$newCode"

            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Errore generazione invito"
            } finally {
                _isBusy.value = false
            }
        }
    }
}