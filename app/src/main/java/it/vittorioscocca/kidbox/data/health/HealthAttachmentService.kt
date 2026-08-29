package it.vittorioscocca.kidbox.data.health

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.support.DocumentImageCompressor
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.data.remote.DocumentStorageManager
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.home.HomeItemAttachmentTag
import it.vittorioscocca.kidbox.data.home.HousePaymentAttachmentTag
import it.vittorioscocca.kidbox.data.pets.PetAttachmentTag
import it.vittorioscocca.kidbox.data.pets.PetEventAttachmentTag
import it.vittorioscocca.kidbox.data.vehicles.VehicleAttachmentTag
import it.vittorioscocca.kidbox.data.vehicles.VehicleEventAttachmentTag
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import java.io.File
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val TAG = "HealthAttachmentSvc"
private const val MAX_BYTES = 30L * 1024L * 1024L

class FileTooLargeException(message: String) : IOException(message)

@Singleton
class HealthAttachmentService @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val folderResolver: HealthFolderResolver,
    private val textExtractor: HealthDocumentTextExtractor,
    private val storageManager: DocumentStorageManager,
    private val documentDao: KBDocumentDao,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val backfillInFlightByFamily = ConcurrentHashMap<String, Boolean>()


    suspend fun uploadVisitAttachment(
        uri: Uri,
        visitId: String,
        familyId: String,
        childId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = childId,
        tag = VisitAttachmentTag.make(visitId),
        storageScopeSegment = "visit-attachments/$visitId",
        resolveCategoryId = { folderResolver.ensureHealthFolders(familyId).second.id },
    )

    suspend fun uploadExamAttachment(
        uri: Uri,
        examId: String,
        familyId: String,
        childId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = childId,
        tag = ExamAttachmentTag.make(examId),
        storageScopeSegment = "exam-attachments/$examId",
        resolveCategoryId = { folderResolver.ensureHealthFolders(familyId).second.id },
    )

    suspend fun uploadTreatmentAttachment(
        uri: Uri,
        treatmentId: String,
        familyId: String,
        childId: String?,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = childId,
        tag = TreatmentAttachmentTag.make(treatmentId),
        storageScopeSegment = "treatment-attachments/$treatmentId",
        resolveCategoryId = { folderResolver.ensureHealthFolders(familyId).second.id },
    )

    /** Allegati veicolo (cartella Documenti › Garage, parity iOS). */
    suspend fun uploadVehicleAttachment(
        uri: Uri,
        vehicleId: String,
        familyId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = null,
        tag = VehicleAttachmentTag.make(vehicleId),
        storageScopeSegment = "vehicle-attachments/$vehicleId",
        resolveCategoryId = { documentRepository.ensureGarageRootFolder(familyId).id },
    )

    suspend fun uploadVehicleEventAttachment(
        uri: Uri,
        eventId: String,
        familyId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = null,
        tag = VehicleEventAttachmentTag.make(eventId),
        storageScopeSegment = "vehicle-event-attachments/$eventId",
        resolveCategoryId = { documentRepository.ensureGarageRootFolder(familyId).id },
    )

    /** Allegati della scheda animale (cartella Documenti › Animali domestici, parity iOS). */
    suspend fun uploadPetAttachment(
        uri: Uri,
        petId: String,
        familyId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = null,
        tag = PetAttachmentTag.make(petId),
        storageScopeSegment = "pet-attachments/$petId",
        resolveCategoryId = { documentRepository.ensurePetsRootFolder(familyId).id },
    )

    /** Allegati evento animale (cartella Documenti › Animali domestici, parity iOS). */
    suspend fun uploadPetEventAttachment(
        uri: Uri,
        eventId: String,
        familyId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = null,
        tag = PetEventAttachmentTag.make(eventId),
        storageScopeSegment = "pet-event-attachments/$eventId",
        resolveCategoryId = { documentRepository.ensurePetsRootFolder(familyId).id },
    )

    suspend fun uploadHomeItemAttachment(
        uri: Uri,
        homeItemId: String,
        familyId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = null,
        tag = HomeItemAttachmentTag.make(homeItemId),
        storageScopeSegment = "home-item-attachments/$homeItemId",
        resolveCategoryId = { documentRepository.ensureCasaRootFolder(familyId).id },
    )

    suspend fun uploadHousePaymentAttachment(
        uri: Uri,
        paymentId: String,
        familyId: String,
    ): Result<KBDocumentEntity> = upload(
        uri = uri,
        familyId = familyId,
        childId = null,
        tag = HousePaymentAttachmentTag.make(paymentId),
        storageScopeSegment = "house-payment-attachments/$paymentId",
        resolveCategoryId = { documentRepository.ensureCasaRootFolder(familyId).id },
    )

    /** Downloads (or returns the cached plaintext) for a document. */
    suspend fun downloadAttachment(doc: KBDocumentEntity): Result<File> = withContext(Dispatchers.IO) {
        runCatching { documentRepository.preparePreviewFile(doc) }
    }

    suspend fun deleteAttachment(doc: KBDocumentEntity) = withContext(Dispatchers.IO) {
        val localFile = doc.localPath?.let { File(it) }
        if (localFile != null && localFile.exists()) {
            runCatching { localFile.delete() }
                .onFailure { KBLog.data.error("Failed to delete local file ${localFile.absolutePath}", TAG, it) }
        }
        documentRepository.deleteDocumentLocal(doc)
        runCatching { storageManager.delete(doc.storagePath) }
            .onFailure { KBLog.data.error("Failed to delete storage blob ${doc.storagePath}", TAG, it) }
        // Flush the soft-delete to Firestore
        runCatching { documentRepository.flushPending(doc.familyId) }
            .onFailure { KBLog.data.error("flushPending after delete failed", TAG, it) }
    }

    suspend fun deleteAllGarageAttachmentsForVehicle(vehicleId: String, familyId: String) = withContext(Dispatchers.IO) {
        if (familyId.isBlank() || vehicleId.isBlank()) return@withContext
        val docs = documentDao.getAllByFamilyId(familyId)
            .filter { !it.isDeleted && VehicleAttachmentTag.matches(it.notes, vehicleId) }
        for (doc in docs) {
            try {
                deleteAttachment(doc)
            } catch (e: Exception) {
                KBLog.data.error("deleteAllGarageAttachmentsForVehicle doc=${doc.id}", TAG, e)
            }
        }
    }

    suspend fun deleteAllGarageAttachmentsForVehicleEvent(eventId: String, familyId: String) = withContext(Dispatchers.IO) {
        if (familyId.isBlank() || eventId.isBlank()) return@withContext
        val docs = documentDao.getAllByFamilyId(familyId)
            .filter { !it.isDeleted && VehicleEventAttachmentTag.matches(it.notes, eventId) }
        for (doc in docs) {
            try {
                deleteAttachment(doc)
            } catch (e: Exception) {
                KBLog.data.error("deleteAllGarageAttachmentsForVehicleEvent doc=${doc.id}", TAG, e)
            }
        }
    }

    /** Gli allegati se ne vanno con l'animale: da soli resterebbero in Documenti
     *  senza più niente che li spieghi. */
    suspend fun deleteAllAttachmentsForPet(petId: String, familyId: String) = withContext(Dispatchers.IO) {
        if (familyId.isBlank() || petId.isBlank()) return@withContext
        val docs = documentDao.getAllByFamilyId(familyId)
            .filter { !it.isDeleted && PetAttachmentTag.matches(it.notes, petId) }
        for (doc in docs) {
            try {
                deleteAttachment(doc)
            } catch (e: Exception) {
                KBLog.data.error("deleteAllAttachmentsForPet doc=${doc.id}", TAG, e)
            }
        }
    }

    /** Ripulisce gli allegati di un evento animale mai salvato: senza evento a cui
     *  appartenere resterebbero appesi in Documenti. */
    suspend fun deleteAllAttachmentsForPetEvent(eventId: String, familyId: String) = withContext(Dispatchers.IO) {
        if (familyId.isBlank() || eventId.isBlank()) return@withContext
        val docs = documentDao.getAllByFamilyId(familyId)
            .filter { !it.isDeleted && PetEventAttachmentTag.matches(it.notes, eventId) }
        for (doc in docs) {
            try {
                deleteAttachment(doc)
            } catch (e: Exception) {
                KBLog.data.error("deleteAllAttachmentsForPetEvent doc=${doc.id}", TAG, e)
            }
        }
    }

    suspend fun deleteAllCasaAttachmentsForHomeItem(homeItemId: String, familyId: String) = withContext(Dispatchers.IO) {
        if (familyId.isBlank() || homeItemId.isBlank()) return@withContext
        val docs = documentDao.getAllByFamilyId(familyId)
            .filter { !it.isDeleted && HomeItemAttachmentTag.matches(it.notes, homeItemId) }
        for (doc in docs) {
            try {
                deleteAttachment(doc)
            } catch (e: Exception) {
                KBLog.data.error("deleteAllCasaAttachmentsForHomeItem doc=${doc.id}", TAG, e)
            }
        }
    }

    suspend fun deleteAllCasaAttachmentsForHousePayment(paymentId: String, familyId: String) = withContext(Dispatchers.IO) {
        if (familyId.isBlank() || paymentId.isBlank()) return@withContext
        val docs = documentDao.getAllByFamilyId(familyId)
            .filter { !it.isDeleted && HousePaymentAttachmentTag.matches(it.notes, paymentId) }
        for (doc in docs) {
            try {
                deleteAttachment(doc)
            } catch (e: Exception) {
                KBLog.data.error("deleteAllCasaAttachmentsForHousePayment doc=${doc.id}", TAG, e)
            }
        }
    }

    /** iOS parity: extract text for old health attachments (visit/exam/treatment) in background. */
    fun enqueueBackfillHealthExtraction(familyId: String) {
        if (familyId.isBlank()) return
        if (backfillInFlightByFamily.putIfAbsent(familyId, true) != null) return
        scope.launch {
            try {
                backfillHealthExtractionInternal(familyId)
            } finally {
                backfillInFlightByFamily.remove(familyId)
            }
        }
    }

    /** Ensures extracted text exists for exam attachments before building AI context. */
    suspend fun ensureExamAttachmentsExtraction(
        familyId: String,
        examId: String,
    ) = withContext(Dispatchers.IO) {
        if (familyId.isBlank() || examId.isBlank()) return@withContext
        val uid = auth.currentUser?.uid ?: "local"
        val candidates = documentDao.getAllByFamilyId(familyId)
            .asSequence()
            .filter { !it.isDeleted && ExamAttachmentTag.matches(it.notes, examId) }
            .filter {
                it.extractedText.isNullOrBlank() ||
                    it.extractionStatusRaw in setOf(
                        KBTextExtractionStatus.NONE.rawValue,
                        KBTextExtractionStatus.PENDING.rawValue,
                        KBTextExtractionStatus.PROCESSING.rawValue,
                        KBTextExtractionStatus.FAILED.rawValue,
                    )
            }
            .toList()
        if (candidates.isEmpty()) return@withContext
        for (doc in candidates) {
            extractAndPersistText(doc, uid)
        }
        runCatching { documentRepository.flushPending(familyId) }
            .onFailure { KBLog.data.error("ensureExamAttachmentsExtraction flushPending failed familyId=$familyId", TAG, it) }
    }

    /** Ensures extracted text exists for visit attachments before building AI context. */
    suspend fun ensureVisitAttachmentsExtraction(
        familyId: String,
        visitId: String,
    ) = withContext(Dispatchers.IO) {
        if (familyId.isBlank() || visitId.isBlank()) return@withContext
        val uid = auth.currentUser?.uid ?: "local"
        val candidates = documentDao.getAllByFamilyId(familyId)
            .asSequence()
            .filter { !it.isDeleted && VisitAttachmentTag.matches(it.notes, visitId) }
            .filter {
                it.extractedText.isNullOrBlank() ||
                    it.extractionStatusRaw in setOf(
                        KBTextExtractionStatus.NONE.rawValue,
                        KBTextExtractionStatus.PENDING.rawValue,
                        KBTextExtractionStatus.PROCESSING.rawValue,
                        KBTextExtractionStatus.FAILED.rawValue,
                    )
            }
            .toList()
        if (candidates.isEmpty()) return@withContext
        for (doc in candidates) {
            extractAndPersistText(doc, uid)
        }
        runCatching { documentRepository.flushPending(familyId) }
            .onFailure { KBLog.data.error("ensureVisitAttachmentsExtraction flushPending failed familyId=$familyId", TAG, it) }
    }

    /** Ensures extracted text exists for treatment attachments before building AI context. */
    suspend fun ensureTreatmentAttachmentsExtraction(
        familyId: String,
        treatmentId: String,
    ) = withContext(Dispatchers.IO) {
        if (familyId.isBlank() || treatmentId.isBlank()) return@withContext
        val uid = auth.currentUser?.uid ?: "local"
        val candidates = documentDao.getAllByFamilyId(familyId)
            .asSequence()
            .filter { !it.isDeleted && TreatmentAttachmentTag.matches(it.notes, treatmentId) }
            .filter {
                it.extractedText.isNullOrBlank() ||
                    it.extractionStatusRaw in setOf(
                        KBTextExtractionStatus.NONE.rawValue,
                        KBTextExtractionStatus.PENDING.rawValue,
                        KBTextExtractionStatus.PROCESSING.rawValue,
                        KBTextExtractionStatus.FAILED.rawValue,
                    )
            }
            .toList()
        if (candidates.isEmpty()) return@withContext
        for (doc in candidates) {
            extractAndPersistText(doc, uid)
        }
        runCatching { documentRepository.flushPending(familyId) }
            .onFailure { KBLog.data.error("ensureTreatmentAttachmentsExtraction flushPending failed familyId=$familyId", TAG, it) }
    }

    /**
     * Estrae testo per allegati Casa / Garage / eventi animali inclusi nel contesto
     * dell'agente di pianificazione (parity iOS: OCR prima del prompt).
     */
    suspend fun ensureLifeAreaAttachmentsForPlanning(
        familyId: String,
        homeItemIds: Set<String>,
        housePaymentIds: Set<String>,
        vehicleIds: Set<String>,
        vehicleEventIds: Set<String>,
        petEventIds: Set<String>,
    ) = withContext(Dispatchers.IO) {
        if (familyId.isBlank()) return@withContext
        val uid = auth.currentUser?.uid ?: "local"
        val pending = documentDao.getLifeAreaDocumentsNeedingExtraction(familyId)
        if (pending.isEmpty()) return@withContext
        val candidates = pending.filter { doc ->
            homeItemIds.any { HomeItemAttachmentTag.matches(doc.notes, it) } ||
                housePaymentIds.any { HousePaymentAttachmentTag.matches(doc.notes, it) } ||
                vehicleIds.any { VehicleAttachmentTag.matches(doc.notes, it) } ||
                vehicleEventIds.any { VehicleEventAttachmentTag.matches(doc.notes, it) } ||
                petEventIds.any { PetEventAttachmentTag.matches(doc.notes, it) }
        }
        if (candidates.isEmpty()) return@withContext
        for (doc in candidates) {
            extractAndPersistText(doc, uid)
        }
        runCatching { documentRepository.flushPending(familyId) }
            .onFailure { KBLog.data.error("ensureLifeAreaAttachmentsForPlanning flushPending failed familyId=$familyId", TAG, it) }
    }

    // ── Private ──────────────────────────────────────────────────────────────────

    private suspend fun upload(
        uri: Uri,
        familyId: String,
        childId: String?,
        tag: String,
        storageScopeSegment: String,
        resolveCategoryId: suspend () -> String,
    ): Result<KBDocumentEntity> = withContext(Dispatchers.IO) {
        runCatching {
            val cr = context.contentResolver

            // 1. Check size before reading bytes
            val fileSize = querySizeFromUri(uri)
            if (fileSize > MAX_BYTES) throw FileTooLargeException("File troppo grande (max 30 MB)")

            // 2. Read bytes
            val bytes = cr.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Impossibile leggere il file")
            if (bytes.isEmpty()) throw IOException("Il file è vuoto")

            // 3. Resolve metadata
            val originalMime = cr.getType(uri)?.takeIf { it.isNotBlank() }
                ?: mimeFromExtension(fileNameFromUri(uri))
            val originalFileName = fileNameFromUri(uri).ifBlank { "attachment_${System.currentTimeMillis()}" }

            // Comprimi le immagini ad alta risoluzione prima di cifrare/caricare.
            val compressed = DocumentImageCompressor.compressIfNeeded(bytes, originalFileName, originalMime)
            val uploadBytes = compressed.bytes
            val fileName = compressed.fileName
            val mimeType = compressed.mimeType
            val title = titleFromFileName(fileName)

            // 4. docId + paths — MUST use `documents/` (same as DocumentStorageManager.uploadEncrypted
            // and iOS DocumentStorageService). Firebase Storage rules typically deny writes under
            // home-item-attachments / vehicle-attachments / … (403).
            val docId = UUID.randomUUID().toString()
            val safeFile = safeFileName(fileName)
            val storagePath = "families/$familyId/documents/$docId/$safeFile.kbenc"
            KBLog.data.debug("Storage path (logical scope=$storageScopeSegment) -> $storagePath", TAG)

            // 5. Target folder (Salute/Referti o Garage)
            val categoryId = resolveCategoryId()

            // 6. Write plaintext to local cache
            val localPath = writePendingFile(docId, fileName, uploadBytes)

            val uid = auth.currentUser?.uid ?: "local"
            val now = System.currentTimeMillis()

            // 7. Persist entity with PENDING_UPSERT so a crash before step 9 is retryable
            val entity = KBDocumentEntity(
                id = docId,
                familyId = familyId,
                childId = childId,
                categoryId = categoryId,
                localPath = localPath,
                title = title,
                fileName = fileName,
                mimeType = mimeType,
                fileSize = uploadBytes.size.toLong(),
                storagePath = storagePath,
                downloadURL = null,
                notes = tag,
                extractedText = null,
                extractedTextUpdatedAtEpochMillis = null,
                extractionStatusRaw = KBTextExtractionStatus.PENDING.rawValue,
                extractionError = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                createdBy = uid,
                visibilityScope = KBVisibilityScope.FAMILY,
                visibilityMemberIdsJson = encodeStringList(emptyList()),
                isDeleted = false,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
            documentDao.upsert(entity)
            KBLog.data.debug("Persisted pending entity docId=$docId tag=$tag", TAG)

            // 8. Encrypt + upload to health-specific path
            val downloadUrl = try {
                storageManager.uploadEncryptedToPath(
                    storagePath = storagePath,
                    familyId = familyId,
                    mimeType = mimeType,
                    fileName = fileName,
                    plainBytes = uploadBytes,
                )
            } catch (e: Exception) {
                documentDao.upsert(entity.copy(lastSyncError = e.message))
                throw e
            }

            // 9. Update with downloadURL and flush to Firestore
            val uploaded = entity.copy(
                downloadURL = downloadUrl,
                updatedAtEpochMillis = System.currentTimeMillis(),
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
            documentDao.upsert(uploaded)
            KBLog.data.debug("Uploaded docId=$docId path=$storagePath", TAG)

            // 10. Push metadata to Firestore (skips re-upload since downloadURL is set)
            runCatching { documentRepository.flushPending(familyId) }
                .onFailure { KBLog.data.error("flushPending failed, will retry on next sync", TAG, it) }

            // 11. OCR in background (iOS-like): upload completes first, extraction status updates asynchronously.
            scope.launch {
                runCatching {
                    extractAndPersistText(uploaded, uid)
                    documentRepository.flushPending(familyId)
                }.onFailure {
                    KBLog.data.error("background OCR update failed docId=$docId", TAG, it)
                }
            }

            uploaded
        }
    }

    private suspend fun backfillHealthExtractionInternal(familyId: String) {
        val uid = auth.currentUser?.uid ?: "local"
        val health = documentDao.getHealthDocumentsNeedingExtraction(familyId)
        val lifeArea = documentDao.getLifeAreaDocumentsNeedingExtraction(familyId)
        val candidates = (health + lifeArea).distinctBy { it.id }
        if (candidates.isEmpty()) return
        KBLog.data.info("Backfill extraction start familyId=$familyId count=${candidates.size} (health+life)", TAG)

        for (doc in candidates) {
            extractAndPersistText(doc, uid)
        }

        runCatching { documentRepository.flushPending(familyId) }
            .onFailure { KBLog.data.error("Backfill flushPending failed familyId=$familyId", TAG, it) }
        KBLog.data.info("Backfill extraction completed familyId=$familyId", TAG)
    }

    private suspend fun extractAndPersistText(doc: KBDocumentEntity, uid: String) {
        val processingAt = System.currentTimeMillis()
        documentDao.upsert(
            doc.copy(
                extractionStatusRaw = KBTextExtractionStatus.PROCESSING.rawValue,
                extractionError = null,
                updatedAtEpochMillis = processingAt,
                updatedBy = uid,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
        val previewFile = runCatching { documentRepository.preparePreviewFile(doc) }
            .onFailure { KBLog.data.error("Extraction skip docId=${doc.id}: preview unavailable", TAG, it) }
            .getOrNull()
        if (previewFile == null) {
            markExtractionFailed(doc, uid, "Anteprima non disponibile")
            return
        }

        val bytes = runCatching { previewFile.readBytes() }
            .onFailure { KBLog.data.error("Extraction readBytes failed docId=${doc.id}", TAG, it) }
            .getOrNull()
        if (bytes == null) {
            markExtractionFailed(doc, uid, "Impossibile leggere il file")
            return
        }
        if (bytes.isEmpty()) {
            markExtractionFailed(doc, uid, "File vuoto")
            return
        }

        val now = System.currentTimeMillis()
        val extracted = runCatching {
            textExtractor.extractText(
                bytes = bytes,
                mimeType = doc.mimeType,
                fileName = doc.fileName,
            )
        }.onFailure {
            KBLog.data.error("Extraction engine failed docId=${doc.id} mime=${doc.mimeType}", TAG, it)
        }.getOrDefault("")

        val sanitized = HealthAiDocumentText.sanitizeExtractedText(extracted)
        val updated = if (sanitized.isBlank()) {
            doc.copy(
                extractionStatusRaw = KBTextExtractionStatus.FAILED.rawValue,
                extractionError = "Nessun testo rilevato nel documento",
                updatedAtEpochMillis = now,
                updatedBy = uid,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
        } else {
            doc.copy(
                extractedText = sanitized,
                extractedTextUpdatedAtEpochMillis = now,
                extractionStatusRaw = KBTextExtractionStatus.COMPLETED.rawValue,
                extractionError = null,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
        }
        documentDao.upsert(updated)
    }

    private suspend fun markExtractionFailed(doc: KBDocumentEntity, uid: String, message: String) {
        val t = System.currentTimeMillis()
        documentDao.upsert(
            doc.copy(
                extractionStatusRaw = KBTextExtractionStatus.FAILED.rawValue,
                extractionError = message,
                updatedAtEpochMillis = t,
                updatedBy = uid,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    private fun writePendingFile(docId: String, fileName: String, bytes: ByteArray): String {
        val dir = File(context.filesDir, "kb_documents_pending").apply { mkdirs() }
        val file = File(dir, "${docId}_${safeFileName(fileName)}")
        file.writeBytes(bytes)
        return file.absolutePath
    }

    private fun querySizeFromUri(uri: Uri): Long {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (col >= 0 && cursor.moveToFirst()) cursor.getLong(col) else 0L
            } ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    private fun fileNameFromUri(uri: Uri): String {
        return try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0 && cursor.moveToFirst()) cursor.getString(col) else null
            } ?: uri.lastPathSegment ?: "file"
        } catch (_: Exception) {
            uri.lastPathSegment ?: "file"
        }
    }

    private fun titleFromFileName(fileName: String): String =
        fileName.substringBeforeLast('.').ifBlank { fileName }

    private fun safeFileName(fileName: String): String =
        fileName.replace(Regex("[^a-zA-Z0-9._\\-]"), "_")

    private fun mimeFromExtension(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return when (ext) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic" -> "image/heic"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "doc" -> "application/msword"
            "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            "xls" -> "application/vnd.ms-excel"
            "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            else -> "application/octet-stream"
        }
    }

}
