package it.vittorioscocca.kidbox.ui.screens.passwords

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.PasswordEntryDao
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import org.json.JSONArray
import it.vittorioscocca.kidbox.data.passwords.otp.OtpConfig
import it.vittorioscocca.kidbox.data.passwords.otp.OtpSecureStore
import it.vittorioscocca.kidbox.data.passwords.security.DuplicateDetector
import it.vittorioscocca.kidbox.data.repository.PasswordsRepository
import it.vittorioscocca.kidbox.feature.passwords.PasswordStrength
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class PasswordDetailUiState(
    val isLoading: Boolean = true,
    val notFound: Boolean = false,
    val id: String = "",
    val familyId: String = "",
    val title: String = "",
    val username: String = "",
    val password: String = "",
    val website: String = "",
    val notes: String = "",
    val iconUrl: String? = null,
    val isFavorite: Boolean = false,
    val pwnedCount: Int? = null,
    val pwnedCheckedAtLabel: String = "",
    val hasDuplicate: Boolean = false,
    val isVulnerable: Boolean = false,
    val estimatedBits: Int = 0,
    val strengthLabel: String = "",
    val strengthAdvice: String = "",
    val updatedAtLabel: String = "",
    val passwordUpdatedAtLabel: String = "",
    val createdBy: String = "",
    val currentUid: String = "",
    val canManage: Boolean = false,
    val visibilityLabel: String = "",
    val otpConfig: OtpConfig? = null,
)

@HiltViewModel
class PasswordDetailViewModel @Inject constructor(
    private val passwordEntryDao: PasswordEntryDao,
    private val passwordCypher: PasswordCypher,
    private val auth: FirebaseAuth,
    private val repository: PasswordsRepository,
    private val duplicateDetector: DuplicateDetector,
    private val otpStore: OtpSecureStore,
    private val familyMemberDao: KBFamilyMemberDao,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PasswordDetailUiState())
    val uiState: StateFlow<PasswordDetailUiState> = _uiState.asStateFlow()

    fun load(passwordId: String) {
        viewModelScope.launch {
            val entry = passwordEntryDao.getById(passwordId)
            if (entry == null) {
                _uiState.value = PasswordDetailUiState(isLoading = false, notFound = true)
                return@launch
            }
            val uid = auth.currentUser?.uid.orEmpty()
            val duplicates = duplicateDetector.duplicates(entry)
            val otpConfig = otpStore.loadOtpConfig(entry.id)
            val visibilityLabel = buildVisibilityLabel(entry, uid)
            _uiState.value = entry
                .toUiState(uid, passwordCypher, duplicates.isNotEmpty(), otpConfig)
                .copy(visibilityLabel = visibilityLabel)
        }
    }

    private suspend fun buildVisibilityLabel(entry: PasswordEntryEntity, currentUid: String): String =
        when (KBVisibilityScope.normalizedPassword(entry.visibility)) {
            KBVisibilityScope.ONLY_CREATOR -> "🔒 Solo io"
            KBVisibilityScope.MEMBERS -> {
                if (entry.createdBy == currentUid) {
                    // Sono il creatore: mostro con chi ho condiviso.
                    val names = parseMemberIds(entry.visibilityMemberIdsJson).mapNotNull { memberId ->
                        familyMemberDao.getActiveByFamilyAndUser(entry.familyId, memberId)
                            ?.displayName?.takeIf { it.isNotBlank() }
                    }
                    if (names.isNotEmpty()) {
                        "👥 Condiviso con ${names.joinToString(", ")}"
                    } else {
                        "👥 Condiviso con membri della famiglia"
                    }
                } else {
                    // Sono un destinatario: mostro chi me l'ha condivisa.
                    val creatorName = familyMemberDao.getActiveByFamilyAndUser(entry.familyId, entry.createdBy)
                        ?.displayName?.takeIf { it.isNotBlank() }
                    if (creatorName != null) {
                        "👥 Condiviso da $creatorName"
                    } else {
                        "👥 Condiviso da un membro della famiglia"
                    }
                }
            }
            else -> "👨‍👩‍👧 Condiviso in famiglia"
        }

    private fun parseMemberIds(json: String?): List<String> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { arr.optString(it, null)?.takeIf { s -> s.isNotBlank() } }
        }.getOrDefault(emptyList())
    }

    fun toggleFavorite() {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.id.isBlank()) return@launch
            val entry = passwordEntryDao.getById(state.id) ?: return@launch
            val updated = entry.copy(
                isFavorite = !entry.isFavorite,
                updatedAtEpochMillis = System.currentTimeMillis(),
                syncStateRaw = 1,
            )
            passwordEntryDao.upsert(updated)
            repository.pushUpsertEntry(updated)
            load(state.id)
        }
    }

    fun deleteCurrent(onDone: () -> Unit) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state.id.isBlank() || state.familyId.isBlank() || !state.canManage) return@launch
            repository.softDeleteEntriesOptimistic(state.familyId, listOf(state.id))
            onDone()
        }
    }

    fun saveOtpConfig(config: OtpConfig) {
        val id = _uiState.value.id
        if (id.isBlank()) return
        otpStore.saveOtpConfig(id, config)
        _uiState.value = _uiState.value.copy(otpConfig = otpStore.loadOtpConfig(id))
    }

    fun deleteOtpConfig() {
        val id = _uiState.value.id
        if (id.isBlank()) return
        otpStore.deleteOtpConfig(id)
        _uiState.value = _uiState.value.copy(otpConfig = null)
    }
}

