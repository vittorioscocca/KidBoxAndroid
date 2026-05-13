package it.vittorioscocca.kidbox.ui.screens.passwords

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.dao.PasswordEntryDao
import it.vittorioscocca.kidbox.data.local.dao.PasswordGroupDao
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.feature.passwords.io.ImportPreview
import it.vittorioscocca.kidbox.feature.passwords.io.MergeStrategy
import it.vittorioscocca.kidbox.feature.passwords.io.PasswordsTxtExporter
import it.vittorioscocca.kidbox.feature.passwords.io.PasswordsTxtImporter
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class PasswordsImportExportUiState(
    val loading: Boolean = false,
    val exportUri: Uri? = null,
    val preview: ImportPreview? = null,
    val error: String? = null,
)

@HiltViewModel
class PasswordsImportExportViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
    private val cypher: PasswordCypher,
    private val entryDao: PasswordEntryDao,
    private val groupDao: PasswordGroupDao,
    private val repository: PasswordsRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PasswordsImportExportUiState())
    val uiState: StateFlow<PasswordsImportExportUiState> = _uiState.asStateFlow()

    fun export(familyId: String, familyName: String?, passphrase: String?) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                val entries = entryDao.observeVisibleByFamily(familyId, uid).first()
                val groups = groupDao.observeVisibleByFamily(familyId, uid).first()
                PasswordsTxtExporter(context, cypher).export(entries, groups, familyName, passphrase, uid)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(loading = false, exportUri = it)
            }.onFailure {
                _uiState.value = _uiState.value.copy(loading = false, error = it.message)
            }
        }
    }

    fun parse(familyId: String, uri: Uri, passphrase: String?) {
        val uid = auth.currentUser?.uid.orEmpty()
        if (uid.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                PasswordsTxtImporter(context, cypher, entryDao, groupDao, repository).parse(uri, passphrase, uid, familyId)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(loading = false, preview = it)
            }.onFailure {
                _uiState.value = _uiState.value.copy(loading = false, error = it.message)
            }
        }
    }

    fun commit(familyId: String, strategy: MergeStrategy) {
        val uid = auth.currentUser?.uid.orEmpty()
        val preview = _uiState.value.preview ?: return
        if (uid.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(loading = true, error = null)
            runCatching {
                PasswordsTxtImporter(context, cypher, entryDao, groupDao, repository).commit(preview, strategy, uid, familyId)
            }.onSuccess {
                _uiState.value = _uiState.value.copy(loading = false, preview = null)
                repository.scheduleAutofillSnapshotRebuild()
            }.onFailure {
                _uiState.value = _uiState.value.copy(loading = false, error = it.message)
            }
        }
    }

    fun clearPreview() {
        _uiState.value = _uiState.value.copy(preview = null)
    }

    fun clearExportUri() {
        _uiState.value = _uiState.value.copy(exportUri = null)
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }
}
