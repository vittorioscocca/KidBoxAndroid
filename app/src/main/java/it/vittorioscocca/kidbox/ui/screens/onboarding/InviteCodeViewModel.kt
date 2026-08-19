package it.vittorioscocca.kidbox.ui.screens.onboarding

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.ActiveFamilyResolver
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.data.remote.family.InviteRemoteStore
import it.vittorioscocca.kidbox.data.remote.family.InviteWrapService
import it.vittorioscocca.kidbox.data.user.UserProfileRepository
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
    private val familySessionPreferences: FamilySessionPreferences,
    private val userProfileRepository: UserProfileRepository,
    private val auth: FirebaseAuth,
) : AndroidViewModel(application) {

    private val remote = InviteRemoteStore()
    private val wrapService = InviteWrapService()
    private val db get() = FirebaseFirestore.getInstance()

    private val _isBusy = MutableStateFlow(false)
    val isBusy: StateFlow<Boolean> = _isBusy.asStateFlow()

    private val _qrPayload = MutableStateFlow<String?>(null)
    val qrPayload: StateFlow<String?> = _qrPayload.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    /**
     * Link d'invito condivisibile: a differenza del solo `code`, trasporta anche
     * la chiave di cifratura (nel frammento dell'URL).
     */
    private val _shareLink = MutableStateFlow<String?>(null)
    val shareLink: StateFlow<String?> = _shareLink.asStateFlow()

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
        _shareLink.value = null
        _currentInviteFamilyId.value = null
        _currentInviteId.value = null
        _errorMessage.value = null
    }

    fun generateInviteCode(preferredFamilyId: String? = null) {
        _isBusy.value = true
        _errorMessage.value = null
        _qrPayload.value = null
        _shareLink.value = null
        _currentInviteFamilyId.value = null
        _currentInviteId.value = null
        viewModelScope.launch {
            try {
                val families = familyDao.observeAll().first()
                val familyId = preferredFamilyId
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: ActiveFamilyResolver.resolveFamilyId(
                        families,
                        familySessionPreferences.getActiveFamilyId(),
                    ).ifBlank { null }
                    ?: error("Nessuna family trovata.")
                val familyName = families.firstOrNull { it.id == familyId }?.name.orEmpty()

                val invite = wrapService.createInvite(
                    context = getApplication(),
                    familyId = familyId,
                    familyName = familyName,
                    inviterDisplayName = currentUserDisplayName(),
                    ttlSeconds = 86400,
                )
                _currentInviteFamilyId.value = familyId
                _currentInviteId.value = invite.inviteId

                // Il QR porta familyId + inviteId + secret: tutto ciò che serve
                // per entrare e sbloccare la chiave. Il vecchio `&code=` è
                // sparito insieme al codice testuale, che creava membri privi
                // di chiave. Allineato a iOS InviteWrapService.
                _qrPayload.value =
                    "kidbox://join?familyId=$familyId&inviteId=${invite.inviteId}&secret=${invite.secretBase64url}"
                _shareLink.value = invite.shareLink

            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Errore generazione invito"
            } finally {
                _isBusy.value = false
            }
        }
    }

    /**
     * Nome mostrato a chi riceve l'invito, prima ancora che entri. Il
     * profilo locale ha la precedenza; altrimenti si spezza il `displayName`
     * di Firebase Auth, come già fa `OnboardingNameViewModel`.
     */
    private suspend fun currentUserDisplayName(): String {
        val uid = auth.currentUser?.uid ?: return ""
        val local = runCatching { userProfileRepository.getByUid(uid) }.getOrNull()
        val name = local?.displayName?.trim().orEmpty()
        if (name.isNotEmpty() && name != "Utente") return name
        return auth.currentUser?.displayName?.trim().orEmpty()
    }
}