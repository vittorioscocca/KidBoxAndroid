package it.vittorioscocca.kidbox.ui.screens.ai.planning

import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.domain.model.KBTodoItem
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import java.util.Calendar
import java.util.UUID
import java.util.regex.Pattern
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R
import androidx.compose.ui.platform.LocalContext

enum class PlanningActionKind {
    CREATE_EVENT,
    CREATE_TODO,
    CREATE_GROCERY,
    CREATE_NOTE,
    SET_REMINDER,
    NAVIGATE,
}

enum class PlanningNavigationTarget {
    CALENDAR,
    TODO,
    HEALTH,
    NONE,
}

sealed class PlanningReminderContext {
    data class FreeText(
        val familyId: String,
        val title: String,
        val dueAtEpochMillis: Long,
        val childId: String? = null,
        val listId: String? = null,
    ) : PlanningReminderContext()

    data class ExistingTodo(val title: String) : PlanningReminderContext()
}

data class PlanningAction(
    val id: String = UUID.randomUUID().toString(),
    val kind: PlanningActionKind,
    val title: String,
    val subtitle: String,
    val navigationTarget: PlanningNavigationTarget = PlanningNavigationTarget.NONE,
    val reminderContext: PlanningReminderContext = PlanningReminderContext.FreeText(
        familyId = "",
        title = "",
        dueAtEpochMillis = System.currentTimeMillis(),
    ),
    val prefilledEventTitle: String? = null,
    val prefilledTodoTitle: String? = null,
    val groceryItems: List<String> = emptyList(),
    val noteBody: String? = null,
)

object PlanningActionParser {

    /** Card solo per proposte, non dopo azioni già completate dall'assistente. */
    fun shouldOfferCreatableActionCard(lower: String): Boolean {
        val completion = listOf(
            "ho aggiunto", "ho creato", "ho inserito", "ho salvato", "ho registrato",
            "è stato aggiunto", "sono stati aggiunti", "li ho aggiunti", "l'ho aggiunto",
            "aggiunto alla lista", "inserito nella lista", "già aggiunto", "già inserito",
            "ho impostato", "promemoria attivo",
        )
        if (completion.any { lower.contains(it) }) return false
        val proposal = listOf(
            "vuoi che", "posso ", "vuoi ", "desideri che", "preferisci che",
            "ti va se", "posso aggiungere", "posso creare", "posso inserire",
            "vuoi che aggiunga", "vuoi che crei", "vuoi che imposti",
            "crea un to-do", "creare un", "aggiungere al calendario",
        )
        return proposal.any { lower.contains(it) }
    }

