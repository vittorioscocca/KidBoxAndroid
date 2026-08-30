package it.vittorioscocca.kidbox.ui.screens.wallet.documents

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.repository.SubscriptionRepository
import it.vittorioscocca.kidbox.data.repository.WalletDocumentRepository
import it.vittorioscocca.kidbox.data.wallet.WalletDocumentAIExtractor
import it.vittorioscocca.kidbox.data.wallet.WalletDocumentExtraction
import it.vittorioscocca.kidbox.data.wallet.WalletDocumentExtractor
import it.vittorioscocca.kidbox.domain.model.DocumentKind
import it.vittorioscocca.kidbox.domain.model.KBPlan
import it.vittorioscocca.kidbox.domain.model.WalletDocumentMetadata
import it.vittorioscocca.kidbox.notifications.WalletDocumentReminderScheduler
import it.vittorioscocca.kidbox.ui.state.PullToRefreshController
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Titolare del documento: un figlio, un membro famiglia, o "famiglia" generico. Mirror di `WalletDocumentOwner` (iOS). */
sealed class WalletDocumentOwner(open val ownerId: String?, open val displayName: String) {
    data class Child(val child: KBChildEntity) : WalletDocumentOwner(child.id, child.name)
    data class Member(val member: KBFamilyMemberEntity) : WalletDocumentOwner(
        member.userId,
        member.displayName?.takeIf { it.isNotBlank() } ?: "Membro famiglia",
    )
    data object Family : WalletDocumentOwner(null, "Famiglia (documento generico)")
}

data class WalletDocumentItem(
    val document: KBDocumentEntity,
    val metadata: WalletDocumentMetadata?,
    val ownerName: String,
)

data class WalletDocumentsUiState(
    val familyId: String = "",
    val items: List<WalletDocumentItem> = emptyList(),
    val owners: List<WalletDocumentOwner> = listOf(WalletDocumentOwner.Family),
    val isSelecting: Boolean = false,
    val selectedIds: Set<String> = emptySet(),
    val isDeleting: Boolean = false,
    val isLoading: Boolean = true,
    val currentPlan: KBPlan = KBPlan.FREE,
    val message: String? = null,
)

