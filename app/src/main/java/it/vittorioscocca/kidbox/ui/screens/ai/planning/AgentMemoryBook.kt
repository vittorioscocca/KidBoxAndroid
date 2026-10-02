package it.vittorioscocca.kidbox.ui.screens.ai.planning

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.health.ExamAttachmentTag
import it.vittorioscocca.kidbox.data.health.HealthLinkStore
import it.vittorioscocca.kidbox.data.health.TreatmentAttachmentTag
import it.vittorioscocca.kidbox.data.health.VisitAttachmentTag
import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.data.health.ai.HealthContextBuilder
import it.vittorioscocca.kidbox.data.health.ai.HealthContextPurpose
import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanPromptBuilder
import it.vittorioscocca.kidbox.data.life.HousePaymentDeadlineCalculator
import it.vittorioscocca.kidbox.data.local.dao.HomeItemDao
import it.vittorioscocca.kidbox.data.local.dao.HousePaymentDao
import it.vittorioscocca.kidbox.data.local.dao.KBCalendarEventDao
import it.vittorioscocca.kidbox.data.local.dao.KBChatMessageDao
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBDocumentDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseCategoryDao
import it.vittorioscocca.kidbox.data.local.dao.KBExpenseDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBGroceryItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBNoteDao
import it.vittorioscocca.kidbox.data.local.dao.KBPediatricProfileDao
import it.vittorioscocca.kidbox.data.local.dao.KBRoutineCheckDao
import it.vittorioscocca.kidbox.data.local.dao.KBRoutineDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoItemDao
import it.vittorioscocca.kidbox.data.local.dao.KBTodoListDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripDayPlanDao
import it.vittorioscocca.kidbox.data.local.dao.KBTripLegDao
import it.vittorioscocca.kidbox.data.local.dao.LoyaltyCardDao
import it.vittorioscocca.kidbox.data.local.dao.PetDao
import it.vittorioscocca.kidbox.data.local.dao.PetEventDao
import it.vittorioscocca.kidbox.data.local.dao.VehicleDao
import it.vittorioscocca.kidbox.data.local.dao.VehicleEventDao
import it.vittorioscocca.kidbox.data.local.dao.WalletTicketDao
import it.vittorioscocca.kidbox.data.local.entity.HomeItemEntity
import it.vittorioscocca.kidbox.data.local.entity.HousePaymentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBCalendarEventEntity
import it.vittorioscocca.kidbox.data.local.entity.KBChatMessageEntity
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBExpenseEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.local.entity.KBGroceryItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBLoyaltyCardEntity
import it.vittorioscocca.kidbox.data.local.entity.KBNoteEntity
import it.vittorioscocca.kidbox.data.local.entity.KBPediatricProfileEntity
import it.vittorioscocca.kidbox.data.local.entity.KBRoutineEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTodoItemEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripDayPlanEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTripLegEntity
import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import it.vittorioscocca.kidbox.data.local.entity.PetEventEntity
import it.vittorioscocca.kidbox.data.local.entity.VehicleEntity
import it.vittorioscocca.kidbox.data.local.entity.VehicleEventEntity
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.data.remote.DocumentCryptoManager
import it.vittorioscocca.kidbox.data.repository.MedicalExamRepository
import it.vittorioscocca.kidbox.data.repository.MedicalVisitRepository
import it.vittorioscocca.kidbox.data.repository.TreatmentRepository
import it.vittorioscocca.kidbox.data.repository.VaccineRepository
import it.vittorioscocca.kidbox.domain.calendar.occurrencesIn
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.domain.model.KBTextExtractionStatus
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import it.vittorioscocca.kidbox.domain.model.WalletDocumentMetadata
import it.vittorioscocca.kidbox.ui.screens.life.homeCategoryLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.housePaymentTypeLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.petEventTypeLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.speciesLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.vehicleEventTypeLabelIt
import it.vittorioscocca.kidbox.ui.screens.notes.htmlToPlainText
import it.vittorioscocca.kidbox.util.KBLocale
import it.vittorioscocca.kidbox.util.decodeStringList
import java.text.Normalizer
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.Calendar
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/*
 * La memoria dell'assistente unico: una scheda markdown per ogni sezione
 * dell'app, costruita sul telefono dai dati locali a ogni domanda. Disegno,
 * budget e regole in `internal/assistente-unico.md`; iOS
 * (`AgentMemoryBook.swift`) e web (`memoryBook.js`) costruiscono le stesse schede.
 *
 * Le schede di salute riusano `HealthContextBuilder` (lo stesso della chat
 * Salute, scopo AGENT_MEMORY): referti, Health Connect e formati non si
 * riscrivono qui.
 */

// ── Focus ────────────────────────────────────────────────────────────────────

/**
 * Da dove si è aperto l'assistente: arriva dai pulsanti di Salute. La
 * conversazione resta una sola, il focus cambia solo il contesto.
 * [personId] è il `childId` di un figlio o lo `userId` di un adulto.
 */
data class AgentFocus(
    val personId: String,
    val personName: String,
    val scope: Scope = Scope.PERSON,
    /** Id della visita o dell'esame per [Scope.VISIT] / [Scope.EXAM]. */
    val itemId: String? = null,
    /** Titolo della visita o dell'esame, già pronto per l'etichetta. */
    val detail: String? = null,
) {
    enum class Scope(val raw: String) {
        PERSON("person"), VISITS("visits"), VISIT("visit"), EXAMS("exams"), EXAM("exam");

        companion object {
            fun fromRaw(raw: String?): Scope? = entries.firstOrNull { it.raw == raw }
        }
    }

    /** Etichetta sotto l'intestazione della chat. */
    fun label(context: Context): String = when (scope) {
        Scope.PERSON -> context.getString(R.string.agent_focus_health_of, personName)
        Scope.VISITS -> context.getString(R.string.agent_focus_visits_of, personName)
        Scope.EXAMS -> context.getString(R.string.agent_focus_exams_of, personName)
        Scope.VISIT, Scope.EXAM -> detail?.takeIf { it.isNotBlank() }?.let { "$it · $personName" } ?: personName
    }

    /**
     * Riga del prompt: cosa stava guardando l'utente. Con gli esempi: scritta solo
     * come «le domande senza soggetto si riferiscono a questo», Haiku rispondeva
     * sulla famiglia intera. Stesso testo su iOS e web.
     */
    val promptLine: String
        get() {
            val what = when (scope) {
                Scope.PERSON -> "la salute di $personName"
                Scope.VISITS -> "le visite di $personName"
                Scope.VISIT -> "la visita «${detail.orEmpty()}» di $personName"
                Scope.EXAMS -> "gli esami di $personName"
                Scope.EXAM -> "l'esame «${detail.orEmpty()}» di $personName"
            }
            return "FOCUS DI QUESTA CONVERSAZIONE: l'utente ha aperto l'assistente da Salute, guardando $what. " +
                "Le domande che non nominano altro («cosa devo fare?», «cosa dice il referto?», «è grave?») riguardano $what: " +
                "rispondi su quello, non sul resto della famiglia. Se chiede esplicitamente d'altro, rispondi d'altro."
        }

    /** Domande d'esempio a tema, al posto dei suggerimenti generici. */
    fun suggestions(context: Context): List<String> = when (scope) {
        Scope.PERSON -> listOf(
            context.getString(R.string.agent_q_person_summary, personName),
            context.getString(R.string.agent_q_person_todo),
            context.getString(R.string.agent_q_person_last_report),
        )
        Scope.VISITS -> listOf(
            context.getString(R.string.agent_q_visits_summary, personName),
            context.getString(R.string.agent_q_visits_followup),
        )
        Scope.VISIT -> listOf(
            context.getString(R.string.agent_q_visit_explain),
            context.getString(R.string.agent_q_visit_report),
            context.getString(R.string.agent_q_visit_next),
        )
        Scope.EXAMS -> listOf(
            context.getString(R.string.agent_q_exams_out_of_range, personName),
            context.getString(R.string.agent_q_exams_pending),
        )
        Scope.EXAM -> listOf(
            context.getString(R.string.agent_q_exam_values),
            context.getString(R.string.agent_q_exam_normal),
        )
    }

    /** Tag degli allegati della visita o dell'esame del focus: passano interi. */
    val itemTags: Set<String>
        get() = when (scope) {
            Scope.VISIT -> itemId?.let { setOf(VisitAttachmentTag.make(it)) }.orEmpty()
            Scope.EXAM -> itemId?.let { setOf(ExamAttachmentTag.make(it)) }.orEmpty()
            else -> emptySet()
        }
}

// ── Snapshot ─────────────────────────────────────────────────────────────────

/** Una persona con i suoi dati sanitari (figlio sempre, adulto solo se ne ha). */
data class AgentHealthPerson(
    val id: String,
    val name: String,
    val child: KBChildEntity?,
    val profile: KBPediatricProfileEntity?,
    val visits: List<KBMedicalVisit>,
    val exams: List<KBMedicalExam>,
    val treatments: List<KBTreatment>,
    val vaccines: List<KBVaccine>,
    val healthSnapshot: HealthImportSnapshot?,
)

/**
 * Tutti i dati della famiglia attiva che l'assistente può vedere, letti una
 * volta per domanda. Le voci «solo per me» degli altri membri sono già fuori.
 */
