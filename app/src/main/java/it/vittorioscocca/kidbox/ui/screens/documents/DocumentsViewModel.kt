package it.vittorioscocca.kidbox.ui.screens.documents

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.DocumentsSavedSort
import it.vittorioscocca.kidbox.data.local.DocumentsSavedViewMode
import it.vittorioscocca.kidbox.data.local.DocumentsUiPreferences
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.CountersService
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.util.Log

enum class DocumentsViewMode { GRID, LIST }
enum class DocumentsSort { NAME, TYPE, DATE, SIZE }
private const val TAG_DOC_VM = "KB_Doc_VM"

data class FolderCrumb(
    val id: String?,
    val title: String,
)

data class DocumentsUiState(
    val familyId: String = "",
    val breadcrumbs: List<FolderCrumb> = listOf(FolderCrumb(id = null, title = "Documenti")),
    val mode: DocumentsViewMode = DocumentsViewMode.GRID,
    val sort: DocumentsSort = DocumentsSort.NAME,
    val sortAscending: Boolean = true,
    val isSelecting: Boolean = false,
    val selectedFolderIds: Set<String> = emptySet(),
    val selectedDocumentIds: Set<String> = emptySet(),
    val folders: List<KBDocumentCategoryEntity> = emptyList(),
    val documents: List<KBDocumentEntity> = emptyList(),
    val highlightedDocumentId: String? = null,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class DocumentsViewModel @Inject constructor(
    private val repository: DocumentRepository,
    private val countersService: CountersService,
    private val homeBadgeManager: HomeBadgeManager,
    private val uiPreferences: DocumentsUiPreferences,
) : ViewModel() {
    private val _uiState = MutableStateFlow(DocumentsUiState())
    val uiState: StateFlow<DocumentsUiState> = _uiState.asStateFlow()
    private var folderObservationJob: Job? = null

    fun bindFamily(familyId: String) {
        if (familyId.isBlank() || _uiState.value.familyId == familyId) return
        _uiState.value = DocumentsUiState(
            familyId = familyId,
            mode = uiPreferences.getViewMode().toUiMode(),
            sort = uiPreferences.getSort().toUiSort(),
            sortAscending = uiPreferences.getSortAscending(),
        )
        repository.startRealtime(
            familyId = familyId,
            onPermissionDenied = {
                _uiState.value = _uiState.value.copy(errorMessage = "Accesso ai documenti negato")
            },
        )
        clearBadge(familyId)
        viewModelScope.launch {
            runCatching {
                repository.healHierarchy(familyId)
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore consolidamento gerarchia")
            }
            observeCurrentFolder()
        }
    }

    fun setMode(mode: DocumentsViewMode) {
        if (_uiState.value.mode == mode) return
        _uiState.value = _uiState.value.copy(mode = mode)
        uiPreferences.setViewMode(mode.toSavedMode())
    }

    fun setSort(sort: DocumentsSort) {
        val newState = if (_uiState.value.sort == sort) {
            _uiState.value.copy(sortAscending = !_uiState.value.sortAscending)
        } else {
            _uiState.value.copy(sort = sort, sortAscending = true)
        }
        _uiState.value = newState
        uiPreferences.setSort(
            sort = newState.sort.toSavedSort(),
            ascending = newState.sortAscending,
        )
    }

    fun toggleSelectionMode() {
        val selecting = !_uiState.value.isSelecting
        _uiState.value = _uiState.value.copy(
            isSelecting = selecting,
            selectedFolderIds = if (selecting) _uiState.value.selectedFolderIds else emptySet(),
            selectedDocumentIds = if (selecting) _uiState.value.selectedDocumentIds else emptySet(),
        )
    }

    fun toggleFolderSelection(folderId: String) {
        val current = _uiState.value.selectedFolderIds.toMutableSet()
        if (!current.add(folderId)) current.remove(folderId)
        _uiState.value = _uiState.value.copy(selectedFolderIds = current)
    }

    fun toggleDocumentSelection(documentId: String) {
        val current = _uiState.value.selectedDocumentIds.toMutableSet()
        if (!current.add(documentId)) current.remove(documentId)
        _uiState.value = _uiState.value.copy(selectedDocumentIds = current)
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            isSelecting = false,
            selectedFolderIds = emptySet(),
            selectedDocumentIds = emptySet(),
        )
    }

    fun deleteSelected() {
        val state = _uiState.value
        val familyId = state.familyId
        if (familyId.isBlank()) return
        val selectedFolders = state.folders.filter { it.id in state.selectedFolderIds }
        val selectedDocuments = state.documents.filter { it.id in state.selectedDocumentIds }
        if (selectedFolders.isEmpty() && selectedDocuments.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                selectedDocuments.forEach { repository.deleteDocumentLocal(it) }
                selectedFolders.forEach { repository.deleteFolderLocal(it) }
                repository.flushPending(familyId)
                clearSelection()
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore eliminazione elementi")
            }
        }
    }

    fun moveSelected(destinationFolderId: String?) {
        val state = _uiState.value
        val familyId = state.familyId
        if (familyId.isBlank()) return
        val selectedFolders = state.folders.filter { it.id in state.selectedFolderIds }
        val selectedDocuments = state.documents.filter { it.id in state.selectedDocumentIds }
        if (selectedFolders.isEmpty() && selectedDocuments.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                selectedDocuments.forEach { repository.moveDocumentLocal(it, destinationFolderId) }
                selectedFolders.forEach { repository.moveFolderLocal(it, destinationFolderId) }
                repository.flushPending(familyId)
                clearSelection()
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore spostamento elementi")
            }
        }
    }

    fun selectedDocuments(): List<KBDocumentEntity> {
        val state = _uiState.value
        return state.documents.filter { it.id in state.selectedDocumentIds }
    }

    fun renameDocument(
        document: KBDocumentEntity,
        newTitle: String,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || newTitle.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.renameDocumentLocal(document, newTitle)
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore rinomina documento")
            }
        }
    }

    fun renameFolder(
        folder: KBDocumentCategoryEntity,
        newTitle: String,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || newTitle.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.renameFolderLocal(folder, newTitle)
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore rinomina cartella")
            }
        }
    }

    fun moveSingleDocument(
        document: KBDocumentEntity,
        destinationFolderId: String?,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.moveDocumentLocal(document, destinationFolderId)
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore spostamento documento")
            }
        }
    }

    fun moveSingleFolder(
        folder: KBDocumentCategoryEntity,
        destinationFolderId: String?,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.moveFolderLocal(folder, destinationFolderId)
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore spostamento cartella")
            }
        }
    }

    fun duplicateDocument(
        document: KBDocumentEntity,
        destinationFolderId: String? = document.categoryId,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.duplicateDocumentLocal(document, destinationFolderId)
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore duplicazione documento")
            }
        }
    }

    fun duplicateFolder(
        folder: KBDocumentCategoryEntity,
        destinationParentId: String? = folder.parentId,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.duplicateFolderLocal(folder, destinationParentId)
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore duplicazione cartella")
            }
        }
    }

    fun navigateToFolder(folder: KBDocumentCategoryEntity) {
        _uiState.value = _uiState.value.copy(
            breadcrumbs = _uiState.value.breadcrumbs + FolderCrumb(id = folder.id, title = folder.title),
            highlightedDocumentId = null,
            isSelecting = false,
            selectedFolderIds = emptySet(),
            selectedDocumentIds = emptySet(),
        )
        observeCurrentFolder()
    }

    fun navigateBack(): Boolean {
        val crumbs = _uiState.value.breadcrumbs
        if (crumbs.size <= 1) return false
        _uiState.value = _uiState.value.copy(
            breadcrumbs = crumbs.dropLast(1),
            highlightedDocumentId = null,
            isSelecting = false,
            selectedFolderIds = emptySet(),
            selectedDocumentIds = emptySet(),
        )
        observeCurrentFolder()
        return true
    }

    fun focusDocument(documentId: String) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || documentId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                val document = repository.getDocumentById(documentId) ?: return@runCatching
                if (document.familyId != familyId || document.isDeleted) return@runCatching
                val pathFolders = mutableListOf<KBDocumentCategoryEntity>()
                var currentFolderId = document.categoryId
                while (!currentFolderId.isNullOrBlank()) {
                    val folder = repository.getFolderById(currentFolderId) ?: break
                    pathFolders.add(folder)
                    currentFolderId = folder.parentId
                }
                val breadcrumbs = buildList {
                    add(FolderCrumb(id = null, title = "Documenti"))
                    pathFolders.asReversed().forEach { folder ->
                        add(FolderCrumb(id = folder.id, title = folder.title))
                    }
                }
                _uiState.value = _uiState.value.copy(
                    breadcrumbs = breadcrumbs,
                    highlightedDocumentId = document.id,
                    isSelecting = false,
                    selectedFolderIds = emptySet(),
                    selectedDocumentIds = emptySet(),
                )
                observeCurrentFolder()
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore apertura documento condiviso")
            }
        }
    }

    fun clearHighlightedDocument() {
        if (_uiState.value.highlightedDocumentId == null) return
        _uiState.value = _uiState.value.copy(highlightedDocumentId = null)
    }

    fun createFolder(name: String) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || name.isBlank()) return
        val parentId = _uiState.value.breadcrumbs.lastOrNull()?.id
        viewModelScope.launch {
            runCatching {
                repository.createFolderLocal(
                    familyId = familyId,
                    title = name.trim(),
                    parentId = parentId,
                )
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore creazione cartella")
            }
        }
    }

    fun createExpenseFolder(
        expenseId: String,
        expenseTitle: String,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.ensureExpenseFolders(familyId, expenseId, expenseTitle)
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore cartella spese")
            }
        }
    }

    fun importDocument(
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        targetFolderId: String? = null,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        val parentId = targetFolderId ?: _uiState.value.breadcrumbs.lastOrNull()?.id
        viewModelScope.launch {
            runCatching {
                repository.uploadDocumentLocal(
                    familyId = familyId,
                    parentFolderId = parentId,
                    fileName = fileName,
                    mimeType = mimeType,
                    bytes = bytes,
                )
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore caricamento documento")
            }
        }
    }

    fun deleteDocument(document: KBDocumentEntity) {
        viewModelScope.launch {
            runCatching {
                repository.deleteDocumentLocal(document)
                repository.flushPending(document.familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore eliminazione documento")
            }
        }
    }

    fun deleteFolder(folder: KBDocumentCategoryEntity) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.deleteFolderLocal(folder)
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore eliminazione cartella")
            }
        }
    }

    suspend fun preparePreviewFile(document: KBDocumentEntity): File =
        withContext(Dispatchers.IO) {
            Log.d(TAG_DOC_VM, "preparePreviewFile dispatch IO docId=${document.id}")
            repository.preparePreviewFile(document)
        }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        repository.stopRealtime()
        super.onCleared()
    }

    private fun observeCurrentFolder() {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        val parentId = _uiState.value.breadcrumbs.lastOrNull()?.id
        folderObservationJob?.cancel()
        folderObservationJob = viewModelScope.launch {
            repository.observeBrowser(familyId, parentId).collectLatest { data ->
                _uiState.value = _uiState.value.copy(
                    folders = data.folders,
                    documents = data.documents,
                    isLoading = false,
                    errorMessage = null,
                )
            }
        }
    }

    private fun clearBadge(familyId: String) {
        homeBadgeManager.clearLocal(CounterField.DOCUMENTS)
        viewModelScope.launch { runCatching { countersService.reset(familyId, CounterField.DOCUMENTS) } }
    }
}

