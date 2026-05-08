package it.vittorioscocca.kidbox.ui.screens.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.functions.FirebaseFunctions
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.domain.model.KBPlan
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class StorageUsageSectionUi(
    val id: String,
    val name: String,
    val iconKey: String,
    val colorHex: Long,
    val bytes: Long,
    val recordCount: Int,
)

data class StorageUsageUiState(
    val isLoading: Boolean = true,
    val familyId: String = "",
    val plan: KBPlan = KBPlan.FREE,
    val usedBytes: Long = 0L,
    val sections: List<StorageUsageSectionUi> = emptyList(),
    val isFamilyOwner: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class StorageUsageViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val subscriptionRepository: SubscriptionRepository,
    private val auth: FirebaseAuth,
) : ViewModel() {

    private val functions = FirebaseFunctions.getInstance("europe-west1")

    private val _uiState = MutableStateFlow(StorageUsageUiState())
    val uiState: StateFlow<StorageUsageUiState> = _uiState.asStateFlow()

    fun load() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            val familyId = familyDao.peekAnyFamilyId().orEmpty()
            if (familyId.isBlank()) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        familyId = "",
                        sections = emptyList(),
                        usedBytes = 0L,
                    )
                }
                return@launch
            }

            val plan = subscriptionRepository.getPlan(familyId)
            val uid = auth.currentUser?.uid.orEmpty()
            val isFamilyOwner = familyDao.getById(familyId)?.createdBy == uid
            runCatching {
                val result = functions.getHttpsCallable("getStorageUsage")
                    .call(hashMapOf("familyId" to familyId))
                    .await()
                @Suppress("UNCHECKED_CAST")
                result.getData() as? Map<String, Any?>
            }.onSuccess { data ->
                val payload = data.orEmpty()
                val sectionsMap = payload["sections"] as? Map<String, Any?> ?: emptyMap()
                val rows = defaultSections().map { base ->
                    val rawBytes = (sectionsMap[base.id] as? Number)?.toLong() ?: 0L
                    val rawCount = ((payload["counts"] as? Map<*, *>)?.get(base.id) as? Number)?.toInt() ?: 0
                    base.copy(bytes = rawBytes, recordCount = rawCount)
                }
                val usedFromCloud = (payload["usedBytes"] as? Number)?.toLong() ?: 0L
                val used = if (usedFromCloud > 0L) usedFromCloud else rows.sumOf { it.bytes }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        familyId = familyId,
                        plan = plan,
                        isFamilyOwner = isFamilyOwner,
                        usedBytes = used,
                        sections = rows,
                    )
                }
            }.onFailure { err ->
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        familyId = familyId,
                        plan = plan,
                        isFamilyOwner = isFamilyOwner,
                        errorMessage = err.localizedMessage ?: "Errore caricamento spazio",
                    )
                }
            }
        }
    }

    private fun defaultSections(): List<StorageUsageSectionUi> = listOf(
        StorageUsageSectionUi("photos", "Foto e video", "photo", 0xFFFF6B9D, 0L, 0),
        StorageUsageSectionUi("documents", "Documenti", "document", 0xFF5B8FDE, 0L, 0),
        StorageUsageSectionUi("wallet", "Wallet", "wallet", 0xFF3E7BFA, 0L, 0),
        StorageUsageSectionUi("chat", "Chat", "chat", 0xFF34C759, 0L, 0),
        StorageUsageSectionUi("expenses", "Spese", "expense", 0xFFFF9500, 0L, 0),
        StorageUsageSectionUi("notes", "Note", "note", 0xFFFF9F0A, 0L, 0),
        StorageUsageSectionUi("calendar", "Calendario", "calendar", 0xFFBF5AF2, 0L, 0),
        StorageUsageSectionUi("todo", "Liste & Todo", "todo", 0xFF30B0C7, 0L, 0),
    )
}

fun KBPlan.storageQuotaBytes(): Long = storageQuota

fun KBPlan.aiDailyMessagesForUi(): Int = aiDailyLimit

fun Long.toStorageString(): String {
    val kb = this / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}
