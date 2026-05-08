package it.vittorioscocca.kidbox.ui.screens.ai.planning

import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.domain.model.KBTodoItem
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import java.util.Calendar
import java.util.Locale
import java.util.UUID

enum class PlanningActionKind { CREATE_EVENT, CREATE_TODO, SET_REMINDER, NAVIGATE }
enum class PlanningNavigationTarget { CALENDAR, TODO, HEALTH, NONE }

sealed class PlanningReminderContext {
    data class Todo(val todo: KBTodoItem, val dueAt: Long) : PlanningReminderContext()
    data class Visit(val visit: KBMedicalVisit, val childName: String) : PlanningReminderContext()
    data class Exam(
        val examName: String,
        val examId: String,
        val childName: String,
        val childId: String,
        val familyId: String,
        val deadline: Long,
    ) : PlanningReminderContext()
    data class Treatment(val treatment: KBTreatment, val childName: String) : PlanningReminderContext()
    data class FreeText(val title: String, val dueAt: Long, val familyId: String) : PlanningReminderContext()
    object None : PlanningReminderContext()
}

data class PlanningAction(
    val id: String = UUID.randomUUID().toString(),
    val kind: PlanningActionKind,
    val title: String,
    val subtitle: String,
    val reminderContext: PlanningReminderContext = PlanningReminderContext.None,
    val navigationTarget: PlanningNavigationTarget = PlanningNavigationTarget.NONE,
    val prefilledEventTitle: String? = null,
    val prefilledTodoTitle: String? = null,
)

object PlanningActionParser {
    fun parse(
        text: String,
        openTodos: List<KBTodoItem>,
        visits: List<KBMedicalVisit>,
        treatments: List<KBTreatment>,
        familyId: String,
    ): List<PlanningAction> {
        val lower = text.lowercase(Locale.ROOT)
        val actions = mutableListOf<PlanningAction>()
        extractCreateEvent(lower, text)?.let(actions::add)
        extractCreateTodo(lower, text)?.let(actions::add)
        extractReminder(lower, text, openTodos, visits, treatments, familyId)?.let(actions::add)
        extractNavigate(lower)?.let(actions::add)
        return actions.take(2)
    }

    private fun extractCreateEvent(lower: String, text: String): PlanningAction? {
        if (listOf("aggiungo al calendario", "creo l'evento", "inserisco nel calendario", "posso aggiungere al calendario").none { lower.contains(it) }) return null
        val title = extractQuoted(text) ?: "Nuovo evento"
        return PlanningAction( kind = PlanningActionKind.CREATE_EVENT, title = "Crea evento", subtitle = title, prefilledEventTitle = title )
    }
    private fun extractCreateTodo(lower: String, text: String): PlanningAction? {
        if (listOf("aggiungo il to-do", "creo il to-do", "aggiungo al to-do", "posso aggiungere il to-do").none { lower.contains(it) }) return null
        val title = extractQuoted(text) ?: "Nuovo to-do"
        return PlanningAction(kind = PlanningActionKind.CREATE_TODO, title = "Aggiungi to-do", subtitle = title, prefilledTodoTitle = title)
    }
    private fun extractReminder(lower: String, text: String, todos: List<KBTodoItem>, visits: List<KBMedicalVisit>, treatments: List<KBTreatment>, familyId: String): PlanningAction? {
        if (listOf("vuoi che imposti un promemoria", "imposto un promemoria", "ti ricordo", "vuoi un promemoria").none { lower.contains(it) }) return null
        val subject = extractQuoted(text) ?: "Promemoria"
        val todo = todos.firstOrNull { it.title.contains(subject, true) }
        if (todo != null) return PlanningAction(kind = PlanningActionKind.SET_REMINDER, title = "Imposta promemoria", subtitle = subject, reminderContext = PlanningReminderContext.Todo(todo, todo.dueAtEpochMillis ?: tomorrow8()))
        val visit = visits.firstOrNull { it.reason.contains(subject, true) }
        if (visit != null) return PlanningAction(kind = PlanningActionKind.SET_REMINDER, title = "Imposta promemoria", subtitle = subject, reminderContext = PlanningReminderContext.Visit(visit, "Figlio"))
        val treatment = treatments.firstOrNull { it.drugName.contains(subject, true) }
        if (treatment != null) return PlanningAction(kind = PlanningActionKind.SET_REMINDER, title = "Imposta promemoria", subtitle = subject, reminderContext = PlanningReminderContext.Treatment(treatment, "Figlio"))
        return PlanningAction(kind = PlanningActionKind.SET_REMINDER, title = "Imposta promemoria", subtitle = subject, reminderContext = PlanningReminderContext.FreeText(subject, parseDate(lower), familyId))
    }
    private fun extractNavigate(lower: String): PlanningAction? = when {
        lower.contains("apri il calendario") || lower.contains("vai al calendario") -> PlanningAction(kind = PlanningActionKind.NAVIGATE, title = "Apri calendario", subtitle = "", navigationTarget = PlanningNavigationTarget.CALENDAR)
        lower.contains("apri i to-do") || lower.contains("vai ai to-do") -> PlanningAction(kind = PlanningActionKind.NAVIGATE, title = "Apri to-do", subtitle = "", navigationTarget = PlanningNavigationTarget.TODO)
        lower.contains("apri la sezione salute") || lower.contains("vai alla salute") -> PlanningAction(kind = PlanningActionKind.NAVIGATE, title = "Apri salute", subtitle = "", navigationTarget = PlanningNavigationTarget.HEALTH)
        else -> null
    }

    private fun parseDate(lower: String): Long {
        if (lower.contains("dopodomani")) return offsetDay(2)
        if (lower.contains("domani")) return offsetDay(1)
        Regex("tra\\s+(\\d+)\\s+giorni").find(lower)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let { return offsetDay(it) }
        Regex("ore\\s*(\\d{1,2}):(\\d{2})").find(lower)?.let { m ->
            val h = m.groupValues[1].toIntOrNull() ?: 8
            val mm = m.groupValues[2].toIntOrNull() ?: 0
            val c = Calendar.getInstance()
            c.set(Calendar.HOUR_OF_DAY, h); c.set(Calendar.MINUTE, mm); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return c.timeInMillis
        }
        return tomorrow8()
    }
    private fun extractQuoted(text: String): String? = Regex("\"(.*?)\"|'(.*?)'").find(text)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
    private fun tomorrow8(): Long = offsetDay(1)
    private fun offsetDay(days: Int): Long {
        val c = Calendar.getInstance()
        c.add(Calendar.DAY_OF_YEAR, days)
        c.set(Calendar.HOUR_OF_DAY, 8); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
        return c.timeInMillis
    }
}
