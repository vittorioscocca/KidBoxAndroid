package it.vittorioscocca.kidbox.data.work

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.remote.DocumentStorageManager
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.util.KBLog
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object WorkAttachmentTag {
    fun make(ownerUid: String) = "work:$ownerUid"
}

/**
 * Il file originale di un documento di Lavoro: un documento cifrato (cartella
 * «Lavoro» in Documenti) con la visibilità della scheda e tag `work:{uid}`.
 * Gemello di `WorkAttachmentService.swift`.
 */
@Singleton
class WorkAttachmentService @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val documentDao: KBDocumentDao,
    private val storageManager: DocumentStorageManager,
    private val auth: FirebaseAuth,
) {
    /** Carica il file e ritorna l'id del documento (con la visibilità della scheda). */
    suspend fun upload(
        familyId: String,
        ownerUid: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        sharedWith: List<String>,
        folderTitle: String,
    ): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val folderId = documentRepository.ensureWorkRootFolder(familyId, folderTitle).id
        val sharing = sharedWith.isNotEmpty()
        documentRepository.uploadDocumentLocal(
            familyId = familyId,
            parentFolderId = folderId,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            notes = WorkAttachmentTag.make(ownerUid),
            forcedId = id,
            visibilityScope = if (sharing) KBVisibilityScope.MEMBERS else KBVisibilityScope.ONLY_CREATOR,
            visibilityMemberIdsJson = encodeStringList(if (sharing) sharedWith else emptyList()),
            createdBy = auth.currentUser?.uid,
        )
        runCatching { documentRepository.flushPending(familyId) }
            .onFailure { KBLog.data.warning("work attachment flush failed: ${it.message}", TAG) }
        id
    }

    /** I file seguono la scheda: condivisa, il file la segue; tornata privata, torna privato. */
    suspend fun syncVisibility(familyId: String, ownerUid: String, sharedWith: List<String>) =
        withContext(Dispatchers.IO) {
            val tag = WorkAttachmentTag.make(ownerUid)
            val sharing = sharedWith.isNotEmpty()
            documentDao.getAllByFamilyId(familyId).filter { it.notes == tag && !it.isDeleted }.forEach { doc ->
                documentRepository.updateDocumentVisibilityLocal(
                    doc,
                    if (sharing) KBVisibilityScope.MEMBERS else KBVisibilityScope.ONLY_CREATOR,
                    encodeStringList(if (sharing) sharedWith else emptyList()),
                )
            }
            runCatching { documentRepository.flushPending(familyId) }
        }

    suspend fun open(documentId: String): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val doc = documentRepository.getDocumentById(documentId) ?: error("File non disponibile")
            documentRepository.preparePreviewFile(doc)
        }
    }

    suspend fun delete(documentId: String) = withContext(Dispatchers.IO) {
        val doc = documentRepository.getDocumentById(documentId) ?: return@withContext
        doc.localPath?.let { runCatching { File(it).delete() } }
        documentRepository.deleteDocumentLocal(doc)
        runCatching { storageManager.delete(doc.storagePath) }
        runCatching { documentRepository.flushPending(doc.familyId) }
    }

    private companion object { const val TAG = "WorkAttachmentService" }
}
