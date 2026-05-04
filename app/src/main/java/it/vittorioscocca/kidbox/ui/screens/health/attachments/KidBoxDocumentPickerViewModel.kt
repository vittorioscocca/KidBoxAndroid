package it.vittorioscocca.kidbox.ui.screens.health.attachments

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import java.io.File
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class KidBoxPickerUiState(
    val title: String = "Documenti",
    val folders: List<KBDocumentCategoryEntity> = emptyList(),
    val documents: List<KBDocumentEntity> = emptyList(),
    val canGoUp: Boolean = false,
    val isBusy: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class KidBoxDocumentPickerViewModel @Inject constructor(
    private val documentRepository: DocumentRepository,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val familyId = MutableStateFlow<String?>(null)
    private val folderStack = MutableStateFlow<List<Pair<String?, String>>>(listOf(null to "Documenti"))
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow<String?>(null)

    private val nav = combine(familyId, folderStack) { fid, stack -> fid to stack }
        .distinctUntilChanged()

    val uiState: StateFlow<KidBoxPickerUiState> = combine(nav, busy, error) { fs, b, err ->
        Triple(fs, b, err)
    }.flatMapLatest { (fs, b, err) ->
        val (fid, stack) = fs
        if (fid == null) {
            flowOf(KidBoxPickerUiState(isBusy = b, error = err))
        } else {
            val parentId = stack.last().first
            documentRepository.observeBrowser(fid, parentId).map { browser ->
                KidBoxPickerUiState(
                    title = stack.last().second,
                    folders = browser.folders,
                    documents = browser.documents,
                    canGoUp = stack.size > 1,
                    isBusy = b,
                    error = err,
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = KidBoxPickerUiState(),
    )

    fun bindFamily(id: String) {
        familyId.value = id
        folderStack.value = listOf(null to "Documenti")
        error.value = null
    }

    fun openFolder(folder: KBDocumentCategoryEntity) {
        folderStack.update { it + (folder.id to folder.title) }
    }

    fun navigateUp() {
        folderStack.update { if (it.size > 1) it.dropLast(1) else it }
    }

    fun clearError() {
        error.value = null
    }

    fun pickDocument(doc: KBDocumentEntity, onResult: (Result<Uri>) -> Unit) {
        viewModelScope.launch {
            busy.value = true
            error.value = null
            val result = runCatching { prepareAttachmentUri(doc) }
            busy.value = false
            result.exceptionOrNull()?.let { error.value = it.message ?: "Errore" }
            onResult(result)
        }
    }

    private suspend fun prepareAttachmentUri(doc: KBDocumentEntity): Uri = withContext(Dispatchers.IO) {
        val src = documentRepository.preparePreviewFile(doc)
        val name = buildDisplayFileName(doc)
        val sub = File(appContext.cacheDir, "kidbox-pick/${UUID.randomUUID()}").apply { mkdirs() }
        val out = File(sub, name)
        src.copyTo(out, overwrite = true)
        FileProvider.getUriForFile(
            appContext,
            "${appContext.packageName}.fileprovider",
            out,
        )
    }

    private fun buildDisplayFileName(doc: KBDocumentEntity): String {
        val rawTitle = doc.title.trim().ifBlank { doc.fileName }
        val extFromFile = doc.fileName.substringAfterLast('.', "").lowercase()
        val ext = extFromFile.ifBlank {
            when {
                doc.mimeType.contains("pdf", ignoreCase = true) -> "pdf"
                doc.mimeType.startsWith("image/", ignoreCase = true) -> "jpg"
                else -> ""
            }
        }
        val base = if (rawTitle.contains('.')) rawTitle.substringBeforeLast('.') else rawTitle
        val safe = base.replace("/", "-").replace(":", "-").trim().ifBlank { "documento" }
        return if (ext.isNotEmpty()) "$safe.$ext" else safe
    }
}