data class AgentMemorySnapshot(
    val familyId: String,
    val familyName: String,
    val uid: String?,
    val members: List<KBFamilyMemberEntity>,
    val children: List<KBChildEntity>,
    val healthPersons: List<AgentHealthPerson>,
    val events: List<KBCalendarEventEntity>,
    val todos: List<KBTodoItemEntity>,
    val todoListNames: Map<String, String>,
    val routines: List<KBRoutineEntity>,
    val todayRoutineChecks: Set<String>,
    val notes: List<KBNoteEntity>,
    val expenses: List<KBExpenseEntity>,
    val expenseCategoryNames: Map<String, String>,
    val grocery: List<KBGroceryItemEntity>,
    val chat: List<KBChatMessageEntity>,
    val documents: List<KBDocumentEntity>,
    val documentCategoryNames: Map<String, String>,
    val walletTickets: List<KBWalletTicketEntity>,
    val walletIdentity: List<Pair<KBDocumentEntity, WalletDocumentMetadata?>>,
    val loyaltyCards: List<KBLoyaltyCardEntity>,
    val pets: List<PetEntity>,
    val petEvents: List<PetEventEntity>,
    val petTreatments: List<KBTreatment>,
    val homeItems: List<HomeItemEntity>,
    val housePayments: List<HousePaymentEntity>,
    val vehicles: List<VehicleEntity>,
    val vehicleEvents: List<VehicleEventEntity>,
    val trips: List<KBTripEntity>,
    val tripLegs: List<KBTripLegEntity>,
    val tripDays: List<KBTripDayPlanEntity>,
    val memoryFacts: List<String>,
) {
    fun memberName(uid: String?): String? =
        uid?.takeIf { it.isNotBlank() }?.let { id -> members.firstOrNull { it.userId == id }?.displayName?.takeIf { it.isNotBlank() } }

    fun personName(personId: String?): String? =
        personId?.let { id -> children.firstOrNull { it.id == id }?.name ?: memberName(id) }

    val openTodos: List<KBTodoItemEntity> get() = todos.filter { !it.isDone }
    val pendingGrocery: List<KBGroceryItemEntity> get() = grocery.filter { !it.isPurchased }
    val activeTreatments: List<KBTreatment> get() = healthPersons.flatMap { p -> p.treatments.filter { it.isCurrentlyActive() } }
    val allVisits: List<KBMedicalVisit> get() = healthPersons.flatMap { it.visits }
}

/** Una cura è in corso se attiva e non finita (stessa regola di `HealthContextBuilder`). */
internal fun KBTreatment.isCurrentlyActive(now: Long = System.currentTimeMillis()): Boolean =
    isActive && !isDeleted && (isLongTerm || endDateEpochMillis == null || endDateEpochMillis >= now)

/** Legge lo [AgentMemorySnapshot] della famiglia da Room. */
@Singleton
class AgentMemoryRepository @Inject constructor(
    private val familyMemberDao: KBFamilyMemberDao,
    private val childDao: KBChildDao,
    private val pediatricProfileDao: KBPediatricProfileDao,
    private val calendarEventDao: KBCalendarEventDao,
    private val todoItemDao: KBTodoItemDao,
    private val todoListDao: KBTodoListDao,
    private val routineDao: KBRoutineDao,
    private val routineCheckDao: KBRoutineCheckDao,
    private val treatmentRepository: TreatmentRepository,
    private val medicalVisitRepository: MedicalVisitRepository,
    private val medicalExamRepository: MedicalExamRepository,
    private val vaccineRepository: VaccineRepository,
    private val noteDao: KBNoteDao,
    private val expenseDao: KBExpenseDao,
    private val expenseCategoryDao: KBExpenseCategoryDao,
    private val groceryItemDao: KBGroceryItemDao,
    private val chatMessageDao: KBChatMessageDao,
    private val documentDao: KBDocumentDao,
    private val documentCategoryDao: KBDocumentCategoryDao,
    private val walletTicketDao: WalletTicketDao,
    private val loyaltyCardDao: LoyaltyCardDao,
    private val petDao: PetDao,
    private val petEventDao: PetEventDao,
    private val homeItemDao: HomeItemDao,
    private val housePaymentDao: HousePaymentDao,
    private val vehicleDao: VehicleDao,
    private val vehicleEventDao: VehicleEventDao,
    private val tripDao: KBTripDao,
    private val tripLegDao: KBTripLegDao,
    private val tripDayPlanDao: KBTripDayPlanDao,
    private val healthLinkStore: HealthLinkStore,
    private val familyMemoryService: FamilyMemoryService,
    private val cryptoManager: DocumentCryptoManager,
    private val auth: FirebaseAuth,
) {
    suspend fun load(familyId: String, familyName: String): AgentMemorySnapshot {
        val uid = auth.currentUser?.uid
        fun visible(scope: String?, membersJson: String?, createdBy: String?): Boolean =
            KBVisibilityScope.isVisible(
                KBVisibilityScope.normalized(scope ?: KBVisibilityScope.FAMILY),
                decodeStringList(membersJson),
                createdBy?.takeIf { it.isNotBlank() },
                uid,
            )

        val members = familyMemberDao.getAllByFamilyId(familyId).filter { !it.isDeleted }
        val children = childDao.getChildrenByFamilyId(familyId)
            .sortedBy { it.birthDateEpochMillis ?: Long.MAX_VALUE }
        val profiles = pediatricProfileDao.observeByFamilyId(familyId).first().associateBy { it.childId }

        // Salute: figli sempre, adulti (chiave = userId) solo se hanno dati.
        suspend fun person(id: String, name: String, child: KBChildEntity?): AgentHealthPerson {
            val treatments = treatmentRepository.listByFamilyAndChild(familyId, id)
                .filter { !it.isDeleted && it.petId.isBlank() }
            return AgentHealthPerson(
                id = id,
                name = name,
                child = child,
                profile = profiles[id],
                visits = medicalVisitRepository.listRecentVisitsForChild(familyId, id, limit = 500).filter { !it.isDeleted },
                exams = medicalExamRepository.listByFamilyAndChild(familyId, id).filter { !it.isDeleted },
                treatments = treatments,
                vaccines = vaccineRepository.observe(familyId, id).first().filter { !it.isDeleted },
                healthSnapshot = runCatching { healthLinkStore.load(id) }.getOrNull(),
            )
        }
        val healthPersons = buildList {
            children.forEach { add(person(it.id, it.name, it)) }
            members.forEach { m ->
                val name = m.displayName?.takeIf { it.isNotBlank() } ?: return@forEach
                val p = person(m.userId, name, null)
                if (p.visits.isNotEmpty() || p.exams.isNotEmpty() || p.treatments.isNotEmpty() || p.vaccines.isNotEmpty()) add(p)
            }
        }

        val todayKey = SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT).format(Date())
        val routines = children.flatMap { routineDao.observeByFamilyAndChild(familyId, it.id).first() }
            .filter { !it.isDeleted && it.isActive }
        val todayChecks = routines.flatMap { r ->
            routineCheckDao.observeByRoutine(familyId, r.id).first().filter { !it.isDeleted && it.dayKey == todayKey }
        }.map { it.routineId }.toSet()

        val documents = documentDao.getAllByFamilyId(familyId)
            .filter { !it.isDeleted && visible(it.visibilityScope, it.visibilityMemberIdsJson, it.createdBy) }
            .sortedByDescending { it.updatedAtEpochMillis }
        val walletIdentity = documents
            .filter { it.notes?.startsWith(WalletDocumentMetadata.NOTES_PREFIX) == true }
            .map { doc -> doc to runCatching { WalletDocumentMetadata.parse(doc.notes, cryptoManager, familyId) }.getOrNull() }

        val pets = petDao.getAllByFamily(familyId).filter { !it.isDeleted }
        val trips = tripDao.getAllOnce(familyId)
        val recentTripIds = trips.filter { it.endDateEpoch >= System.currentTimeMillis() - 60L * DAY_MS }.map { it.id }

        return AgentMemorySnapshot(
            familyId = familyId,
            familyName = familyName,
            uid = uid,
            members = members,
            children = children,
            healthPersons = healthPersons,
            events = calendarEventDao.observeByFamilyId(familyId).first()
                .filter { !it.isDeleted && visible(it.visibilityScope, it.visibilityMemberIdsJson, it.createdBy) },
            todos = todoItemDao.getByFamily(familyId)
                .filter { !it.isDeleted && visible(it.visibilityScope, it.visibilityMemberIdsJson, it.createdBy) },
            todoListNames = todoListDao.getByFamily(familyId).filter { !it.isDeleted }.associate { it.id to it.name },
            routines = routines,
            todayRoutineChecks = todayChecks,
            notes = noteDao.observeByFamilyId(familyId).first()
                .filter { !it.isDeleted && visible(it.visibilityScope, it.visibilityMemberIdsJson, it.createdBy) }
                .sortedByDescending { it.updatedAtEpochMillis },
            expenses = expenseDao.getAllByFamilyId(familyId).filter { !it.isDeleted }.sortedByDescending { it.dateEpochMillis },
            expenseCategoryNames = expenseCategoryDao.getAllByFamilyId(familyId).filter { !it.isDeleted }.associate { it.id to it.name },
            grocery = groceryItemDao.observeByFamilyId(familyId).first().filter { !it.isDeleted },
            chat = chatMessageDao.getAllByFamilyId(familyId).filter { !it.isDeleted }.sortedBy { it.createdAtEpochMillis },
            documents = documents,
            documentCategoryNames = documentCategoryDao.getAllByFamilyId(familyId).filter { !it.isDeleted }.associate { it.id to it.title },
            walletTickets = uid?.let { walletTicketDao.getActiveByFamilyId(familyId, it) }.orEmpty(),
            walletIdentity = walletIdentity,
            loyaltyCards = uid?.let { loyaltyCardDao.observeActiveByFamilyId(familyId, it).first() }.orEmpty(),
            pets = pets,
            petEvents = petEventDao.observeByFamily(familyId).first().filter { !it.isDeleted },
            petTreatments = pets.flatMap { treatmentRepository.listByFamilyAndPet(familyId, it.id) }.filter { !it.isDeleted },
            homeItems = homeItemDao.observeByFamily(familyId).first().filter { !it.isDeleted },
            housePayments = housePaymentDao.observeByFamily(familyId).first().filter { !it.isDeleted },
            vehicles = vehicleDao.observeByFamily(familyId).first().filter { !it.isDeleted },
            vehicleEvents = vehicleEventDao.observeByFamily(familyId).first().filter { !it.isDeleted },
            trips = trips,
            tripLegs = tripLegDao.observeByFamily(familyId).first(),
            tripDays = recentTripIds.flatMap { tripDayPlanDao.observeByTrip(it).first() },
            memoryFacts = familyMemoryService.fetchFactTexts(familyId),
        )
    }
}

