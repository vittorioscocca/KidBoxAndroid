package it.vittorioscocca.kidbox.ui.screens.ai.planning

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker.PERMISSION_GRANTED
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.notification.TodoReminderScheduler
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.TodoRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.notifications.ExamReminderScheduler
import it.vittorioscocca.kidbox.notifications.TreatmentNotificationManager
import it.vittorioscocca.kidbox.notifications.VisitReminderScheduler
import javax.inject.Inject
import javax.inject.Singleton

sealed class PlanningReminderResult {
    data class Scheduled(val description: String) : PlanningReminderResult()
    object NotAuthorized : PlanningReminderResult()
    data class Failed(val error: Exception) : PlanningReminderResult()
}

@Singleton
class PlanningReminderService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val todoRepository: TodoRepository,
    private val visitRepository: MedicalVisitRepository,
    private val examRepository: MedicalExamRepository,
    private val treatmentRepository: TreatmentRepository,
    private val todoReminderScheduler: TodoReminderScheduler,
    private val visitReminderScheduler: VisitReminderScheduler,
    private val examReminderScheduler: ExamReminderScheduler,
    private val treatmentNotificationManager: TreatmentNotificationManager,
) {
    suspend fun schedule(reminderContext: PlanningReminderContext): PlanningReminderResult {
        if (!ensureNotificationPermission()) return PlanningReminderResult.NotAuthorized
        return runCatching {
            when (reminderContext) {
                is PlanningReminderContext.Todo -> {
                    todoReminderScheduler.cancel(reminderContext.todo.id)
                    val dueAt = reminderContext.dueAt
                    todoRepository.updateTodo(
                        todoId = reminderContext.todo.id,
                        title = reminderContext.todo.title,
                        notes = reminderContext.todo.notes,
                        dueAtEpochMillis = dueAt,
                        assignedTo = reminderContext.todo.assignedTo,
                        priorityRaw = reminderContext.todo.priorityRaw,
                        reminderEnabled = true,
                    )
                    PlanningReminderResult.Scheduled("Promemoria impostato per '${reminderContext.todo.title}'")
                }
                is PlanningReminderContext.Visit -> {
                    val visit = reminderContext.visit
                    val next = visit.nextVisitDateEpochMillis ?: return PlanningReminderResult.Failed(
                        IllegalStateException("nextVisitDate assente"),
                    )
                    visitReminderScheduler.schedule(
                        reminderKey = "${visit.id}_next_visit",
                        visitDateMillis = next,
                        title = "Visita: ${visit.reason}",
                        visitId = visit.id,
                        familyId = visit.familyId,
                        childId = visit.childId,
                    )
                    visitRepository.save(visit.copy(nextVisitReminderOn = true))
                    PlanningReminderResult.Scheduled("Promemoria visita impostato per ${reminderContext.childName}")
                }
                is PlanningReminderContext.Exam -> {
                    val exam: KBMedicalExam = examRepository.getById(reminderContext.examId)
                        ?: return PlanningReminderResult.Failed(IllegalStateException("Esame non trovato"))
                    examReminderScheduler.scheduleExamReminder(exam.copy(deadlineEpochMillis = reminderContext.deadline), reminderContext.childName)
                    PlanningReminderResult.Scheduled("Promemoria esame '${reminderContext.examName}' impostato")
                }
                is PlanningReminderContext.Treatment -> {
                    val treatment: KBTreatment = treatmentRepository.getById(reminderContext.treatment.id)
                        ?: reminderContext.treatment
                    treatmentNotificationManager.schedule(treatment, reminderContext.childName)
                    treatmentRepository.upsert(treatment.copy(reminderEnabled = true))
                    PlanningReminderResult.Scheduled("Promemoria dosi ${treatment.drugName} attivato per ${reminderContext.childName}")
                }
                is PlanningReminderContext.FreeText -> {
                    todoRepository.addTodo(
                        familyId = reminderContext.familyId,
                        childId = "",
                        listId = "",
                        title = reminderContext.title,
                        notes = null,
                        dueAtEpochMillis = reminderContext.dueAt,
                        assignedTo = null,
                        priorityRaw = 0,
                        reminderEnabled = true,
                    )
                    PlanningReminderResult.Scheduled("Promemoria '${reminderContext.title}' impostato")
                }
                PlanningReminderContext.None -> PlanningReminderResult.Failed(IllegalArgumentException("Nessun contesto"))
            }
        }.getOrElse { PlanningReminderResult.Failed(it as? Exception ?: Exception(it.message)) }
    }

    fun ensureNotificationPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PERMISSION_GRANTED
    }
}
