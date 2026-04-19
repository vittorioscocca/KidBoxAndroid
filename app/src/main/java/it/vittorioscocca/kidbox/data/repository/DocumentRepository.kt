package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import android.util.Log
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseDao
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.remote.DocumentRemoteChange
import it.vittorioscocca.kidbox.data.remote.DocumentRemoteStore
import it.vittorioscocca.kidbox.data.remote.DocumentStorageManager
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import java.io.File
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val TAG_DOC_REPO = "KB_Doc_Repo"
private const val TAG_DOC_SYNC = "KB_Doc_Sync"

/** Titoli usati per i placeholder di categoria creati al volo da applyInboundDocument. */
private val PLACEHOLDER_CATEGORY_TITLES = setOf(
    "Cartella",
    "Spese",
    "Spesa",
    "Allegato Spesa",
)

data class DocumentBrowserData(
    val folders: List<KBDocumentCategoryEntity>,
    val documents: List<KBDocumentEntity>,
)

@Singleton
class DocumentRepository @Inject constructor(
    private val documentDao: KBDocumentDao,
    private val categoryDao: KBDocumentCategoryDao,
    private val expenseDao: KBExpenseDao,
    private val database: KidBoxDatabase,
    private val remoteStore: DocumentRemoteStore,
    private val storageManager: DocumentStorageManager,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private val inboundMutex = Mutex()
    private var docsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null
    private val healedFamiliesInSession = mutableSetOf<String>()
    private val lastRootSystemHiddenSignatureByFamily = mutableMapOf<String, String>()

    fun observeBrowser(
        familyId: String,
        parentFolderId: String?,
    ): Flow<DocumentBrowserData> {
        val categoriesFlow = categoryDao.observeByFamilyId(familyId)
        return if (parentFolderId == null) {
            combine(
                categoriesFlow,
                documentDao.observeRootVisibleByFamilyId(familyId),
                documentDao.observeRootHiddenSystemEncodedByFamilyId(familyId),
            ) { categories, rootVisibleDocuments, hiddenSystemEncoded ->
                val hiddenSystemNames = hiddenSystemEncoded.map { it.fileName.ifBlank { it.title } }.sorted()
                val hiddenSystemSignature = hiddenSystemNames.joinToString("|")
                val previousSystem = lastRootSystemHiddenSignatureByFamily[familyId]
                if (hiddenSystemSignature != previousSystem) {
                    lastRootSystemHiddenSignatureByFamily[familyId] = hiddenSystemSignature
                    hiddenSystemNames.forEach { fileName ->
                        Log.d(TAG_DOC_SYNC, "Hiding system-encoded file from Root: $fileName")
                    }
                }
                DocumentBrowserData(
                    folders = categories
                        .filter { it.parentId == null && !it.isDeleted }
                        .sortedWith(compareBy<KBDocumentCategoryEntity> { it.sortOrder }.thenBy { it.title.lowercase() }),
                    documents = rootVisibleDocuments.sortedByDescending { it.updatedAtEpochMillis },
                )
            }
        } else {
            combine(
                categoriesFlow,
                documentDao.observeByFamilyId(familyId),
            ) { categories, documents ->
                val expenseIdFromFolder = parseExpenseIdFromCategoryId(parentFolderId)
                val documentsInFolder = documents
                    .filter { doc ->
                        if (doc.isDeleted) return@filter false
                        if (doc.categoryId == parentFolderId) return@filter true
                        // Resilience guard: in expense subfolders, keep docs visible while
                        // categoryId is temporarily null during cross-module realtime alignment.
                        expenseIdFromFolder != null && parseExpenseIdFromNotes(doc.notes) == expenseIdFromFolder
                    }
                    .distinctBy { it.id }
                    .sortedByDescending { it.updatedAtEpochMillis }
                DocumentBrowserData(
                    folders = categories
                        .filter { it.parentId == parentFolderId && !it.isDeleted }
                        .sortedWith(compareBy<KBDocumentCategoryEntity> { it.sortOrder }.thenBy { it.title.lowercase() }),
                    documents = documentsInFolder,
                )
            }
        }
    }

    fun observeAllDocuments(familyId: String): Flow<List<KBDocumentEntity>> =
        documentDao.observeByFamilyId(familyId)
            .map { list -> list.filter { !it.isDeleted } }

    fun observeAllFolders(familyId: String): Flow<List<KBDocumentCategoryEntity>> =
        categoryDao.observeByFamilyId(familyId)
            .map { list -> list.filter { !it.isDeleted } }

    suspend fun getDocumentById(documentId: String): KBDocumentEntity? = documentDao.getById(documentId)

    suspend fun getFolderById(folderId: String): KBDocumentCategoryEntity? = categoryDao.getById(folderId)

    suspend fun healHierarchy(
        familyId: String,
        force: Boolean = false,
    ): Int {
        if (!force && healedFamiliesInSession.contains(familyId)) {
            Log.d(TAG_DOC_SYNC, "healHierarchy skipped for familyId=$familyId (already healed this session)")
            return 0
        }
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        val restored = database.withTransaction {
            var restoredCount = 0
            var restoredDocs = 0
            var restoredCats = 0
            val rootId = expensesRootFolderId(familyId)
            val root = categoryDao.getById(rootId)
            val rootExists = root != null && !root.isDeleted && root.parentId == null
            val ensuredRoot = when {
                root == null -> {
                    restoredCount += 1
                    restoredCats += 1
                    KBDocumentCategoryEntity(
                        id = rootId,
                        familyId = familyId,
                        title = "Spese",
                        sortOrder = 99,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        updatedBy = uid,
                        isDeleted = false,
                        parentId = null,
                        syncStateRaw = KBSyncState.SYNCED.rawValue,
                        lastSyncError = null,
                    )
                }
                root.isDeleted || root.parentId != null -> {
                    restoredCount += 1
                    restoredCats += 1
                    root.copy(
                        isDeleted = false,
                        parentId = null,
                        updatedAtEpochMillis = now,
                        updatedBy = uid,
                        syncStateRaw = KBSyncState.SYNCED.rawValue,
                        lastSyncError = null,
                    )
                }
                else -> root
            }
            categoryDao.upsert(ensuredRoot)

            val orphanCats = categoryDao.getOrphanedExpenseCategories(familyId)
            orphanCats.forEach { category ->
                if (KBSyncState.fromRaw(category.syncStateRaw) == KBSyncState.PENDING_UPSERT) return@forEach
                restoredCount += 1
                restoredCats += 1
                categoryDao.upsert(
                    category.copy(
                        parentId = rootId,
                        updatedAtEpochMillis = now,
                        updatedBy = uid,
                        syncStateRaw = KBSyncState.SYNCED.rawValue,
                        lastSyncError = null,
                    ),
                )
            }

            // Revive deterministic expense folders that may have been soft-deleted by stale sync.
            val allCategoriesForRevive = categoryDao.getAllByFamilyId(familyId)
            allCategoriesForRevive
                .filter { it.id.startsWith("exp-cat-") }
                .forEach { category ->
                    if (KBSyncState.fromRaw(category.syncStateRaw) == KBSyncState.PENDING_UPSERT) return@forEach
                    val expenseId = parseExpenseIdFromCategoryId(category.id)
                    val linkedExpense = expenseId?.let { expenseDao.getById(it) }
                    if (linkedExpense != null && !linkedExpense.isDeleted && linkedExpense.familyId == familyId) {
                        val shouldRevive = category.isDeleted || category.parentId != rootId || category.title.isBlank()
                        if (shouldRevive) {
                            restoredCount += 1
                            restoredCats += 1
                            categoryDao.upsert(
                                category.copy(
                                    title = if (category.title.isBlank()) linkedExpense.title.trim().ifBlank { "Spesa" } else category.title,
                                    parentId = rootId,
                                    isDeleted = false,
                                    updatedAtEpochMillis = now,
                                    updatedBy = uid,
                                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                                    lastSyncError = null,
                                ),
                            )
                        }
                    }
                }

            val orphanDocs = documentDao.getOrphanedExpenseDocuments(familyId)
            val allCategories = categoryDao.getAllByFamilyId(familyId).associateBy { it.id }
            orphanDocs.forEach { doc ->
                if (KBSyncState.fromRaw(doc.syncStateRaw) == KBSyncState.PENDING_UPSERT) return@forEach
                val categoryId = doc.categoryId
                val existingCategoryDeleted = categoryId != null && allCategories[categoryId]?.isDeleted == true
                val expenseIdFromNotes = parseExpenseIdFromNotes(doc.notes)
                val targetExpenseFolderId = expenseIdFromNotes?.let(::expenseCategoryFolderId)
                if (!targetExpenseFolderId.isNullOrBlank()) {
                    val expense = expenseDao.getById(expenseIdFromNotes)
                    if (expense != null && !expense.isDeleted && expense.familyId == familyId) {
                        val expenseFolder = categoryDao.getById(targetExpenseFolderId)?.copy(
                            title = expense.title.trim().ifBlank { "Spesa" },
                            parentId = rootId,
                            isDeleted = false,
                            updatedAtEpochMillis = now,
                            updatedBy = uid,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        ) ?: KBDocumentCategoryEntity(
                            id = targetExpenseFolderId,
                            familyId = familyId,
                            title = expense.title.trim().ifBlank { "Spesa" },
                            sortOrder = 0,
                            createdAtEpochMillis = now,
                            updatedAtEpochMillis = now,
                            updatedBy = uid,
                            isDeleted = false,
                            parentId = rootId,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        )
                        categoryDao.upsert(expenseFolder)
                        restoredCount += 1
                        restoredDocs += 1
                        documentDao.upsert(
                            doc.copy(
                                categoryId = targetExpenseFolderId,
                                updatedAtEpochMillis = now,
                                updatedBy = uid,
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                            ),
                        )
                        return@forEach
                    }
                }
                if (existingCategoryDeleted) {
                    restoredCount += 1
                    restoredDocs += 1
                    documentDao.upsert(
                        doc.copy(
                            categoryId = rootId,
                            updatedAtEpochMillis = now,
                            updatedBy = uid,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        ),
                    )
                    return@forEach
                }
                restoredCount += 1
                restoredDocs += 1
                documentDao.upsert(
                    doc.copy(
                        categoryId = rootId,
                        updatedAtEpochMillis = now,
                        updatedBy = uid,
                        syncStateRaw = KBSyncState.SYNCED.rawValue,
                        lastSyncError = null,
                    ),
                )
            }

            val expenseLinkedDocs = documentDao.getAllByFamilyId(familyId)
                .filter { isExpenseLinkedDocument(it.id, it.notes) }
            expenseLinkedDocs.forEach { doc ->
                if (KBSyncState.fromRaw(doc.syncStateRaw) == KBSyncState.PENDING_UPSERT) return@forEach
                val expenseId = parseExpenseIdFromNotes(doc.notes) ?: return@forEach
                val expense = expenseDao.getById(expenseId)
                val targetCategoryId = expenseCategoryFolderId(expenseId)
                if (expense == null) {
                    Log.d(
                        TAG_DOC_SYNC,
                        "healHierarchy expense not local yet for doc=${doc.id} expenseId=$expenseId; reattaching to deterministic folder",
                    )
                    val existingTargetCategory = categoryDao.getById(targetCategoryId)
                    if (existingTargetCategory == null) {
                        categoryDao.upsert(
                            KBDocumentCategoryEntity(
                                id = targetCategoryId,
                                familyId = familyId,
                                title = "Spesa",
                                sortOrder = 0,
                                createdAtEpochMillis = now,
                                updatedAtEpochMillis = now,
                                updatedBy = uid,
                                isDeleted = false,
                                parentId = rootId,
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                            ),
                        )
                        restoredCount += 1
                        restoredCats += 1
                    } else if (existingTargetCategory.isDeleted || existingTargetCategory.parentId != rootId) {
                        categoryDao.upsert(
                            existingTargetCategory.copy(
                                isDeleted = false,
                                parentId = rootId,
                                updatedAtEpochMillis = now,
                                updatedBy = uid,
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                            ),
                        )
                        restoredCount += 1
                        restoredCats += 1
                    }
                    if (doc.categoryId != targetCategoryId || doc.isDeleted) {
                        documentDao.upsert(
                            doc.copy(
                                categoryId = targetCategoryId,
                                isDeleted = false,
                                updatedAtEpochMillis = now,
                                updatedBy = uid,
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                            ),
                        )
                        restoredCount += 1
                        restoredDocs += 1
                    }
                    return@forEach
                }
                if (expense.isDeleted || expense.familyId != familyId) {
                    restoredCount += 1
                    restoredDocs += 1
                    documentDao.upsert(
                        doc.copy(
                            categoryId = rootId,
                            updatedAtEpochMillis = now,
                            updatedBy = uid,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        ),
                    )
                } else {
                    val shouldReviveDoc = doc.isDeleted || doc.categoryId != targetCategoryId
                    if (shouldReviveDoc) {
                        if (categoryDao.getById(targetCategoryId) == null) {
                            categoryDao.upsert(
                                KBDocumentCategoryEntity(
                                    id = targetCategoryId,
                                    familyId = familyId,
                                    title = expense.title.trim().ifBlank { "Spesa" },
                                    sortOrder = 0,
                                    createdAtEpochMillis = now,
                                    updatedAtEpochMillis = now,
                                    updatedBy = uid,
                                    isDeleted = false,
                                    parentId = rootId,
                                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                                    lastSyncError = null,
                                ),
                            )
                            restoredCount += 1
                            restoredCats += 1
                        }
                        restoredCount += 1
                        restoredDocs += 1
                        documentDao.upsert(
                            doc.copy(
                                categoryId = targetCategoryId,
                                isDeleted = false,
                                updatedAtEpochMillis = now,
                                updatedBy = uid,
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                            ),
                        )
                    }
                }
            }

            val systemEncodedRootDocs = documentDao.getAllByFamilyId(familyId)
                .filter { doc ->
                    !doc.isDeleted &&
                        doc.categoryId == null &&
                        isSystemEncodedSelectionResidual(doc)
                }
            systemEncodedRootDocs.forEach { doc ->
                restoredCount += 1
                restoredDocs += 1
                documentDao.upsert(
                    doc.copy(
                        isDeleted = true,
                        updatedAtEpochMillis = now,
                        updatedBy = uid,
                        syncStateRaw = KBSyncState.SYNCED.rawValue,
                        lastSyncError = null,
                    ),
                )
            }

            Log.d(
                TAG_DOC_SYNC,
                """
                --- 🛡️ Hierarchy Healing Report ---
                FamilyId: $familyId
                Root Node Status: ${if (rootExists) "VALID" else "REPAIRED/CREATED"}
                Orphaned Documents Fixed: $restoredDocs
                Orphaned Categories Fixed: $restoredCats
                Result: Hierarchy integrity secured for Expense module.
                ----------------------------------
                """.trimIndent(),
            )
            Log.d(TAG_DOC_SYNC, "Hierarchy Healing executed. Restored $restoredCount orphaned expense items.")
            restoredCount
        }
        healedFamiliesInSession += familyId
        return restored
    }

    suspend fun hasCriticalExpenseHierarchyInstability(familyId: String): Boolean {
        val rootId = expensesRootFolderId(familyId)
        val root = categoryDao.getById(rootId)
        if (root == null || root.isDeleted || root.parentId != null) return true

        val activeCategories = categoryDao.getAllByFamilyId(familyId).filter { !it.isDeleted }
        val activeCategoryIds = activeCategories.map { it.id }.toHashSet()
        if (activeCategories.any { it.id.startsWith("exp-cat-") && it.parentId != rootId }) return true

        val activeExpenseDocs = documentDao.getAllByFamilyId(familyId)
            .filter { !it.isDeleted && isExpenseLinkedDocument(it.id, it.notes) }

        return activeExpenseDocs.any { doc ->
            val categoryId = doc.categoryId?.trim()?.takeIf { it.isNotEmpty() } ?: return@any true
            categoryId !in activeCategoryIds
        }
    }

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && docsListener != null && categoriesListener != null) return@withLock
                stopRealtimeLocked()
                listeningFamilyId = familyId

                // 1) Pre-carica tutte le categorie con un get() one-shot e applicale in DB
                //    PRIMA di avviare il listener documenti.
                //    In questo modo, quando i documenti arrivano dal listener, l'albero delle
                //    cartelle è già completo con i parentId corretti: niente più placeholder
                //    agganciati al root per pochi ms (esperienza utente "file esplosi").
                runCatching {
                    val categories = remoteStore.fetchCategoriesOnce(familyId)
                    Log.d(
                        TAG_DOC_SYNC,
                        "prefetch categories familyId=$familyId count=${categories.size}",
                    )
                    categories.forEach { dto ->
                        applyInboundChange(
                            familyId,
                            DocumentRemoteChange.UpsertCategory(dto, isFromCache = false),
                        )
                    }
                }.onFailure { err ->
                    Log.w(TAG_DOC_SYNC, "prefetch categories failed familyId=$familyId: ${err.message}")
                    if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        onPermissionDenied?.invoke()
                        return@withLock
                    }
                    // Se il prefetch fallisce per motivi diversi (es. offline), si prosegue
                    // comunque coi listener: la cache persistita di Firestore farà fallback,
                    // e il comportamento legacy (placeholder) resta come safety net.
                }

                // 2) Avvia il listener categorie (per gli aggiornamenti incrementali)
                categoriesListener = remoteStore.listenCategories(
                    familyId = familyId,
                    onChange = { change ->
                        scope.launch(Dispatchers.IO) { applyInboundChange(familyId, change) }
                    },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )

                // 3) Solo ora avvia il listener documenti
                docsListener = remoteStore.listenDocuments(
                    familyId = familyId,
                    onChange = { change ->
                        scope.launch(Dispatchers.IO) { applyInboundChange(familyId, change) }
                    },
                    onError = { err ->
                        if (err is FirebaseFirestoreException && err.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            onPermissionDenied?.invoke()
                        }
                    },
                )
            }
        }
    }

    fun stopRealtime() {
        scope.launch {
            realtimeMutex.withLock { stopRealtimeLocked() }
        }
    }

    suspend fun createFolderLocal(
        familyId: String,
        title: String,
        parentId: String?,
        forcedId: String? = null,
        sortOrder: Int = 0,
    ): KBDocumentCategoryEntity {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        val id = forcedId ?: UUID.randomUUID().toString()
        val existing = categoryDao.getById(id)
        val folder = if (existing == null) {
            KBDocumentCategoryEntity(
                id = id,
                familyId = familyId,
                title = title.trim(),
                sortOrder = sortOrder,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                parentId = parentId,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
        } else {
            existing.copy(
                title = title.trim(),
                parentId = parentId,
                sortOrder = sortOrder,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
        }
        categoryDao.upsert(folder)
        return folder
    }

    suspend fun ensureExpenseFolders(
        familyId: String,
        expenseId: String,
        expenseTitle: String,
    ): KBDocumentCategoryEntity {
        val root = createFolderLocal(
            familyId = familyId,
            title = "Spese",
            parentId = null,
            forcedId = expensesRootFolderId(familyId),
            sortOrder = 99,
        )
        if (expenseId.isBlank()) return root
        val expenseFolder = createFolderLocal(
            familyId = familyId,
            title = expenseTitle.trim().ifBlank { "Spesa" },
            parentId = root.id,
            forcedId = expenseCategoryFolderId(expenseId),
            sortOrder = 0,
        )
        Log.d(
            TAG_DOC_REPO,
            "ensureExpenseFolders familyId=$familyId rootId=${root.id} expenseId=$expenseId expenseFolderId=${expenseFolder.id} parentId=${expenseFolder.parentId}",
        )
        return expenseFolder
    }

    suspend fun uploadDocumentLocal(
        familyId: String,
        parentFolderId: String?,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        notes: String? = null,
        forcedId: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        val id = forcedId ?: UUID.randomUUID().toString()
        val localPath = persistPendingPlainFile(id, fileName, bytes).absolutePath
        val placeholderStoragePath = "families/$familyId/documents/$id/${safeFileName(fileName)}.kbenc"
        val entity = KBDocumentEntity(
            id = id,
            familyId = familyId,
            childId = null,
            categoryId = parentFolderId,
            localPath = localPath,
            title = titleFromFileName(fileName),
            fileName = fileName,
            mimeType = mimeType,
            fileSize = bytes.size.toLong(),
            storagePath = placeholderStoragePath,
            downloadURL = null,
            notes = notes,
            extractedText = null,
            extractedTextUpdatedAtEpochMillis = null,
            extractionStatusRaw = 0,
            extractionError = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            isDeleted = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        documentDao.upsert(entity)
    }

    suspend fun createExpenseAttachmentLocalAtomically(
        familyId: String,
        expenseId: String,
        expenseTitle: String,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        forcedId: String? = null,
    ): KBDocumentEntity {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        val docId = forcedId ?: UUID.randomUUID().toString()
        val rootId = expensesRootFolderId(familyId)
        val expenseFolderId = expenseCategoryFolderId(expenseId)
        val localPath = persistPendingPlainFile(docId, fileName, bytes).absolutePath
        val placeholderStoragePath = "families/$familyId/documents/$docId/${safeFileName(fileName)}.kbenc"
        return database.withTransaction {
            val root = categoryDao.getById(rootId)?.copy(
                title = "Spese",
                parentId = null,
                sortOrder = 99,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ) ?: KBDocumentCategoryEntity(
                id = rootId,
                familyId = familyId,
                title = "Spese",
                sortOrder = 99,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                parentId = null,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
            categoryDao.upsert(root)
            val expenseFolder = categoryDao.getById(expenseFolderId)?.copy(
                title = expenseTitle.trim().ifBlank { "Spesa" },
                parentId = rootId,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ) ?: KBDocumentCategoryEntity(
                id = expenseFolderId,
                familyId = familyId,
                title = expenseTitle.trim().ifBlank { "Spesa" },
                sortOrder = 0,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                parentId = rootId,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
            categoryDao.upsert(expenseFolder)
            val entity = KBDocumentEntity(
                id = docId,
                familyId = familyId,
                childId = null,
                categoryId = expenseFolderId,
                localPath = localPath,
                title = titleFromFileName(fileName),
                fileName = fileName,
                mimeType = mimeType,
                fileSize = bytes.size.toLong(),
                storagePath = placeholderStoragePath,
                downloadURL = null,
                notes = "expense:$expenseId",
                extractedText = null,
                extractedTextUpdatedAtEpochMillis = null,
                extractionStatusRaw = 0,
                extractionError = null,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = uid,
                isDeleted = false,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
            documentDao.upsert(entity)
            Log.d(
                TAG_DOC_SYNC,
                "Local hierarchy consolidated for expense folder $expenseFolderId",
            )
            entity
        }
    }

    suspend fun deleteDocumentLocal(document: KBDocumentEntity) {
        documentDao.upsert(
            document.copy(
                isDeleted = true,
                syncStateRaw = KBSyncState.PENDING_DELETE.rawValue,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: document.updatedBy,
                lastSyncError = null,
            ),
        )
    }

    suspend fun deleteFolderLocal(folder: KBDocumentCategoryEntity) {
        if (folder.id.startsWith("exp-root-")) {
            Log.d(TAG_DOC_SYNC, "Blocked delete on expense root folder id=${folder.id}")
            return
        }
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: folder.updatedBy
        database.withTransaction {
            val allFolders = categoryDao.getAllByFamilyId(folder.familyId)
            val descendantsByParent = allFolders.groupBy { it.parentId }
            val folderIdsToDelete = linkedSetOf<String>()
            val queue = ArrayDeque<String>()
            queue.add(folder.id)
            while (queue.isNotEmpty()) {
                val currentId = queue.removeFirst()
                if (!folderIdsToDelete.add(currentId)) continue
                descendantsByParent[currentId].orEmpty().forEach { child ->
                    queue.add(child.id)
                }
            }

            val allDocs = documentDao.getAllByFamilyId(folder.familyId)
            allDocs
                .filter { it.categoryId != null && folderIdsToDelete.contains(it.categoryId) }
                .forEach { doc ->
                    if (KBSyncState.fromRaw(doc.syncStateRaw) == KBSyncState.PENDING_DELETE) return@forEach
                    documentDao.upsert(
                        doc.copy(
                            isDeleted = true,
                            syncStateRaw = KBSyncState.PENDING_DELETE.rawValue,
                            updatedAtEpochMillis = now,
                            updatedBy = uid,
                            lastSyncError = null,
                        ),
                    )
                }

            allFolders
                .filter { folderIdsToDelete.contains(it.id) }
                .forEach { current ->
                    if (KBSyncState.fromRaw(current.syncStateRaw) == KBSyncState.PENDING_DELETE) return@forEach
                    categoryDao.upsert(
                        current.copy(
                            isDeleted = true,
                            syncStateRaw = KBSyncState.PENDING_DELETE.rawValue,
                            updatedAtEpochMillis = now,
                            updatedBy = uid,
                            lastSyncError = null,
                        ),
                    )
                }
        }
    }

    suspend fun moveDocumentLocal(
        document: KBDocumentEntity,
        destinationFolderId: String?,
    ) {
        database.withTransaction {
            documentDao.upsert(
                document.copy(
                    categoryId = destinationFolderId,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    updatedBy = auth.currentUser?.uid ?: document.updatedBy,
                    syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                    lastSyncError = null,
                ),
            )
        }
    }

    suspend fun attachExistingDocumentToExpense(
        familyId: String,
        expenseId: String,
        expenseTitle: String,
        documentId: String,
    ): KBDocumentEntity? {
        val doc = documentDao.getById(documentId) ?: return null
        if (doc.familyId != familyId || doc.isDeleted) return null
        val folder = ensureExpenseFolders(
            familyId = familyId,
            expenseId = expenseId,
            expenseTitle = expenseTitle,
        )
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: doc.updatedBy
        val updated = doc.copy(
            categoryId = folder.id,
            notes = "expense:$expenseId",
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
            isDeleted = false,
        )
        documentDao.upsert(updated)
        return updated
    }

    suspend fun moveFolderLocal(
        folder: KBDocumentCategoryEntity,
        destinationFolderId: String?,
    ) {
        if (folder.id == destinationFolderId) return
        database.withTransaction {
            categoryDao.upsert(
                folder.copy(
                    parentId = destinationFolderId,
                    updatedAtEpochMillis = System.currentTimeMillis(),
                    updatedBy = auth.currentUser?.uid ?: folder.updatedBy,
                    syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                    lastSyncError = null,
                ),
            )
        }
    }

    suspend fun renameDocumentLocal(
        document: KBDocumentEntity,
        newTitle: String,
    ) {
        documentDao.upsert(
            document.copy(
                title = newTitle.trim(),
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: document.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun renameFolderLocal(
        folder: KBDocumentCategoryEntity,
        newTitle: String,
    ) {
        categoryDao.upsert(
            folder.copy(
                title = newTitle.trim(),
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: folder.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun duplicateDocumentLocal(
        source: KBDocumentEntity,
        destinationFolderId: String? = source.categoryId,
    ): KBDocumentEntity {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: source.updatedBy
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            categoryId = destinationFolderId,
            title = "${source.title} copia",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        documentDao.upsert(copy)
        return copy
    }

    suspend fun duplicateFolderLocal(
        source: KBDocumentCategoryEntity,
        destinationParentId: String? = source.parentId,
    ): KBDocumentCategoryEntity {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: source.updatedBy
        val copy = source.copy(
            id = UUID.randomUUID().toString(),
            parentId = destinationParentId,
            title = "${source.title} copia",
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        categoryDao.upsert(copy)
        return copy
    }

    suspend fun flushPending(familyId: String) {
        categoryDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue)
            .forEach { folder ->
                runCatching { remoteStore.upsertCategory(folder) }
                    .onSuccess {
                        categoryDao.upsert(
                            folder.copy(
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                            ),
                        )
                    }
                    .onFailure { err ->
                        categoryDao.upsert(folder.copy(lastSyncError = err.localizedMessage))
                    }
            }

        documentDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue)
            .forEach { doc ->
                val withUploadedBlob = ensureStorageUploaded(doc)
                runCatching { remoteStore.upsertDocument(withUploadedBlob) }
                    .onSuccess {
                        documentDao.upsert(
                            withUploadedBlob.copy(
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                                updatedAtEpochMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                    .onFailure { err ->
                        documentDao.upsert(withUploadedBlob.copy(lastSyncError = err.localizedMessage))
                    }
            }

        documentDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue)
            .forEach { doc ->
                runCatching {
                    remoteStore.softDeleteDocument(familyId, doc.id)
                    storageManager.delete(doc.storagePath)
                    documentDao.deleteById(doc.id)
                }.onFailure { err ->
                    documentDao.upsert(doc.copy(lastSyncError = err.localizedMessage))
                }
            }

        categoryDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue)
            .forEach { folder ->
                runCatching {
                    remoteStore.softDeleteCategory(familyId, folder.id)
                    categoryDao.deleteById(folder.id)
                }.onFailure { err ->
                    categoryDao.upsert(folder.copy(lastSyncError = err.localizedMessage))
                }
            }
    }

    suspend fun preparePreviewFile(document: KBDocumentEntity): File {
        Log.d(
            TAG_DOC_REPO,
            "preparePreviewFile docId=${document.id} hasLocal=${!document.localPath.isNullOrBlank()} storagePath=${document.storagePath}",
        )
        val previewDir = File(context.cacheDir, "kb_documents_preview").apply { mkdirs() }
        val output = File(previewDir, "${document.id}_${safeFileName(document.fileName)}")
        val local = document.localPath?.let { File(it) }
        if (local != null && local.exists()) {
            Log.d(TAG_DOC_REPO, "preparePreviewFile using local file docId=${document.id} localPath=${local.absolutePath}")
            local.copyTo(output, overwrite = true)
            Log.d(TAG_DOC_REPO, "preparePreviewFile local copy done docId=${document.id} out=${output.absolutePath}")
            return output
        }
        Log.d(TAG_DOC_REPO, "preparePreviewFile downloading+decrypting docId=${document.id}")
        val decrypted = storageManager.downloadDecrypted(
            storagePath = document.storagePath,
            familyId = document.familyId,
        )
        output.writeBytes(decrypted)
        Log.d(TAG_DOC_REPO, "preparePreviewFile decrypt done docId=${document.id} outSize=${output.length()}")
        return output
    }

    fun expensesRootFolderId(familyId: String): String = "exp-root-$familyId"

    fun expenseCategoryFolderId(expenseId: String): String = "exp-cat-$expenseId"

    private suspend fun ensureStorageUploaded(doc: KBDocumentEntity): KBDocumentEntity {
        if (!doc.downloadURL.isNullOrBlank()) return doc
        val local = doc.localPath?.let { File(it) }
        if (local == null || !local.exists()) return doc
        val upload = storageManager.uploadEncrypted(
            familyId = doc.familyId,
            docId = doc.id,
            fileName = doc.fileName,
            mimeType = doc.mimeType,
            plainBytes = local.readBytes(),
        )
        return doc.copy(storagePath = upload.storagePath, downloadURL = upload.downloadUrl)
    }

    private suspend fun applyInboundChange(
        familyId: String,
        change: DocumentRemoteChange,
    ) {
        inboundMutex.withLock {
            val now = System.currentTimeMillis()
            when (change) {
                is DocumentRemoteChange.RemoveDocument -> applyInboundDocumentDelete(
                    familyId = familyId,
                    id = change.id,
                    now = now,
                    isFromCache = change.isFromCache,
                )
                is DocumentRemoteChange.RemoveCategory -> applyInboundCategoryDelete(
                    familyId = familyId,
                    id = change.id,
                    now = now,
                    isFromCache = change.isFromCache,
                )
                is DocumentRemoteChange.UpsertCategory -> applyInboundCategory(
                    familyId = familyId,
                    dto = change.dto,
                    now = now,
                    isFromCache = change.isFromCache,
                )
                is DocumentRemoteChange.UpsertDocument -> applyInboundDocument(
                    familyId = familyId,
                    dto = change.dto,
                    now = now,
                    isFromCache = change.isFromCache,
                )
            }
        }
    }

    private suspend fun applyInboundCategory(
        familyId: String,
        dto: it.vittorioscocca.kidbox.data.remote.RemoteDocumentCategoryDto,
        now: Long,
        isFromCache: Boolean,
    ) {
        val local = categoryDao.getById(dto.id)
        val localSync = local?.let { KBSyncState.fromRaw(it.syncStateRaw) }
        val remoteUpdatedAt = dto.updatedAtEpochMillis ?: 0L
        Log.d(
            TAG_DOC_SYNC,
            "inbound category id=${dto.id} parent=${dto.parentId} remoteUpdatedAt=$remoteUpdatedAt localUpdatedAt=${local?.updatedAtEpochMillis} isFromCache=$isFromCache",
        )

        if (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.PENDING_DELETE) {
            Log.d(TAG_DOC_SYNC, "Dropped: Local is Pending category id=${dto.id} state=$localSync")
            return
        }

        if (dto.isDeleted) {
            val linkedExpenseId = parseExpenseIdFromCategoryId(dto.id)
            if (!linkedExpenseId.isNullOrBlank()) {
                val linkedExpense = expenseDao.getById(linkedExpenseId)
                if (linkedExpense != null && !linkedExpense.isDeleted && linkedExpense.familyId == familyId) {
                    Log.d(
                        TAG_DOC_SYNC,
                        "Dropped category delete id=${dto.id} reason=linked_expense_still_active expenseId=$linkedExpenseId",
                    )
                    return
                }
            }
            if (local == null) return
            if (remoteUpdatedAt <= local.updatedAtEpochMillis) {
                Log.d(TAG_DOC_SYNC, "Dropped category delete id=${dto.id} reason=remote_not_newer")
                return
            }
            categoryDao.upsert(
                local.copy(
                    isDeleted = true,
                    updatedAtEpochMillis = remoteUpdatedAt,
                    updatedBy = dto.updatedBy ?: local.updatedBy,
                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                    lastSyncError = null,
                ),
            )
            Log.d(TAG_DOC_SYNC, "Applied category soft-delete id=${dto.id}")
            return
        }

        var resolvedParentId = dto.parentId?.trim()?.takeIf { it.isNotEmpty() }
            ?: local?.parentId
        if (dto.id.startsWith("exp-root-")) {
            resolvedParentId = null
        } else if (resolvedParentId == dto.id) {
            resolvedParentId = null
        }
        val expectedParentId = expectedParentForCategoryId(
            familyId = familyId,
            categoryId = dto.id,
        )
        if (resolvedParentId.isNullOrBlank() && !expectedParentId.isNullOrBlank()) {
            Log.d(
                TAG_DOC_SYNC,
                "guard category id=${dto.id} reason=missing_parent_for_deterministic_id forcingParent=$expectedParentId",
            )
            resolvedParentId = expectedParentId
        }
        // Se il record locale è un placeholder appena creato (titolo generico),
        // accettiamo comunque l'inbound remoto anche se più "vecchio" — il remoto
        // contiene il titolo autoritativo che dobbiamo adottare.
        val isLocalPlaceholderTitle = local != null && local.title in PLACEHOLDER_CATEGORY_TITLES
        val remoteHasRealTitle = dto.title.isNotBlank() && dto.title !in PLACEHOLDER_CATEGORY_TITLES
        val shouldOverrideLwwForHierarchy = local != null &&
            remoteUpdatedAt <= local.updatedAtEpochMillis &&
            (
                (local.parentId.isNullOrBlank() && !resolvedParentId.isNullOrBlank()) ||
                    (local.parentId != resolvedParentId && dto.id.startsWith("exp-cat-")) ||
                    (isLocalPlaceholderTitle && remoteHasRealTitle)
                )
        val preserveLocalHierarchy = local != null &&
            local.parentId != null &&
            dto.parentId == null &&
            !dto.id.startsWith("exp-root-") &&
            (remoteUpdatedAt - local.updatedAtEpochMillis) < 5_000L
        if (preserveLocalHierarchy) {
            resolvedParentId = local.parentId
            Log.d(TAG_DOC_SYNC, "Overriding LWW to preserve hierarchy for ID: ${dto.id}")
        }
        if (
            isFromCache &&
            local != null &&
            dto.parentId == null &&
            !local.parentId.isNullOrBlank() &&
            !dto.id.startsWith("exp-root-")
        ) {
            resolvedParentId = local.parentId
            Log.d(TAG_DOC_SYNC, "Overriding LWW to preserve hierarchy for ID: ${dto.id}")
        }
        if (local != null && remoteUpdatedAt <= local.updatedAtEpochMillis && !shouldOverrideLwwForHierarchy) {
            Log.d(TAG_DOC_SYNC, "Dropped category id=${dto.id} reason=remote_not_newer")
            return
        }
        if (shouldOverrideLwwForHierarchy) {
            Log.d(TAG_DOC_SYNC, "Overriding LWW to preserve hierarchy for ID: ${dto.id}")
        }

        if (!resolvedParentId.isNullOrBlank() && categoryDao.getById(resolvedParentId) == null) {
            Log.d(TAG_DOC_SYNC, "create placeholder parent category id=$resolvedParentId child=${dto.id}")
            categoryDao.upsert(
                KBDocumentCategoryEntity(
                    id = resolvedParentId,
                    familyId = familyId,
                    title = "Cartella",
                    sortOrder = 0,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    updatedBy = dto.updatedBy ?: local?.updatedBy ?: "",
                    isDeleted = false,
                    parentId = null,
                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                    lastSyncError = null,
                ),
            )
        }

        val targetCategory = KBDocumentCategoryEntity(
            id = dto.id,
            familyId = familyId,
            title = dto.title.ifBlank { local?.title.orEmpty() },
            sortOrder = dto.sortOrder,
            createdAtEpochMillis = local?.createdAtEpochMillis ?: dto.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = remoteUpdatedAt.takeIf { it > 0L } ?: now,
            updatedBy = dto.updatedBy ?: local?.updatedBy ?: "",
            isDeleted = false,
            parentId = resolvedParentId,
            syncStateRaw = KBSyncState.SYNCED.rawValue,
            lastSyncError = null,
        )
        val unchangedCategory = local != null &&
            local.title == targetCategory.title &&
            local.sortOrder == targetCategory.sortOrder &&
            local.parentId == targetCategory.parentId &&
            local.isDeleted == targetCategory.isDeleted &&
            local.updatedAtEpochMillis == targetCategory.updatedAtEpochMillis &&
            local.updatedBy == targetCategory.updatedBy &&
            KBSyncState.fromRaw(local.syncStateRaw) == KBSyncState.SYNCED
        if (unchangedCategory) {
            Log.d(TAG_DOC_SYNC, "Skipped category upsert id=${dto.id} reason=unchanged")
            return
        }
        categoryDao.upsert(targetCategory)
        Log.d(TAG_DOC_SYNC, "Applied category id=${dto.id} parentResolved=$resolvedParentId")
    }

    private suspend fun applyInboundDocument(
        familyId: String,
        dto: it.vittorioscocca.kidbox.data.remote.RemoteDocumentDto,
        now: Long,
        isFromCache: Boolean,
    ) {
        val local = documentDao.getById(dto.id)
        val localSync = local?.let { KBSyncState.fromRaw(it.syncStateRaw) }
        val remoteUpdatedAt = dto.updatedAtEpochMillis ?: 0L
        Log.d(
            TAG_DOC_SYNC,
            "inbound document id=${dto.id} category=${dto.categoryId} remoteUpdatedAt=$remoteUpdatedAt localUpdatedAt=${local?.updatedAtEpochMillis} isFromCache=$isFromCache",
        )

        if (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.PENDING_DELETE) {
            Log.d(TAG_DOC_SYNC, "Dropped: Local is Pending document id=${dto.id} state=$localSync")
            return
        }

        if (dto.isDeleted) {
            if (local == null) return
            if (remoteUpdatedAt <= local.updatedAtEpochMillis) {
                Log.d(TAG_DOC_SYNC, "Dropped document delete id=${dto.id} reason=remote_not_newer")
                return
            }
            documentDao.upsert(
                local.copy(
                    isDeleted = true,
                    updatedAtEpochMillis = remoteUpdatedAt,
                    updatedBy = dto.updatedBy ?: local.updatedBy,
                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                    lastSyncError = null,
                ),
            )
            Log.d(TAG_DOC_SYNC, "Applied document soft-delete id=${dto.id}")
            return
        }

        var resolvedCategoryId = dto.categoryId?.trim()?.takeIf { it.isNotEmpty() } ?: local?.categoryId
        val expectedCategoryId = expectedCategoryForDocument(
            documentId = dto.id,
            notes = dto.notes ?: local?.notes,
        )
        if (resolvedCategoryId.isNullOrBlank() && !expectedCategoryId.isNullOrBlank()) {
            Log.d(
                TAG_DOC_SYNC,
                "guard document id=${dto.id} reason=missing_category_for_deterministic_payload forcingCategory=$expectedCategoryId",
            )
            resolvedCategoryId = expectedCategoryId
        }
        if (resolvedCategoryId.isNullOrBlank() && isExpenseLinkedDocument(dto.id, dto.notes ?: local?.notes)) {
            val fallbackRootId = expensesRootFolderId(familyId)
            Log.d(
                TAG_DOC_SYNC,
                "guard document id=${dto.id} reason=expense_linked_null_category forcingRoot=$fallbackRootId",
            )
            resolvedCategoryId = fallbackRootId
        }
        val linkedExpenseId = parseExpenseIdFromNotes(dto.notes ?: local?.notes)
        if (!linkedExpenseId.isNullOrBlank()) {
            val expense = expenseDao.getById(linkedExpenseId)
            val deterministicExpenseFolderId = expenseCategoryFolderId(linkedExpenseId)
            // Preferisci sempre la sottocartella deterministica (exp-cat-<expenseId>):
            // - se la spesa non è ancora in locale (race di startup tra listeners) NON
            //   ricablare a root, altrimenti i documenti finiscono "esplosi" sopra la
            //   loro sottocartella;
            // - la creazione del placeholder per la cartella mancante avviene poco più
            //   sotto, così entrando nella subfolder l'utente vede comunque il file.
            val preferredExpenseCategoryId = when {
                !resolvedCategoryId.isNullOrBlank() &&
                    resolvedCategoryId.startsWith("exp-cat-") -> resolvedCategoryId
                !local?.categoryId.isNullOrBlank() &&
                    local!!.categoryId!!.startsWith("exp-cat-") -> local.categoryId
                else -> deterministicExpenseFolderId
            }
            resolvedCategoryId = preferredExpenseCategoryId
            when {
                expense == null -> {
                    Log.d(
                        TAG_DOC_SYNC,
                        "expense not local yet for document id=${dto.id} expenseId=$linkedExpenseId; keeping deterministic folder=$preferredExpenseCategoryId",
                    )
                }
                expense.isDeleted || expense.familyId != familyId -> {
                    val fallbackRootId = expensesRootFolderId(familyId)
                    Log.d(
                        TAG_DOC_SYNC,
                        "guard document id=${dto.id} reason=linked_expense_missing_or_deleted expenseId=$linkedExpenseId forcingRoot=$fallbackRootId",
                    )
                    resolvedCategoryId = fallbackRootId
                }
                categoryDao.getById(deterministicExpenseFolderId)?.isDeleted == true -> {
                    // La sottocartella era stata soft-deleted: la riviviamo nel blocco
                    // "create/restore placeholder" più sotto, così non perdiamo gerarchia.
                    Log.d(
                        TAG_DOC_SYNC,
                        "expense subfolder soft-deleted for document id=${dto.id} expenseId=$linkedExpenseId; will revive placeholder=$preferredExpenseCategoryId",
                    )
                }
            }
        }
        if (isFromCache && local != null && dto.categoryId == null && !local.categoryId.isNullOrBlank()) {
            resolvedCategoryId = local.categoryId
            Log.d(
                TAG_DOC_SYNC,
                "Overriding LWW to preserve hierarchy for ID: ${dto.id} reason=cache_null_category_keep_local",
            )
        }
        val preserveLocalHierarchy = local != null &&
            local.categoryId != null &&
            dto.categoryId == null &&
            (remoteUpdatedAt - local.updatedAtEpochMillis) < 5_000L
        if (preserveLocalHierarchy) {
            resolvedCategoryId = local.categoryId
            Log.d(TAG_DOC_SYNC, "Overriding LWW to preserve hierarchy for ID: ${dto.id}")
        }
        val shouldOverrideLwwForHierarchy = local != null &&
            remoteUpdatedAt <= local.updatedAtEpochMillis &&
            local.categoryId.isNullOrBlank() &&
            !resolvedCategoryId.isNullOrBlank()
        if (local != null && remoteUpdatedAt <= local.updatedAtEpochMillis && !shouldOverrideLwwForHierarchy) {
            Log.d(TAG_DOC_SYNC, "Dropped document id=${dto.id} reason=remote_not_newer")
            return
        }
        if (shouldOverrideLwwForHierarchy) {
            Log.d(TAG_DOC_SYNC, "Overriding LWW to preserve hierarchy for ID: ${dto.id}")
        }
        if (!resolvedCategoryId.isNullOrBlank()) {
            val isExpenseDocument = isExpenseLinkedDocument(dto.id, dto.notes ?: local?.notes)
            var existingCategory = categoryDao.getById(resolvedCategoryId)
            if (existingCategory == null || existingCategory.isDeleted) {
                // Fix: NON ricablare a root quando manca la subfolder. Lasciamo
                // resolvedCategoryId puntare alla cartella deterministica (es.
                // exp-cat-<expenseId>) e creiamo/ripristiniamo qui sotto il
                // placeholder, così la gerarchia non viene "schiacciata" sulla root.
                Log.d(TAG_DOC_SYNC, "create/restore placeholder category id=$resolvedCategoryId for document=${dto.id} isExpense=$isExpenseDocument")
                val expenseIdFromCategory = parseExpenseIdFromCategoryId(resolvedCategoryId)
                val linkedExpense = expenseIdFromCategory?.let { expenseDao.getById(it) }
                val placeholderTitle = when {
                    resolvedCategoryId.startsWith("exp-root-") -> "Spese"
                    !existingCategory?.title.isNullOrBlank() -> existingCategory?.title.orEmpty()
                    linkedExpense != null && !linkedExpense.isDeleted && linkedExpense.familyId == familyId ->
                        linkedExpense.title.trim().ifBlank { "Spesa" }
                    isExpenseDocument -> "Allegato Spesa"
                    else -> "Cartella"
                }
                categoryDao.upsert(
                    KBDocumentCategoryEntity(
                        id = resolvedCategoryId,
                        familyId = familyId,
                        title = placeholderTitle,
                        sortOrder = existingCategory?.sortOrder ?: 0,
                        createdAtEpochMillis = existingCategory?.createdAtEpochMillis ?: now,
                        updatedAtEpochMillis = now,
                        updatedBy = dto.updatedBy ?: local?.updatedBy ?: "",
                        isDeleted = false,
                        parentId = expectedParentForCategoryId(familyId, resolvedCategoryId),
                        // Placeholder must stay local-only until authoritative category arrives.
                        syncStateRaw = KBSyncState.SYNCED.rawValue,
                        lastSyncError = null,
                    ),
                )
            }
        }

        val targetDocument = KBDocumentEntity(
            id = dto.id,
            familyId = familyId,
            childId = dto.childId,
            categoryId = resolvedCategoryId,
            localPath = local?.localPath,
            title = dto.title.ifBlank { titleFromFileName(dto.fileName) },
            fileName = dto.fileName,
            mimeType = dto.mimeType,
            fileSize = dto.fileSize,
            storagePath = dto.storagePath,
            downloadURL = dto.downloadURL,
            notes = dto.notes,
            extractedText = local?.extractedText,
            extractedTextUpdatedAtEpochMillis = local?.extractedTextUpdatedAtEpochMillis,
            extractionStatusRaw = local?.extractionStatusRaw ?: 0,
            extractionError = local?.extractionError,
            createdAtEpochMillis = local?.createdAtEpochMillis ?: dto.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = remoteUpdatedAt.takeIf { it > 0L } ?: now,
            updatedBy = dto.updatedBy ?: local?.updatedBy ?: "",
            isDeleted = false,
            syncStateRaw = KBSyncState.SYNCED.rawValue,
            lastSyncError = null,
        )
        val unchangedDocument = local != null &&
            local.categoryId == targetDocument.categoryId &&
            local.title == targetDocument.title &&
            local.fileName == targetDocument.fileName &&
            local.mimeType == targetDocument.mimeType &&
            local.fileSize == targetDocument.fileSize &&
            local.storagePath == targetDocument.storagePath &&
            local.downloadURL == targetDocument.downloadURL &&
            local.notes == targetDocument.notes &&
            local.updatedAtEpochMillis == targetDocument.updatedAtEpochMillis &&
            local.updatedBy == targetDocument.updatedBy &&
            !local.isDeleted &&
            KBSyncState.fromRaw(local.syncStateRaw) == KBSyncState.SYNCED
        if (unchangedDocument) {
            Log.d(TAG_DOC_SYNC, "Skipped document upsert id=${dto.id} reason=unchanged")
            return
        }
        val isInsert = local == null
        documentDao.upsert(targetDocument)
        if (isInsert) {
            recalculateDocumentHierarchy(
                familyId = familyId,
                documentId = dto.id,
                now = now,
            )
        }
        Log.d(TAG_DOC_SYNC, "Applied document id=${dto.id} categoryResolved=$resolvedCategoryId")
    }

    private suspend fun applyInboundDocumentDelete(
        familyId: String,
        id: String,
        now: Long,
        isFromCache: Boolean,
    ) {
        if (isFromCache) {
            Log.d(TAG_DOC_SYNC, "Dropped: cache remove document id=$id")
            return
        }
        val local = documentDao.getById(id) ?: return
        val localSync = KBSyncState.fromRaw(local.syncStateRaw)
        if (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.PENDING_DELETE) {
            Log.d(TAG_DOC_SYNC, "Dropped: Local is Pending document delete id=$id state=$localSync")
            return
        }
        documentDao.upsert(
            local.copy(
                isDeleted = true,
                updatedAtEpochMillis = maxOf(local.updatedAtEpochMillis, now),
                updatedBy = auth.currentUser?.uid ?: local.updatedBy,
                syncStateRaw = KBSyncState.SYNCED.rawValue,
                lastSyncError = null,
            ),
        )
        Log.d(TAG_DOC_SYNC, "Applied document remove event as soft-delete id=$id familyId=$familyId")
    }

    private suspend fun applyInboundCategoryDelete(
        familyId: String,
        id: String,
        now: Long,
        isFromCache: Boolean,
    ) {
        if (isFromCache) {
            Log.d(TAG_DOC_SYNC, "Dropped: cache remove category id=$id")
            return
        }
        val local = categoryDao.getById(id) ?: return
        val localSync = KBSyncState.fromRaw(local.syncStateRaw)
        if (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.PENDING_DELETE) {
            Log.d(TAG_DOC_SYNC, "Dropped: Local is Pending category delete id=$id state=$localSync")
            return
        }
        categoryDao.upsert(
            local.copy(
                isDeleted = true,
                updatedAtEpochMillis = maxOf(local.updatedAtEpochMillis, now),
                updatedBy = auth.currentUser?.uid ?: local.updatedBy,
                syncStateRaw = KBSyncState.SYNCED.rawValue,
                lastSyncError = null,
            ),
        )
        Log.d(TAG_DOC_SYNC, "Applied category remove event as soft-delete id=$id familyId=$familyId")
    }

    private fun expectedParentForCategoryId(
        familyId: String,
        categoryId: String,
    ): String? = when {
        categoryId.startsWith("exp-cat-") -> expensesRootFolderId(familyId)
        else -> null
    }

    private fun expectedCategoryForDocument(
        documentId: String,
        notes: String?,
    ): String? {
        if (documentId.startsWith("exp-doc-")) {
            val expenseId = documentId.removePrefix("exp-doc-").trim()
            if (expenseId.isNotEmpty()) return expenseCategoryFolderId(expenseId)
        }
        val expenseIdFromNotes = notes
            ?.takeIf { it.startsWith("expense:") }
            ?.substringAfter("expense:")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
        return expenseIdFromNotes?.let(::expenseCategoryFolderId)
    }

    private fun isExpenseLinkedDocument(
        documentId: String,
        notes: String?,
    ): Boolean =
        documentId.startsWith("exp-") ||
            notes?.startsWith("expense:") == true

    private fun parseExpenseIdFromNotes(notes: String?): String? =
        notes
            ?.takeIf { it.startsWith("expense:") }
            ?.substringAfter("expense:")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun parseExpenseIdFromCategoryId(categoryId: String?): String? =
        categoryId
            ?.takeIf { it.startsWith("exp-cat-") }
            ?.removePrefix("exp-cat-")
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun isSystemEncodedSelectionResidual(doc: KBDocumentEntity): Boolean {
        val name = doc.fileName.lowercase()
        val title = doc.title.lowercase()
        return name.startsWith("document:") ||
            title.startsWith("document:") ||
            name.contains("%3a") ||
            title.contains("%3a")
    }

    private suspend fun recalculateDocumentHierarchy(
        familyId: String,
        documentId: String,
        now: Long,
    ) {
        val doc = documentDao.getById(documentId) ?: return
        if (doc.familyId != familyId || doc.isDeleted) return
        var categoryId = doc.categoryId?.trim()?.takeIf { it.isNotEmpty() }
            ?: expectedCategoryForDocument(documentId = doc.id, notes = doc.notes)
        if (!categoryId.isNullOrBlank() && categoryDao.getById(categoryId) == null) {
            Log.d(TAG_DOC_SYNC, "refresh insert create placeholder category id=$categoryId document=$documentId")
            val expenseIdFromCategory = parseExpenseIdFromCategoryId(categoryId)
            val linkedExpense = expenseIdFromCategory?.let { expenseDao.getById(it) }
            val placeholderTitle = when {
                categoryId.startsWith("exp-root-") -> "Spese"
                linkedExpense != null && !linkedExpense.isDeleted && linkedExpense.familyId == familyId ->
                    linkedExpense.title.trim().ifBlank { "Spesa" }
                doc.notes?.startsWith("expense:") == true -> "Allegato Spesa"
                else -> "Cartella"
            }
            categoryDao.upsert(
                KBDocumentCategoryEntity(
                    id = categoryId,
                    familyId = familyId,
                    title = placeholderTitle,
                    sortOrder = 0,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    updatedBy = auth.currentUser?.uid ?: doc.updatedBy,
                    isDeleted = false,
                    parentId = expectedParentForCategoryId(familyId, categoryId),
                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                    lastSyncError = null,
                ),
            )
        }
        if (categoryId != doc.categoryId) {
            documentDao.upsert(
                doc.copy(
                    categoryId = categoryId,
                    updatedAtEpochMillis = maxOf(doc.updatedAtEpochMillis, now),
                    updatedBy = auth.currentUser?.uid ?: doc.updatedBy,
                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                    lastSyncError = null,
                ),
            )
            Log.d(TAG_DOC_SYNC, "refresh insert document=$documentId categoryRepaired=$categoryId")
        }
    }

    private fun persistPendingPlainFile(
        docId: String,
        fileName: String,
        bytes: ByteArray,
    ): File {
        val dir = File(context.filesDir, "kb_documents_pending").apply { mkdirs() }
        return File(dir, "${docId}_${safeFileName(fileName)}").apply {
            writeBytes(bytes)
        }
    }

    private fun safeFileName(name: String): String =
        name.replace(Regex("[^a-zA-Z0-9._-]"), "_").ifBlank { "file.bin" }

    private fun titleFromFileName(name: String): String {
        val idx = name.lastIndexOf('.')
        return if (idx > 0) name.substring(0, idx) else name
    }

    private fun stopRealtimeLocked() {
        docsListener?.remove()
        categoriesListener?.remove()
        docsListener = null
        categoriesListener = null
        listeningFamilyId = null
    }
}
