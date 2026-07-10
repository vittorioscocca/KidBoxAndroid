package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.remote.DocumentCryptoManager
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.domain.model.WalletDocumentMetadata
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Wallet → Documenti d'identità: sottile layer sopra [DocumentRepository]
 * (nessuna nuova tabella Room). I documenti restano `KBDocumentEntity`,
 * distinti dai file generici tramite il tag [WalletDocumentMetadata.NOTES_PREFIX]
 * in `notes` — stessa convenzione di iOS (`KBWalletDocumentKind`/`KBDocument.notes`).
 */
@Singleton
class WalletDocumentRepository @Inject constructor(
    private val documentRepository: DocumentRepository,
    private val documentDao: KBDocumentDao,
    private val categoryDao: KBDocumentCategoryDao,
    private val cryptoManager: DocumentCryptoManager,
    private val auth: FirebaseAuth,
) {
    companion object {
        const val IDENTITY_FOLDER_TITLE = "Documenti d'identità"
    }

    /**
     * Avvia il listener realtime Firestore della collezione `documents` (condiviso con
     * la sezione "Documenti" generica). Senza questa chiamata, se l'utente non ha mai
     * aperto quella sezione su questo device, il Room locale resta vuoto e il Wallet
     * non vede i documenti d'identità creati su un altro device/piattaforma (bug:
     * "sincronizzato" solo in apparenza, in realtà mai scaricato da Firestore).
     */
    fun startRealtime(familyId: String, onPermissionDenied: (() -> Unit)? = null) {
        documentRepository.startRealtime(familyId, onPermissionDenied)
    }

    /** Documenti Wallet della famiglia (filtrati per tag, non serve una query Room dedicata). */
    fun observeWalletDocuments(familyId: String): Flow<List<KBDocumentEntity>> =
        documentDao.observeByFamilyId(familyId).map { list ->
            list.filter { it.notes?.startsWith(WalletDocumentMetadata.NOTES_PREFIX) == true }
        }

    fun metadataFor(document: KBDocumentEntity): WalletDocumentMetadata? =
        WalletDocumentMetadata.parse(document.notes, cryptoManager, document.familyId)

    /** Documenti di "Documenti" non ancora collegati al Wallet, candidati per "Collega documento esistente". */
    fun observeLinkableDocuments(familyId: String): Flow<List<KBDocumentEntity>> =
        documentRepository.observeAllDocuments(familyId).map { list ->
            list.filter { it.notes?.startsWith(WalletDocumentMetadata.NOTES_PREFIX) != true }
                .sortedByDescending { it.updatedAtEpochMillis }
        }

    /**
     * Sfoglia cartelle/documenti di "Documenti" per una data cartella (`null` = radice),
     * per il picker "Collega documento esistente" (stesso albero della sezione Documenti,
     * mirror di `WalletDocumentPickerSheet`/`DocumentFolderViewModel` su iOS). I documenti
     * già collegati al Wallet vengono esclusi per non ri-collegare lo stesso file.
     */
    fun observeBrowser(familyId: String, parentFolderId: String?): Flow<DocumentBrowserData> =
        documentRepository.observeBrowser(familyId, parentFolderId).map { data ->
            data.copy(
                documents = data.documents.filter { it.notes?.startsWith(WalletDocumentMetadata.NOTES_PREFIX) != true },
            )
        }

    /** Trova (o crea) la cartella radice "Documenti d'identità" nella sezione Documenti. */
    suspend fun findOrCreateIdentityFolder(familyId: String): String {
        val existing = categoryDao.getAllByFamilyId(familyId)
            .firstOrNull { !it.isDeleted && it.parentId == null && it.title == IDENTITY_FOLDER_TITLE }
        if (existing != null) return existing.id
        val created = documentRepository.createFolderLocal(
            familyId = familyId,
            title = IDENTITY_FOLDER_TITLE,
            parentId = null,
        )
        return created.id
    }

    /** Scansione nuova: cifra il PDF (via [DocumentRepository]) e i metadati (via [cryptoManager]), stessa cartella. */
    suspend fun saveNewDocument(
        familyId: String,
        childId: String?,
        title: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        metadata: WalletDocumentMetadata,
    ): String {
        val folderId = findOrCreateIdentityFolder(familyId)
        val docId = UUID.randomUUID().toString()
        documentRepository.uploadDocumentLocal(
            familyId = familyId,
            parentFolderId = folderId,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            notes = metadata.encode(cryptoManager, familyId),
            forcedId = docId,
        )
        documentDao.getById(docId)?.let { doc ->
            documentDao.upsert(doc.copy(childId = childId, title = title.trim().ifEmpty { doc.title }))
        }
        documentRepository.flushPending(familyId)
        return docId
    }

    /**
     * Collega un documento già esistente in Documenti: lo sposta (stesso file,
     * non duplicato) nella cartella "Documenti d'identità" e lo tagga.
     */
    suspend fun linkExistingDocument(
        document: KBDocumentEntity,
        childId: String?,
        metadata: WalletDocumentMetadata,
    ) {
        val folderId = findOrCreateIdentityFolder(document.familyId)
        val uid = auth.currentUser?.uid ?: document.updatedBy
        val updated = document.copy(
            categoryId = folderId,
            childId = childId,
            notes = metadata.encode(cryptoManager, document.familyId),
            updatedAtEpochMillis = System.currentTimeMillis(),
            updatedBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
            isDeleted = false,
        )
        documentDao.upsert(updated)
        documentRepository.flushPending(document.familyId)
    }

    /** Modifica dei campi (titolo, titolare, metadati) di un documento Wallet già salvato. */
    suspend fun updateMetadata(
        document: KBDocumentEntity,
        childId: String?,
        metadata: WalletDocumentMetadata,
        newTitle: String,
    ) {
        val uid = auth.currentUser?.uid ?: document.updatedBy
        val updated = document.copy(
            title = newTitle.trim(),
            childId = childId,
            notes = metadata.encode(cryptoManager, document.familyId),
            updatedAtEpochMillis = System.currentTimeMillis(),
            updatedBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        documentDao.upsert(updated)
        documentRepository.flushPending(document.familyId)
    }

    /** Eliminazione singola o multipla (soft-delete + push immediato). */
    suspend fun deleteDocuments(documents: List<KBDocumentEntity>) {
        if (documents.isEmpty()) return
        val familyId = documents.first().familyId
        documents.forEach { documentRepository.deleteDocumentLocal(it) }
        documentRepository.flushPending(familyId)
    }
}
