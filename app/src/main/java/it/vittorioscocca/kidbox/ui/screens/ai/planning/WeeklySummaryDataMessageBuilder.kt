package it.vittorioscocca.kidbox.ui.screens.ai.planning

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.data.home.HomeItemAttachmentTag
import it.vittorioscocca.kidbox.data.home.HousePaymentAttachmentTag
import it.vittorioscocca.kidbox.data.local.dao.HomeItemDao
import it.vittorioscocca.kidbox.data.local.dao.HousePaymentDao
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.PetDao
import it.vittorioscocca.kidbox.data.local.dao.PetEventDao
import it.vittorioscocca.kidbox.data.local.dao.VehicleDao
import it.vittorioscocca.kidbox.data.local.dao.VehicleEventDao
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.data.pets.PetEventAttachmentTag
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.data.vehicles.VehicleAttachmentTag
import it.vittorioscocca.kidbox.data.vehicles.VehicleEventAttachmentTag
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.text.SimpleDateFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONException

/**
 * Costruisce il messaggio dati per la sintesi settimanale (parity iOS [WeeklySummaryService.buildWeeklyDataMessage]):
 * periodo ISO, eventi, to-do urgenti, cure, visite/esami in scadenza, spese, lista spesa, allegati life-area.
 */
@Singleton
class WeeklySummaryDataMessageBuilder @Inject constructor(
    private val childDao: KBChildDao,
    private val calendarEventDao: KBCalendarEventDao,
    private val todoItemDao: KBTodoItemDao,
    private val treatmentRepository: TreatmentRepository,
    private val medicalVisitRepository: MedicalVisitRepository,
    private val expenseDao: KBExpenseDao,
    private val groceryItemDao: KBGroceryItemDao,
    private val documentDao: KBDocumentDao,
    private val healthAttachmentService: HealthAttachmentService,
    private val petDao: PetDao,
    private val petEventDao: PetEventDao,
    private val homeItemDao: HomeItemDao,
    private val housePaymentDao: HousePaymentDao,
    private val vehicleDao: VehicleDao,
    private val vehicleEventDao: VehicleEventDao,
    private val auth: FirebaseAuth,
) {

    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val localeIt = Locale("it", "IT")
    private val dateFmt = SimpleDateFormat("EEEE d MMMM yyyy", localeIt).apply {
        timeZone = java.util.TimeZone.getTimeZone(zone)
    }

    suspend fun buildWeeklyDataMessage(familyId: String, familyName: String): String {
        if (familyId.isBlank()) {
            return "Genera la sintesi settimanale basandoti su questi dati:\n\n" +
                "Nessun dato famiglia disponibile.\n\nGenera ora la sintesi seguendo le regole del sistema."
        }

        val now = System.currentTimeMillis()
        val (weekStart, windowEndExclusive, displayWeekEndDay) = weeklyWindowMillis(now)

        val children = childDao.getChildrenByFamilyId(familyId)
        val childNames = children.associate { it.id to it.name }

        val uid = auth.currentUser?.uid
        val calendarEvents = calendarEventDao.observeByFamilyId(familyId).first()
            .filterNot { it.isDeleted }
            .filter { row ->
                KBVisibilityScope.isVisible(
                    KBVisibilityScope.normalized(row.visibilityScope),
                    decodeStringList(row.visibilityMemberIdsJson),
                    row.createdBy.takeIf { it.isNotBlank() },
                    uid,
                )
            }

        val events = calendarEvents.filter { ev ->
            ev.startDateEpochMillis >= weekStart &&
                ev.startDateEpochMillis < windowEndExclusive
        }.sortedBy { it.startDateEpochMillis }

        val allTodos = children.flatMap { child ->
            todoItemDao.getByFamilyAndChild(familyId, child.id)
        }.filterNot { it.isDeleted }

        val urgent = allTodos.filter { todo ->
            !todo.isDone &&
                (
                    (todo.priorityRaw ?: 0) >= 1 ||
                        ((todo.dueAtEpochMillis ?: Long.MAX_VALUE) < windowEndExclusive)
                    )
        }

        val allTreatments = children.flatMap { child ->
            treatmentRepository.listByFamilyAndChild(familyId, child.id)
        }
        val treats = allTreatments.filter { it.isActive && !it.isDeleted }

        val allVisits = children.flatMap { child ->
            medicalVisitRepository.listRecentVisitsForChild(familyId, child.id, limit = 500)
        }.filterNot { it.isDeleted }

        val nextVisits = allVisits.mapNotNull { v ->
            val d = v.nextVisitDateEpochMillis ?: return@mapNotNull null
            if (d >= windowEndExclusive) return@mapNotNull null
            Triple(childNames[v.childId] ?: v.childId, d, v)
        }.sortedBy { it.second }

        val pendingExams = allVisits.flatMap { v ->
            parsePrescribedExamsWithDeadlines(v.prescribedExamsJson).mapNotNull { pe ->
                val dl = pe.deadlineMillis ?: return@mapNotNull null
                if (dl >= windowEndExclusive) return@mapNotNull null
                Triple(childNames[v.childId] ?: v.childId, pe.name, dl)
            }
        }.sortedBy { it.third }

        val expenses = expenseDao.getAllByFamilyId(familyId)
            .filterNot { it.isDeleted }
            .filter { it.dateEpochMillis in weekStart until windowEndExclusive }

        val groceryPending = groceryItemDao.observeByFamilyId(familyId).first()
            .filterNot { it.isDeleted }
            .count { !it.isPurchased }

        val pets = petDao.getAllByFamily(familyId)
        val petEvents = pets.flatMap { pet ->
            petEventDao.observeByPet(familyId, pet.id).first()
        }.filterNot { it.isDeleted }

        val homeItems = homeItemDao.observeByFamily(familyId).first().filterNot { it.isDeleted }
        val housePayments = housePaymentDao.observeByFamily(familyId).first().filterNot { it.isDeleted }
        val vehicles = vehicleDao.observeByFamily(familyId).first().filterNot { it.isDeleted }
        val vehicleEvents = vehicles.flatMap { v ->
            vehicleEventDao.observeByVehicle(familyId, v.id).first()
        }.filterNot { it.isDeleted }

        val homeItemIds = homeItems.map { it.id }.toSet()
        val housePaymentIds = housePayments.map { it.id }.toSet()
        val vehicleIds = vehicles.map { it.id }.toSet()
        val vehicleEventIds = vehicleEvents.map { it.id }.toSet()
        val petEventIds = petEvents.map { it.id }.toSet()

        healthAttachmentService.ensureLifeAreaAttachmentsForPlanning(
            familyId = familyId,
            homeItemIds = homeItemIds,
            housePaymentIds = housePaymentIds,
            vehicleIds = vehicleIds,
            vehicleEventIds = vehicleEventIds,
            petEventIds = petEventIds,
        )

        val lifeDocs = documentDao.getAllByFamilyId(familyId)
            .asSequence()
            .filterNot { it.isDeleted }
            .filter {
                it.extractionStatusRaw == KBTextExtractionStatus.COMPLETED.rawValue &&
                    !it.extractedText.isNullOrBlank() &&
                    lifeAreaDocMatchesContext(
                        it,
                        homeItemIds,
                        housePaymentIds,
                        vehicleIds,
                        vehicleEventIds,
                        petEventIds,
                    )
            }
            .sortedByDescending { it.updatedAtEpochMillis }
            .take(8)
            .toList()

        return buildString {
            appendLine("Genera la sintesi settimanale basandoti su questi dati:")
            appendLine()
            append("Settimana: ")
            append(dateFmt.format(Date(weekStart)))
            append(" — ")
            append(dateFmt.format(Date(displayWeekEndDay)))
            appendLine()
            appendLine()

            if (events.isNotEmpty()) {
                appendLine("EVENTI (${events.size}):")
                events.take(8).forEach { e ->
                    append("  • ")
                    append(dateFmt.format(Date(e.startDateEpochMillis)))
                    append(": ")
                    appendLine(e.title)
                }
            }

            if (urgent.isNotEmpty()) {
                appendLine()
                appendLine("TO-DO URGENTI (${urgent.size}):")
                urgent.take(5).forEach { t ->
                    append("  • ")
                    append(t.title)
                    val due = t.dueAtEpochMillis
                    if (due != null) {
                        append(" (entro ")
                        append(dateFmt.format(Date(due)))
                        append(")")
                    }
                    appendLine()
                }
            }

            if (treats.isNotEmpty()) {
                appendLine()
                appendLine("CURE ATTIVE (${treats.size}):")
                treats.take(5).forEach { t ->
                    val child = childNames[t.childId] ?: t.childId
                    val doses = t.scheduleTimesList().size
                    appendLine("  • ${t.drugName} per $child — $doses dose/die")
                }
            }

            if (nextVisits.isNotEmpty()) {
                appendLine()
                appendLine("VISITE PROGRAMMATE:")
                nextVisits.take(3).forEach { (child, date, _) ->
                    append("  • Visita di ")
                    append(child)
                    append(": ")
                    appendLine(dateFmt.format(Date(date)))
                }
            }

            if (pendingExams.isNotEmpty()) {
                appendLine()
                appendLine("ESAMI IN SCADENZA:")
                pendingExams.take(3).forEach { (child, name, dl) ->
                    append("  • ")
                    append(name)
                    append(" per ")
                    append(child)
                    append(": entro ")
                    appendLine(dateFmt.format(Date(dl)))
                }
            }

            if (expenses.isNotEmpty()) {
                appendLine()
                val total = expenses.sumOf { it.amount }
                append("SPESE SETTIMANA: €")
                append(String.format(localeIt, "%.2f", total))
                append(" (")
                append(expenses.size)
                appendLine(" voci)")
            }

            if (groceryPending > 0) {
                appendLine()
                append("LISTA SPESA: ")
                append(groceryPending)
                appendLine(" articoli da acquistare")
            }

            if (lifeDocs.isNotEmpty()) {
                appendLine()
                appendLine("ALLEGATI CASA / GARAGE / ANIMALI (testo estratto, max 8 file):")
                for (doc in lifeDocs) {
                    val body = HealthAiDocumentText.prepareExtractedTextForAi(
                        doc.extractedText,
                        maxChars = 6_000,
                    )
                    if (body.isBlank()) continue
                    append("  — ")
                    appendLine("${doc.title}:")
                    body.lineSequence().forEach { line ->
                        if (line.isNotBlank()) {
                            append("    ")
                            appendLine(line)
                        }
                    }
                }
            }

            appendLine()
            appendLine("Genera ora la sintesi seguendo le regole del sistema.")
        }.trim()
    }

    private data class ParsedPrescribedExam(val name: String, val deadlineMillis: Long?)

    private fun parsePrescribedExamsWithDeadlines(json: String?): List<ParsedPrescribedExam> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val name = o.optString("name").trim()
                if (name.isEmpty()) return@mapNotNull null
                val dl = when {
                    o.has("deadlineEpochMillis") && !o.isNull("deadlineEpochMillis") ->
                        o.optLong("deadlineEpochMillis", 0L).takeIf { it > 0L }
                    o.has("deadline") && !o.isNull("deadline") ->
                        o.optLong("deadline", 0L).takeIf { it > 0L }
                    else -> null
                }
                ParsedPrescribedExam(name = name, deadlineMillis = dl)
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    private fun weeklyWindowMillis(now: Long): Triple<Long, Long, Long> {
        val today = LocalDate.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
        val monday = today.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStart = monday.atStartOfDay(zone).toInstant().toEpochMilli()
        val windowEndExclusive = monday.plusDays(14).atStartOfDay(zone).toInstant().toEpochMilli()
        val displayWeekEndDay = monday.plusDays(13).atStartOfDay(zone).toInstant().toEpochMilli()
        return Triple(weekStart, windowEndExclusive, displayWeekEndDay)
    }

    private fun lifeAreaDocMatchesContext(
        doc: KBDocumentEntity,
        homeItemIds: Set<String>,
        housePaymentIds: Set<String>,
        vehicleIds: Set<String>,
        vehicleEventIds: Set<String>,
        petEventIds: Set<String>,
    ): Boolean =
        homeItemIds.any { HomeItemAttachmentTag.matches(doc.notes, it) } ||
            housePaymentIds.any { HousePaymentAttachmentTag.matches(doc.notes, it) } ||
            vehicleIds.any { VehicleAttachmentTag.matches(doc.notes, it) } ||
            vehicleEventIds.any { VehicleEventAttachmentTag.matches(doc.notes, it) } ||
            petEventIds.any { PetEventAttachmentTag.matches(doc.notes, it) }
}
