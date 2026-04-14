package it.vittorioscocca.kidbox.ui.screens.expenses

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseCategoryEntity
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseEntity
import it.vittorioscocca.kidbox.data.notification.CounterField
import it.vittorioscocca.kidbox.data.notification.CountersService
import it.vittorioscocca.kidbox.data.notification.HomeBadgeManager
import it.vittorioscocca.kidbox.data.repository.ExpenseRepository
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.io.File
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ExpensePeriod(
    val label: String,
    val months: Long?,
) {
    ONE_MONTH("1 mese", 1),
    THREE_MONTHS("3 mesi", 3),
    SIX_MONTHS("6 mesi", 6),
    ONE_YEAR("1 anno", 12),
    CUSTOM("Personalizzato", null),
}

data class MonthlyExpenseBar(
    val id: String,
    val label: String,
    val total: Double,
)

data class ExpenseCategorySlice(
    val id: String,
    val name: String,
    val colorHex: String,
    val total: Double,
    val percentage: Double,
)

data class PendingExpenseAttachment(
    val fileName: String,
    val mimeType: String,
    val bytes: ByteArray,
)

data class ExpensesUiState(
    val familyId: String = "",
    val period: ExpensePeriod = ExpensePeriod.SIX_MONTHS,
    val customStartEpochMillis: Long = System.currentTimeMillis(),
    val customEndEpochMillis: Long = System.currentTimeMillis(),
    val expenses: List<KBExpenseEntity> = emptyList(),
    val visibleExpenses: List<KBExpenseEntity> = emptyList(),
    val categories: List<KBExpenseCategoryEntity> = emptyList(),
    val documents: List<KBDocumentEntity> = emptyList(),
    val documentFolders: List<KBDocumentCategoryEntity> = emptyList(),
    val monthlyBars: List<MonthlyExpenseBar> = emptyList(),
    val categorySlices: List<ExpenseCategorySlice> = emptyList(),
    val totalAmount: Double = 0.0,
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
)

