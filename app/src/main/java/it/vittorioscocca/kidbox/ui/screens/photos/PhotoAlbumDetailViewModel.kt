package it.vittorioscocca.kidbox.ui.screens.photos

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import it.vittorioscocca.kidbox.data.repository.PhotoVideoRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class PhotoAlbumDetailUiState(
    val familyId: String = "",
    val albumId: String = "",
    val albumTitle: String = "",
    val isTripAlbum: Boolean = false,
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val uploadingPhotoIds: Set<String> = emptySet(),
    val photos: List<KBFamilyPhotoEntity> = emptyList(),
    val errorMessage: String? = null,
)

@HiltViewModel
class PhotoAlbumDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PhotoVideoRepository,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()
    private val albumId: String = savedStateHandle.get<String>("albumId").orEmpty()
    private val navAlbumTitle: String = savedStateHandle.get<String>("albumTitle").orEmpty()
    private val isTripAlbum: Boolean = savedStateHandle.get<Boolean>("isTripAlbum") == true

    private val _uiState = MutableStateFlow(
        PhotoAlbumDetailUiState(
            familyId = familyId,
            albumId = albumId,
            albumTitle = navAlbumTitle,
            isTripAlbum = isTripAlbum,
        ),
    )
    val uiState: StateFlow<PhotoAlbumDetailUiState> = _uiState.asStateFlow()

    init {
        if (familyId.isBlank() || albumId.isBlank()) {
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                errorMessage = "Album non valido",
            )
        } else {
            repository.startRealtime(familyId)
            viewModelScope.launch {
                val resolvedTitle = repository.getAlbum(albumId)?.title?.takeIf { it.isNotBlank() }
                    ?: navAlbumTitle
                if (resolvedTitle.isNotBlank()) {
                    _uiState.value = _uiState.value.copy(albumTitle = resolvedTitle)
                }
            }
            viewModelScope.launch {
                repository.observePhotos(familyId).collectLatest { allPhotos ->
                    val albumPhotos = allPhotos
                        .filter { !it.isDeleted && it.albumIdsRaw.containsAlbum(albumId) }
                        .sortedByDescending { it.takenAtEpochMillis }
                    _uiState.value = _uiState.value.copy(
                        photos = albumPhotos,
                        isLoading = false,
                    )
                }
            }
            viewModelScope.launch { repository.flushPending(familyId) }
        }
    }

    fun importMedia(uri: Uri) {
        importMediaBatch(listOf(uri))
    }

    fun importMediaBatch(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(errorMessage = null)
            runCatching {
                val imported = uris.map { uri ->
                    repository.importMediaFromUri(
                        familyId = familyId,
                        uri = uri,
                        albumId = albumId,
                    )
                }
                val importedIds = imported.map { it.id }.toSet()
                _uiState.value = _uiState.value.copy(uploadingPhotoIds = importedIds)
                repository.flushPendingWithProgress(familyId) { photoId, _, _ ->
                    _uiState.value = _uiState.value.copy(
                        uploadingPhotoIds = _uiState.value.uploadingPhotoIds - photoId,
                    )
                }
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = err.localizedMessage ?: "Errore import media",
                )
            }
            _uiState.value = _uiState.value.copy(uploadingPhotoIds = emptySet())
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            runCatching {
                repository.deletePhoto(photoId)
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = err.localizedMessage ?: "Errore eliminazione",
                )
            }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun deletePhotos(photoIds: Collection<String>) {
        if (photoIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            runCatching {
                photoIds.forEach { repository.deletePhoto(it) }
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = err.localizedMessage ?: "Errore eliminazione multipla",
                )
            }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun removePhotosFromAlbum(photoIds: Collection<String>) {
        if (photoIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            runCatching {
                photoIds.forEach { repository.removePhotoFromAlbum(it, albumId) }
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(
                    errorMessage = err.localizedMessage ?: "Errore rimozione da album",
                )
            }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    suspend fun preparePreviewFile(photo: KBFamilyPhotoEntity): File =
        repository.preparePreviewFile(photo)

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    override fun onCleared() {
        repository.stopRealtime()
        super.onCleared()
    }

    private fun String.containsAlbum(albumId: String): Boolean {
        if (isBlank()) return false
        return split(",").map { it.trim() }.any { it == albumId }
    }
}
