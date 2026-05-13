package it.vittorioscocca.kidbox.ui.screens.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.data.passwords.security.DuplicateDetector
import it.vittorioscocca.kidbox.data.passwords.security.NetworkMonitor
import it.vittorioscocca.kidbox.data.passwords.security.SecurityAuditService
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.feature.passwords.PasswordStrength
import it.vittorioscocca.kidbox.feature.passwords.PasswordStrengthLevel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PasswordCompromisedUi(val id: String, val title: String, val subtitle: String)
data class PasswordDuplicateClusterUi(val id: String, val header: String, val items: List<PasswordCompromisedUi>)
sealed interface ScanState {
    data object Idle : ScanState
    data object Running : ScanState
    data class Done(val compromised: Int, val duplicates: Int, val weak: Int, val unknown: Int) : ScanState
    data class Error(val message: String) : ScanState
}
data class PasswordsSecurityUiState(
    val compromised: List<PasswordCompromisedUi> = emptyList(),
    val duplicateClusters: List<PasswordDuplicateClusterUi> = emptyList(),
    val weak: List<PasswordCompromisedUi> = emptyList(),
    val isOffline: Boolean = false,
    val totalIssues: Int = 0,
)

@HiltViewModel
class PasswordsSecurityViewModel @Inject constructor(
    private val repository: PasswordsRepository,
    private val passwordCypher: PasswordCypher,
    private val auth: FirebaseAuth,
    private val duplicateDetector: DuplicateDetector,
    private val audit: SecurityAuditService,
    networkMonitor: NetworkMonitor,
) : ViewModel() {
    private val familyIdFlow = MutableStateFlow<String?>(null)
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    val uiState: StateFlow<PasswordsSecurityUiState> = familyIdFlow
        .filterNotNull()
        .flatMapLatest { familyId ->
            combine(repository.observeVisibleEntries(familyId), networkMonitor.isOnline) { entries, online ->
                buildState(familyId, entries, !online)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PasswordsSecurityUiState())

    fun bind(familyId: String) {
        familyIdFlow.value = familyId
    }

    fun rescan() {
        viewModelScope.launch {
            _scanState.value = ScanState.Running
            runCatching { audit.runFullScan() }
                .onSuccess { r ->
                    _scanState.value = ScanState.Done(r.compromised.size, r.duplicates.size, r.weak.size, r.unknown)
                }
                .onFailure { _scanState.value = ScanState.Error(it.localizedMessage ?: "Errore scansione") }
        }
    }

    private suspend fun buildState(
        familyId: String,
        entries: List<PasswordEntryEntity>,
        offline: Boolean,
    ): PasswordsSecurityUiState {
        val uid = auth.currentUser?.uid.orEmpty()
        val visible = entries.filter { it.deletedAtEpochMillis == null }
        val compromised = visible
            .filter { (it.pwnedCount ?: 0) > 0 }
            .mapNotNull { e ->
                val title = decryptOrNull(e.titleCipher, e, uid)?.ifBlank { null } ?: return@mapNotNull null
                PasswordCompromisedUi(
                    id = e.id,
                    title = title,
                    subtitle = "Trovata ${e.pwnedCount ?: 0} volte in data breach",
                )
            }
            .sortedBy { it.title.lowercase() }

        val clusters = duplicateDetector.allDuplicateClusters(familyId)
        val duplicates = clusters.mapIndexed { index, group ->
            PasswordDuplicateClusterUi(
                id = "cluster_$index",
                header = "Cluster da ${group.size} voci",
                items = group.mapNotNull { e ->
                    val title = decryptOrNull(e.titleCipher, e, uid)?.ifBlank { null } ?: return@mapNotNull null
                    PasswordCompromisedUi(e.id, title, "Password uguale ad altre credenziali")
                },
            )
        }.filter { it.items.size >= 2 }

        val weak = visible.mapNotNull { e ->
            val password = decryptOrNull(e.passwordCipher, e, uid) ?: return@mapNotNull null
            val strength = PasswordStrength.evaluate(password).level
            if (strength != PasswordStrengthLevel.WEAK && strength != PasswordStrengthLevel.VERY_WEAK) return@mapNotNull null
            val title = decryptOrNull(e.titleCipher, e, uid)?.ifBlank { null } ?: return@mapNotNull null
            PasswordCompromisedUi(e.id, title, "Password debole")
        }

        val totalIssues = compromised.size + weak.size + duplicates.sumOf { it.items.size }
        return PasswordsSecurityUiState(compromised, duplicates, weak, offline, totalIssues)
    }

    private fun decryptOrNull(cipher: ByteArray, entry: PasswordEntryEntity, uid: String): String? = runCatching {
        passwordCypher.decrypt(cipher, entry.familyId, entry.visibility, entry.createdBy, uid)
    }.getOrNull()
}
