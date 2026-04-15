package it.vittorioscocca.kidbox.ui.screens.photos

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyPhotoEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPhotoAlbumEntity
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.CountersService
import it.vittorioscocca.kidbox.data.repository.PhotoVideoRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

data class FamilyPhotosUiState(
    val familyId: String = "",
    val isLoading: Boolean = true,
    val isBusy: Boolean = false,
    val uploadProgressDone: Int = 0,
    val uploadProgressTotal: Int = 0,
    val uploadingPhotoIds: Set<String> = emptySet(),
    val albums: List<KBPhotoAlbumEntity> = emptyList(),
    val photos: List<KBFamilyPhotoEntity> = emptyList(),
    val selectedAlbumId: String? = null,
    val errorMessage: String? = null,
) {
    val filteredPhotos: List<KBFamilyPhotoEntity>
        get() {
            val albumId = selectedAlbumId ?: return photos
            return photos.filter { photo ->
                photo.albumIdsRaw
                    .split(",")
                    .map { it.trim() }
                    .any { it == albumId }
            }
        }
}

@HiltViewModel
class FamilyPhotosViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: PhotoVideoRepository,
    private val countersService: CountersService,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()
    private val _uiState = MutableStateFlow(FamilyPhotosUiState(familyId = familyId))
    val uiState: StateFlow<FamilyPhotosUiState> = _uiState.asStateFlow()

    init {
        if (familyId.isBlank()) {
            _uiState.value = _uiState.value.copy(isLoading = false, errorMessage = "Famiglia non valida")
        } else {
            repository.startRealtime(familyId)
            viewModelScope.launch {
                runCatching { countersService.reset(familyId, CounterField.PHOTOS) }
            }
            viewModelScope.launch {
                combine(
                    repository.observeAlbums(familyId),
                    repository.observePhotos(familyId),
                ) { albums, photos ->
                    albums to photos.sortedByDescending { it.takenAtEpochMillis }
                }.collectLatest { (albums, photos) ->
                    _uiState.value = _uiState.value.copy(
                        albums = albums,
                        photos = photos,
                        isLoading = false,
                    )
                }
            }
            viewModelScope.launch { repository.flushPending(familyId) }
        }
    }

    fun selectAlbum(albumId: String?) {
        _uiState.value = _uiState.value.copy(selectedAlbumId = albumId)
    }

    fun createAlbum(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            runCatching {
                repository.createAlbum(familyId, title)
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore creazione album")
            }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun importMedia(
        uri: Uri,
        targetAlbumId: String?,
    ) {
        importMediaBatch(listOf(uri), targetAlbumId)
    }

    fun importMediaBatch(
        uris: List<Uri>,
        targetAlbumId: String?,
    ) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(errorMessage = null)
            runCatching {
                val imported = uris.map { uri ->
                    repository.importMediaFromUri(
                        familyId = familyId,
                        uri = uri,
                        albumId = targetAlbumId,
                    )
                }
                val importedIds = imported.map { it.id }.toSet()
                _uiState.value = _uiState.value.copy(
                    uploadingPhotoIds = importedIds,
                    uploadProgressDone = 0,
                    uploadProgressTotal = imported.size,
                )
                repository.flushPendingWithProgress(familyId) { photoId, done, total ->
                    _uiState.value = _uiState.value.copy(
                        uploadingPhotoIds = _uiState.value.uploadingPhotoIds - photoId,
                        uploadProgressDone = done,
                        uploadProgressTotal = total,
                    )
                }
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore import media")
            }
            _uiState.value = _uiState.value.copy(
                uploadingPhotoIds = emptySet(),
                uploadProgressDone = 0,
                uploadProgressTotal = 0,
            )
        }
    }

    fun deletePhoto(photoId: String) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            runCatching {
                repository.deletePhoto(photoId)
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore eliminazione")
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
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore eliminazione multipla")
            }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun addPhotosToAlbum(
        photoIds: Collection<String>,
        albumId: String,
    ) {
        if (photoIds.isEmpty() || albumId.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            runCatching {
                photoIds.forEach { repository.addPhotoToAlbum(it, albumId) }
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore aggiornamento album")
            }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun movePhotosToAlbum(
        photoIds: Collection<String>,
        targetAlbumId: String,
    ) {
        if (photoIds.isEmpty() || targetAlbumId.isBlank()) return
        val sourceAlbumId = _uiState.value.selectedAlbumId
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            runCatching {
                if (!sourceAlbumId.isNullOrBlank()) {
                    photoIds.forEach { repository.removePhotoFromAlbum(it, sourceAlbumId) }
                }
                photoIds.forEach { repository.addPhotoToAlbum(it, targetAlbumId) }
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore spostamento album")
            }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun removePhotosFromCurrentAlbum(photoIds: Collection<String>) {
        val albumId = _uiState.value.selectedAlbumId ?: return
        if (photoIds.isEmpty()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            runCatching {
                photoIds.forEach { repository.removePhotoFromAlbum(it, albumId) }
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore rimozione da album")
            }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun setCurrentAlbumCover(photoId: String) {
        val albumId = _uiState.value.selectedAlbumId ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isBusy = true, errorMessage = null)
            runCatching {
                repository.setAlbumCover(albumId, photoId)
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore impostazione copertina")
            }
            _uiState.value = _uiState.value.copy(isBusy = false)
        }
    }

    fun reorderAlbums(orderedAlbumIds: List<String>) {
        if (orderedAlbumIds.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                repository.reorderAlbums(familyId, orderedAlbumIds)
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore riordino album")
            }
        }
    }

    fun saveEditedPhotoCopy(
        sourcePhoto: KBFamilyPhotoEntity,
        jpegBytes: ByteArray,
    ) {
        if (jpegBytes.isEmpty()) return
        val sourceName = sourcePhoto.fileName
        val baseName = sourceName.substringBeforeLast(".")
        val newFileName = "${baseName}_modificata.jpg"
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(errorMessage = null)
            runCatching {
                val targetAlbum = _uiState.value.selectedAlbumId
                repository.importMediaFromBytes(
                    familyId = familyId,
                    bytes = jpegBytes,
                    mimeType = "image/jpeg",
                    fileName = newFileName,
                    albumId = targetAlbum,
                )
                repository.flushPending(familyId)
            }.onFailure { err ->
                _uiState.value = _uiState.value.copy(errorMessage = err.localizedMessage ?: "Errore salvataggio modifica")
            }
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
}