private fun PasswordEntryEntity.toUiState(
    uid: String,
    cypher: PasswordCypher,
    hasDuplicate: Boolean,
    otpConfig: OtpConfig?,
): PasswordDetailUiState {
    val title = runCatching {
        cypher.decrypt(titleCipher, familyId, visibility, createdBy, uid)
    }.getOrDefault("")
    val username = usernameCipher?.let { data ->
        runCatching { cypher.decrypt(data, familyId, visibility, createdBy, uid) }.getOrNull()
    }.orEmpty()
    val password = runCatching {
        cypher.decrypt(passwordCipher, familyId, visibility, createdBy, uid)
    }.getOrDefault("")
    val website = websiteCipher?.let { data ->
        runCatching { cypher.decrypt(data, familyId, visibility, createdBy, uid) }.getOrNull()
    }.orEmpty()
    val notes = notesCipher?.let { data ->
        runCatching { cypher.decrypt(data, familyId, visibility, createdBy, uid) }.getOrNull()
    }.orEmpty()
    val strength = PasswordStrength.evaluate(password)
    val dateFmt = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.ITALY)
    val advice = when (strength.level) {
        it.vittorioscocca.kidbox.feature.passwords.PasswordStrengthLevel.VERY_WEAK,
        it.vittorioscocca.kidbox.feature.passwords.PasswordStrengthLevel.WEAK,
        -> "Considera una password piu lunga e varia."
        it.vittorioscocca.kidbox.feature.passwords.PasswordStrengthLevel.FAIR -> "Aggiungi simboli e aumenta la lunghezza."
        else -> "Buona password."
    }

    return PasswordDetailUiState(
        isLoading = false,
        notFound = false,
        id = id,
        familyId = familyId,
        title = title,
        username = username,
        password = password,
        website = website,
        notes = notes,
        iconUrl = iconURL,
        isFavorite = isFavorite,
        pwnedCount = pwnedCount,
        pwnedCheckedAtLabel = pwnedCheckedAt?.let { dateFmt.format(Date(it)) }.orEmpty(),
        hasDuplicate = hasDuplicate,
        isVulnerable = (pwnedCount ?: 0) > 0 || hasDuplicate,
        estimatedBits = strength.estimatedBits.toInt(),
        strengthLabel = strength.level.labelIt(),
        strengthAdvice = advice,
        updatedAtLabel = dateFmt.format(Date(updatedAtEpochMillis)),
        passwordUpdatedAtLabel = dateFmt.format(Date(passwordUpdatedAtEpochMillis)),
        createdBy = createdBy,
        currentUid = uid,
        canManage = createdBy == uid,
        otpConfig = otpConfig,
    )
}