// ── Book ─────────────────────────────────────────────────────────────────────

data class AgentMemoryFile(val name: String, val title: String, val summary: String, val body: String)

/** Un documento con testo letto che entra nel quaderno, in una scheda o nell'altra. */
data class AgentTextDocument(
    val doc: KBDocumentEntity,
    val fullLength: Int,
    val personId: String?,
    val place: String,
)

class AgentMemoryBook(val files: List<AgentMemoryFile>) {
    val rendered: String
        get() = buildString {
            appendLine("<indice>")
            files.forEach { appendLine("- ${it.name} — ${it.title}: ${it.summary}") }
            append("</indice>")
            files.forEach { f ->
                appendLine()
                appendLine()
                appendLine("<scheda file=\"${f.name}\" titolo=\"${f.title}\">")
                appendLine(f.body)
                append("</scheda>")
            }
        }
}

private const val DAY_MS = 24L * 60 * 60 * 1000

/**
 * Costruisce le schede da uno snapshot. `docAllowance` null = testi interi;
 * altrimenti caratteri concessi a ogni documento (0 = solo il titolo).
 */
class AgentMemoryBookBuilder(
    private val s: AgentMemorySnapshot,
    private val now: Long = System.currentTimeMillis(),
) {
    private val locale = KBLocale.current()
    private val dateFmt = SimpleDateFormat("d MMMM yyyy", locale)
    private val dateTimeFmt = SimpleDateFormat("d MMM yyyy HH:mm", locale)
    private val timeFmt = SimpleDateFormat("HH:mm", locale)
    private val weekdayFmt = SimpleDateFormat("EEEE d MMMM", locale)
    private val monthFmt = SimpleDateFormat("MMMM yyyy", locale)

    private sealed class DocHome {
        data class Health(val personId: String, val label: String) : DocHome()
        data class Home(val label: String) : DocHome()
        data class Vehicle(val label: String) : DocHome()
        data class Pet(val label: String) : DocHome()
        data class Expense(val label: String) : DocHome()
        data class Wallet(val kind: String) : DocHome()
        data object General : DocHome()
    }

    /**
     * Tutti i documenti con testo letto che il quaderno può includere. Gli
     * identificativi del Wallet non ci sono mai: il loro testo contiene numeri e
     * codici che l'assistente non deve vedere.
     */
    fun textDocuments(): List<AgentTextDocument> = s.documents.mapNotNull { doc ->
        if (!doc.hasText()) return@mapNotNull null
        val home = homeOf(doc)
        if (home is DocHome.Wallet) return@mapNotNull null
        val length = HealthAiDocumentText.sanitizeExtractedText(doc.extractedText.orEmpty()).length
        if (length == 0) return@mapNotNull null
        AgentTextDocument(doc, length, personIdOf(doc, home), placeLabel(doc, home))
    }

    fun build(docAllowance: Map<String, Int>?): AgentMemoryBook {
        val files = buildList {
            add(familyFile())
            memoryFile()?.let { add(it) }
            add(todayFile())
            add(calendarFile())
            add(todoFile())
            add(groceryFile())
            notesFile()?.let { add(it) }
            expensesFile()?.let { add(it) }
            addAll(healthFiles(docAllowance))
            add(documentsFile(docAllowance))
            walletFile()?.let { add(it) }
            homeFile(docAllowance)?.let { add(it) }
            vehiclesFile(docAllowance)?.let { add(it) }
            petsFile(docAllowance)?.let { add(it) }
            tripsFile()?.let { add(it) }
            chatFile()?.let { add(it) }
        }
        return AgentMemoryBook(files)
    }

    // famiglia.md
    private fun familyFile(): AgentMemoryFile {
        val adults = s.members.mapNotNull { m ->
            val name = m.displayName?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            if (m.role == "admin") "$name (amministratore)" else name
        }
        val body = buildString {
            appendLine("# Famiglia ${s.familyName}")
            s.memberName(s.uid)?.let { appendLine("Sta scrivendo: $it") }
            if (adults.isNotEmpty()) {
                appendLine("\n## Adulti")
                adults.forEach { appendLine("- $it") }
            }
            if (s.children.isNotEmpty()) {
                appendLine("\n## Figli")
                s.children.forEach { c ->
                    val birth = c.birthDateEpochMillis?.let {
                        // Prossimo compleanno già calcolato: lasciato al modello, lo
                        // sbagliava («compie 1 anno» a una bambina di tre).
                        val (next, turning) = nextBirthday(it)
                        ", ${ageLabel(it)} (nato/a il ${fmtDate(it)}) — prossimo compleanno: " +
                            "${weekdayFmt.format(Date(next))}, compie $turning anni"
                    }.orEmpty()
                    appendLine("- ${c.name}$birth")
                }
            }
            if (s.pets.isNotEmpty()) {
                appendLine("\n## Animali")
                s.pets.forEach { appendLine("- ${it.name} (${speciesLabelIt(it.species)})") }
            }
        }.trimEnd()
        return AgentMemoryFile("famiglia.md", "Famiglia", "${adults.size} adulti, ${s.children.size} figli, ${s.pets.size} animali", body)
    }

    // ricordi.md
    private fun memoryFile(): AgentMemoryFile? {
        if (s.memoryFacts.isEmpty()) return null
        val body = buildString {
            appendLine("# Ricordi")
            appendLine("Fatti emersi dalle conversazioni passate. Usali per personalizzare senza citarli se non serve.")
            s.memoryFacts.forEach { appendLine("- $it") }
        }.trimEnd()
        return AgentMemoryFile("ricordi.md", "Ricordi", "${s.memoryFacts.size} fatti", body)
    }

    // oggi.md
    private fun todayFile(): AgentMemoryFile {
        val start = startOfDay(now)
        val end = start + DAY_MS
        val today = occurrences(start, end)
        val urgent = s.openTodos.filter { (it.priorityRaw ?: 0) == 1 || (it.dueAtEpochMillis?.let { d -> d <= end } == true) }
        val unchecked = s.routines.filter { it.id !in s.todayRoutineChecks }
        val doses = s.healthPersons.flatMap { p ->
            p.treatments.filter { it.isCurrentlyActive(now) }.flatMap { t ->
                t.scheduleTimesList().map { "- ore $it: ${t.drugName} ${fmtNumber(t.dosageValue)} ${t.dosageUnit} (${p.name})" }
            }
        }.sorted()
        val body = buildString {
            appendLine("# Oggi, ${weekdayFmt.format(Date(now))}")
            if (today.isEmpty()) appendLine("Nessun evento in calendario.") else {
                appendLine("## Eventi")
                today.forEach { appendLine("- ${eventLine(it)}") }
            }
            if (urgent.isNotEmpty()) {
                appendLine("## To-do urgenti o in scadenza")
                urgent.take(20).forEach { appendLine("- ${todoLine(it)}") }
            }
            if (unchecked.isNotEmpty()) {
                appendLine("## Routine non ancora fatte")
                unchecked.forEach { appendLine("- ${it.title} (${s.personName(it.childId) ?: "—"})") }
            }
            if (doses.isNotEmpty()) {
                appendLine("## Dosi di farmaci")
                doses.forEach { appendLine(it) }
            }
        }.trimEnd()
        return AgentMemoryFile("oggi.md", "Oggi", "${today.size} eventi, ${urgent.size} to-do urgenti, ${doses.size} dosi", body)
    }

    // calendario.md
    private fun calendarFile(): AgentMemoryFile {
        val start = startOfDay(now) - CALENDAR_PAST_DAYS * DAY_MS
        val end = now + CALENDAR_FUTURE_DAYS * DAY_MS
        val all = occurrences(start, end)
        val shown = all.take(CALENDAR_MAX_OCCURRENCES)
        val body = buildString {
            appendLine("# Calendario")
            appendLine("Dal ${fmtDate(start)} al ${fmtDate(end)}. Gli eventi ricorrenti compaiono in ogni ripetizione.")
            if (shown.isEmpty()) appendLine("Nessun evento in questo periodo.")
            var currentDay = ""
            shown.forEach { e ->
                val day = weekdayFmt.format(Date(e.startDateEpochMillis))
                if (day != currentDay) {
                    appendLine("\n## $day")
                    currentDay = day
                }
                appendLine("- ${eventLine(e)}")
            }
            if (all.size > shown.size) appendLine("\n(Altre ${all.size - shown.size} ripetizioni oltre il limite non elencate.)")
        }.trimEnd()
        val upcoming = all.count { it.startDateEpochMillis >= now }
        return AgentMemoryFile("calendario.md", "Calendario", "$upcoming eventi nei prossimi $CALENDAR_FUTURE_DAYS giorni, ricorrenze comprese", body)
    }

    private fun occurrences(start: Long, end: Long): List<KBCalendarEventEntity> =
        s.events.flatMap { it.occurrencesIn(start, end) }.sortedBy { it.startDateEpochMillis }

    private fun eventLine(e: KBCalendarEventEntity): String = buildString {
        append(if (e.isAllDay) "tutto il giorno" else "${timeFmt.format(Date(e.startDateEpochMillis))}–${timeFmt.format(Date(e.endDateEpochMillis))}")
        append(" ${e.title}")
        s.personName(e.childId)?.let { append(" ($it)") }
        append(" [${categoryLabel(e.categoryRaw)}]")
        e.location?.takeIf { it.isNotBlank() }?.let { append(" @ $it") }
        recurrenceLabel(e.recurrenceRaw)?.let { append(" — $it") }
        e.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" — note: ${clip(it, 120)}") }
    }

    // todo.md
    private fun todoFile(): AgentMemoryFile {
        val open = s.openTodos
        val overdue = open.filter { (it.dueAtEpochMillis ?: Long.MAX_VALUE) < now }.sortedBy { it.dueAtEpochMillis }
        val dated = open.filter { it.dueAtEpochMillis?.let { d -> d >= now } == true }.sortedBy { it.dueAtEpochMillis }
        val undated = open.filter { it.dueAtEpochMillis == null }.sortedByDescending { it.priorityRaw ?: 0 }
        val done = s.todos.filter { it.isDone && (it.doneAtEpochMillis ?: 0) >= now - 7 * DAY_MS }
            .sortedByDescending { it.doneAtEpochMillis }
        val body = buildString {
            appendLine("# To-do")
            if (open.isEmpty()) appendLine("Nessun to-do aperto.")
            if (overdue.isNotEmpty()) {
                appendLine("\n## Scaduti (${overdue.size})")
                overdue.take(40).forEach { appendLine("- ${todoLine(it)}") }
            }
            if (dated.isNotEmpty()) {
                appendLine("\n## Con scadenza (${dated.size})")
                dated.take(60).forEach { appendLine("- ${todoLine(it)}") }
            }
            if (undated.isNotEmpty()) {
                appendLine("\n## Senza data (${undated.size})")
                undated.take(60).forEach { appendLine("- ${todoLine(it)}") }
            }
            if (done.isNotEmpty()) {
                appendLine("\n## Fatti negli ultimi 7 giorni")
                done.take(20).forEach { t ->
                    val by = s.memberName(t.doneBy)?.let { " — fatto da $it" }.orEmpty()
                    val at = t.doneAtEpochMillis?.let { " il ${fmtDate(it)}" }.orEmpty()
                    appendLine("- ${t.title}$by$at")
                }
            }
        }.trimEnd()
        return AgentMemoryFile("todo.md", "To-do", "${open.size} aperti, ${overdue.size} scaduti", body)
    }

    private fun todoLine(t: KBTodoItemEntity): String = buildString {
        append(t.title)
        if ((t.priorityRaw ?: 0) == 1) append(" [URGENTE]")
        t.dueAtEpochMillis?.let { append(" — scadenza ${if (t.dueHasTime) fmtDateTime(it) else fmtDate(it)}") }
        val who = s.memberName(t.assignedTo)
        when {
            who != null -> append(" → $who")
            !t.assignedExternalName.isNullOrBlank() -> append(" → ${t.assignedExternalName} (fuori dall'app)")
        }
        t.listId?.let { s.todoListNames[it] }?.let { append(" · lista $it") }
        t.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" — ${clip(it, 120)}") }
    }

    // spesa.md
    private fun groceryFile(): AgentMemoryFile {
        val pending = s.pendingGrocery
        val bought = s.grocery.filter { it.isPurchased && (it.purchasedAtEpochMillis ?: 0) >= now - 7 * DAY_MS }
        val body = buildString {
            appendLine("# Lista della spesa")
            if (pending.isEmpty()) appendLine("Niente da comprare.") else {
                appendLine("\n## Da comprare (${pending.size})")
                pending.groupBy { it.category?.takeIf { c -> c.isNotBlank() } ?: "Altro" }.toSortedMap().forEach { (category, items) ->
                    val names = items.joinToString(", ") { item ->
                        buildString {
                            append(item.name)
                            item.quantity?.takeIf { it > 1 }?.let { append(" ×$it") }
                            s.memberName(item.createdBy)?.let { append(" (da $it)") }
                        }
                    }
                    appendLine("- [$category] $names")
                }
            }
            if (bought.isNotEmpty()) {
                appendLine("\n## Comprati negli ultimi 7 giorni")
                appendLine(bought.take(30).joinToString(", ") { it.name })
            }
        }.trimEnd()
        return AgentMemoryFile("spesa.md", "Lista della spesa", "${pending.size} articoli da comprare", body)
    }

    // note.md
    private fun notesFile(): AgentMemoryFile? {
        if (s.notes.isEmpty()) return null
        var used = 0
        val titlesOnly = mutableListOf<String>()
        val body = buildString {
            appendLine("# Note")
            s.notes.forEach { n ->
                val title = n.title.ifBlank { "(senza titolo)" }
                if (used >= NOTES_TOTAL_MAX_CHARS) {
                    titlesOnly.add(title)
                    return@forEach
                }
                val text = clip(n.body.htmlToPlainText().trim(), NOTE_BODY_MAX_CHARS)
                used += text.length
                val author = s.memberName(n.updatedBy) ?: n.updatedByName.takeIf { it.isNotBlank() }
                appendLine("\n## $title")
                appendLine("Aggiornata il ${fmtDate(n.updatedAtEpochMillis)}${author?.let { " da $it" }.orEmpty()}")
                if (text.isNotEmpty()) appendLine(text)
            }
            if (titlesOnly.isNotEmpty()) {
                appendLine("\n## Altre note (solo titolo)")
                titlesOnly.forEach { appendLine("- $it") }
            }
        }.trimEnd()
        return AgentMemoryFile("note.md", "Note", "${s.notes.size} note", body)
    }

    // spese.md
    private fun expensesFile(): AgentMemoryFile? {
        if (s.expenses.isEmpty()) return null
        fun category(e: KBExpenseEntity) = e.categoryId?.let { s.expenseCategoryNames[it] } ?: "Altro"
        val recent = s.expenses.filter { it.dateEpochMillis >= now - EXPENSE_DETAIL_DAYS * DAY_MS }
        val cal = Calendar.getInstance()
        val yearCutoff = Calendar.getInstance().apply { timeInMillis = now; add(Calendar.MONTH, -12) }.timeInMillis
        val thisYear = cal.apply { timeInMillis = now }.get(Calendar.YEAR)
        val body = buildString {
            appendLine("# Spese")
            appendLine("\n## Voci degli ultimi $EXPENSE_DETAIL_DAYS giorni (${recent.size})")
            recent.take(80).forEach { e ->
                val who = s.memberName(e.createdByUid)?.let { " — $it" }.orEmpty()
                val notes = e.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { " (${clip(it, 80)})" }.orEmpty()
                appendLine("- ${fmtDate(e.dateEpochMillis)} — ${e.title}: ${fmtEuro(e.amount)} [${category(e)}]$who$notes")
            }
            val byMonth = s.expenses.filter { it.dateEpochMillis >= yearCutoff }
                .groupBy { val c = Calendar.getInstance().apply { timeInMillis = it.dateEpochMillis }; c.get(Calendar.YEAR) * 100 + c.get(Calendar.MONTH) }
            if (byMonth.isNotEmpty()) {
                appendLine("\n## Totale per mese (ultimi 12 mesi)")
                byMonth.toSortedMap(reverseOrder()).forEach { (_, items) ->
                    appendLine("- ${monthFmt.format(Date(items.first().dateEpochMillis))}: ${fmtEuro(items.sumOf { it.amount })} (${items.size} voci)")
                }
            }
            val byCategory = s.expenses
                .filter { Calendar.getInstance().apply { timeInMillis = it.dateEpochMillis }.get(Calendar.YEAR) == thisYear }
                .groupBy(::category)
            if (byCategory.isNotEmpty()) {
                appendLine("\n## Totale per categoria nel $thisYear")
                byCategory.entries.sortedByDescending { e -> e.value.sumOf { it.amount } }.forEach { (name, items) ->
                    appendLine("- $name: ${fmtEuro(items.sumOf { it.amount })}")
                }
            }
        }.trimEnd()
        return AgentMemoryFile("spese.md", "Spese", "${recent.size} voci negli ultimi $EXPENSE_DETAIL_DAYS giorni, ${fmtEuro(recent.sumOf { it.amount })}", body)
    }

    // salute-<nome>.md
    private fun healthFiles(docAllowance: Map<String, Int>?): List<AgentMemoryFile> {
        val docsByTag = s.documents.filter { it.notes != null }.groupBy { it.notes.orEmpty() }
        val usedNames = mutableSetOf<String>()
        return s.healthPersons.map { p ->
            var fileName = healthFileName(p.name)
            if (fileName in usedNames) fileName = "salute-${slug(p.name)}-${usedNames.size + 1}.md"
            usedNames.add(fileName)
            val active = p.treatments.filter { it.isCurrentlyActive(now) }
            val past = p.treatments.filter { !it.isCurrentlyActive(now) }
            val body = buildString {
                appendLine("# Salute di ${p.name}")
                profileLines(p).forEach { appendLine(it) }
                pastTreatmentLines(past).forEach { appendLine(it) }
                append(
                    HealthContextBuilder.buildSystemPrompt(
                        subjectName = p.name,
                        subjectId = p.id,
                        exams = p.exams,
                        visits = p.visits,
                        treatments = active,
                        vaccines = p.vaccines,
                        documentsByExamId = p.exams.associate { it.id to docsByTag[ExamAttachmentTag.make(it.id)].orEmpty() },
                        documentsByVisitId = p.visits.associate { it.id to docsByTag[VisitAttachmentTag.make(it.id)].orEmpty() },
                        documentsByTreatmentId = active.associate { it.id to docsByTag[TreatmentAttachmentTag.make(it.id)].orEmpty() },
                        // Senza budget il referto va intero (la «massima accuratezza» della
                        // chat Salute); col budget decide `docAllowance`.
                        refertoMaxChars = if (docAllowance == null) null else HealthAiDocumentText.STANDARD_REFERTO_MAX_CHARS,
                        refertoMaxCharsByDocId = docAllowance,
                        healthSnapshot = p.healthSnapshot,
                        purpose = HealthContextPurpose.AGENT_MEMORY,
                    ).trimEnd(),
                )
            }
            val referti = s.documents.count { doc ->
                val tag = doc.notes ?: return@count false
                doc.hasText() && (
                    p.exams.any { tag == ExamAttachmentTag.make(it.id) } ||
                        p.visits.any { tag == VisitAttachmentTag.make(it.id) } ||
                        active.any { tag == TreatmentAttachmentTag.make(it.id) }
                    )
            }
            AgentMemoryFile(
                name = fileName,
                title = "Salute di ${p.name}",
                summary = "${active.size} cure attive, ${p.visits.size} visite, ${p.exams.size} esami, ${p.vaccines.size} vaccini, $referti referti letti",
                body = body,
            )
        }
    }

    private fun profileLines(p: AgentHealthPerson): List<String> = buildList {
        add("\n--- PROFILO PERSONALE ---")
        val child = p.child
        if (child != null) {
            child.birthDateEpochMillis?.let { add("Data di nascita: ${fmtDate(it)} (${ageLabel(it)})") }
            child.weightKg?.let { add("Peso: ${fmtNumber(it, 1)} kg") }
            child.heightCm?.let { add("Altezza: ${fmtNumber(it)} cm") }
        } else {
            add("Adulto della famiglia")
        }
        p.profile?.let { prof ->
            prof.bloodGroup?.takeIf { it.isNotBlank() }?.let { add("Gruppo sanguigno: $it") }
            prof.allergies?.takeIf { it.isNotBlank() }?.let { add("Allergie: $it") }
            prof.medicalNotes?.takeIf { it.isNotBlank() }?.let { add("Note mediche: $it") }
            prof.doctorName?.takeIf { it.isNotBlank() }?.let { add("${if (child == null) "Medico" else "Pediatra"}: $it") }
            prof.doctorPhone?.takeIf { it.isNotBlank() }?.let { add("Telefono del medico: $it") }
            prof.doctorEmail?.takeIf { it.isNotBlank() }?.let { add("Email del medico: $it") }
            prof.doctorAddress?.takeIf { it.isNotBlank() }?.let { add("Studio: $it") }
        }
    }

    private fun pastTreatmentLines(past: List<KBTreatment>): List<String> {
        val recent = past.filter { it.startDateEpochMillis >= now - 730 * DAY_MS }.sortedByDescending { it.startDateEpochMillis }
        if (recent.isEmpty()) return emptyList()
        return listOf("\n--- CURE CONCLUSE (ultimi 2 anni) ---") + recent.take(20).map { t ->
            buildString {
                append("• ${t.drugName} ${fmtNumber(t.dosageValue)} ${t.dosageUnit} — dal ${fmtDate(t.startDateEpochMillis)}")
                t.endDateEpochMillis?.let { append(" al ${fmtDate(it)}") }
                t.notes?.takeIf { it.isNotBlank() }?.let { append(" — ${clip(it, 80)}") }
            }
        }
    }

    // documenti.md
    private fun homeOf(doc: KBDocumentEntity): DocHome {
        val tag = doc.notes ?: return DocHome.General
        if (tag.startsWith(WalletDocumentMetadata.NOTES_PREFIX)) {
            val kind = s.walletIdentity.firstOrNull { it.first.id == doc.id }?.second?.kind?.displayName ?: "Documento"
            return DocHome.Wallet(kind)
        }
        val colon = tag.indexOf(':').takeIf { it > 0 } ?: return DocHome.General
        val prefix = tag.substring(0, colon)
        val id = tag.substring(colon + 1)
        return when (prefix) {
            "visit" -> s.allVisits.firstOrNull { it.id == id }?.let { v ->
                DocHome.Health(v.childId, "visita del ${fmtDate(v.dateEpochMillis)}${v.reason.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()}")
            } ?: DocHome.General
            "exam" -> s.healthPersons.asSequence().flatMap { it.exams }.firstOrNull { it.id == id }?.let { DocHome.Health(it.childId, "esame ${it.name}") }
                ?: DocHome.General
            "treatment" -> s.healthPersons.asSequence().flatMap { it.treatments }.firstOrNull { it.id == id }?.let { DocHome.Health(it.childId, "cura ${it.drugName}") }
                ?: DocHome.General
            "homeItem" -> DocHome.Home(s.homeItems.firstOrNull { it.id == id }?.name ?: "oggetto di casa")
            "housePayment" -> DocHome.Home(s.housePayments.firstOrNull { it.id == id }?.name ?: "scadenza di casa")
            "vehicle" -> DocHome.Vehicle(s.vehicles.firstOrNull { it.id == id }?.name ?: "veicolo")
            "vehicleEvent" -> DocHome.Vehicle(s.vehicleEvents.firstOrNull { it.id == id }?.title ?: "intervento")
            "pet" -> DocHome.Pet(s.pets.firstOrNull { it.id == id }?.name ?: "animale")
            "petEvent" -> DocHome.Pet(s.petEvents.firstOrNull { it.id == id }?.title ?: "evento veterinario")
            "expense" -> DocHome.Expense(s.expenses.firstOrNull { it.id == id }?.title ?: "spesa")
            else -> DocHome.General
        }
    }

    private fun personIdOf(doc: KBDocumentEntity, home: DocHome): String? =
        (home as? DocHome.Health)?.personId ?: doc.childId

    private fun placeLabel(doc: KBDocumentEntity, home: DocHome): String = when (home) {
        is DocHome.Health -> "Salute di ${s.personName(home.personId) ?: "—"}, ${home.label}"
        is DocHome.Home -> "Casa, ${home.label}"
        is DocHome.Vehicle -> "Veicoli, ${home.label}"
        is DocHome.Pet -> "Animali, ${home.label}"
        is DocHome.Expense -> "ricevuta della spesa «${home.label}»"
        is DocHome.Wallet -> "Wallet, ${home.kind}"
        DocHome.General -> doc.categoryId?.let { s.documentCategoryNames[it] }?.let { "cartella $it" } ?: "Documenti"
    }

    private fun documentsFile(docAllowance: Map<String, Int>?): AgentMemoryFile {
        val docs = s.documents
        var clipped = 0
        var omitted = 0
        val general = textDocuments().filter { homeOf(it.doc).let { h -> h is DocHome.General || h is DocHome.Expense } }
        val body = buildString {
            appendLine("# Documenti")
            appendLine("\n## Elenco (${docs.size}, i più recenti prima)")
            docs.take(DOCUMENT_INDEX_MAX).forEach { doc ->
                val home = homeOf(doc)
                append("- ${doc.title} — ${placeLabel(doc, home)}")
                if (home == DocHome.General) s.personName(doc.childId)?.let { append(", di $it") }
                append(" · ${fmtDate(doc.createdAtEpochMillis)} · ${fileKind(doc)}")
                when {
                    home is DocHome.Wallet -> Unit // il testo dei documenti d'identità non entra mai
                    doc.hasText() -> append(" · testo letto")
                    doc.extractionStatusRaw == KBTextExtractionStatus.PENDING.rawValue ||
                        doc.extractionStatusRaw == KBTextExtractionStatus.PROCESSING.rawValue -> append(" · lettura in corso")
                }
                appendLine()
            }
            if (docs.size > DOCUMENT_INDEX_MAX) appendLine("(Altri ${docs.size - DOCUMENT_INDEX_MAX} documenti più vecchi non elencati.)")
            // Testo dei documenti senza una scheda propria: gli allegati di salute,
            // casa, veicoli e animali compaiono nelle loro schede.
            if (general.isNotEmpty()) {
                appendLine("\n## Testo letto dei documenti")
                general.forEach { item ->
                    val text = documentText(item.doc, docAllowance)
                    when (text.second) {
                        TextState.CLIPPED -> clipped++
                        TextState.OMITTED -> omitted++
                        TextState.FULL -> Unit
                    }
                    appendLine("\n### ${item.doc.title} (${item.place}, ${fmtDate(item.doc.createdAtEpochMillis)})")
                    appendLine(text.first)
                }
            }
        }.trimEnd()
        val read = docs.count { it.hasText() }
        var summary = "${docs.size} documenti"
        if (read > 0) summary += ", $read con testo letto"
        if (clipped + omitted > 0) summary += " (qui: $clipped testi accorciati e $omitted non inclusi per spazio)"
        return AgentMemoryFile("documenti.md", "Documenti", summary, body)
    }

    private enum class TextState { FULL, CLIPPED, OMITTED }

    private fun documentText(doc: KBDocumentEntity, allowance: Map<String, Int>?): Pair<String, TextState> {
        val clean = HealthAiDocumentText.sanitizeExtractedText(doc.extractedText.orEmpty())
        val max = allowance?.get(doc.id) ?: return clean to TextState.FULL
        if (max <= 0) return "(testo letto non incluso per spazio)" to TextState.OMITTED
        if (clean.length <= max) return clean to TextState.FULL
        return "${clean.take(max)}\n[… testo accorciato: $max caratteri su ${clean.length}]" to TextState.CLIPPED
    }

    private fun attachmentLines(tag: String, allowance: Map<String, Int>?, indent: String = "  "): List<String> =
        s.documents.filter { it.notes == tag && it.hasText() }.flatMap { doc ->
            val text = documentText(doc, allowance).first
            listOf("${indent}Allegato «${doc.title}» — testo letto:") +
                text.lines().filter { it.isNotBlank() }.map { "$indent  $it" }
        }

    private fun fileKind(doc: KBDocumentEntity): String = when {
        doc.mimeType.contains("pdf", ignoreCase = true) -> "PDF"
        doc.mimeType.startsWith("image/") -> "immagine"
        else -> doc.fileName.substringAfterLast('.', "").uppercase().ifBlank { "file" }
    }

    // wallet.md
    private fun walletFile(): AgentMemoryFile? {
        if (s.walletTickets.isEmpty() && s.walletIdentity.isEmpty() && s.loyaltyCards.isEmpty()) return null
        val body = buildString {
            appendLine("# Wallet")
            if (s.walletTickets.isNotEmpty()) {
                appendLine("\n## Biglietti e prenotazioni")
                s.walletTickets.sortedBy { it.eventDateEpochMillis ?: Long.MAX_VALUE }.take(30).forEach { t ->
                    append("- ${t.title} [${t.kindRaw}]")
                    t.eventDateEpochMillis?.let { append(" — ${fmtDateTime(it)}") }
                    t.eventEndDateEpochMillis?.let { append(" → ${fmtDateTime(it)}") }
                    t.location?.takeIf { it.isNotBlank() }?.let { append(" — da/luogo: $it") }
                    t.arrivalLocation?.takeIf { it.isNotBlank() }?.let { append(" — a: $it") }
                    t.seat?.takeIf { it.isNotBlank() }?.let { append(" — posto $it") }
                    t.holderName?.takeIf { it.isNotBlank() }?.let { append(" — intestato a $it") }
                    t.emitter?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                    t.bookingCode?.takeIf { it.isNotBlank() }?.let { append(" — prenotazione $it") }
                    t.price?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                    appendLine()
                }
            }
            if (s.walletIdentity.isNotEmpty()) {
                appendLine("\n## Documenti d'identità (numeri e codici esclusi di proposito)")
                s.walletIdentity.forEach { (_, meta) ->
                    meta ?: return@forEach
                    append("- ${meta.kind.displayName}")
                    meta.holderName?.takeIf { it.isNotBlank() }?.let { append(" di $it") }
                    meta.effectiveExpiryDate?.let { exp ->
                        append(" — scade il ${fmtLocalDate(exp)}")
                        if (exp.isBefore(LocalDate.now())) append(" ⚠️ SCADUTO")
                    }
                    if (meta.patenteCategories.isNotEmpty()) append(" — categorie ${meta.patenteCategories.joinToString(", ") { it.code }}")
                    appendLine()
                }
            }
            if (s.loyaltyCards.isNotEmpty()) {
                appendLine("\n## Carte fedeltà")
                appendLine(s.loyaltyCards.map { it.brandName }.sorted().joinToString(", "))
            }
        }.trimEnd()
        return AgentMemoryFile(
            "wallet.md", "Wallet",
            "${s.walletTickets.size} biglietti, ${s.walletIdentity.size} documenti d'identità, ${s.loyaltyCards.size} carte fedeltà",
            body,
        )
    }

    // casa.md
    private fun homeFile(docAllowance: Map<String, Int>?): AgentMemoryFile? {
        if (s.homeItems.isEmpty() && s.housePayments.isEmpty()) return null
        val body = buildString {
            appendLine("# Casa")
            if (s.homeItems.isNotEmpty()) {
                appendLine("\n## Oggetti, impianti e contratti (${s.homeItems.size})")
                s.homeItems.sortedBy { it.name }.take(40).forEach { h ->
                    append("- ${h.name} [${homeCategoryLabelIt(h.category)}]")
                    listOfNotNull(h.brand?.trim()?.takeIf { it.isNotEmpty() }, h.model?.trim()?.takeIf { it.isNotEmpty() })
                        .joinToString(" ").takeIf { it.isNotBlank() }?.let { append(" — $it") }
                    h.purchaseDate?.let { append(" — acquisto ${fmtDate(it)}") }
                    h.warrantyExpiryDate?.let { append(" — garanzia fino al ${fmtDate(it)}${if (it < now) " (scaduta)" else ""}") }
                    h.nextServiceDate?.let { append(" — prossima manutenzione ${fmtDate(it)}") }
                    h.servicePeriodMonths?.let { append(" — ogni $it mesi") }
                    h.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" — note: ${clip(it, 120)}") }
                    appendLine()
                    attachmentLines(HomeItemAttachmentTagPrefix + h.id, docAllowance).forEach { appendLine(it) }
                }
            }
            if (s.housePayments.isNotEmpty()) {
                appendLine("\n## Scadenze e pagamenti (${s.housePayments.size})")
                s.housePayments.sortedBy { it.name }.take(40).forEach { p ->
                    append("- ${p.name} — ${housePaymentTypeLabelIt(p.typeRaw)}")
                    p.subtypeRaw?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" ($it)") }
                    p.importo?.let { append(" — ${fmtEuro(it)}") }
                    p.giornoDiScadenzaMensile?.let { append(" — ogni mese il giorno $it") }
                    p.dataScadenza?.let { append(" — scadenza di riferimento ${fmtDate(it)}") }
                    p.dataScadenzaContratto?.let { append(" — contratto fino al ${fmtDate(it)}") }
                    p.fornitore?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" — gestore $it") }
                    HousePaymentDeadlineCalculator.earliestDisplayDeadlineMillis(p, now)?.let { append(" — prossima scadenza ${fmtDate(it)}") }
                    p.note?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" — note: ${clip(it, 120)}") }
                    appendLine()
                    attachmentLines(HousePaymentAttachmentTagPrefix + p.id, docAllowance).forEach { appendLine(it) }
                }
            }
        }.trimEnd()
        return AgentMemoryFile("casa.md", "Casa", "${s.homeItems.size} oggetti, ${s.housePayments.size} scadenze e pagamenti", body)
    }

    // veicoli.md
    private fun vehiclesFile(docAllowance: Map<String, Int>?): AgentMemoryFile? {
        if (s.vehicles.isEmpty()) return null
        val body = buildString {
            appendLine("# Veicoli")
            s.vehicles.sortedBy { it.name }.forEach { v ->
                appendLine("\n## ${v.name}")
                listOfNotNull(
                    v.licensePlate?.takeIf { it.isNotBlank() }?.let { "targa $it" },
                    listOfNotNull(v.brand?.trim()?.takeIf { it.isNotEmpty() }, v.model?.trim()?.takeIf { it.isNotEmpty() })
                        .joinToString(" ").takeIf { it.isNotBlank() },
                    v.year?.let { "anno $it" },
                    v.currentKm?.let { "$it km" },
                ).takeIf { it.isNotEmpty() }?.let { appendLine(it.joinToString(" · ")) }
                listOf(
                    "Assicurazione" to v.insuranceExpiryDate,
                    "Revisione" to v.revisionExpiryDate,
                    "Bollo" to v.taxExpiryDate,
                    "Tagliando" to v.nextServiceDate,
                ).forEach { (label, date) -> date?.let { appendLine("- $label: ${fmtDate(it)}${if (it < now) " ⚠️ PASSATA" else ""}") } }
                v.lastServiceDate?.let { appendLine("- Ultimo tagliando: ${fmtDate(it)}") }
                v.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { appendLine("Note: ${clip(it, 200)}") }
                attachmentLines(VehicleAttachmentTagPrefix + v.id, docAllowance).forEach { appendLine(it) }
                val events = s.vehicleEvents.filter { it.vehicleId == v.id && it.date >= now - 730 * DAY_MS }.sortedByDescending { it.date }
                if (events.isNotEmpty()) {
                    appendLine("Interventi (ultimi 2 anni):")
                    events.take(30).forEach { ev ->
                        append("- ${fmtDate(ev.date)} ${ev.title} — ${vehicleEventTypeLabelIt(ev.eventType)}")
                        ev.km?.let { append(" — $it km") }
                        ev.cost?.let { append(" — ${fmtEuro(it)}") }
                        ev.garageName?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" — $it") }
                        ev.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" — ${clip(it, 100)}") }
                        appendLine()
                        attachmentLines(VehicleEventAttachmentTagPrefix + ev.id, docAllowance).forEach { appendLine(it) }
                    }
                }
            }
        }.trimEnd()
        return AgentMemoryFile("veicoli.md", "Veicoli", "${s.vehicles.size} veicoli, ${s.vehicleEvents.size} interventi", body)
    }

    // animali.md
    private fun petsFile(docAllowance: Map<String, Int>?): AgentMemoryFile? {
        if (s.pets.isEmpty()) return null
        val body = buildString {
            appendLine("# Animali")
            s.pets.sortedBy { it.name }.forEach { p ->
                appendLine("\n## ${p.name}")
                appendLine(
                    listOfNotNull(
                        speciesLabelIt(p.species),
                        p.breed?.takeIf { it.isNotBlank() },
                        p.birthDate?.let { "nato/a il ${fmtDate(it)}" },
                        p.color?.takeIf { it.isNotBlank() },
                        p.chipCode?.takeIf { it.isNotBlank() }?.let { "microchip $it" },
                    ).joinToString(" · "),
                )
                p.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { appendLine("Note: ${clip(it, 200)}") }
                attachmentLines(PetAttachmentTagPrefix + p.id, docAllowance).forEach { appendLine(it) }
                val cures = s.petTreatments.filter { it.petId == p.id && it.isCurrentlyActive(now) }
                if (cures.isNotEmpty()) {
                    appendLine("Cure in corso: " + cures.joinToString(", ") { "${it.drugName} ${fmtNumber(it.dosageValue)} ${it.dosageUnit}" })
                }
                val events = s.petEvents.filter { it.petId == p.id }.sortedByDescending { it.date }
                if (events.isNotEmpty()) {
                    appendLine("Eventi:")
                    events.take(30).forEach { ev ->
                        append("- ${fmtDate(ev.date)} ${ev.title} — ${petEventTypeLabelIt(ev.eventType)}")
                        ev.nextDueDate?.let { append(" — prossimo ${fmtDate(it)}") }
                        ev.vetName?.takeIf { it.isNotBlank() }?.let { append(" — $it") }
                        ev.cost?.let { append(" — ${fmtEuro(it)}") }
                        ev.notes?.trim()?.takeIf { it.isNotEmpty() }?.let { append(" — ${clip(it, 100)}") }
                        appendLine()
                        attachmentLines(PetEventAttachmentTagPrefix + ev.id, docAllowance).forEach { appendLine(it) }
                    }
                }
            }
        }.trimEnd()
        return AgentMemoryFile("animali.md", "Animali", "${s.pets.size} animali, ${s.petEvents.size} eventi", body)
    }

    // viaggi.md
    private fun tripsFile(): AgentMemoryFile? {
        if (s.trips.isEmpty()) return null
        val cutoff = now - 60 * DAY_MS
        val relevant = s.trips.filter { it.endDateEpoch >= cutoff }.sortedBy { it.startDateEpoch }
        val older = s.trips.filter { it.endDateEpoch < cutoff }.sortedByDescending { it.startDateEpoch }
        val body = buildString {
            appendLine("# Viaggi")
            relevant.take(8).forEach { trip ->
                appendLine("\n## ${trip.name}")
                append("Dal ${fmtDate(trip.startDateEpoch)} al ${fmtDate(trip.endDateEpoch)} — stato: ${trip.statusRaw}")
                if (trip.budgetTotal > 0) append(" — budget ${fmtNumber(trip.budgetTotal)} ${trip.currency}")
                appendLine()
                s.tripLegs.filter { it.tripId == trip.id }.sortedBy { it.order }.forEach { leg ->
                    append("- Tappa: ${leg.fromLocation} → ${leg.toLocation} (${transportLabel(leg.transportModeRaw)})")
                    leg.departureAtEpoch?.let { append(" partenza ${fmtDateTime(it)}") }
                    appendLine()
                }
                s.tripDays.filter { it.tripId == trip.id }.sortedBy { it.dateString }.take(21).forEach { day ->
                    append("- ${day.dateString} a ${day.location}")
                    val plan = listOf(day.morningPlan, day.afternoonPlan, day.eveningPlan).filter { it.isNotBlank() }.map { clip(it, 120) }
                    if (plan.isNotEmpty()) append(": ${plan.joinToString(" / ")}")
                    day.accommodationName?.takeIf { it.isNotBlank() }?.let { append(" — alloggio $it") }
                    appendLine()
                }
            }
            if (older.isNotEmpty()) {
                appendLine("\n## Viaggi passati")
                older.take(20).forEach { appendLine("- ${it.name}: ${fmtDate(it.startDateEpoch)} – ${fmtDate(it.endDateEpoch)}") }
            }
        }.trimEnd()
        return AgentMemoryFile("viaggi.md", "Viaggi", "${relevant.size} in corso o in programma, ${older.size} passati", body)
    }

    // chat.md
    private fun chatFile(): AgentMemoryFile? {
        val texts = s.chat.mapNotNull { m ->
            val body = when (m.typeRaw) {
                "text" -> m.text
                "audio" -> m.transcriptText?.let { "(vocale) $it" }
                else -> null
            }?.trim()?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            "- [${fmtDateTime(m.createdAtEpochMillis)}] ${m.senderName}: ${clip(body, 300)}"
        }
        if (texts.isEmpty()) return null
        val last = texts.takeLast(CHAT_MAX_MESSAGES)
        val body = (listOf("# Chat di famiglia", "Ultimi ${last.size} messaggi di testo, dal più vecchio.") + last).joinToString("\n")
        return AgentMemoryFile("chat.md", "Chat di famiglia", "ultimi ${last.size} messaggi", body)
    }

    // Formattazione

    private fun KBDocumentEntity.hasText(): Boolean =
        extractionStatusRaw == KBTextExtractionStatus.COMPLETED.rawValue && !extractedText.isNullOrBlank()

    private fun clip(text: String, max: Int): String = if (text.length > max) text.take(max) + "…" else text
    private fun fmtDate(millis: Long): String = dateFmt.format(Date(millis))
    private fun fmtDateTime(millis: Long): String = dateTimeFmt.format(Date(millis))
    private fun fmtLocalDate(date: LocalDate): String = fmtDate(date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli())
    private fun fmtNumber(value: Double, decimals: Int = 0): String =
        if (decimals == 0 || value == Math.floor(value)) "%.0f".format(locale, value) else "%.${decimals}f".format(locale, value)
    private fun fmtEuro(value: Double): String = "€ " + "%.2f".format(locale, value)

    private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = millis
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun ageLabel(birth: Long): String {
        val b = Calendar.getInstance().apply { timeInMillis = birth }
        val n = Calendar.getInstance().apply { timeInMillis = now }
        var months = (n.get(Calendar.YEAR) - b.get(Calendar.YEAR)) * 12 + (n.get(Calendar.MONTH) - b.get(Calendar.MONTH))
        if (n.get(Calendar.DAY_OF_MONTH) < b.get(Calendar.DAY_OF_MONTH)) months -= 1
        months = months.coerceAtLeast(0)
        return if (months < 24) "$months mesi" else "${months / 12} anni"
    }

    /** Data del prossimo compleanno (oggi compreso) e anni che compie. */
    private fun nextBirthday(birth: Long): Pair<Long, Int> {
        val b = Calendar.getInstance().apply { timeInMillis = birth }
        val today = startOfDay(now)
        val next = Calendar.getInstance().apply {
            timeInMillis = today
            // Prima il giorno 1: il 31 di oggi spostato a febbraio scivolerebbe a marzo.
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.MONTH, b.get(Calendar.MONTH))
            set(Calendar.DAY_OF_MONTH, minOf(b.get(Calendar.DAY_OF_MONTH), getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        if (next.timeInMillis < today) next.add(Calendar.YEAR, 1)
        return next.timeInMillis to (next.get(Calendar.YEAR) - b.get(Calendar.YEAR))
    }

    private fun categoryLabel(raw: String): String = when (raw) {
        "children" -> "figli"
        "school" -> "scuola"
        "health" -> "salute"
        "family" -> "famiglia"
        "admin" -> "burocrazia"
        "leisure" -> "tempo libero"
        else -> raw
    }

    private fun recurrenceLabel(raw: String): String? = when (raw) {
        "daily" -> "ogni giorno"
        "weekly" -> "ogni settimana"
        "monthly" -> "ogni mese"
        "yearly" -> "ogni anno"
        else -> null
    }

    private fun transportLabel(raw: String): String = when (raw) {
        "flight" -> "aereo"
        "train" -> "treno"
        "ship" -> "nave"
        "car" -> "auto"
        "walk" -> "a piedi"
        "bike" -> "bici"
        else -> raw
    }

    companion object {
        private const val CALENDAR_PAST_DAYS = 7L
        private const val CALENDAR_FUTURE_DAYS = 60L
        private const val CALENDAR_MAX_OCCURRENCES = 150
        private const val NOTE_BODY_MAX_CHARS = 1_500
        private const val NOTES_TOTAL_MAX_CHARS = 20_000
        private const val EXPENSE_DETAIL_DAYS = 90L
        private const val CHAT_MAX_MESSAGES = 30
        private const val DOCUMENT_INDEX_MAX = 300

        // Prefissi dei tag allegato (`notes` del documento), come su iOS.
        private const val HomeItemAttachmentTagPrefix = "homeItem:"
        private const val HousePaymentAttachmentTagPrefix = "housePayment:"
        private const val VehicleAttachmentTagPrefix = "vehicle:"
        private const val VehicleEventAttachmentTagPrefix = "vehicleEvent:"
        private const val PetAttachmentTagPrefix = "pet:"
        private const val PetEventAttachmentTagPrefix = "petEvent:"

        /** Nome della scheda salute di una persona (`salute-marco.md`). */
        fun healthFileName(name: String): String = "salute-${slug(name)}.md"

        fun slug(name: String): String {
            val folded = AgentRelevance.fold(name)
            val mapped = folded.map { if (it.isLetterOrDigit()) it else '-' }.joinToString("")
            return mapped.split('-').filter { it.isNotEmpty() }.joinToString("-").ifEmpty { "persona" }
        }
    }
}

// ── Prompt ───────────────────────────────────────────────────────────────────

object AgentPrompt {

    /** «venerdì 2 ottobre»: le regole sono in italiano, la data anche. */
    private fun promptDay(millis: Long): String =
        SimpleDateFormat("EEEE d MMMM", java.util.Locale.ITALIAN).format(Date(millis))

    /** Col calendario, non +24 ore: la notte del cambio d'ora «domani» sarebbe ancora oggi. */
    private fun tomorrow(millis: Long): Long =
        Calendar.getInstance().apply { timeInMillis = millis; add(Calendar.DAY_OF_MONTH, 1) }.timeInMillis

    /** Ruolo e regole dell'assistente unico. Stesso testo su iOS e web. */
    fun rules(familyName: String, now: Long = System.currentTimeMillis()): String = """
Sei l'assistente di KidBox della famiglia $familyName. KidBox è l'app in cui la famiglia tiene calendario, to-do, lista della spesa, note, spese, documenti, salute, wallet, casa, veicoli, animali e viaggi.
Qui sotto c'è la tua memoria: una scheda per ogni sezione dell'app, aggiornata adesso, con un indice in cima.

COME RISPONDI
- Usa i dati delle schede; quando aiuta, di' da dove li prendi («dal referto del 12 marzo…», «nella scheda Casa…»).
- Se un dato non c'è, dillo chiaramente: non inventare date, importi, nomi, valori o documenti.
- Se l'indice o una scheda dice che un testo è accorciato o non incluso e la domanda riguarda proprio quello, dillo e suggerisci di ripetere la domanda con la massima accuratezza.
- Salute: linguaggio semplice, adatto a un genitore. Puoi spiegare referti, valori, cure, vaccini e visite; non fare diagnosi e non cambiare terapie: quando la questione è clinica, dopo aver risposto ricorda di sentire il medico.
- Pianificazione: aiuta a trovare spazi liberi e a non dimenticare scadenze; quando proponi un evento o un to-do indica titolo, data/ora e chi se ne occupa.
- Le password non sono nella tua memoria: se te le chiedono, rimanda alla sezione Password dell'app.
- Date: oggi è ${promptDay(now)}, domani è ${promptDay(tomorrow(now))}. Prima di dire «domani», «dopodomani» o un giorno della settimana controlla la data scritta nelle schede: non contare a memoria.
- Rispondi in ${MealPlanPromptBuilder.responseLanguageName()}, con tono caldo e pratico, senza preamboli.
    """.trimIndent()

    /**
     * Il focus va due volte, prima e dopo il quaderno: provato il 02/10/2026 su
     * Haiku, solo in fondo «cosa devo fare adesso?» aperto da una visita tornava
     * una volta su due con le cose della famiglia; in cima e in fondo, 4 su 4.
     */
    fun systemPrompt(familyName: String, book: AgentMemoryBook, focus: AgentFocus?): String =
        listOfNotNull(
            rules(familyName),
            focus?.promptLine,
            book.rendered,
            PlanningAIActionBlock.promptSection,
            focus?.promptLine,
        ).joinToString("\n\n")
}

// ── Fitting ──────────────────────────────────────────────────────────────────

/**
 * Divide un budget di caratteri fra i testi letti dei documenti quando il
 * quaderno completo non sta in un messaggio. Ordine: allegati della visita o
 * dell'esame del focus, poi i documenti pertinenti alla domanda o alla persona
 * del focus, poi gli altri dal più recente.
 */
object AgentContextFitter {
    const val FOCUS_ITEM_MAX_CHARS = 12_000
    const val RELEVANT_MAX_CHARS = HealthAiDocumentText.STANDARD_REFERTO_MAX_CHARS
    const val OTHER_MAX_CHARS = 1_500
    private const val PER_DOC_OVERHEAD = 150
    private const val MIN_USEFUL_CHARS = 300

    fun allowances(
        textDocuments: List<AgentTextDocument>,
        question: String,
        focus: AgentFocus?,
        personNames: Map<String, String>,
        availableChars: Int,
    ): Map<String, Int> {
        val terms = AgentRelevance.terms(question)
        val foldedQuestion = AgentRelevance.fold(question)
        val mentioned = personNames.filter { (_, name) ->
            val f = AgentRelevance.fold(name)
            f.length >= 3 && foldedQuestion.contains(f)
        }.keys
        val focusTags = focus?.itemTags.orEmpty()

        data class Candidate(val item: AgentTextDocument, val tier: Int, val score: Int, val cap: Int)
        val ordered = textDocuments.map { item ->
            if (item.doc.notes != null && item.doc.notes in focusTags) {
                Candidate(item, 0, 0, FOCUS_ITEM_MAX_CHARS)
            } else {
                var score = AgentRelevance.score(item, terms)
                item.personId?.let { pid ->
                    if (pid in mentioned) score += 2
                    if (pid == focus?.personId) score += 1
                }
                if (score > 0) Candidate(item, 1, score, RELEVANT_MAX_CHARS) else Candidate(item, 2, 0, OTHER_MAX_CHARS)
            }
        }.sortedWith(
            compareBy<Candidate> { it.tier }
                .thenByDescending { it.score }
                .thenByDescending { it.item.doc.updatedAtEpochMillis },
        )

        var remaining = availableChars
        val out = mutableMapOf<String, Int>()
        for (c in ordered) {
            val room = remaining - PER_DOC_OVERHEAD
            if (room < MIN_USEFUL_CHARS) {
                out[c.item.doc.id] = 0
                continue
            }
            val give = minOf(c.item.fullLength, c.cap, room)
            out[c.item.doc.id] = give
            remaining -= give + PER_DOC_OVERHEAD
        }
        // Secondo giro: lo spazio che avanza va ai testi ancora accorciati, nello
        // stesso ordine, fino al testo intero. Il messaggio costa uguale.
        for (c in ordered) {
            if (remaining <= 0) break
            val id = c.item.doc.id
            val given = out[id] ?: 0
            if (given >= c.item.fullLength) continue
            if (given == 0) {
                val room = remaining - PER_DOC_OVERHEAD
                if (room < MIN_USEFUL_CHARS) continue
                val give = minOf(c.item.fullLength, room)
                out[id] = give
                remaining -= give + PER_DOC_OVERHEAD
            } else {
                val extra = minOf(c.item.fullLength - given, remaining)
                out[id] = given + extra
                remaining -= extra
            }
        }
        return out
    }
}

/** Pertinenza di un documento a una domanda: parole in comune, senza accenti. */
object AgentRelevance {
    /** Parole di 4+ lettere che non dicono niente sul documento cercato. */
    private val stopwords = setOf(
        "della", "delle", "degli", "dello", "dalla", "dalle", "dagli", "nella", "nelle", "negli", "nello",
        "sulla", "sulle", "sugli", "sullo", "alla", "alle", "agli", "allo", "questo", "questa", "questi",
        "queste", "quello", "quella", "quelli", "quelle", "come", "cosa", "cose", "dove", "quando", "quanto",
        "quanti", "quante", "quale", "quali", "perche", "sono", "siamo", "hanno", "abbiamo", "avete", "fare",
        "fatto", "fatta", "dire", "detto", "anche", "ancora", "sempre", "dopo", "prima", "oggi", "domani",
        "ieri", "ogni", "tutto", "tutti", "tutte", "tutta", "molto", "poco", "meno", "essere", "stato",
        "stata", "stati", "ultimo", "ultima", "ultimi", "ultime", "prossimo", "prossima", "prossimi",
        "prossime", "miei", "nostro", "nostra", "nostri", "nostre", "loro", "suoi", "dimmi", "fammi",
        "spiegami", "ricordami", "riassumi", "riassumimi", "vorrei", "posso", "puoi", "devo", "deve",
        "serve", "servono", "documento", "documenti", "what", "when", "where", "which", "with",
        "about", "have", "does", "this", "that", "there", "their", "from", "please", "dans", "pour",
        "avec", "quel", "quelle", "sont", "cual", "como", "donde", "cuando", "para", "sobre", "tiene",
    )

    fun fold(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase(java.util.Locale.ROOT)

    fun terms(question: String): Set<String> =
        fold(question).split(Regex("[^\\p{L}\\p{N}]+")).filter { it.length >= 4 && it !in stopwords }.toSet()

    fun score(item: AgentTextDocument, terms: Set<String>): Int {
        if (terms.isEmpty()) return 0
        val head = fold("${item.doc.title} ${item.doc.fileName} ${item.place}")
        val body = fold(item.doc.extractedText.orEmpty().take(30_000))
        var score = 0
        for (term in terms) {
            score += when {
                head.contains(term) -> 2
                body.contains(term) -> 1
                else -> 0
            }
        }
        return score
    }
}
