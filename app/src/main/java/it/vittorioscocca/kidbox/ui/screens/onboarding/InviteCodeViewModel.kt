package it.vittorioscocca.kidbox.ui.screens.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.data.remote.family.InviteRemoteStore
import it.vittorioscocca.kidbox.data.remote.family.InviteWrapService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
    private val db = FirebaseFirestore.getInstance()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _qrPayload = MutableStateFlow<String?>(null)
    val qrPayload: StateFlow<String?> = _qrPayload.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _code = MutableStateFlow<String?>(null)
    val code: StateFlow<String?> = _code.asStateFlow()

    /** Allineato a InviteWrapService / iOS: `families/{familyId}/invites/{inviteId}` */
    private val _currentInviteFamilyId = MutableStateFlow<String?>(null)
    val currentInviteFamilyId: StateFlow<String?> = _currentInviteFamilyId.asStateFlow()

    private val _currentInviteId = MutableStateFlow<String?>(null)
    val currentInviteId: StateFlow<String?> = _currentInviteId.asStateFlow()

    init {
        generateInviteCode()
    }

    /**
     * Elimina il documento invite su Firestore (stesso path di iOS JoinWrapService).
     * resetInviteUiState (QR e invite id) viene chiamato solo dopo delete riuscito,
     * così in caso di errore di rete la UI resta sul codice attuale.
     */
    fun revokeInvite(familyId: String, inviteId: String) {
        if (familyId.isBlank() || inviteId.isBlank()) return
        viewModelScope.launch {
            _isBusy.value = true
            _errorMessage.value = null
            try {
                db.collection("families")
                    .document(familyId)
                    .collection("invites")
                    .document(inviteId)
                    .delete()
                    .await()
                if (_currentInviteFamilyId.value == familyId && _currentInviteId.value == inviteId) {
                    resetInviteUiState()
                }
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Errore revoca invito"
            } finally {
                _isBusy.value = false
            }
        }
    }

    private fun resetInviteUiState() {
        _qrPayload.value = null
        _code.value = null
        _currentInviteFamilyId.value = null
        _currentInviteId.value = null
        _errorMessage.value = null
    }

    fun generateInviteCode() {
        _isBusy.value = true
        _errorMessage.value = null
        _qrPayload.value = null
        _code.value = null
        _currentInviteFamilyId.value = null
        _currentInviteId.value = null
        viewModelScope.launch {
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
                _currentInviteFamilyId.value = familyId
                _currentInviteId.value = invite.inviteId

                // URL completo allineato a iOS JoinWrapService / InviteWrapService:
                // kidbox://join?familyId=&inviteId=&secret=[BASE64URL]&code=
                _qrPayload.value =
                    "kidbox://join?familyId=$familyId&inviteId=${invite.inviteId}&secret=${invite.secretBase64url}&code=$newCode"

            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Errore generazione invito"
            } finally {
                _isBusy.value = false
            }
        }
    }
}