@HiltViewModel
class ExpensesViewModel @Inject constructor(
    private val repository: ExpenseRepository,
    private val countersService: CountersService,
    private val homeBadgeManager: HomeBadgeManager,
) : ViewModel() {
    private val _uiState = MutableStateFlow(ExpensesUiState())
    val uiState: StateFlow<ExpensesUiState> = _uiState.asStateFlow()

    private var observeJob: Job? = null

    fun bindFamily(familyId: String) {
        if (familyId.isBlank() || _uiState.value.familyId == familyId) return
        _uiState.value = _uiState.value.copy(
            familyId = familyId,
            customStartEpochMillis = nowDate().minusMonths(6).toEpochMillis(),
            customEndEpochMillis = nowDate().toEpochMillis(),
            isLoading = true,
            errorMessage = null,
        )
        repository.startRealtime(
            familyId = familyId,
            onPermissionDenied = {
                _uiState.value = _uiState.value.copy(errorMessage = "Accesso alle spese negato")
            },
        )
        clearBadge(familyId)
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            repository.observeExpensesData(familyId).collectLatest { data ->
                val availableDocumentIds = data.documents.map { it.id }.toHashSet()
                val sanitizedExpenses = data.expenses.map { expense ->
                    val attachedId = expense.attachedDocumentId
                    if (!attachedId.isNullOrBlank() && attachedId !in availableDocumentIds) {
                        expense.copy(attachedDocumentId = null)
                    } else {
                        expense
                    }
                }
                _uiState.value = _uiState.value.copy(
                    expenses = sanitizedExpenses,
                    categories = data.categories,
                    documents = data.documents,
                    documentFolders = data.documentFolders,
                    isLoading = false,
                )
                recomputeCharts()
            }
        }
        viewModelScope.launch {
            runCatching { repository.seedDefaultCategories(familyId) }
            runCatching { repository.flushPending(familyId) }
        }
    }

    fun setPeriod(period: ExpensePeriod) {
        _uiState.value = _uiState.value.copy(period = period)
        recomputeCharts()
    }

    fun setCustomRange(
        startEpochMillis: Long,
        endEpochMillis: Long,
    ) {
        _uiState.value = _uiState.value.copy(
            customStartEpochMillis = startEpochMillis,
            customEndEpochMillis = endEpochMillis,
            period = ExpensePeriod.CUSTOM,
        )
        recomputeCharts()
    }

    fun saveExpense(
        editing: KBExpenseEntity?,
        title: String,
        amount: Double,
        dateEpochMillis: Long,
        categoryId: String?,
        notes: String?,
        existingDocumentId: String?,
        attachment: PendingExpenseAttachment?,
    ) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || title.isBlank()) return
        val effectiveExistingDocumentId = existingDocumentId?.takeIf { attachment == null }
        viewModelScope.launch {
            runCatching {
                val target = if (editing == null) {
                    repository.createExpenseLocal(
                        familyId = familyId,
                        title = title,
                        amount = amount,
                        dateEpochMillis = dateEpochMillis,
                        categoryId = categoryId,
                        notes = notes,
                        attachedDocumentId = effectiveExistingDocumentId,
                    )
                } else {
                    repository.updateExpenseLocal(
                        current = editing,
                        title = title,
                        amount = amount,
                        dateEpochMillis = dateEpochMillis,
                        categoryId = categoryId,
                        notes = notes,
                        attachedDocumentId = effectiveExistingDocumentId,
                    )
                    editing.copy(
                        title = title,
                        amount = amount,
                        dateEpochMillis = dateEpochMillis,
                        categoryId = categoryId,
                        notes = notes,
                        attachedDocumentId = effectiveExistingDocumentId,
                    )
                }

                if (!effectiveExistingDocumentId.isNullOrBlank()) {
                    repository.attachExistingDocumentToExpense(
                        expense = target,
                        documentId = effectiveExistingDocumentId,
                    )
                }

                if (attachment != null) {
                    repository.attachDocumentToExpense(
                        expense = target,
                        fileName = attachment.fileName,
                        mimeType = attachment.mimeType,
                        bytes = attachment.bytes,
                    )
                }

                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore salvataggio spesa")
            }
        }
    }

    fun deleteExpense(expense: KBExpenseEntity) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.deleteExpenseLocal(expense)
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore eliminazione spesa")
            }
        }
    }

    fun detachAttachmentFromExpense(expense: KBExpenseEntity) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank()) return
        viewModelScope.launch {
            runCatching {
                repository.updateExpenseLocal(
                    current = expense,
                    title = expense.title,
                    amount = expense.amount,
                    dateEpochMillis = expense.dateEpochMillis,
                    categoryId = expense.categoryId,
                    notes = expense.notes,
                    attachedDocumentId = null,
                )
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore rimozione allegato")
            }
        }
    }

    fun deleteExpenses(expenses: List<KBExpenseEntity>) {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || expenses.isEmpty()) return
        viewModelScope.launch {
            runCatching {
                expenses.forEach { repository.deleteExpenseLocal(it) }
                repository.flushPending(familyId)
            }.onFailure {
                _uiState.value = _uiState.value.copy(errorMessage = it.localizedMessage ?: "Errore eliminazione spese")
            }
        }
    }

    fun clearError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    suspend fun prepareAttachmentPreviewFile(documentId: String): Pair<KBDocumentEntity, File>? {
        val familyId = _uiState.value.familyId
        if (familyId.isBlank() || documentId.isBlank()) return null
        return withContext(Dispatchers.IO) {
            repository.prepareAttachmentPreviewFile(
                familyId = familyId,
                documentId = documentId,
            )
        }
    }

    override fun onCleared() {
        repository.stopRealtime()
        super.onCleared()
    }

    private fun recomputeCharts() {
        val state = _uiState.value
        val range = effectiveRange(
            period = state.period,
            customStartEpochMillis = state.customStartEpochMillis,
            customEndEpochMillis = state.customEndEpochMillis,
        )
        val visible = state.expenses.filter { it.dateEpochMillis in range.startEpochMillis until range.endExclusiveEpochMillis }
        val total = visible.sumOf { it.amount }
        val bars = buildMonthlyBars(visible, range)
        val slices = buildCategorySlices(
            expenses = visible,
            categories = state.categories,
            total = total,
        )
        _uiState.value = state.copy(
            visibleExpenses = visible,
            totalAmount = total,
            monthlyBars = bars,
            categorySlices = slices,
        )
    }

    private fun clearBadge(familyId: String) {
        homeBadgeManager.clearLocal(CounterField.EXPENSES)
        viewModelScope.launch { runCatching { countersService.reset(familyId, CounterField.EXPENSES) } }
    }
}