@HiltViewModel
class WalletDocumentsViewModel @Inject constructor(
    private val walletDocumentRepository: WalletDocumentRepository,
    private val childDao: KBChildDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val subscriptionRepository: SubscriptionRepository,
    private val reminderScheduler: WalletDocumentReminderScheduler,
    private val aiExtractor: WalletDocumentAIExtractor,
    private val auth: FirebaseAuth,
) : ViewModel() {
    private val _uiState = MutableStateFlow(WalletDocumentsUiState())
    val uiState: StateFlow<WalletDocumentsUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null
    private var boundFamilyId: String? = null

    private val pullToRefresh = PullToRefreshController(viewModelScope)
    val isRefreshing: StateFlow<Boolean> = pullToRefresh.isRefreshing

    /** Pull-to-refresh: rilegge i documenti del Wallet da Firestore. */
    fun forceRefresh() = pullToRefresh.refresh {
        val familyId = boundFamilyId ?: return@refresh
        walletDocumentRepository.awaitForceRestartRealtime(familyId)
    }

    fun bind(familyId: String) {
        if (familyId.isBlank()) {
            _uiState.value = WalletDocumentsUiState(isLoading = false)
            return
        }
        if (boundFamilyId == familyId && observeJob != null) return
        boundFamilyId = familyId

        walletDocumentRepository.startRealtime(familyId)

        viewModelScope.launch {
            val plan = subscriptionRepository.getPlan(familyId)
            _uiState.value = _uiState.value.copy(currentPlan = plan)
        }

        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            combine(
                walletDocumentRepository.observeWalletDocuments(familyId),
                childDao.observeByFamilyId(familyId),
                familyMemberDao.observeActiveByFamilyId(familyId),
            ) { docs, children, members ->
                val owners: List<WalletDocumentOwner> = children.map { WalletDocumentOwner.Child(it) } +
                    members.map { WalletDocumentOwner.Member(it) } +
                    listOf(WalletDocumentOwner.Family)
                val items = docs.map { doc ->
                    WalletDocumentItem(
                        document = doc,
                        metadata = walletDocumentRepository.metadataFor(doc),
                        ownerName = resolveOwnerName(doc.childId, children, members),
                    )
                }.sortedByDescending { it.document.updatedAtEpochMillis }
                items to owners
            }.collect { (items, owners) ->
                _uiState.value = _uiState.value.copy(
                    familyId = familyId,
                    items = items,
                    owners = owners,
                    isLoading = false,
                )
            }
        }
    }

    private fun resolveOwnerName(
        childId: String?,
        children: List<KBChildEntity>,
        members: List<KBFamilyMemberEntity>,
    ): String {
        if (childId.isNullOrBlank()) return "Famiglia"
        children.firstOrNull { it.id == childId }?.let { return it.name }
        members.firstOrNull { it.userId == childId }?.let {
            return it.displayName?.takeIf { n -> n.isNotBlank() } ?: "Membro famiglia"
        }
        return "Famiglia"
    }

    /** Documenti "Documenti" non ancora nel Wallet, per la sheet "Collega documento esistente". */
    fun linkableDocuments(familyId: String) = walletDocumentRepository.observeLinkableDocuments(familyId)

    /** Cartelle/documenti di una cartella (picker ad albero "Collega documento esistente"). */
    fun browse(familyId: String, parentFolderId: String?) = walletDocumentRepository.observeBrowser(familyId, parentFolderId)

    fun dismissMessage() {
        _uiState.value = _uiState.value.copy(message = null)
    }

    // region Selezione multipla

    fun enterSelectionMode() {
        _uiState.value = _uiState.value.copy(isSelecting = true, selectedIds = emptySet())
    }

    fun exitSelectionMode() {
        _uiState.value = _uiState.value.copy(isSelecting = false, selectedIds = emptySet())
    }

    fun toggleSelection(documentId: String) {
        val current = _uiState.value.selectedIds
        _uiState.value = _uiState.value.copy(
            selectedIds = if (current.contains(documentId)) current - documentId else current + documentId,
        )
    }

    fun deleteSelected() {
        val ids = _uiState.value.selectedIds
        val docs = _uiState.value.items.filter { ids.contains(it.document.id) }.map { it.document }
        deleteDocuments(docs)
        exitSelectionMode()
    }

    fun deleteSingle(document: KBDocumentEntity) {
        deleteDocuments(listOf(document))
    }

    private fun deleteDocuments(docs: List<KBDocumentEntity>) {
        if (docs.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isDeleting = true)
            runCatching {
                walletDocumentRepository.deleteDocuments(docs)
                docs.forEach { reminderScheduler.cancel(it.id) }
            }.onFailure {
                _uiState.value = _uiState.value.copy(message = it.localizedMessage ?: "Errore eliminazione documento")
            }
            _uiState.value = _uiState.value.copy(isDeleting = false)
        }
    }

    // endregion

    // region Estrazione

    suspend fun runLocalExtraction(bitmaps: List<Bitmap>, kind: DocumentKind): WalletDocumentExtraction =
        WalletDocumentExtractor.extract(bitmaps, kind)

    suspend fun runAIExtraction(
        bitmaps: List<Bitmap>,
        kind: DocumentKind,
        familyId: String,
    ): Result<WalletDocumentExtraction> = aiExtractor.extract(bitmaps, kind, familyId)

    fun estimatedAiMessageCost(imageCount: Int): Int = WalletDocumentAIExtractor.estimatedMessageUnits(imageCount)

    // endregion

    // region Salvataggio / collegamento / modifica

    suspend fun saveNewDocument(
        familyId: String,
        ownerId: String?,
        title: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        metadata: WalletDocumentMetadata,
    ): Result<String> = runCatching {
        val docId = walletDocumentRepository.saveNewDocument(
            familyId = familyId,
            childId = ownerId,
            title = title,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            metadata = metadata,
        )
        scheduleReminderIfNeeded(docId, familyId, title, metadata)
        docId
    }

    suspend fun linkExisting(
        document: KBDocumentEntity,
        ownerId: String?,
        metadata: WalletDocumentMetadata,
    ): Result<Unit> = runCatching {
        walletDocumentRepository.linkExistingDocument(document, ownerId, metadata)
        scheduleReminderIfNeeded(document.id, document.familyId, document.title, metadata)
    }

    suspend fun updateExisting(
        document: KBDocumentEntity,
        ownerId: String?,
        metadata: WalletDocumentMetadata,
        title: String,
    ): Result<Unit> = runCatching {
        walletDocumentRepository.updateMetadata(document, ownerId, metadata, title)
        scheduleReminderIfNeeded(document.id, document.familyId, title, metadata)
    }

    private fun scheduleReminderIfNeeded(documentId: String, familyId: String, title: String, metadata: WalletDocumentMetadata) {
        val expiry = metadata.effectiveExpiryDate
        if (metadata.notifyBeforeExpiry && expiry != null) {
            reminderScheduler.schedule(documentId, familyId, title, expiry)
        } else {
            reminderScheduler.cancel(documentId)
        }
    }

    // endregion
}
