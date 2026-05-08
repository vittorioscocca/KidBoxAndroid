package it.vittorioscocca.kidbox.ui.screens.ai.planning

import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import it.vittorioscocca.kidbox.domain.model.*
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

typealias KBWalletTicket = KBWalletTicketEntity

data class PlanningContextInput(
    val familyName: String,
    val memberNames: List<String>,
    val horizonDays: Int = 14,
    val calendarEvents: List<KBCalendarEvent>,
    val openTodos: List<KBTodoItem>,
    val activeRoutines: List<KBRoutine>,
    val todayChecks: List<KBRoutineCheck>,
    val childNames: List<String>,
    val activeTreatments: List<KBTreatment>,
    val visitsWithNextDate: List<KBMedicalVisit>,
    val visitsWithPendingExams: List<KBMedicalVisit>,
    val upcomingVaccines: List<KBVaccine>,
    val recentNotes: List<KBNote>,
    val recentExpenses: List<KBExpense>,
    val expenseCategoryNames: List<String>,
    val pendingGroceryItems: List<KBGroceryItem>,
    val recentChatMessages: List<KBChatMessage>,
    val recentDocuments: List<KBDocument>,
    val recentWalletTickets: List<KBWalletTicket>,
    val children: List<KBChild>,
    val pediatricProfiles: List<KBPediatricProfile>,
    val allVisits: List<KBMedicalVisit>,
    val allExams: List<KBMedicalExam>,
    val allVaccines: List<KBVaccine>,
)

object PlanningContextBuilder {
    private val locale = Locale("it", "IT")
    fun build(input: PlanningContextInput): String {
        val now = System.currentTimeMillis()
        val horizonEnd = now + input.horizonDays * 24L * 60 * 60 * 1000
        return buildString {
            appendLine("Sei un assistente di pianificazione familiare integrato nell'app KidBox per la famiglia ${input.familyName}.")
            appendLine("Membri: ${input.memberNames.joinToString(", ")}.")
            appendLine("Oggi è ${formatDate(now)}.")
            appendLine("Orizzonte temporale: da oggi a ${formatDate(horizonEnd)}.")
            appendLine()
            appendLine("Regole:")
            appendLine("- Aiuta i genitori a pianificare, trovare slot liberi e gestire le attività familiari")
            appendLine("- Non dare consigli medici vincolanti o diagnosi")
            appendLine("- Parla sempre in italiano, tono caldo e pratico")
            appendLine("- Quando proponi di creare un evento o reminder, usa ESATTAMENTE questa forma:")
            appendLine("  'Vuoi che imposti un promemoria per \"[titolo]\"?'")
            appendLine("  o 'Posso aggiungere al calendario \"[titolo evento]\"?'")
            appendLine("  o 'Posso aggiungere il to-do \"[titolo]\"?'")
            appendLine("- Proponi UNA sola azione alla volta")
            appendLine()
            appendLine("## Oggi ${formatDateShort(now)}")
            appendLine("Slot ore: ${input.calendarEvents.filter { sameDay(it.startDateEpochMillis, now) }.joinToString(", ") { "${formatTime(it.startDateEpochMillis)}-${formatTime(it.endDateEpochMillis)}" }}")
            appendLine("Todo urgenti: ${input.openTodos.filter { (it.priorityRaw ?: 0) >= 2 || (it.dueAtEpochMillis ?: Long.MAX_VALUE) < now }.joinToString(", ") { it.title }.ifBlank { "Nessuno" }}")
            appendLine("Routine da completare: ${input.activeRoutines.filter { r -> input.todayChecks.none { it.routineId == r.id } }.joinToString(", ") { it.title }.ifBlank { "Nessuna" }}")
            appendLine("Dosi farmaci oggi: ${input.activeTreatments.sumOf { it.dailyFrequency }}")
            appendLine()
            input.children.forEach { child ->
                val profile = input.pediatricProfiles.firstOrNull { it.childId == child.id }
                appendLine(
                    PediatricAdvancedContextBuilder.build(
                        PediatricAdvancedInput(
                            familyId = child.familyId ?: "",
                            child = child,
                            profile = profile,
                            subjectId = child.id,
                            allVisits = input.allVisits,
                            allExams = input.allExams,
                            allTreatments = input.activeTreatments,
                            allVaccines = input.allVaccines,
                        ),
                    ),
                )
            }
        }.trim()
    }

    fun formatDate(date: Long): String = SimpleDateFormat("EEEE d MMMM yyyy", locale).format(Date(date)).lowercase(locale)
    fun formatDateShort(date: Long): String = SimpleDateFormat("EEE d MMM", locale).format(Date(date)).lowercase(locale)
    fun formatDateTime(date: Long): String = SimpleDateFormat("d MMM HH:mm", locale).format(Date(date)).lowercase(locale)
    fun formatTime(date: Long): String = SimpleDateFormat("HH:mm", locale).format(Date(date))
    fun currentISOWeek(): String {
        val c = Calendar.getInstance(locale)
        return "%04d-W%02d".format(locale, c.weekYear, c.get(Calendar.WEEK_OF_YEAR))
    }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) && ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
