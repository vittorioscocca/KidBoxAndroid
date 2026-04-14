package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import android.util.Log
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseEntity
import it.vittorioscocca.kidbox.data.remote.expenses.ExpenseRemoteChange
import it.vittorioscocca.kidbox.data.remote.expenses.ExpenseRemoteStore
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class ExpenseBrowserData(
    val expenses: List<KBExpenseEntity>,
    val categories: List<KBExpenseCategoryEntity>,
    val documents: List<KBDocumentEntity>,
    val documentFolders: List<KBDocumentCategoryEntity>,
)

@Singleton
class ExpenseRepository @Inject constructor(
    private val expenseDao: KBExpenseDao,
    private val categoryDao: KBExpenseCategoryDao,
    private val remoteStore: ExpenseRemoteStore,
    private val documentRepository: DocumentRepository,
    private val auth: FirebaseAuth,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val realtimeMutex = Mutex()
    private var expensesListener: ListenerRegistration? = null
    private var listeningFamilyId: String? = null

    fun observeExpensesData(familyId: String): Flow<ExpenseBrowserData> = combine(
        expenseDao.observeAllByFamilyId(familyId),
        categoryDao.observeByFamilyId(familyId),
        documentRepository.observeAllDocuments(familyId),
        documentRepository.observeAllFolders(familyId),
    ) { expenses, categories, documents, folders ->
        ExpenseBrowserData(
            expenses = expenses
                .filter { !it.isDeleted }
                .sortedByDescending { it.dateEpochMillis },
            categories = categories
                .filter { !it.isDeleted }
                .sortedWith(compareBy<KBExpenseCategoryEntity> { it.sortIndex }.thenBy { it.name.lowercase() }),
            documents = documents.sortedByDescending { it.updatedAtEpochMillis },
            documentFolders = folders
                .filter { !it.isDeleted }
                .sortedWith(compareBy<KBDocumentCategoryEntity> { it.sortOrder }.thenBy { it.title.lowercase() }),
        )
    }

    fun startRealtime(
        familyId: String,
        onPermissionDenied: (() -> Unit)? = null,
    ) {
        scope.launch {
            realtimeMutex.withLock {
                if (listeningFamilyId == familyId && expensesListener != null) return@withLock
                stopRealtimeLocked()
                listeningFamilyId = familyId
                seedDefaultCategories(familyId)
                // Pre-create "Spese" root folder for attachment parity.
                runCatching { documentRepository.ensureExpenseFolders(familyId, "", "") }
                runCatching { documentRepository.flushPending(familyId) }
                expensesListener = remoteStore.listenExpenses(
                    familyId = familyId,
                    onChange = { changes -> scope.launch { applyInbound(familyId, changes) } },
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

    suspend fun seedDefaultCategories(familyId: String) {
        val now = System.currentTimeMillis()
        val existing = categoryDao.getAllByFamilyId(familyId)
        val existingDefaults = existing.filter { it.isDefault && !it.isDeleted }
        val alreadyDeterministic = existingDefaults.count { it.id.startsWith("expcat-") } == defaultCategories.size
        if (alreadyDeterministic && existingDefaults.size == defaultCategories.size) return

        val oldByName = existingDefaults.associateBy { it.name }
        val oldToNewCategoryId = mutableMapOf<String, String>()
        val deterministic = defaultCategories.mapIndexed { index, item ->
            val newId = defaultCategoryId(familyId, item.slug)
            val old = oldByName[item.name]
            if (old != null && old.id != newId) oldToNewCategoryId[old.id] = newId
            KBExpenseCategoryEntity(
                id = newId,
                familyId = familyId,
                name = item.name,
                icon = item.icon,
                colorHex = item.colorHex,
                isDefault = true,
                sortIndex = index,
                createdAtEpochMillis = old?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = now,
                isDeleted = false,
            )
        }
        categoryDao.upsertAll(deterministic)

        // Migrate old default random IDs to deterministic IDs.
        if (oldToNewCategoryId.isNotEmpty()) {
            val migrated = expenseDao.getAllByFamilyId(familyId).map { exp ->
                val mapped = exp.categoryId?.let { oldToNewCategoryId[it] }
                if (mapped != null) {
                    exp.copy(
                        categoryId = mapped,
                        updatedAtEpochMillis = now,
                        syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                        lastSyncError = null,
                    )
                } else {
                    exp
                }
            }
            expenseDao.upsertAll(migrated)
            oldToNewCategoryId.keys.forEach { oldId -> categoryDao.deleteById(oldId) }
        }
    }

    suspend fun createExpenseLocal(
        familyId: String,
        title: String,
        amount: Double,
        dateEpochMillis: Long,
        categoryId: String?,
        notes: String?,
        attachedDocumentId: String? = null,
    ): KBExpenseEntity {
        val now = System.currentTimeMillis()
        val uid = auth.currentUser?.uid
        val safeAttachedDocumentId = sanitizeAttachedDocumentId(
            familyId = familyId,
            attachedDocumentId = attachedDocumentId,
        )
        val entity = KBExpenseEntity(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            title = title.trim(),
            amount = amount,
            dateEpochMillis = dateEpochMillis,
            categoryId = categoryId,
            notes = notes?.trim()?.takeIf { it.isNotEmpty() },
            attachedDocumentId = safeAttachedDocumentId,
            receiptThumbnailData = null,
            createdByUid = uid,
            updatedBy = uid,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            isDeleted = false,
            syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
            lastSyncError = null,
        )
        expenseDao.upsert(entity)
        return entity
    }

    suspend fun updateExpenseLocal(
        current: KBExpenseEntity,
        title: String,
        amount: Double,
        dateEpochMillis: Long,
        categoryId: String?,
        notes: String?,
        attachedDocumentId: String?,
    ) {
        val safeAttachedDocumentId = sanitizeAttachedDocumentId(
            familyId = current.familyId,
            attachedDocumentId = attachedDocumentId,
        )
        expenseDao.upsert(
            current.copy(
                title = title.trim(),
                amount = amount,
                dateEpochMillis = dateEpochMillis,
                categoryId = categoryId,
                notes = notes?.trim()?.takeIf { it.isNotEmpty() },
                attachedDocumentId = safeAttachedDocumentId,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: current.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun attachExistingDocumentToExpense(
        expense: KBExpenseEntity,
        documentId: String?,
    ) {
        val desiredDocumentId = documentId?.takeIf { it.isNotBlank() }
        val movedDocument = desiredDocumentId?.let { docId ->
            documentRepository.attachExistingDocumentToExpense(
                familyId = expense.familyId,
                expenseId = expense.id,
                expenseTitle = expense.title,
                documentId = docId,
            )
        }
        if (desiredDocumentId != null && movedDocument == null) {
            error("Documento selezionato non disponibile per lo spostamento nella cartella spesa")
        }
        Log.d(
            TAG_EXP_REPO,
            "attachExistingDocumentToExpense expenseId=${expense.id} title=${expense.title} docId=${movedDocument?.id} docCategoryId=${movedDocument?.categoryId}",
        )
        documentRepository.flushPending(expense.familyId)
        expenseDao.upsert(
            expense.copy(
                attachedDocumentId = movedDocument?.id,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: expense.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun deleteExpenseLocal(current: KBExpenseEntity) {
        expenseDao.upsert(
            current.copy(
                isDeleted = true,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: current.updatedBy,
                syncStateRaw = KBSyncState.PENDING_DELETE.rawValue,
                lastSyncError = null,
            ),
        )
    }

    suspend fun attachDocumentToExpense(
        expense: KBExpenseEntity,
        fileName: String,
        mimeType: String,
        bytes: ByteArray,
    ): String {
        val docId = UUID.randomUUID().toString()
        val localDoc = documentRepository.createExpenseAttachmentLocalAtomically(
            familyId = expense.familyId,
            expenseId = expense.id,
            expenseTitle = expense.title,
            fileName = fileName,
            mimeType = mimeType,
            bytes = bytes,
            forcedId = docId,
        )
        Log.d(
            TAG_EXP_REPO,
            "attachDocumentToExpense expenseId=${expense.id} title=${expense.title} folderId=${localDoc.categoryId} docId=$docId",
        )
        documentRepository.flushPending(expense.familyId)
        expenseDao.upsert(
            expense.copy(
                attachedDocumentId = docId,
                updatedAtEpochMillis = System.currentTimeMillis(),
                updatedBy = auth.currentUser?.uid ?: expense.updatedBy,
                syncStateRaw = KBSyncState.PENDING_UPSERT.rawValue,
                lastSyncError = null,
            ),
        )
        return docId
    }

    suspend fun flushPending(familyId: String) {
        expenseDao.getBySyncState(familyId, KBSyncState.PENDING_UPSERT.rawValue)
            .forEach { local ->
                runCatching { remoteStore.upsertExpense(local) }
                    .onSuccess {
                        expenseDao.upsert(
                            local.copy(
                                syncStateRaw = KBSyncState.SYNCED.rawValue,
                                lastSyncError = null,
                                updatedAtEpochMillis = System.currentTimeMillis(),
                            ),
                        )
                    }
                    .onFailure { err ->
                        expenseDao.upsert(local.copy(lastSyncError = err.localizedMessage))
                    }
            }

        expenseDao.getBySyncState(familyId, KBSyncState.PENDING_DELETE.rawValue)
            .forEach { local ->
                runCatching { remoteStore.softDeleteExpense(familyId, local.id) }
                    .onSuccess { expenseDao.deleteById(local.id) }
                    .onFailure { err ->
                        expenseDao.upsert(local.copy(lastSyncError = err.localizedMessage))
                    }
            }
    }

    suspend fun prepareAttachmentPreviewFile(
        familyId: String,
        documentId: String,
    ): Pair<KBDocumentEntity, File>? {
        val doc = documentRepository.getDocumentById(documentId) ?: return null
        if (doc.familyId != familyId || doc.isDeleted) return null
        val file = documentRepository.preparePreviewFile(doc)
        return doc to file
    }

    private suspend fun applyInbound(
        familyId: String,
        changes: List<ExpenseRemoteChange>,
    ) {
        val now = System.currentTimeMillis()
        changes.forEach { change ->
            when (change) {
                is ExpenseRemoteChange.Remove -> expenseDao.deleteById(change.id)
                is ExpenseRemoteChange.Upsert -> {
                    val dto = change.dto
                    if (dto.isDeleted) {
                        expenseDao.deleteById(dto.id)
                        return@forEach
                    }
                    val local = expenseDao.getById(dto.id)
                    if (
                        local != null &&
                        local.isDeleted &&
                        KBSyncState.fromRaw(local.syncStateRaw) == KBSyncState.PENDING_DELETE
                    ) {
                        return@forEach
                    }
                    if (local != null && (dto.updatedAtEpochMillis ?: 0L) < local.updatedAtEpochMillis) {
                        return@forEach
                    }
                    val safeAttachedDocumentId = sanitizeAttachedDocumentId(
                        familyId = familyId,
                        attachedDocumentId = dto.attachedDocumentId,
                    )
                    expenseDao.upsert(
                        KBExpenseEntity(
                            id = dto.id,
                            familyId = familyId,
                            title = dto.title,
                            amount = dto.amount,
                            dateEpochMillis = dto.dateEpochMillis,
                            categoryId = dto.categoryId,
                            notes = dto.notes,
                            attachedDocumentId = safeAttachedDocumentId,
                            receiptThumbnailData = local?.receiptThumbnailData,
                            createdByUid = local?.createdByUid ?: dto.createdByUid,
                            updatedBy = dto.updatedBy ?: local?.updatedBy,
                            createdAtEpochMillis = local?.createdAtEpochMillis ?: dto.createdAtEpochMillis ?: now,
                            updatedAtEpochMillis = dto.updatedAtEpochMillis ?: now,
                            isDeleted = false,
                            syncStateRaw = KBSyncState.SYNCED.rawValue,
                            lastSyncError = null,
                        ),
                    )
                }
            }
        }
    }

    private fun stopRealtimeLocked() {
        expensesListener?.remove()
        expensesListener = null
        listeningFamilyId = null
    }

    private suspend fun sanitizeAttachedDocumentId(
        familyId: String,
        attachedDocumentId: String?,
    ): String? {
        val id = attachedDocumentId?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val doc = documentRepository.getDocumentById(id) ?: return null
        return id.takeIf { doc.familyId == familyId && !doc.isDeleted }
    }

    companion object {
        private const val TAG_EXP_REPO = "KB_Exp_Repo"

        data class DefaultExpenseCategory(
            val slug: String,
            val name: String,
            val icon: String,
            val colorHex: String,
        )

        val defaultCategories: List<DefaultExpenseCategory> = listOf(
            DefaultExpenseCategory("spesa", "Spesa", "cart.fill", "#4CAF50"),
            DefaultExpenseCategory("casa", "Casa", "house.fill", "#2196F3"),
            DefaultExpenseCategory("trasporti", "Trasporti", "car.fill", "#FF9800"),
            DefaultExpenseCategory("salute", "Salute", "heart.fill", "#E91E63"),
            DefaultExpenseCategory("istruzione", "Istruzione", "book.fill", "#9C27B0"),
            DefaultExpenseCategory("sport", "Sport", "figure.run", "#00BCD4"),
            DefaultExpenseCategory("abbigliamento", "Abbigliamento", "tshirt.fill", "#FF5722"),
            DefaultExpenseCategory("ristoranti", "Ristoranti", "fork.knife", "#795548"),
            DefaultExpenseCategory("intrattenimento", "Intrattenimento", "gamecontroller.fill", "#607D8B"),
            DefaultExpenseCategory("viaggi", "Viaggi", "airplane", "#03A9F4"),
            DefaultExpenseCategory("elettronica", "Elettronica", "desktopcomputer", "#3F51B5"),
            DefaultExpenseCategory("animali", "Animali", "pawprint.fill", "#8BC34A"),
            DefaultExpenseCategory("altro", "Altro", "ellipsis.circle.fill", "#9E9E9E"),
        )

        fun defaultCategoryId(
            familyId: String,
            slug: String,
        ): String = "expcat-$familyId-$slug"
    }
}