    fun parse(
        context: android.content.Context,
        text: String,
        openTodos: List<KBTodoItem>,
        visits: List<KBMedicalVisit>,
        treatments: List<KBTreatment>,
        familyId: String,
    ): List<PlanningAction> {
        val lower = text.lowercase()
        val actions = mutableListOf<PlanningAction>()
        val offerCreatableCards = shouldOfferCreatableActionCard(lower)

        if (offerCreatableCards &&
            (lower.contains("vuoi che crei l'evento") || lower.contains("posso aggiungere al calendario") ||
                lower.contains("aggiungere al calendario"))
        ) {
            val title = extractQuoted(text) ?: context.getString(R.string.ai_new_event)
            actions += PlanningAction(
                kind = PlanningActionKind.CREATE_EVENT,
                title = title,
                subtitle = context.getString(R.string.ai_create_calendar_event),
                prefilledEventTitle = title,
            )
        }

        val grocerySignals = listOf(
            "lista della spesa", "lista spesa", "alla spesa", "nella spesa",
            "aggiungo alla lista", "aggiunto alla lista",
        )
        if (offerCreatableCards &&
            grocerySignals.any { lower.contains(it) } &&
            (lower.contains("vuoi che") || lower.contains("posso "))
        ) {
            val items = extractGroceryItems(text)
            val title = items.firstOrNull() ?: extractQuoted(text) ?: context.getString(R.string.ai_grocery_items)
            actions += PlanningAction(
                kind = PlanningActionKind.CREATE_GROCERY,
                title = title,
                subtitle = if (items.size > 1) "${items.size} articoli" else context.getString(R.string.ai_add_to_grocery),
                groceryItems = items.ifEmpty { listOf(title) },
            )
        }

        if (offerCreatableCards &&
            (lower.contains("vuoi che") || lower.contains("posso ")) &&
            lower.contains("nota")
        ) {
            val title = extractQuoted(text) ?: context.getString(R.string.ai_new_note)
            actions += PlanningAction(
                kind = PlanningActionKind.CREATE_NOTE,
                title = title,
                subtitle = context.getString(R.string.ai_save_family_notes),
                noteBody = title,
            )
        }

        if (offerCreatableCards &&
            (lower.contains("vuoi che aggiunga il to-do") || lower.contains("crea un to-do") ||
                lower.contains("posso aggiungere il to-do"))
        ) {
            val title = extractQuoted(text) ?: context.getString(R.string.ai_new_todo)
            actions += PlanningAction(
                kind = PlanningActionKind.CREATE_TODO,
                title = title,
                subtitle = context.getString(R.string.ai_added_shared_list),
                prefilledTodoTitle = title,
            )
        }

        val reminderPhrases = listOf(
            "vuoi che imposti un promemoria",
            "posso impostare un promemoria",
            "ti ricordo con una notifica",
        )
        if (reminderPhrases.any { lower.contains(it) }) {
            val quoted = extractQuoted(text)
            val due = extractDate(lower) ?: (System.currentTimeMillis() + 86_400_000L)
            actions += PlanningAction(
                kind = PlanningActionKind.SET_REMINDER,
                title = quoted ?: context.getString(R.string.ai_reminder),
                subtitle = context.getString(R.string.ai_local_notification),
                reminderContext = PlanningReminderContext.FreeText(
                    familyId = familyId,
                    title = quoted ?: context.getString(R.string.ai_reminder),
                    dueAtEpochMillis = due,
                ),
            )
        }

        if (lower.contains("apri il calendario") || lower.contains("vai al calendario")) {
            actions += PlanningAction(
                kind = PlanningActionKind.NAVIGATE,
                title = context.getString(R.string.ai_open_calendar),
                subtitle = context.getString(R.string.ai_go_calendar),
                navigationTarget = PlanningNavigationTarget.CALENDAR,
            )
        }
        if (lower.contains("apri i to-do") || lower.contains("vai ai to-do")) {
            actions += PlanningAction(
                kind = PlanningActionKind.NAVIGATE,
                title = context.getString(R.string.ai_open_todo),
                subtitle = context.getString(R.string.ai_go_todo),
                navigationTarget = PlanningNavigationTarget.TODO,
            )
        }
        if (lower.contains("apri salute") || lower.contains("vai alla sezione salute")) {
            actions += PlanningAction(
                kind = PlanningActionKind.NAVIGATE,
                title = context.getString(R.string.ai_open_health),
                subtitle = context.getString(R.string.ai_go_health),
                navigationTarget = PlanningNavigationTarget.HEALTH,
            )
        }

        return actions
    }

    private fun extractQuoted(text: String): String? {
        val patterns = listOf(
            Pattern.compile("\"([^\"]+)\""),
            Pattern.compile("«([^»]+)»"),
            Pattern.compile("'([^']+)'"),
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) return matcher.group(1)?.trim()
        }
        return null
    }

    private fun extractGroceryItems(text: String): List<String> {
        val quoted = Pattern.compile("[\"«']([^\"»']+)[\"»']").matcher(text)
        val items = mutableListOf<String>()
        while (quoted.find()) {
            quoted.group(1)?.trim()?.takeIf { it.isNotEmpty() }?.let { items += it }
        }
        if (items.isNotEmpty()) return items
        text.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("- ") || trimmed.startsWith("• ")) {
                items += trimmed.removePrefix("- ").removePrefix("• ").trim()
            }
        }
        return items
    }

    private fun extractDate(lower: String): Long? {
        val cal = Calendar.getInstance()
        val base = when {
            lower.contains("dopodomani") -> cal.apply { add(Calendar.DAY_OF_YEAR, 2) }.timeInMillis
            lower.contains("domani") -> cal.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
            else -> cal.apply { add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        }
        return base
    }
}
