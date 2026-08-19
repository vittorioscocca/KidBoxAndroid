package it.vittorioscocca.kidbox.ui.screens.settings

import it.vittorioscocca.kidbox.util.KBLog

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import it.vittorioscocca.kidbox.data.remote.family.PendingFamilyInvite
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.remote.family.InviteRemoteStore
import it.vittorioscocca.kidbox.data.remote.family.JoinInviteError
import it.vittorioscocca.kidbox.data.remote.family.JoinWrapService
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.data.sync.FamilySyncCenter
import it.vittorioscocca.kidbox.data.user.UserProfileRepository
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
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
    /** Popolato solo dopo un join riuscito: serve all'onboarding per chiamare
     *  `onFamilyCreated(familyId)` e lasciare l'onboarding. */
    val joinedFamilyId: String? = null,
)

/**
 * Gestisce l'ingresso in famiglia, da link d'invito o da QR.
 *
 * Il codice testuale non esiste più: creava membri privi della chiave di
 * cifratura, che poi non potevano leggere password, documenti, wallet e chat.
 * Entrambe le strade rimaste trasportano la chiave.
 */
@HiltViewModel
class JoinFamilyViewModel @Inject constructor(
    application: Application,
    private val familySyncCenter: FamilySyncCenter,
    private val passwordsRepository: PasswordsRepository,
    private val userProfileRepository: UserProfileRepository,
) : AndroidViewModel(application) {

    private val inviteRemote = InviteRemoteStore()
    private val joinWrapService = JoinWrapService()

    /** familyId del join in corso, ripubblicato da [acknowledgeMissingVaultKey]. */
    private var pendingFamilyId: String? = null

    private val _uiState = MutableStateFlow(JoinFamilyUiState())
    val uiState: StateFlow<JoinFamilyUiState> = _uiState.asStateFlow()

    /**
     * Chiamato dopo la lettura di un QR.
     *
     * Il codice testuale non esiste più: prima questo flusso sbloccava la chiave,
     * poi estraeva un `code` dal payload per creare la membership — pur avendo già
     * `familyId` sotto mano. Ora QR e link d'invito passano dallo stesso percorso.
     */
    fun onQRScanned(rawPayload: String, onJoined: () -> Unit) {
        val invite = PendingFamilyInvite.parseQrPayload(rawPayload)
        if (invite == null) {
            _uiState.value = JoinFamilyUiState(
                error = getApplication<Application>().getString(R.string.settings_join_invalid_qr),
            )
            return
        }
        joinFromInvite(invite, onJoined)
    }

    /**
     * Entra in famiglia da un invito, che arrivi dal QR o dal link.
     *
     * L'ordine dei due passi non è indifferente: **prima la chiave, poi la
     * membership**. `JoinWrapService` valida il segreto in transazione e marca
     * l'invito come usato; se fallisce (scaduto, già usato, segreto errato) non
     * si deve entrare affatto, altrimenti si ricadrebbe nello stato "membro
     * senza chiave" che questo lavoro serve a eliminare.
     */
    fun joinFromInvite(invite: PendingFamilyInvite, onJoined: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = JoinFamilyUiState(isBusy = true)
            AppAnalytics.familyJoinAttempted(getApplication())
            try {
                joinWrapService.join(getApplication(), invite.qrEquivalentPayload)
                KBLog.ui.info("invito: master key sbloccata familyId=${invite.familyId}", TAG)

                inviteRemote.addMember(invite.familyId)
                KBLog.ui.info("invito: membership creata familyId=${invite.familyId}", TAG)

                // `addMember` crea il membro senza `displayName`: senza questa
                // riga chi entra resta anonimo per gli altri. Sta qui e non nei
                // chiamanti perché questo è l'unico punto attraversato da tutte
                // le strade di join (QR, link nel wizard, link ad app avviata).
                runCatching { userProfileRepository.propagateDisplayNameToMember(invite.familyId) }
                    .onFailure { KBLog.ui.error("propagazione nome fallita: ${it.message}", TAG, it) }

                pendingFamilyId = invite.familyId
                _uiState.value = JoinFamilyUiState(
                    didJoin = true,
                    joinedFamilyId = invite.familyId,
                )

                familySyncCenter.stopSync()
                familySyncCenter.startSync(invite.familyId)

                withTimeoutOrNull(30_000) {
                    familySyncCenter.initialSyncDone.first { it }
                } ?: KBLog.ui.warning("initialSyncDone timeout familyId=${invite.familyId}", TAG)

                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                if (uid.isNotBlank()) {
                    withContext(Dispatchers.IO) {
                        runCatching { passwordsRepository.hydratePasswordRoomFromServer(invite.familyId) }
                            .onFailure { e ->
                                KBLog.ui.error("hydratePasswordRoomFromServer failed: ${e.message}", TAG, e)
                            }
                    }
                    withContext(Dispatchers.IO) {
                        passwordsRepository.awaitForceRestartRealtime(invite.familyId)
                    }
                }

                val vaultKeyAvailable = FamilyKeyStore.hasFamilyKey(getApplication(), invite.familyId, uid)
                AppAnalytics.familyJoined(getApplication(), vaultKeyAvailable)

                onJoined()
            } catch (e: Exception) {
                KBLog.ui.error("invito fallito: ${e.message}", TAG, e)
                val reason = when (e) {
                    is JoinInviteError.InvalidPayload -> "invalid_payload"
                    is JoinInviteError.Expired -> "expired"
                    is JoinInviteError.InvalidSecret -> "invalid_secret"
                    is JoinInviteError.AlreadyUsed -> "already_used"
                    else -> "unknown"
                }
                AppAnalytics.familyJoinFailed(getApplication(), reason)
                _uiState.value = JoinFamilyUiState(error = e.localizedMessage ?: "Errore invito")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}