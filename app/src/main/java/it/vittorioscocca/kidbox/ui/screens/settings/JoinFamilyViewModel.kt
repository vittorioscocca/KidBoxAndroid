package it.vittorioscocca.kidbox.ui.screens.settings

import it.vittorioscocca.kidbox.util.KBLog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyEscrow
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import it.vittorioscocca.kidbox.data.remote.family.InviteRemoteStore
import it.vittorioscocca.kidbox.data.remote.family.JoinPayloadParser
import it.vittorioscocca.kidbox.data.remote.family.JoinWrapService
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.data.sync.FamilySyncCenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

private const val TAG = "JoinFamilyViewModel"

data class JoinFamilyUiState(
    val isBusy: Boolean = false,
    val didJoin: Boolean = false,
    val error: String? = null,
    /** Popolato solo dopo un join riuscito (QR o codice): serve all'onboarding
     *  per chiamare `onFamilyCreated(familyId)` e lasciare l'onboarding. */
    val joinedFamilyId: String? = null,
    /**
     * `true` quando il join è riuscito ma la master key della famiglia non è
     * disponibile: l'utente è membro a tutti gli effetti, però Password,
     * Documenti e Wallet condivisi restano illeggibili.
     *
     * Non è un errore — `error` resta null e `didJoin` è `true` — ma va detto
     * all'utente, altrimenti scopre da solo che metà app è vuota senza capire
     * perché. Prima questo caso finiva solo in un `KBLog.ui.error` invisibile.
     * Gemello di `FamilyJoinOutcome.missingVaultKey` su iOS.
     */
    val missingVaultKey: Boolean = false,
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
    private val passwordsRepository: PasswordsRepository,
) : AndroidViewModel(application) {

    private val inviteRemote = InviteRemoteStore()
    private val joinWrapService = JoinWrapService()

    /** familyId del join in corso, ripubblicato da [acknowledgeMissingVaultKey]. */
    private var pendingFamilyId: String? = null

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
                val parsedForFamilyCheck = joinWrapService.parse(raw)
                joinWrapService.join(getApplication(), raw)
                KBLog.ui.info("QR join: master key saved", TAG)

                // Step 2: estrai membership code dal payload (URL kidbox://join o codice plain)
                val code = JoinPayloadParser.extractInviteCode(raw)
                if (code.isNullOrBlank()) {
                    _uiState.value = JoinFamilyUiState(
                        error = "QR valido ma senza codice invito."
                    )
                    return@launch
                }

                // Step 3: join tramite membership code (stesso casing dei doc `invites/{CODE}` su Firestore)
                joinWithCodeInternal(
                    code = code.trim().uppercase(),
                    onJoined = onJoined,
                    expectedFamilyIdFromInviteQr = parsedForFamilyCheck?.familyId,
                    didCryptoUnwrap = true,
                )

            } catch (e: Exception) {
                KBLog.ui.error("QR join failed: ${e.message}", TAG)
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
            joinWithCodeInternal(
                code = code.trim().uppercase(),
                onJoined = onJoined,
                expectedFamilyIdFromInviteQr = null,
                didCryptoUnwrap = false,
            )
        }
    }

    private suspend fun joinWithCodeInternal(
        code: String,
        onJoined: () -> Unit,
        expectedFamilyIdFromInviteQr: String?,
        didCryptoUnwrap: Boolean,
    ) {
        try {
            KBLog.ui.debug("resolveInvite start code=$code", TAG)
            val familyId = inviteRemote.resolveInvite(code)
            KBLog.ui.debug("resolveInvite OK familyId=$familyId", TAG)

            if (expectedFamilyIdFromInviteQr != null && expectedFamilyIdFromInviteQr != familyId) {
                KBLog.ui.error("join aborted: invite QR familyId=$expectedFamilyIdFromInviteQr != code familyId=$familyId", TAG)
                _uiState.value = JoinFamilyUiState(
                    error = "Invito incoerente: il codice non corrisponde al QR.",
                )
                return
            }

            KBLog.ui.debug("addMember start familyId=$familyId", TAG)
            inviteRemote.addMember(familyId)
            KBLog.ui.debug("addMember OK", TAG)
            KBLog.ui.info("join OK familyId=$familyId", TAG)

            // ─────────────────────────────────────────────────────────────────
            // VERIFICA CHIAVE — deve stare QUI, prima di pubblicare
            // `joinedFamilyId`: l'onboarding esce dalla pagina osservando quel
            // campo (OnboardingScreen.kt:1111), quindi un controllo fatto più
            // tardi non riuscirebbe mai a mostrare l'avviso.
            // Non dipende dal sync: la chiave arriva dall'unwrap del QR (già
            // avvenuto) o dall'escrow, che richiede solo la membership appena
            // creata.
            // ─────────────────────────────────────────────────────────────────
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            var hasVaultKey = didCryptoUnwrap
            if (uid.isNotBlank() && !hasVaultKey) {
                hasVaultKey = withContext(Dispatchers.IO) {
                    FamilyKeyEscrow.ensureFamilyKeyAvailable(getApplication(), familyId, uid)
                } || FamilyKeyStore.hasFamilyKey(getApplication(), familyId, uid)
            }
            val vaultKeyMissing = uid.isNotBlank() && !hasVaultKey
            if (vaultKeyMissing) {
                KBLog.ui.error("join: membership code only — no vault key (use crypto invite QR) familyId=$familyId", TAG)
            }

            pendingFamilyId = familyId
            _uiState.value = JoinFamilyUiState(
                didJoin = true,
                // Senza chiave l'uscita non è automatica: l'avviso deve restare
                // leggibile. `joinedFamilyId` viene pubblicato dal pulsante
                // "Ho capito, continua" via [acknowledgeMissingVaultKey].
                joinedFamilyId = if (vaultKeyMissing) null else familyId,
                missingVaultKey = vaultKeyMissing,
            )

            // Reset FamilySyncCenter così startObserving() fa bootstrap della nuova famiglia
            familySyncCenter.stopSync()
            familySyncCenter.startSync(familyId)

            withTimeoutOrNull(30_000) {
                familySyncCenter.initialSyncDone.first { it }
            } ?: KBLog.ui.warning("initialSyncDone timeout after join familyId=$familyId — continuing", TAG)

            if (uid.isNotBlank() && hasVaultKey) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        passwordsRepository.hydratePasswordRoomFromServer(familyId)
                    }.onFailure { e ->
                        KBLog.ui.error("hydratePasswordRoomFromServer failed: ${e.message}", TAG, e)
                    }
                }
                withContext(Dispatchers.IO) {
                    passwordsRepository.awaitForceRestartRealtime(familyId)
                }
            }

            if (vaultKeyMissing) return

            onJoined()
        } catch (e: Exception) {
            KBLog.ui.error("joinWithCode failed: ${e.message}", TAG)
            _uiState.value = JoinFamilyUiState(error = e.message ?: "Errore join")
        }
    }

    /**
     * Chiamata dal pulsante "Ho capito, continua" mostrato con l'avviso di
     * chiave mancante: sblocca l'avanzamento che [joinWithCodeInternal] aveva
     * volutamente sospeso, pubblicando `joinedFamilyId` (che fa uscire
     * l'onboarding) e invocando la callback usata dalla schermata Impostazioni.
     */
    fun acknowledgeMissingVaultKey(onJoined: () -> Unit) {
        _uiState.value = _uiState.value.copy(
            missingVaultKey = false,
            joinedFamilyId = pendingFamilyId,
        )
        onJoined()
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}