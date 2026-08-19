package it.vittorioscocca.kidbox.data.repository

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import androidx.room.withTransaction
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseDao
import it.vittorioscocca.kidbox.data.local.dao.OnboardingSignalsDao
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.support.DocumentImageCompressor
import it.vittorioscocca.kidbox.data.remote.DocumentRemoteChange
import it.vittorioscocca.kidbox.data.remote.DocumentRemoteStore
import it.vittorioscocca.kidbox.data.remote.RemoteDocumentDto
import it.vittorioscocca.kidbox.data.remote.DocumentStorageManager
import it.vittorioscocca.kidbox.data.remote.chat.ChatStorageService
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.model.KBSyncState
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import java.io.File
import java.util.ArrayDeque
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

private fun KBDocumentEntity.isVisibleToCurrentUser(uid: String?): Boolean {
    val scope = KBVisibilityScope.normalized(visibilityScope)
    val members = decodeStringList(visibilityMemberIdsJson)
    val creator = createdBy.takeIf { it.isNotBlank() } ?: updatedBy.takeIf { it.isNotBlank() }
    return KBVisibilityScope.isVisible(scope, members, creator, uid)
}


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
    private val chatStorageService: ChatStorageService,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
    private val onboardingSignalsDao: OnboardingSignalsDao,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private val inboundMutex = Mutex()
    private var docsListener: ListenerRegistration? = null
    private var categoriesListener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null
    private val healedFamiliesInSession = mutableSetOf<String>()
    private val lastRootSystemHiddenSignatureByFamily = mutableMapOf<String, String>()

    /**
     * Cartella visibile se il sottoalbero è **vuoto** (nessun doc non eliminato) oppure
     * se esiste **almeno un documento** nel sottoalbero che l’utente corrente può vedere.
     */
    private fun folderIsBrowsableByViewer(
        folder: KBDocumentCategoryEntity,
        allCategories: List<KBDocumentCategoryEntity>,
        allDocuments: List<KBDocumentEntity>,
        viewerUid: String?,
    ): Boolean {
        if (folder.isDeleted) return false
        val subtreeDocs = documentsInSubtreeForFolder(folder, allCategories, allDocuments)
        if (subtreeDocs.isEmpty()) return true
        return subtreeDocs.any { it.isVisibleToCurrentUser(viewerUid) }
    }

    private fun collectDescendantFolderIds(rootId: String, categories: List<KBDocumentCategoryEntity>): Set<String> {
        val active = categories.filter { !it.isDeleted }
        val byParent = active.groupBy { it.parentId }
        val out = linkedSetOf<String>()
        val queue = ArrayDeque<String>()
        queue.add(rootId)
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            if (!out.add(id)) continue
            byParent[id]?.forEach { queue.add(it.id) }
        }
        return out
    }

    private fun documentsInSubtreeForFolder(
        folder: KBDocumentCategoryEntity,
        allCategories: List<KBDocumentCategoryEntity>,
        allDocuments: List<KBDocumentEntity>,
    ): List<KBDocumentEntity> {
        val subtreeIds = collectDescendantFolderIds(folder.id, allCategories)
        val expenseIdFolder = parseExpenseIdFromCategoryId(folder.id)
        return allDocuments.filter { doc ->
            if (doc.isDeleted) return@filter false
            if (!doc.categoryId.isNullOrBlank() && doc.categoryId in subtreeIds) return@filter true
            if (expenseIdFolder != null &&
                parseExpenseIdFromNotes(doc.notes) == expenseIdFolder &&
                (doc.categoryId.isNullOrBlank() || doc.categoryId in subtreeIds)
            ) {
                return@filter true
            }
            false
        }
    }

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
                documentDao.observeByFamilyId(familyId),
            ) { categories, rootVisibleDocuments, hiddenSystemEncoded, allFamilyDocuments ->
                val viewerUid = auth.currentUser?.uid
                val hiddenSystemNames = hiddenSystemEncoded.map { it.fileName.ifBlank { it.title } }.sorted()
                val hiddenSystemSignature = hiddenSystemNames.joinToString("|")
                val previousSystem = lastRootSystemHiddenSignatureByFamily[familyId]
                if (hiddenSystemSignature != previousSystem) {
                    lastRootSystemHiddenSignatureByFamily[familyId] = hiddenSystemSignature
                    hiddenSystemNames.forEach { fileName ->
                        KBLog.data.debug("Hiding system-encoded file from Root: $fileName", TAG_DOC_SYNC)
                    }
                }
                val rootFolders = categories
                    .filter { it.parentId == null && !it.isDeleted }
                    .filter { folderIsBrowsableByViewer(it, categories, allFamilyDocuments, viewerUid) }
                    .sortedWith(compareBy<KBDocumentCategoryEntity> { it.sortOrder }.thenBy { it.title.lowercase() })
                DocumentBrowserData(
                    folders = rootFolders,
                    documents = rootVisibleDocuments
                        .filter { it.isVisibleToCurrentUser(viewerUid) }
                        .sortedByDescending { it.updatedAtEpochMillis },
                )
            }
        } else {
            combine(
                categoriesFlow,
                documentDao.observeByFamilyId(familyId),
            ) { categories, documents ->
                val viewerUid = auth.currentUser?.uid
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
                    .filter { it.isVisibleToCurrentUser(viewerUid) }
                    .sortedByDescending { it.updatedAtEpochMillis }
                val childFolders = categories
                    .filter { it.parentId == parentFolderId && !it.isDeleted }
                    .filter { folderIsBrowsableByViewer(it, categories, documents, viewerUid) }
                    .sortedWith(compareBy<KBDocumentCategoryEntity> { it.sortOrder }.thenBy { it.title.lowercase() })
                DocumentBrowserData(
                    folders = childFolders,
                    documents = documentsInFolder,
                )
            }
        }
    }

    fun observeAllDocuments(familyId: String): Flow<List<KBDocumentEntity>> =
        documentDao.observeByFamilyId(familyId)
            .map { list ->
                val uid = auth.currentUser?.uid
                list.filter { !it.isDeleted && it.isVisibleToCurrentUser(uid) }
            }

    fun observeAllFolders(familyId: String): Flow<List<KBDocumentCategoryEntity>> =
        combine(
            categoryDao.observeByFamilyId(familyId),
            documentDao.observeByFamilyId(familyId),
        ) { categories, docs ->
            val uid = auth.currentUser?.uid
            categories.filter {
                !it.isDeleted &&
                    folderIsBrowsableByViewer(it, categories, docs, uid)
            }
        }

    suspend fun getDocumentById(documentId: String): KBDocumentEntity? = documentDao.getById(documentId)

    suspend fun getFolderById(folderId: String): KBDocumentCategoryEntity? = categoryDao.getById(folderId)

    suspend fun healHierarchy(
        familyId: String,
        force: Boolean = false,
    ): Int {
        if (!force && healedFamiliesInSession.contains(familyId)) {
            KBLog.data.debug("healHierarchy skipped for familyId=$familyId (already healed this session)", TAG_DOC_SYNC)
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
                    KBLog.data.debug("healHierarchy expense not local yet for doc=${doc.id} expenseId=$expenseId; reattaching to deterministic folder", TAG_DOC_SYNC)
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

            KBLog.data.debug("""
                --- 🛡️ Hierarchy Healing Report ---
                FamilyId: $familyId
                Root Node Status: ${if (rootExists) "VALID" else "REPAIRED/CREATED"}
                Orphaned Documents Fixed: $restoredDocs
                Orphaned Categories Fixed: $restoredCats
                Result: Hierarchy integrity secured for Expense module.
                ----------------------------------
                """.trimIndent(), TAG_DOC_SYNC)
            KBLog.data.debug("Hierarchy Healing executed. Restored $restoredCount orphaned expense items.", TAG_DOC_SYNC)
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
                    KBLog.data.debug("prefetch categories familyId=$familyId count=${categories.size}", TAG_DOC_SYNC)
                    categories.forEach { dto ->
                        applyInboundChange(
                            familyId,
                            DocumentRemoteChange.UpsertCategory(dto, isFromCache = false),
                        )
                    }
                }.onFailure { err ->
                    KBLog.data.warning("prefetch categories failed familyId=$familyId: ${err.message}", TAG_DOC_SYNC)
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
        KBLog.data.debug("ensureExpenseFolders familyId=$familyId rootId=${root.id} expenseId=$expenseId expenseFolderId=${expenseFolder.id} parentId=${expenseFolder.parentId}", TAG_DOC_REPO)
        return expenseFolder
    }

    /** Cartella root «Garage» in Documenti (id deterministico, parity iOS `gar-root-{familyId}`). */
    fun garageRootFolderId(familyId: String): String = "gar-root-$familyId"

    suspend fun ensureGarageRootFolder(familyId: String): KBDocumentCategoryEntity {
        val all = categoryDao.getAllByFamilyId(familyId)
        val nextSort = (all.filter { it.parentId == null }.maxOfOrNull { it.sortOrder } ?: 0) + 1
        return createFolderLocal(
            familyId = familyId,
            title = "Garage",
            parentId = null,
            forcedId = garageRootFolderId(familyId),
            sortOrder = nextSort.coerceAtMost(96),
        )
    }

    fun casaRootFolderId(familyId: String): String = "home-root-$familyId"

    suspend fun ensureCasaRootFolder(familyId: String): KBDocumentCategoryEntity {
        val all = categoryDao.getAllByFamilyId(familyId)
        val nextSort = (all.filter { it.parentId == null }.maxOfOrNull { it.sortOrder } ?: 0) + 1
        return createFolderLocal(
            familyId = familyId,
            title = "Casa",
            parentId = null,
            forcedId = casaRootFolderId(familyId),
            sortOrder = nextSort.coerceAtMost(85),
        )
    }

    suspend fun uploadDocumentLocal(
        familyId: String,
        parentFolderId: String?,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
        notes: String? = null,
        forcedId: String? = null,
        visibilityScope: String = KBVisibilityScope.FAMILY,
        visibilityMemberIdsJson: String = encodeStringList(emptyList()),
        createdBy: String? = null,
    ) {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid ?: "local"
        var effectiveScope = KBVisibilityScope.normalized(visibilityScope)
        var membersJson = visibilityMemberIdsJson
        if (effectiveScope == KBVisibilityScope.MEMBERS && decodeStringList(membersJson).isEmpty()) {
            effectiveScope = KBVisibilityScope.FAMILY
            membersJson = encodeStringList(emptyList())
        } else if (effectiveScope != KBVisibilityScope.MEMBERS) {
            membersJson = encodeStringList(emptyList())
        }
        val storedCreatedBy = (createdBy?.takeIf { it.isNotBlank() } ?: uid).ifBlank { "local" }
        val isFirstDocument = onboardingSignalsDao.documentCount(familyId) == 0
        val id = forcedId ?: UUID.randomUUID().toString()
        // Comprimi le immagini ad alta risoluzione prima di persistere/caricare.
        val compressed = DocumentImageCompressor.compressIfNeeded(bytes, fileName, mimeType)
        val uploadBytes = compressed.bytes
        val uploadFileName = compressed.fileName
        val uploadMime = compressed.mimeType
        val localPath = persistPendingPlainFile(id, uploadFileName, uploadBytes).absolutePath
        val placeholderStoragePath = "families/$familyId/documents/$id/${safeFileName(uploadFileName)}.kbenc"
        val entity = KBDocumentEntity(
            id = id,
            familyId = familyId,
            childId = null,
            categoryId = parentFolderId,
            localPath = localPath,
            title = titleFromFileName(uploadFileName),
            fileName = uploadFileName,
            mimeType = uploadMime,
            fileSize = uploadBytes.size.toLong(),
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
            createdBy = storedCreatedBy,
            visibilityScope = effectiveScope,
            visibilityMemberIdsJson = membersJson,
            isDeleted = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        documentDao.upsert(entity)
        AppAnalytics.contentCreated(context, "documents")
        if (isFirstDocument) {
            AppAnalytics.featureFirstUse(context, feature = "documents")
        }
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
        // Comprimi le immagini ad alta risoluzione prima di persistere/caricare.
        val compressed = DocumentImageCompressor.compressIfNeeded(bytes, fileName, mimeType)
        val uploadBytes = compressed.bytes
        val uploadFileName = compressed.fileName
        val uploadMime = compressed.mimeType
        val localPath = persistPendingPlainFile(docId, uploadFileName, uploadBytes).absolutePath
        val placeholderStoragePath = "families/$familyId/documents/$docId/${safeFileName(uploadFileName)}.kbenc"
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
                title = titleFromFileName(uploadFileName),
                fileName = uploadFileName,
                mimeType = uploadMime,
                fileSize = uploadBytes.size.toLong(),
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
                createdBy = uid,
                visibilityScope = KBVisibilityScope.FAMILY,
                visibilityMemberIdsJson = encodeStringList(emptyList()),
                isDeleted = false,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            )
            documentDao.upsert(entity)
            KBLog.data.debug("Local hierarchy consolidated for expense folder $expenseFolderId", TAG_DOC_SYNC)
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
            KBLog.data.debug("Blocked delete on expense root folder id=${folder.id}", TAG_DOC_SYNC)
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

    suspend fun updateDocumentVisibilityLocal(
        document: KBDocumentEntity,
        visibilityScope: String,
        visibilityMemberIdsJson: String,
    ) {
        val uid = auth.currentUser?.uid ?: document.updatedBy
        var scope = KBVisibilityScope.normalized(visibilityScope)
        var membersJson = visibilityMemberIdsJson
        if (scope == KBVisibilityScope.MEMBERS && decodeStringList(membersJson).isEmpty()) {
            scope = KBVisibilityScope.FAMILY
            membersJson = encodeStringList(emptyList())
        } else if (scope != KBVisibilityScope.MEMBERS) {
            membersJson = encodeStringList(emptyList())
        }
        val storedCreatedBy = when {
            scope == KBVisibilityScope.ONLY_CREATOR ->
                document.createdBy.takeIf { it.isNotBlank() }
                    ?: uid.takeIf { it.isNotBlank() }
                    ?: document.updatedBy
            else -> document.createdBy
        }
        documentDao.upsert(
            document.copy(
                visibilityScope = scope,
                visibilityMemberIdsJson = membersJson,
                createdBy = storedCreatedBy,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = uid,
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
            createdBy = uid,
            visibilityScope = KBVisibilityScope.FAMILY,
            visibilityMemberIdsJson = encodeStringList(emptyList()),
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
        KBLog.data.debug("preparePreviewFile docId=${document.id} hasLocal=${!document.localPath.isNullOrBlank()} storagePath=${document.storagePath}", TAG_DOC_REPO)
        val previewDir = File(context.cacheDir, "kb_documents_preview").apply { mkdirs() }
        val output = File(previewDir, "${document.id}_${safeFileName(document.fileName)}")
        val local = document.localPath?.let { File(it) }
        if (local != null && local.exists()) {
            KBLog.data.debug("preparePreviewFile using local file docId=${document.id} localPath=${local.absolutePath}", TAG_DOC_REPO)
            val raw = local.readBytes()
            val plain = when {
                isPlainChatDocument(document, local.absolutePath) -> raw
                isPendingPlaintextAttachmentLocal(local.absolutePath) -> raw
                else -> storageManager.decryptCachedDocumentBytes(raw, document.familyId)
            }
            output.writeBytes(plain)
            KBLog.data.debug("preparePreviewFile local decrypt done docId=${document.id} out=${output.absolutePath} plainBytes=${plain.size}", TAG_DOC_REPO)
            return output
        }
        KBLog.data.debug("preparePreviewFile downloading docId=${document.id} chatPath=${isChatStoragePath(document.storagePath)}", TAG_DOC_REPO)
        val decrypted = if (isChatStoragePath(document.storagePath)) {
            chatStorageService.downloadDecrypted(
                storagePath = document.storagePath,
                familyId = document.familyId,
            )
        } else {
            storageManager.downloadDecrypted(
                storagePath = document.storagePath,
                familyId = document.familyId,
            )
        }
        output.writeBytes(decrypted)
        KBLog.data.debug("preparePreviewFile download done docId=${document.id} outSize=${output.length()}", TAG_DOC_REPO)
        return output
    }

    /** File allegati chat: su Storage sono in chiaro (stesso modello iOS), salvo legacy `.kbenc`. */
    private fun isChatStoragePath(storagePath: String): Boolean =
        storagePath.contains("/chat/")

    private fun isPlainChatDocument(doc: KBDocumentEntity, absoluteLocalPath: String): Boolean =
        doc.notes == "chat_plain" ||
            absoluteLocalPath.contains("chat_media${File.separator}") ||
            absoluteLocalPath.contains("/chat_media/")

    /** Allegati Salute/Casa/Garage: [HealthAttachmentService.writePendingFile] salva il file in chiaro prima dell'upload. */
    private fun isPendingPlaintextAttachmentLocal(absoluteLocalPath: String): Boolean =
        absoluteLocalPath.contains("${File.separator}kb_documents_pending${File.separator}") ||
            absoluteLocalPath.contains("/kb_documents_pending/")

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

    /** Ordine pipeline OCR: snapshot Firestore spesso indietro rispetto al device locale. */
    private fun extractionProgressRank(raw: Int): Int = when (raw) {
        KBTextExtractionStatus.NONE.rawValue -> 0
        KBTextExtractionStatus.PENDING.rawValue -> 1
        KBTextExtractionStatus.PROCESSING.rawValue -> 2
        KBTextExtractionStatus.COMPLETED.rawValue,
        KBTextExtractionStatus.FAILED.rawValue,
        -> 3
        else -> 0
    }

    private fun mergeInboundDocumentExtractionStatus(remote: Int?, local: Int?): Int {
        val loc = local ?: KBTextExtractionStatus.NONE.rawValue
        if (remote == null) return loc
        return if (extractionProgressRank(loc) >= extractionProgressRank(remote)) loc else remote
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
        KBLog.data.debug("inbound category id=${dto.id} parent=${dto.parentId} remoteUpdatedAt=$remoteUpdatedAt localUpdatedAt=${local?.updatedAtEpochMillis} isFromCache=$isFromCache", TAG_DOC_SYNC)

        if (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.PENDING_DELETE) {
            KBLog.data.debug("Dropped: Local is Pending category id=${dto.id} state=$localSync", TAG_DOC_SYNC)
            return
        }

        if (dto.isDeleted) {
            val linkedExpenseId = parseExpenseIdFromCategoryId(dto.id)
            if (!linkedExpenseId.isNullOrBlank()) {
                val linkedExpense = expenseDao.getById(linkedExpenseId)
                if (linkedExpense != null && !linkedExpense.isDeleted && linkedExpense.familyId == familyId) {
                    KBLog.data.debug("Dropped category delete id=${dto.id} reason=linked_expense_still_active expenseId=$linkedExpenseId", TAG_DOC_SYNC)
                    return
                }
            }
            if (local == null) return
            if (remoteUpdatedAt <= local.updatedAtEpochMillis) {
                KBLog.data.debug("Dropped category delete id=${dto.id} reason=remote_not_newer", TAG_DOC_SYNC)
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
            KBLog.data.debug("Applied category soft-delete id=${dto.id}", TAG_DOC_SYNC)
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
            KBLog.data.debug("guard category id=${dto.id} reason=missing_parent_for_deterministic_id forcingParent=$expectedParentId", TAG_DOC_SYNC)
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
            KBLog.data.debug("Overriding LWW to preserve hierarchy for ID: ${dto.id}", TAG_DOC_SYNC)
        }
        if (
            isFromCache &&
            local != null &&
            dto.parentId == null &&
            !local.parentId.isNullOrBlank() &&
            !dto.id.startsWith("exp-root-")
        ) {
            resolvedParentId = local.parentId
            KBLog.data.debug("Overriding LWW to preserve hierarchy for ID: ${dto.id}", TAG_DOC_SYNC)
        }
        if (local != null && remoteUpdatedAt <= local.updatedAtEpochMillis && !shouldOverrideLwwForHierarchy) {
            KBLog.data.debug("Dropped category id=${dto.id} reason=remote_not_newer", TAG_DOC_SYNC)
            return
        }
        if (shouldOverrideLwwForHierarchy) {
            KBLog.data.debug("Overriding LWW to preserve hierarchy for ID: ${dto.id}", TAG_DOC_SYNC)
        }

        if (!resolvedParentId.isNullOrBlank() && categoryDao.getById(resolvedParentId) == null) {
            KBLog.data.debug("create placeholder parent category id=$resolvedParentId child=${dto.id}", TAG_DOC_SYNC)
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
            KBLog.data.debug("Skipped category upsert id=${dto.id} reason=unchanged", TAG_DOC_SYNC)
            return
        }
        categoryDao.upsert(targetCategory)
        KBLog.data.debug("Applied category id=${dto.id} parentResolved=$resolvedParentId", TAG_DOC_SYNC)
    }

    private suspend fun applyInboundDocument(
        familyId: String,
        dto: RemoteDocumentDto,
        now: Long,
        isFromCache: Boolean,
    ) {
        val local = documentDao.getById(dto.id)
        val localSync = local?.let { KBSyncState.fromRaw(it.syncStateRaw) }
        val remoteUpdatedAt = dto.updatedAtEpochMillis ?: 0L
        KBLog.data.debug("inbound document id=${dto.id} category=${dto.categoryId} remoteUpdatedAt=$remoteUpdatedAt localUpdatedAt=${local?.updatedAtEpochMillis} isFromCache=$isFromCache", TAG_DOC_SYNC)

        if (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.PENDING_DELETE) {
            KBLog.data.debug("Dropped: Local is Pending document id=${dto.id} state=$localSync", TAG_DOC_SYNC)
            return
        }

        if (dto.isDeleted) {
            if (local == null) return
            if (remoteUpdatedAt <= local.updatedAtEpochMillis) {
                KBLog.data.debug("Dropped document delete id=${dto.id} reason=remote_not_newer", TAG_DOC_SYNC)
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
            KBLog.data.debug("Applied document soft-delete id=${dto.id}", TAG_DOC_SYNC)
            return
        }

        var resolvedCategoryId = dto.categoryId?.trim()?.takeIf { it.isNotEmpty() } ?: local?.categoryId
        val expectedCategoryId = expectedCategoryForDocument(
            documentId = dto.id,
            notes = dto.notes ?: local?.notes,
        )
        if (resolvedCategoryId.isNullOrBlank() && !expectedCategoryId.isNullOrBlank()) {
            KBLog.data.debug("guard document id=${dto.id} reason=missing_category_for_deterministic_payload forcingCategory=$expectedCategoryId", TAG_DOC_SYNC)
            resolvedCategoryId = expectedCategoryId
        }
        if (resolvedCategoryId.isNullOrBlank() && isExpenseLinkedDocument(dto.id, dto.notes ?: local?.notes)) {
            val fallbackRootId = expensesRootFolderId(familyId)
            KBLog.data.debug("guard document id=${dto.id} reason=expense_linked_null_category forcingRoot=$fallbackRootId", TAG_DOC_SYNC)
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
                    KBLog.data.debug("expense not local yet for document id=${dto.id} expenseId=$linkedExpenseId; keeping deterministic folder=$preferredExpenseCategoryId", TAG_DOC_SYNC)
                }
                expense.isDeleted || expense.familyId != familyId -> {
                    val fallbackRootId = expensesRootFolderId(familyId)
                    KBLog.data.debug("guard document id=${dto.id} reason=linked_expense_missing_or_deleted expenseId=$linkedExpenseId forcingRoot=$fallbackRootId", TAG_DOC_SYNC)
                    resolvedCategoryId = fallbackRootId
                }
                categoryDao.getById(deterministicExpenseFolderId)?.isDeleted == true -> {
                    // La sottocartella era stata soft-deleted: la riviviamo nel blocco
                    // "create/restore placeholder" più sotto, così non perdiamo gerarchia.
                    KBLog.data.debug("expense subfolder soft-deleted for document id=${dto.id} expenseId=$linkedExpenseId; will revive placeholder=$preferredExpenseCategoryId", TAG_DOC_SYNC)
                }
            }
        }
        if (isFromCache && local != null && dto.categoryId == null && !local.categoryId.isNullOrBlank()) {
            resolvedCategoryId = local.categoryId
            KBLog.data.debug("Overriding LWW to preserve hierarchy for ID: ${dto.id} reason=cache_null_category_keep_local", TAG_DOC_SYNC)
        }
        val preserveLocalHierarchy = local != null &&
            local.categoryId != null &&
            dto.categoryId == null &&
            (remoteUpdatedAt - local.updatedAtEpochMillis) < 5_000L
        if (preserveLocalHierarchy) {
            resolvedCategoryId = local.categoryId
            KBLog.data.debug("Overriding LWW to preserve hierarchy for ID: ${dto.id}", TAG_DOC_SYNC)
        }
        val shouldOverrideLwwForHierarchy = local != null &&
            remoteUpdatedAt <= local.updatedAtEpochMillis &&
            local.categoryId.isNullOrBlank() &&
            !resolvedCategoryId.isNullOrBlank()
        if (local != null && remoteUpdatedAt <= local.updatedAtEpochMillis && !shouldOverrideLwwForHierarchy) {
            KBLog.data.debug("Dropped document id=${dto.id} reason=remote_not_newer", TAG_DOC_SYNC)
            return
        }
        if (shouldOverrideLwwForHierarchy) {
            KBLog.data.debug("Overriding LWW to preserve hierarchy for ID: ${dto.id}", TAG_DOC_SYNC)
        }
        if (!resolvedCategoryId.isNullOrBlank()) {
            val isExpenseDocument = isExpenseLinkedDocument(dto.id, dto.notes ?: local?.notes)
            var existingCategory = categoryDao.getById(resolvedCategoryId)
            if (existingCategory == null || existingCategory.isDeleted) {
                // Fix: NON ricablare a root quando manca la subfolder. Lasciamo
                // resolvedCategoryId puntare alla cartella deterministica (es.
                // exp-cat-<expenseId>) e creiamo/ripristiniamo qui sotto il
                // placeholder, così la gerarchia non viene "schiacciata" sulla root.
                KBLog.data.debug("create/restore placeholder category id=$resolvedCategoryId for document=${dto.id} isExpense=$isExpenseDocument", TAG_DOC_SYNC)
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
                        parentId = ensureParentCategoryExists(
                            familyId = familyId,
                            parentId = expectedParentForCategoryId(familyId, resolvedCategoryId),
                            now = now,
                            updatedBy = dto.updatedBy ?: local?.updatedBy ?: "",
                        ),
                        // Placeholder must stay local-only until authoritative category arrives.
                        syncStateRaw = KBSyncState.SYNCED.rawValue,
                        lastSyncError = null,
                    ),
                )
            }
        }

        // Rete di sicurezza: kb_documents.categoryId ha una FK verso le categorie.
        // Se a questo punto la cartella non esiste comunque (placeholder fallito o
        // rimosso da un CASCADE concorrente), puntare il documento a un id inesistente
        // farebbe fallire l'insert. Meglio mostrarlo in root che perdere la sync.
        if (!resolvedCategoryId.isNullOrBlank() && categoryDao.getById(resolvedCategoryId) == null) {
            KBLog.data.debug(
                "category $resolvedCategoryId still missing for document=${dto.id}; falling back to root",
                TAG_DOC_SYNC,
            )
            resolvedCategoryId = null
        }

        val remoteNormScope = KBVisibilityScope.normalized(dto.visibilityScope)
        val remoteMembers = dto.visibilityMemberIds.orEmpty().distinct().sorted()
        val visibilityMemberIdsStored = encodeStringList(
            if (remoteNormScope == KBVisibilityScope.MEMBERS) remoteMembers else emptyList(),
        )
        val inferredCreatedBy = dto.createdBy?.takeIf { it.isNotBlank() }
            ?: dto.updatedBy?.takeIf { it.isNotBlank() }
            ?: local?.createdBy?.takeIf { it.isNotBlank() }
            ?: local?.updatedBy?.takeIf { it.isNotBlank() }
            ?: ""

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
            // Keep local linkage tags (visit:/exam:/treatment:) when remote payload is partial.
            notes = dto.notes ?: local?.notes,
            extractedText = dto.extractedText ?: local?.extractedText,
            extractedTextUpdatedAtEpochMillis = dto.extractedTextUpdatedAtEpochMillis ?: local?.extractedTextUpdatedAtEpochMillis,
            extractionStatusRaw = mergeInboundDocumentExtractionStatus(
                remote = dto.extractionStatusRaw,
                local = local?.extractionStatusRaw,
            ),
            extractionError = dto.extractionError ?: local?.extractionError,
            createdAtEpochMillis = local?.createdAtEpochMillis ?: dto.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = remoteUpdatedAt.takeIf { it > 0L } ?: now,
            updatedBy = dto.updatedBy ?: local?.updatedBy ?: "",
            createdBy = inferredCreatedBy,
            visibilityScope = remoteNormScope,
            visibilityMemberIdsJson = visibilityMemberIdsStored,
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
            local.createdBy == targetDocument.createdBy &&
            local.visibilityScope == targetDocument.visibilityScope &&
            local.visibilityMemberIdsJson == targetDocument.visibilityMemberIdsJson &&
            !local.isDeleted &&
            KBSyncState.fromRaw(local.syncStateRaw) == KBSyncState.SYNCED
        if (unchangedDocument) {
            KBLog.data.debug("Skipped document upsert id=${dto.id} reason=unchanged", TAG_DOC_SYNC)
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
        KBLog.data.debug("Applied document id=${dto.id} categoryResolved=$resolvedCategoryId", TAG_DOC_SYNC)
    }

    private suspend fun applyInboundDocumentDelete(
        familyId: String,
        id: String,
        now: Long,
        isFromCache: Boolean,
    ) {
        if (isFromCache) {
            KBLog.data.debug("Dropped: cache remove document id=$id", TAG_DOC_SYNC)
            return
        }
        val local = documentDao.getById(id) ?: return
        val localSync = KBSyncState.fromRaw(local.syncStateRaw)
        if (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.PENDING_DELETE) {
            KBLog.data.debug("Dropped: Local is Pending document delete id=$id state=$localSync", TAG_DOC_SYNC)
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
        KBLog.data.debug("Applied document remove event as soft-delete id=$id familyId=$familyId", TAG_DOC_SYNC)
    }

    private suspend fun applyInboundCategoryDelete(
        familyId: String,
        id: String,
        now: Long,
        isFromCache: Boolean,
    ) {
        if (isFromCache) {
            KBLog.data.debug("Dropped: cache remove category id=$id", TAG_DOC_SYNC)
            return
        }
        val local = categoryDao.getById(id) ?: return
        val localSync = KBSyncState.fromRaw(local.syncStateRaw)
        if (localSync == KBSyncState.PENDING_UPSERT || localSync == KBSyncState.PENDING_DELETE) {
            KBLog.data.debug("Dropped: Local is Pending category delete id=$id state=$localSync", TAG_DOC_SYNC)
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
        KBLog.data.debug("Applied category remove event as soft-delete id=$id familyId=$familyId", TAG_DOC_SYNC)
    }

    private fun expectedParentForCategoryId(
        familyId: String,
        categoryId: String,
    ): String? = when {
        categoryId.startsWith("exp-cat-") -> expensesRootFolderId(familyId)
        else -> null
    }

    /**
     * Garantisce che la categoria padre esista prima di inserire un figlio.
     *
     * `kb_document_categories.parentId` ha una FK auto-referenziante: inserire una
     * categoria il cui padre non è ancora presente in locale fa fallire l'insert con
     * SQLITE_CONSTRAINT_FOREIGNKEY e, girando fuori dal main thread, l'eccezione
     * risaliva fino a KBCrashHandler facendo crashare l'app durante la sync.
     *
     * Succedeva con le cartelle spesa: il placeholder `exp-cat-<id>` veniva creato con
     * parent `exp-root-<familyId>`, che però non è garantito essere già arrivato (primo
     * avvio, sync parziale, o root rimossa da un CASCADE precedente).
     *
     * Se il padre manca lo creiamo come root placeholder (parentId = null, quindi
     * sempre valido); se non riusciamo a materializzarlo restituiamo null, così il
     * figlio finisce in root invece di far fallire l'intera sincronizzazione.
     */
    private suspend fun ensureParentCategoryExists(
        familyId: String,
        parentId: String?,
        now: Long,
        updatedBy: String,
    ): String? {
        val target = parentId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (categoryDao.getById(target) != null) return target

        KBLog.data.debug("create placeholder root category id=$target (missing parent)", TAG_DOC_SYNC)
        val created = runCatching {
            categoryDao.upsert(
                KBDocumentCategoryEntity(
                    id = target,
                    familyId = familyId,
                    title = if (target.startsWith("exp-root-")) "Spese" else "Cartella",
                    sortOrder = 0,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                    updatedBy = updatedBy,
                    isDeleted = false,
                    parentId = null,
                    syncStateRaw = KBSyncState.SYNCED.rawValue,
                    lastSyncError = null,
                ),
            )
        }.isSuccess
        if (!created) {
            KBLog.data.debug("placeholder root creation failed id=$target; falling back to root", TAG_DOC_SYNC)
        }
        return target.takeIf { created }
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
            KBLog.data.debug("refresh insert create placeholder category id=$categoryId document=$documentId", TAG_DOC_SYNC)
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
            KBLog.data.debug("refresh insert document=$documentId categoryRepaired=$categoryId", TAG_DOC_SYNC)
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