private fun DocumentsSavedViewMode.toUiMode(): DocumentsViewMode =
    when (this) {
        DocumentsSavedViewMode.GRID -> DocumentsViewMode.GRID
        DocumentsSavedViewMode.LIST -> DocumentsViewMode.LIST
    }

private fun DocumentsViewMode.toSavedMode(): DocumentsSavedViewMode =
    when (this) {
        DocumentsViewMode.GRID -> DocumentsSavedViewMode.GRID
        DocumentsViewMode.LIST -> DocumentsSavedViewMode.LIST
    }

private fun DocumentsSavedSort.toUiSort(): DocumentsSort =
    when (this) {
        DocumentsSavedSort.NAME -> DocumentsSort.NAME
        DocumentsSavedSort.TYPE -> DocumentsSort.TYPE
        DocumentsSavedSort.DATE -> DocumentsSort.DATE
        DocumentsSavedSort.SIZE -> DocumentsSort.SIZE
    }

private fun DocumentsSort.toSavedSort(): DocumentsSavedSort =
    when (this) {
        DocumentsSort.NAME -> DocumentsSavedSort.NAME
        DocumentsSort.TYPE -> DocumentsSavedSort.TYPE
        DocumentsSort.DATE -> DocumentsSavedSort.DATE
        DocumentsSort.SIZE -> DocumentsSavedSort.SIZE
    }
