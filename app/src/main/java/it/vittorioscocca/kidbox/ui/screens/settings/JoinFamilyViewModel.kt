package it.vittorioscocca.kidbox.ui.screens.settings

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.remote.family.InviteRemoteStore
import it.vittorioscocca.kidbox.data.remote.family.JoinPayloadParser
import it.vittorioscocca.kidbox.data.remote.family.JoinWrapService
import it.vittorioscocca.kidbox.data.sync.FamilySyncCenter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "JoinFamilyViewModel"

data class JoinFamilyUiState(
    val isBusy: Boolean = false,
    val didJoin: Boolean = false,
    val error: String? = null,
)

/**
 * Gestisce il join via QR code o codice testuale.
 * Logica identica a iOS JoinFamilyView + JoinWrapService:
 *
 * Via QR:
 * 1) JoinWrapService.join() — unwrap master key + salva in FamilyKeyStore
 * 2) JoinPayloadParser.extractInviteCode() — estrae membership code dal payload kidbox://join
 * 3) joinWithCode(code) — resolveInvite + addMember su Firestore
 *
 * Via codice testuale:
 * 1) joinWithCode(code) diretto
 */
@HiltViewModel
class JoinFamilyViewModel @Inject constructor(
    application: Application,
    private val familySyncCenter: FamilySyncCenter,
) : AndroidViewModel(application) {

    private val inviteRemote = InviteRemoteStore()
    private val joinWrapService = JoinWrapService()

    private val _uiState = MutableStateFlow(JoinFamilyUiState())
    val uiState: StateFlow<JoinFamilyUiState> = _uiState.asStateFlow()

    /**
     * Chiamato dopo che la camera ha letto un QR.
     * Flusso identico a iOS JoinFamilyView.QRScannerSheet.onDetected.
     */
    fun onQRScanned(rawPayload: String, onJoined: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = JoinFamilyUiState(isBusy = true)
            try {
                val raw = rawPayload.trim()
                // Step 1: unwrap master key (secret Base64URL da kidbox://join) + salva in FamilyKeyStore
                joinWrapService.join(getApplication(), raw)
                Log.i(TAG, "QR join: master key saved")

                // Step 2: estrai membership code dal payload (URL kidbox://join o codice plain)
                val code = JoinPayloadParser.extractInviteCode(raw)
                if (code.isNullOrBlank()) {
                    _uiState.value = JoinFamilyUiState(
                        error = "QR valido ma senza codice invito."
                    )
                    return@launch
                }

                // Step 3: join tramite membership code
                joinWithCodeInternal(code, onJoined)

            } catch (e: Exception) {
                Log.e(TAG, "QR join failed: ${e.message}")
                _uiState.value = JoinFamilyUiState(error = e.message ?: "Errore join QR")
            }
        }
    }

    /**
     * Chiamato dal bottone "Entra" con codice testuale.
     */
    fun joinWithCode(code: String, onJoined: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = JoinFamilyUiState(isBusy = true)
            joinWithCodeInternal(code.trim().uppercase(), onJoined)
        }
    }

    private suspend fun joinWithCodeInternal(code: String, onJoined: () -> Unit) {
        try {
            Log.d(TAG, "resolveInvite start code=$code")
            val familyId = inviteRemote.resolveInvite(code)
            Log.d(TAG, "resolveInvite OK familyId=$familyId")

            Log.d(TAG, "addMember start familyId=$familyId")
            inviteRemote.addMember(familyId)
            Log.d(TAG, "addMember OK")
            Log.i(TAG, "join OK familyId=$familyId")
            _uiState.value = JoinFamilyUiState(didJoin = true)
            // Reset FamilySyncCenter così startObserving() fa bootstrap della nuova famiglia
            familySyncCenter.stopSync()
            familySyncCenter.startSync(familyId)
            onJoined()
        } catch (e: Exception) {
            Log.e(TAG, "joinWithCode failed: ${e.message}")
            _uiState.value = JoinFamilyUiState(error = e.message ?: "Errore join")
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}