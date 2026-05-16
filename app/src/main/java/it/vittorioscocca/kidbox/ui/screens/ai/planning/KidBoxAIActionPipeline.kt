package it.vittorioscocca.kidbox.ui.screens.ai.planning

import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

data class KidBoxAIActionOutcome(
    val displayText: String,
    val executionSummary: String?,
    val didAutoExecute: Boolean,
)

@Singleton
class KidBoxAIActionPipeline @Inject constructor(
    private val executor: PlanningActionExecutor,
    private val groceryItemDao: KBGroceryItemDao,
) {
    suspend fun processReply(
        reply: String,
        familyId: String,
        defaultChildId: String? = null,
    ): KidBoxAIActionOutcome {
        val processed = PlanningAIActionBlock.process(reply)
        if (processed.actions.isEmpty() || familyId.isBlank()) {
            return KidBoxAIActionOutcome(
                displayText = processed.displayText,
                executionSummary = null,
                didAutoExecute = false,
            )
        }
        val pending = groceryItemDao.observeByFamilyId(familyId).first()
            .filter { !it.isPurchased && !it.isDeleted }
            .map { it.name.lowercase() }
            .toSet()
        val actions = processed.actions.map { action ->
            if (action.childId.isNullOrBlank() && !defaultChildId.isNullOrBlank()) {
                action.copy(childId = defaultChildId)
            } else {
                action
            }
        }
        val summary = executor.execute(
            familyId = familyId,
            actions = actions,
            pendingGroceryNames = pending,
        )
        return KidBoxAIActionOutcome(
            displayText = processed.displayText,
            executionSummary = summary,
            didAutoExecute = true,
        )
    }

    suspend fun executeActions(
        familyId: String,
        actions: List<PlanningExecutableActionDto>,
        defaultChildId: String? = null,
    ): String? {
        if (actions.isEmpty() || familyId.isBlank()) return null
        val pending = groceryItemDao.observeByFamilyId(familyId).first()
            .filter { !it.isPurchased && !it.isDeleted }
            .map { it.name.lowercase() }
            .toSet()
        val enriched = actions.map { action ->
            if (action.childId.isNullOrBlank() && !defaultChildId.isNullOrBlank()) {
                action.copy(childId = defaultChildId)
            } else {
                action
            }
        }
        return executor.execute(
            familyId = familyId,
            actions = enriched,
            pendingGroceryNames = pending,
        )
    }
}
