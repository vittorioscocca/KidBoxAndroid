package it.vittorioscocca.kidbox.ui.screens.ai.planning

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException

/**
 * Messaggio dati per il briefing giornaliero (prossime 48h) — parity iOS DailyBriefingService.
 */
@Singleton
class DailyBriefingDataMessageBuilder @Inject constructor(
    private val childDao: KBChildDao,
    private val calendarEventDao: KBCalendarEventDao,
    private val todoItemDao: KBTodoItemDao,
    private val treatmentRepository: TreatmentRepository,
    private val medicalVisitRepository: MedicalVisitRepository,
    private val auth: FirebaseAuth,
) {
    private val localeIt = Locale("it", "IT")
    private val dayFmt = SimpleDateFormat("EEEE d MMMM", localeIt)
    private val timeFmt = SimpleDateFormat("HH:mm", localeIt)

    suspend fun buildDailyDataMessage(familyId: String): String {
        if (familyId.isBlank()) {
            return "Genera il briefing del mattino basandoti su questi dati:\n\nNessun dato.\n\nGenera ora il briefing seguendo le regole del sistema."
        }

        val now = System.currentTimeMillis()
        val horizon = now + TimeUnit.HOURS.toMillis(48)
        val cal = Calendar.getInstance(localeIt)

        val children = childDao.getChildrenByFamilyId(familyId)
        val childNames = children.associate { it.id to it.name }

        val uid = auth.currentUser?.uid
        val events = calendarEventDao.observeByFamilyId(familyId).first()
            .filterNot { it.isDeleted }
            .filter { row ->
                KBVisibilityScope.isVisible(
                    KBVisibilityScope.normalized(row.visibilityScope),
                    decodeStringList(row.visibilityMemberIdsJson),
                    row.createdBy.takeIf { it.isNotBlank() },
                    uid,
                )
            }
            .filter { it.startDateEpochMillis in now..horizon }
            .sortedBy { it.startDateEpochMillis }

        val allTodos = children.flatMap { child ->
            todoItemDao.getByFamilyAndChild(familyId, child.id)
        }.filterNot { it.isDeleted }

        val dueTodos = allTodos.filter { todo ->
            !todo.isDone && (
                (todo.priorityRaw ?: 0) >= 1 ||
                    ((todo.dueAtEpochMillis ?: Long.MAX_VALUE) in now..horizon)
                )
        }

        val treats = children.flatMap { child ->
            treatmentRepository.listByFamilyAndChild(familyId, child.id)
        }.filter { it.isActive && !it.isDeleted }

        val allVisits = children.flatMap { child ->
            medicalVisitRepository.listRecentVisitsForChild(familyId, child.id, limit = 200)
        }.filterNot { it.isDeleted }

        val lines = mutableListOf<String>()
        lines += "Genera il briefing del mattino basandoti su questi dati:"
        lines += ""
        lines += "Oggi: ${dayFmt.format(now)}"
        lines += ""

        if (events.isNotEmpty()) {
            lines += "EVENTI (oggi e domani):"
            events.take(12).forEach { ev ->
                val dayLabel = when {
                    isSameDay(ev.startDateEpochMillis, now) -> "oggi"
                    isTomorrow(ev.startDateEpochMillis, now) -> "domani"
                    else -> dayFmt.format(ev.startDateEpochMillis)
                }
                lines += "  • $dayLabel ${timeFmt.format(ev.startDateEpochMillis)} — ${ev.title}"
            }
        }

        if (treats.isNotEmpty()) {
            lines += ""
            lines += "DOSI MEDICINE OGGI:"
            treats.take(8).forEach { t ->
                val child = childNames[t.childId] ?: t.childId
                val times = t.scheduleTimesList()
                if (times.isEmpty()) {
                    lines += "  • ${t.drugName} per $child"
                } else {
                    times.forEach { slot ->
                        lines += "  • $slot: ${t.drugName} ($child)"
                    }
                }
            }
        }

        if (dueTodos.isNotEmpty()) {
            lines += ""
            lines += "TO-DO (oggi / urgenti):"
            dueTodos.take(8).forEach { todo ->
                var line = "  • ${todo.title}"
                todo.dueAtEpochMillis?.let { due ->
                    line += " (entro ${dayFmt.format(due)})"
                }
                lines += line
            }
        }

        val critical = mutableListOf<String>()
        allVisits.forEach { visit ->
            val next = visit.nextVisitDateEpochMillis
            if (next != null && next in now..horizon) {
                val child = childNames[visit.childId] ?: visit.childId
                critical += "  • Visita $child: ${dayFmt.format(next)}"
            }
            parsePrescribedExams(visit.prescribedExamsJson).forEach { (name, deadline) ->
                if (deadline in now..horizon) {
                    val child = childNames[visit.childId] ?: visit.childId
                    critical += "  • Esame $name ($child): entro ${dayFmt.format(deadline)}"
                }
            }
        }
        if (critical.isNotEmpty()) {
            lines += ""
            lines += "SCADENZE CRITICHE (48h):"
            lines += critical.take(6)
        }

        lines += ""
        lines += "Genera ora il briefing seguendo le regole del sistema."
        return lines.joinToString("\n")
    }

    private fun isSameDay(a: Long, b: Long): Boolean {
        val ca = Calendar.getInstance().apply { timeInMillis = a }
        val cb = Calendar.getInstance().apply { timeInMillis = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    private fun isTomorrow(eventMillis: Long, nowMillis: Long): Boolean {
        val tomorrow = Calendar.getInstance().apply {
            timeInMillis = nowMillis
            add(Calendar.DAY_OF_YEAR, 1)
        }
        val ev = Calendar.getInstance().apply { timeInMillis = eventMillis }
        return ev.get(Calendar.YEAR) == tomorrow.get(Calendar.YEAR) &&
            ev.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR)
    }

    private fun parsePrescribedExams(json: String?): List<Pair<String, Long>> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = mutableListOf<Pair<String, Long>>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                val name = obj.optString("name")
                if (name.isBlank()) continue
                val deadline = obj.optLong("deadline", -1L)
                if (deadline > 0) out.add(name to deadline)
            }
            out
        } catch (_: JSONException) {
            emptyList()
        }
    }
}
