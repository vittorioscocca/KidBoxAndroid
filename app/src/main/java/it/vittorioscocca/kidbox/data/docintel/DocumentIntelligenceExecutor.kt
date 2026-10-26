package it.vittorioscocca.kidbox.data.docintel

import android.content.Context
import android.net.Uri
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.health.HealthAttachmentService
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.mapper.displayTitle
import it.vittorioscocca.kidbox.data.repository.DocumentRepository
import it.vittorioscocca.kidbox.data.repository.ExpenseRepository
import it.vittorioscocca.kidbox.data.repository.HousePaymentRepository
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.data.repository.VaccineRepository
import it.vittorioscocca.kidbox.data.repository.VehicleEventRepository
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import it.vittorioscocca.kidbox.domain.model.KBVisitStatus
import it.vittorioscocca.kidbox.domain.model.VaccineStatus
import it.vittorioscocca.kidbox.domain.model.VaccineType
import it.vittorioscocca.kidbox.ui.screens.ai.planning.PlanningActionExecutor
import it.vittorioscocca.kidbox.ui.screens.ai.planning.PlanningExecutableActionDto
import it.vittorioscocca.kidbox.util.KBLog
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Esegue le azioni confermate nella scheda. Gemello di
 * `DocumentIntelligenceExecutor.swift`: prima le schede che prendono il file
 * (Casa, visita, esame, cura, vaccino), poi il file allegato alla prima di
 * queste — o in Documenti se nessuna lo prende —, poi il resto, che può
 * puntare al documento (spesa, rinomina).
 */
