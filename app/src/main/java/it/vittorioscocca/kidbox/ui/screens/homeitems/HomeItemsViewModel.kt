package it.vittorioscocca.kidbox.ui.screens.homeitems

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.home.HomeItemAttachmentTag
import it.vittorioscocca.kidbox.data.home.HousePaymentAttachmentTag
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.entity.HomeItemEntity
import it.vittorioscocca.kidbox.data.local.entity.HousePaymentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.repository.HomeItemRepository
import it.vittorioscocca.kidbox.data.repository.HousePaymentRepository
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HomeItemsViewModel @Inject constructor(
    @ApplicationContext private val appContext: Context,
    savedStateHandle: SavedStateHandle,
    private val homeItemRepository: HomeItemRepository,
    private val housePaymentRepository: HousePaymentRepository,
    private val attachmentService: HealthAttachmentService,
    private val documentDao: KBDocumentDao,
) : ViewModel() {
    private val familyId: String = savedStateHandle.get<String>("familyId").orEmpty()

    init {
        // Bonifica una tantum dei sottotipi ereditati da un cambio di tipo.
        viewModelScope.launch { housePaymentRepository.cleanupInheritedSubtypes(appContext, familyId) }
    }

    /** Es. KidBox Documenti picker nella schermata lista Casa. */
    val familyIdForPicker: String get() = familyId

    val homeItems: StateFlow<List<HomeItemEntity>> =
        homeItemRepository.observeByFamily(familyId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val housePayments: StateFlow<List<HousePaymentEntity>> =
        housePaymentRepository.observeByFamily(familyId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _draftAttachmentUploading = MutableStateFlow(false)
    val draftAttachmentUploading: StateFlow<Boolean> get() = _draftAttachmentUploading

    private val _draftAttachmentError = MutableStateFlow<String?>(null)
    val draftAttachmentError: StateFlow<String?> get() = _draftAttachmentError

    private val _openDraftFileEvent = MutableStateFlow<Pair<String, File>?>(null)
    val openDraftFileEvent: StateFlow<Pair<String, File>?> get() = _openDraftFileEvent

    init {
        if (familyId.isNotBlank()) {
            homeItemRepository.startRealtime(familyId)
            housePaymentRepository.startRealtime(familyId)
        }
    }

    override fun onCleared() {
        homeItemRepository.stopRealtime()
        housePaymentRepository.stopRealtime()
        super.onCleared()
    }

    fun observeDraftHomeItemAttachments(draftId: String): Flow<List<KBDocumentEntity>> =
        documentDao.observeByFamilyId(familyId).map { docs ->
            docs.filter { HomeItemAttachmentTag.matches(it.notes, draftId) }
        }

    fun observeDraftHousePaymentAttachments(draftId: String): Flow<List<KBDocumentEntity>> =
        documentDao.observeByFamilyId(familyId).map { docs ->
            docs.filter { HousePaymentAttachmentTag.matches(it.notes, draftId) }
        }

    fun uploadDraftHomeItemAttachment(uri: Uri, draftHomeItemId: String) {
        if (familyId.isBlank() || draftHomeItemId.isBlank()) return
        _draftAttachmentUploading.value = true
        _draftAttachmentError.value = null
        viewModelScope.launch {
            attachmentService.uploadHomeItemAttachment(uri, draftHomeItemId, familyId)
                .onSuccess { _draftAttachmentUploading.value = false }
                .onFailure {
                    _draftAttachmentUploading.value = false
                    _draftAttachmentError.value = it.message ?: "Errore durante l'upload"
                }
        }
    }

    fun uploadDraftHousePaymentAttachment(uri: Uri, draftPaymentId: String) {
        if (familyId.isBlank() || draftPaymentId.isBlank()) return
        _draftAttachmentUploading.value = true
        _draftAttachmentError.value = null
        viewModelScope.launch {
            attachmentService.uploadHousePaymentAttachment(uri, draftPaymentId, familyId)
                .onSuccess { _draftAttachmentUploading.value = false }
                .onFailure {
                    _draftAttachmentUploading.value = false
                    _draftAttachmentError.value = it.message ?: "Errore durante l'upload"
                }
        }
    }

    fun deleteDraftAttachment(doc: KBDocumentEntity) {
        viewModelScope.launch { attachmentService.deleteAttachment(doc) }
    }

    fun openDraftAttachment(doc: KBDocumentEntity) {
        viewModelScope.launch {
            attachmentService.downloadAttachment(doc)
                .onSuccess { file -> _openDraftFileEvent.value = (doc.mimeType ?: "application/octet-stream") to file }
                .onFailure { _draftAttachmentError.value = it.message ?: "Errore apertura file" }
        }
    }

    fun consumeOpenDraftFileEvent() {
        _openDraftFileEvent.value = null
    }

    fun consumeDraftAttachmentError() {
        _draftAttachmentError.value = null
    }

    fun discardDraftHomeItemAttachments(draftHomeItemId: String) {
        if (familyId.isBlank() || draftHomeItemId.isBlank()) return
        viewModelScope.launch {
            attachmentService.deleteAllCasaAttachmentsForHomeItem(draftHomeItemId, familyId)
        }
    }

    fun discardDraftHousePaymentAttachments(draftPaymentId: String) {
        if (familyId.isBlank() || draftPaymentId.isBlank()) return
        viewModelScope.launch {
            attachmentService.deleteAllCasaAttachmentsForHousePayment(draftPaymentId, familyId)
        }
    }

    fun addHomeItem(
        name: String,
        category: String,
        brand: String?,
        model: String?,
        serialNumber: String?,
        purchaseDate: Long?,
        warrantyExpiryDate: Long?,
        nextServiceDate: Long?,
        servicePeriodMonths: Int?,
        notes: String?,
        reminderEnabled: Boolean,
        presetItemId: String?,
        onError: (String) -> Unit,
    ) {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                homeItemRepository.addHomeItem(
                    familyId = familyId,
                    name = name,
                    category = category,
                    brand = brand,
                    model = model,
                    serialNumber = serialNumber,
                    purchaseDate = purchaseDate,
                    warrantyExpiryDate = warrantyExpiryDate,
                    nextServiceDate = nextServiceDate,
                    servicePeriodMonths = servicePeriodMonths,
                    notes = notes,
                    reminderEnabled = reminderEnabled,
                    presetItemId = presetItemId,
                )
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun addHousePayment(
        name: String,
        typeRaw: String,
        subtypeRaw: String?,
        importo: Double?,
        giornoDiScadenzaMensile: Int?,
        dataScadenza: Long?,
        dataScadenzaContratto: Long?,
        fornitore: String?,
        note: String?,
        reminderOn: Boolean,
        presetPaymentId: String?,
        onError: (String) -> Unit,
    ) {
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                housePaymentRepository.addHousePayment(
                    familyId = familyId,
                    name = name,
                    typeRaw = typeRaw,
                    subtypeRaw = subtypeRaw,
                    importo = importo,
                    giornoDiScadenzaMensile = giornoDiScadenzaMensile,
                    dataScadenza = dataScadenza,
                    dataScadenzaContratto = dataScadenzaContratto,
                    fornitore = fornitore,
                    note = note,
                    reminderOn = reminderOn,
                    presetPaymentId = presetPaymentId,
                )
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }

    fun deleteHomeItem(entity: HomeItemEntity, onError: (String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                attachmentService.deleteAllCasaAttachmentsForHomeItem(entity.id, entity.familyId)
                homeItemRepository.deleteHomeItem(entity)
            }.onFailure { onError(it.message ?: "Errore") }
        }
    }
}