private data class EpochRange(
    val startEpochMillis: Long,
    val endExclusiveEpochMillis: Long,
)

private fun effectiveRange(
    period: ExpensePeriod,
    customStartEpochMillis: Long,
    customEndEpochMillis: Long,
): EpochRange {
    val today = nowDate()
    val endExclusive = today.plusDays(1).toEpochMillis()
    val start = when (period) {
        ExpensePeriod.ONE_MONTH -> today.minusMonths(1).toEpochMillis()
        ExpensePeriod.THREE_MONTHS -> today.minusMonths(3).toEpochMillis()
        ExpensePeriod.SIX_MONTHS -> today.minusMonths(6).toEpochMillis()
        ExpensePeriod.ONE_YEAR -> today.minusYears(1).toEpochMillis()
        ExpensePeriod.CUSTOM -> Instant.ofEpochMilli(customStartEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate().toEpochMillis()
    }
    val customEndExclusive = Instant.ofEpochMilli(customEndEpochMillis).atZone(ZoneId.systemDefault()).toLocalDate().plusDays(1).toEpochMillis()
    return if (period == ExpensePeriod.CUSTOM) {
        EpochRange(startEpochMillis = start, endExclusiveEpochMillis = maxOf(start + 86_400_000L, customEndExclusive))
    } else {
        EpochRange(startEpochMillis = start, endExclusiveEpochMillis = endExclusive)
    }
}

private fun buildMonthlyBars(
    expenses: List<KBExpenseEntity>,
    range: EpochRange,
): List<MonthlyExpenseBar> {
    val formatter = DateTimeFormatter.ofPattern("MMM ''yy", Locale.ITALIAN)
    val zone = ZoneId.systemDefault()
    val totalsByMonth = expenses.groupBy {
        val date = Instant.ofEpochMilli(it.dateEpochMillis).atZone(zone).toLocalDate()
        "${date.year}-${date.monthValue.toString().padStart(2, '0')}"
    }.mapValues { (_, list) -> list.sumOf { it.amount } }

    val startMonth = Instant.ofEpochMilli(range.startEpochMillis).atZone(zone).toLocalDate().withDayOfMonth(1)
    val endMonth = Instant.ofEpochMilli(range.endExclusiveEpochMillis - 1).atZone(zone).toLocalDate().withDayOfMonth(1)

    val bars = mutableListOf<MonthlyExpenseBar>()
    var current = startMonth
    while (!current.isAfter(endMonth)) {
        val key = "${current.year}-${current.monthValue.toString().padStart(2, '0')}"
        bars.add(
            MonthlyExpenseBar(
                id = key,
                label = current.format(formatter),
                total = totalsByMonth[key] ?: 0.0,
            ),
        )
        current = current.plusMonths(1)
    }
    return bars
}

private fun buildCategorySlices(
    expenses: List<KBExpenseEntity>,
    categories: List<KBExpenseCategoryEntity>,
    total: Double,
): List<ExpenseCategorySlice> {
    val byCategory = expenses.groupBy { it.categoryId ?: "_none" }.mapValues { (_, list) -> list.sumOf { it.amount } }
    return byCategory.map { (categoryId, amount) ->
        val cat = categories.firstOrNull { it.id == categoryId }
        ExpenseCategorySlice(
            id = categoryId,
            name = cat?.name ?: "Altro",
            colorHex = cat?.colorHex ?: "#9E9E9E",
            total = amount,
            percentage = if (total > 0.0) (amount / total) * 100.0 else 0.0,
        )
    }.sortedByDescending { it.total }
}

private fun nowDate(): LocalDate = LocalDate.now()

private fun LocalDate.toEpochMillis(): Long =
    atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