@Singleton
class DocumentIntelligenceExecutor @Inject constructor(
    private val housePaymentRepository: HousePaymentRepository,
    private val visitRepository: MedicalVisitRepository,
    private val examRepository: MedicalExamRepository,
    private val treatmentRepository: TreatmentRepository,
    private val vaccineRepository: VaccineRepository,
    private val vehicleEventRepository: VehicleEventRepository,
    private val expenseRepository: ExpenseRepository,
    private val documentRepository: DocumentRepository,
    private val documentDao: KBDocumentDao,
    private val attachmentService: HealthAttachmentService,
    private val planningExecutor: PlanningActionExecutor,
    private val auth: FirebaseAuth,
    @ApplicationContext private val context: Context,
) {
    private data class AttachTarget(val type: String, val id: String, val personId: String)

    suspend fun execute(
        familyId: String,
        actions: List<DocIntelAction>,
        sourceFile: File,
        persons: List<DocIntelPerson>,
        vehicles: List<DocIntelVehicle>,
    ): String? = withContext(Dispatchers.IO) {
        if (actions.isEmpty() && !sourceFile.exists()) return@withContext null
        val personIds = persons.map { it.id }.toSet()
        val childIds = persons.filter { !it.isAdult }.map { it.id }.toSet()
        val vehicleIds = vehicles.map { it.id }.toSet()
        val lines = mutableListOf<String>()
        var target: AttachTarget? = null
        fun setTarget(type: String, id: String, personId: String) {
            if (target == null) target = AttachTarget(type, id, personId)
        }

        val primary = actions.filter { it.type in PRIORITY }.sortedBy { PRIORITY.indexOf(it.type) }
        val rest = actions.filter { it.type !in PRIORITY }

        for (a in primary) {
            val line = runCatching {
                when (a.type) {
                    "house_payment" -> addHousePayment(familyId, a)?.let { (id, l) -> setTarget(a.type, id, ""); l }
                    "medical_visit" -> a.childId?.takeIf { it in personIds }?.let { p ->
                        addVisit(familyId, p, a)?.let { (id, l) -> setTarget(a.type, id, p); l }
                    }
                    "exam_add" -> a.childId?.takeIf { it in personIds }?.let { p ->
                        addExam(familyId, p, a)?.let { (id, l) -> setTarget(a.type, id, p); l }
                    }
                    "treatment_add" -> a.childId?.takeIf { it in personIds }?.let { p ->
                        addTreatment(familyId, p, a)?.let { (id, l) -> setTarget(a.type, id, p); l }
                    }
                    "vaccine_add" -> a.childId?.takeIf { it in childIds }?.let { addVaccine(familyId, it, a) }
                    else -> null
                }
            }.onFailure { KBLog.ai.warning("DocIntel ${a.type} fallita: ${it.message}", TAG) }.getOrNull()
            line?.let { lines += it }
        }

        // Il file: alla scheda principale, o in Documenti.
        var documentId: String? = null
        if (sourceFile.exists()) {
            val attached = attach(familyId, sourceFile, target)
            if (attached != null) {
                documentId = attached
                lines += context.getString(R.string.docintel_line_attached)
            } else {
                documentId = saveInDocuments(familyId, sourceFile)
                if (documentId != null) lines += context.getString(R.string.docintel_line_saved_documents)
            }
        }

        val planning = mutableListOf<PlanningExecutableActionDto>()
        for (a in rest) {
            val line = runCatching {
                when (a.type) {
                    "expense_add" -> addExpense(familyId, a, documentId)
                    "vehicle_event" -> a.vehicleId?.takeIf { it in vehicleIds }?.let { addVehicleEvent(familyId, it, a) }
                    "rename_document" -> documentId?.let { renameDocument(familyId, it, a) }
                    "event_add", "todo_add", "note_add", "health_reminder" -> {
                        planning += a.toPlanningDto()
                        null
                    }
                    else -> null
                }
            }.onFailure { KBLog.ai.warning("DocIntel ${a.type} fallita: ${it.message}", TAG) }.getOrNull()
            line?.let { lines += it }
        }
        if (planning.isNotEmpty()) {
            planningExecutor.execute(familyId, planning, emptySet())?.let { lines += it }
        }
        if (lines.isEmpty()) null else lines.joinToString("\n")
    }

    // ── Schede ───────────────────────────────────────────────────────────

    private suspend fun addHousePayment(familyId: String, a: DocIntelAction): Pair<String, String>? {
        val name = a.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val type = a.paymentType?.trim()?.lowercase()?.takeIf { it in PAYMENT_TYPES } ?: "bolletta"
        val id = UUID.randomUUID().toString()
        val amount = a.amount?.takeIf { it > 0 }
        // La spesa nasce dal pagamento, come dal modulo di Casa: niente doppioni.
        // Un errore di rete lascia la riga in Room (stato ERROR), che si risincronizza.
        runCatching {
            housePaymentRepository.addHousePayment(
                familyId = familyId,
                name = name,
                typeRaw = type,
                subtypeRaw = a.subtype?.trim()?.takeIf { it.isNotEmpty() },
                importo = amount,
                giornoDiScadenzaMensile = null,
                dataScadenza = parseEpoch(a.date),
                dataScadenzaContratto = null,
                fornitore = a.provider?.trim()?.takeIf { it.isNotEmpty() },
                note = a.notes?.trim()?.takeIf { it.isNotEmpty() },
                reminderOn = true,
                presetPaymentId = id,
            )
        }.onFailure { KBLog.ai.warning("DocIntel pagamento Casa non sincronizzato: ${it.message}", TAG) }
        val line = if (amount != null) {
            context.getString(R.string.docintel_line_house_amount, name, formatEuro(amount))
        } else {
            context.getString(R.string.docintel_line_house, name)
        }
        return id to line
    }

    private suspend fun addVisit(familyId: String, personId: String, a: DocIntelAction): Pair<String, String>? {
        val now = System.currentTimeMillis()
        val date = parseEpoch(a.date) ?: now
        val doctor = a.doctorName?.trim()?.takeIf { it.isNotEmpty() }
        val visit = KBMedicalVisit(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            childId = personId,
            dateEpochMillis = date,
            doctorName = doctor,
            doctorSpecializationRaw = null,
            travelDetailsJson = null,
            reason = a.title?.trim()?.takeIf { it.isNotEmpty() } ?: context.getString(R.string.docintel_visit_default_reason),
            diagnosis = a.diagnosis?.trim()?.takeIf { it.isNotEmpty() },
            recommendations = a.recommendations?.trim()?.takeIf { it.isNotEmpty() },
            linkedTreatmentIdsJson = "[]",
            linkedExamIdsJson = "[]",
            asNeededDrugsJson = null,
            therapyTypesJson = "[]",
            prescribedExamsJson = null,
            photoUrlsJson = "[]",
            notes = a.notes?.trim()?.takeIf { it.isNotEmpty() },
            nextVisitDateEpochMillis = null,
            nextVisitReason = null,
            visitStatusRaw = (if (date <= now) KBVisitStatus.COMPLETED else KBVisitStatus.BOOKED).rawValue,
            reminderOn = false,
            nextVisitReminderOn = false,
            isDeleted = false,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = null,
            createdBy = auth.currentUser?.uid,
            syncStateRaw = 0,
            lastSyncError = null,
        )
        visitRepository.save(visit)
        val line = if (doctor != null) {
            context.getString(R.string.docintel_line_visit_doctor, doctor)
        } else {
            context.getString(R.string.docintel_line_visit)
        }
        return visit.id to line
    }

    private suspend fun addExam(familyId: String, personId: String, a: DocIntelAction): Pair<String, String>? {
        val name = a.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val now = System.currentTimeMillis()
        val date = parseEpoch(a.date)
        val result = a.resultText?.trim()?.takeIf { it.isNotEmpty() }
        val exam = KBMedicalExam(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            childId = personId,
            name = name,
            isUrgent = false,
            deadlineEpochMillis = if (result == null) date else null,
            preparation = null,
            notes = a.notes?.trim()?.takeIf { it.isNotEmpty() },
            location = a.location?.trim()?.takeIf { it.isNotEmpty() },
            statusRaw = (if (result == null) KBExamStatus.PENDING else KBExamStatus.RESULT_IN).rawValue,
            resultText = result,
            resultDateEpochMillis = if (result == null) null else (date ?: now),
            prescribingVisitId = null,
            reminderOn = false,
            isDeleted = false,
            syncStateRaw = 0,
            lastSyncError = null,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = "",
            createdBy = auth.currentUser?.uid.orEmpty(),
        )
        examRepository.upsert(exam)
        return exam.id to context.getString(R.string.docintel_line_exam, name)
    }

    private suspend fun addTreatment(familyId: String, personId: String, a: DocIntelAction): Pair<String, String>? {
        val drug = a.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()
        val startDay = Instant.ofEpochMilli(parseEpoch(a.date) ?: now).atZone(zone).toLocalDate()
        val start = startDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val isLongTerm = a.isLongTerm == true
        val days = (a.durationDays ?: 7).coerceAtLeast(1)
        val frequency = (a.dailyFrequency ?: 1).coerceIn(1, 6)
        val treatment = KBTreatment(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            childId = personId,
            drugName = drug,
            activeIngredient = a.activeIngredient?.trim()?.takeIf { it.isNotEmpty() },
            dosageValue = a.dosageValue ?: 0.0,
            dosageUnit = a.dosageUnit?.trim()?.takeIf { it.isNotEmpty() } ?: "ml",
            isLongTerm = isLongTerm,
            durationDays = days,
            startDateEpochMillis = start,
            endDateEpochMillis = if (isLongTerm) null else startDay.plusDays((days - 1).toLong()).atStartOfDay(zone).toInstant().toEpochMilli(),
            dailyFrequency = frequency,
            scheduleTimesData = defaultTimes(frequency).joinToString(","),
            isActive = true,
            notes = a.notes?.trim()?.takeIf { it.isNotEmpty() },
            // Promemoria spenti come su iOS: si accendono dalla scheda della cura.
            reminderEnabled = false,
            isDeleted = false,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = "",
            createdBy = auth.currentUser?.uid.orEmpty(),
            syncStatus = 0,
            lastSyncError = null,
            syncStateRaw = 0,
        )
        treatmentRepository.upsert(treatment)
        return treatment.id to context.getString(R.string.docintel_line_treatment, drug)
    }

    private suspend fun addVaccine(familyId: String, childId: String, a: DocIntelAction): String {
        val now = System.currentTimeMillis()
        val type = VaccineType.entries.firstOrNull { it.rawValue == a.vaccineType } ?: VaccineType.ALTRO
        val draft = KBVaccine(
            id = UUID.randomUUID().toString(),
            familyId = familyId,
            childId = childId,
            name = "",
            vaccineTypeRaw = type.rawValue,
            statusRaw = VaccineStatus.ADMINISTERED.rawValue,
            commercialName = a.title?.trim()?.takeIf { it.isNotEmpty() },
            doseNumber = 1,
            totalDoses = 1,
            administeredDateEpochMillis = parseEpoch(a.date) ?: now,
            scheduledDateEpochMillis = null,
            lotNumber = null,
            doctorName = null,
            location = null,
            administeredBy = null,
            administrationSiteRaw = null,
            notes = a.notes?.trim()?.takeIf { it.isNotEmpty() },
            reminderOn = false,
            nextDoseDateEpochMillis = null,
            isDeleted = false,
            createdAtEpochMillis = now,
            updatedAtEpochMillis = now,
            updatedBy = null,
            createdBy = auth.currentUser?.uid,
            syncStateRaw = 0,
            lastSyncError = null,
        )
        val vaccine = draft.copy(name = draft.displayTitle())
        vaccineRepository.upsert(vaccine)
        return context.getString(R.string.docintel_line_vaccine, vaccine.name)
    }

    private suspend fun addExpense(familyId: String, a: DocIntelAction, documentId: String?): String? {
        val title = a.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val amount = a.amount?.takeIf { it > 0 } ?: return null
        expenseRepository.createExpenseLocal(
            familyId = familyId,
            title = title,
            amount = amount,
            dateEpochMillis = parseEpoch(a.date) ?: System.currentTimeMillis(),
            categoryId = null,
            notes = a.notes,
            attachedDocumentId = documentId,
        )
        runCatching { expenseRepository.flushPending(familyId) }
        return context.getString(R.string.docintel_line_expense, title, formatEuro(amount))
    }

    private suspend fun addVehicleEvent(familyId: String, vehicleId: String, a: DocIntelAction): String? {
        val title = a.title?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        runCatching {
            vehicleEventRepository.addVehicleEvent(
                familyId = familyId,
                vehicleId = vehicleId,
                title = title,
                eventType = a.vehicleEventType?.trim()?.takeIf { it.isNotEmpty() } ?: "altro",
                dateMillis = parseEpoch(a.date) ?: System.currentTimeMillis(),
                km = null,
                cost = a.amount?.takeIf { it > 0 },
                garageName = null,
                notes = a.notes,
            )
        }.onFailure { KBLog.ai.warning("DocIntel scadenza veicolo non sincronizzata: ${it.message}", TAG) }
        return context.getString(R.string.docintel_line_vehicle, title)
    }

    private suspend fun renameDocument(familyId: String, documentId: String, a: DocIntelAction): String? {
        val newName = a.renameTo?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        val doc = documentDao.getById(documentId) ?: return null
        if (doc.title == newName) return null
        documentRepository.renameDocumentLocal(doc, newName)
        runCatching { documentRepository.flushPending(familyId) }
        return context.getString(R.string.docintel_line_renamed, newName)
    }

    // ── File ─────────────────────────────────────────────────────────────

    private suspend fun attach(familyId: String, file: File, target: AttachTarget?): String? {
        val t = target ?: return null
        val uri = Uri.fromFile(file)
        val result = when (t.type) {
            "house_payment" -> attachmentService.uploadHousePaymentAttachment(uri, t.id, familyId)
            "medical_visit" -> attachmentService.uploadVisitAttachment(uri, t.id, familyId, t.personId)
            "exam_add" -> attachmentService.uploadExamAttachment(uri, t.id, familyId, t.personId)
            "treatment_add" -> attachmentService.uploadTreatmentAttachment(uri, t.id, familyId, t.personId)
            else -> return null
        }
        return result.onFailure { KBLog.ai.warning("DocIntel allegato non riuscito: ${it.message}", TAG) }
            .getOrNull()?.id
    }

    private suspend fun saveInDocuments(familyId: String, file: File): String? = runCatching {
        val id = UUID.randomUUID().toString()
        documentRepository.uploadDocumentLocal(
            familyId = familyId,
            parentFolderId = null,
            fileName = file.name,
            mimeType = mimeFor(file.name),
            bytes = file.readBytes(),
            forcedId = id,
        )
        runCatching { documentRepository.flushPending(familyId) }
        id
    }.onFailure { KBLog.ai.warning("DocIntel salvataggio in Documenti non riuscito: ${it.message}", TAG) }.getOrNull()

    // ── Utilità ──────────────────────────────────────────────────────────

    private fun DocIntelAction.toPlanningDto() = PlanningExecutableActionDto(
        type = type,
        title = title,
        body = body,
        notes = notes,
        dueAt = if (type == "event_add") null else date,
        startAt = if (type == "event_add") date else null,
        endAt = endDate,
        isAllDay = isAllDay,
        childId = childId,
    )

    private fun formatEuro(amount: Double): String = String.format(Locale.ITALY, "%.2f €", amount)

    companion object {
        private const val TAG = "DocumentIntelligence"
        private val PRIORITY = listOf("house_payment", "medical_visit", "exam_add", "treatment_add", "vaccine_add")
        private val PAYMENT_TYPES = setOf("mutuo", "affitto", "bolletta", "tassa", "altro")

        fun mimeFor(fileName: String): String = when (fileName.substringAfterLast('.', "").lowercase()) {
            "pdf" -> "application/pdf"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "heic" -> "image/heic"
            "webp" -> "image/webp"
            else -> "application/octet-stream"
        }

        /** Stessi orari proposti dal modulo della cura per 1…6 somministrazioni. */
        fun defaultTimes(frequency: Int): List<String> = when (frequency) {
            1 -> listOf("08:00")
            2 -> listOf("08:00", "20:00")
            3 -> listOf("08:00", "14:00", "20:00")
            4 -> listOf("08:00", "12:00", "16:00", "20:00")
            5 -> listOf("07:00", "10:00", "13:00", "16:00", "19:00")
            else -> listOf("06:00", "09:00", "12:00", "15:00", "18:00", "21:00")
        }

        fun parseEpoch(raw: String?): Long? {
            val s = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
            val zone = ZoneId.systemDefault()
            return runCatching { Instant.parse(s).toEpochMilli() }.getOrNull()
                ?: runCatching { OffsetDateTime.parse(s).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDateTime.parse(s).atZone(zone).toInstant().toEpochMilli() }.getOrNull()
                ?: runCatching { LocalDate.parse(s.take(10)).atStartOfDay(zone).toInstant().toEpochMilli() }.getOrNull()
        }

        fun formatIso(epochMillis: Long): String = Instant.ofEpochMilli(epochMillis).toString()
    }
}
