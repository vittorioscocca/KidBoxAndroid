package it.vittorioscocca.kidbox.ui.screens.ai.planning

import it.vittorioscocca.kidbox.data.health.ai.HealthAiDocumentText
import it.vittorioscocca.kidbox.data.home.HomeItemAttachmentTag
import it.vittorioscocca.kidbox.data.home.HousePaymentAttachmentTag
import it.vittorioscocca.kidbox.data.life.HousePaymentDeadlineCalculator
import it.vittorioscocca.kidbox.data.local.entity.HomeItemEntity
import it.vittorioscocca.kidbox.data.local.entity.HousePaymentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBWalletTicketEntity
import it.vittorioscocca.kidbox.data.local.entity.PetEntity
import it.vittorioscocca.kidbox.data.local.entity.PetEventEntity
import it.vittorioscocca.kidbox.data.local.entity.VehicleEntity
import it.vittorioscocca.kidbox.data.local.entity.VehicleEventEntity
import it.vittorioscocca.kidbox.data.pets.PetEventAttachmentTag
import it.vittorioscocca.kidbox.data.vehicles.VehicleAttachmentTag
import it.vittorioscocca.kidbox.data.vehicles.VehicleEventAttachmentTag
import it.vittorioscocca.kidbox.domain.model.*
import it.vittorioscocca.kidbox.ui.screens.life.homeCategoryLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.housePaymentTypeLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.petEventTypeLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.speciesLabelIt
import it.vittorioscocca.kidbox.ui.screens.life.vehicleEventTypeLabelIt
import java.text.NumberFormat
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
    val pets: List<PetEntity> = emptyList(),
    val petEvents: List<PetEventEntity> = emptyList(),
    val homeItems: List<HomeItemEntity> = emptyList(),
    val housePayments: List<HousePaymentEntity> = emptyList(),
    val vehicles: List<VehicleEntity> = emptyList(),
    val vehicleEvents: List<VehicleEventEntity> = emptyList(),
    /** Documenti Casa / Garage / eventi animali con OCR completato (testo pronto per l'AI). */
    val lifeAreaDocuments: List<KBDocument> = emptyList(),
    /** Fatti narrativi appresi dalle conversazioni precedenti. */
    val familyMemoryFacts: List<String> = emptyList(),
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
            if (input.familyMemoryFacts.isNotEmpty()) {
                appendLine()
                appendLine("## Memoria famiglia (fatti appresi dalle conversazioni precedenti)")
                input.familyMemoryFacts.forEach { fact ->
                    appendLine("• $fact")
                }
                appendLine()
                appendLine(
                    "Usa questi fatti per personalizzare le risposte senza menzionare esplicitamente " +
                        "che li hai memorizzati, a meno che non sia rilevante.",
                )
            }
            appendLine()
            appendLine("Regole:")
            appendLine("- Aiuta i genitori a pianificare, trovare slot liberi e gestire le attività familiari")
            appendLine("- Puoi fare riferimento anche a animali domestici, oggetti di casa (garanzie, manutenzione, contratti), scadenze e pagamenti domestici (bollette, tasse, mutuo, affitto) e veicoli/interventi garage quando i dati sono presenti sotto")
            appendLine("- Non dare consigli medici vincolanti o diagnosi")
            appendLine("- Parla sempre in italiano, tono caldo e pratico")
            appendLine("- Quando proponi di creare un evento o reminder, usa ESATTAMENTE questa forma:")
            appendLine("  'Vuoi che imposti un promemoria per \"[titolo]\"?'")
            appendLine("  o 'Posso aggiungere al calendario \"[titolo evento]\"?'")
            appendLine("  o 'Posso aggiungere il to-do \"[titolo]\"?'")
            appendLine("- Proponi UNA sola azione alla volta")
            appendLine()
            appendLine(PlanningAIActionBlock.promptSection)
            appendLine()
            appendLine("## Oggi ${formatDateShort(now)}")
            appendLine("Slot ore: ${input.calendarEvents.filter { sameDay(it.startDateEpochMillis, now) }.joinToString(", ") { "${formatTime(it.startDateEpochMillis)}-${formatTime(it.endDateEpochMillis)}" }}")
            appendLine("Todo urgenti: ${input.openTodos.filter { (it.priorityRaw ?: 0) >= 2 || (it.dueAtEpochMillis ?: Long.MAX_VALUE) < now }.joinToString(", ") { it.title }.ifBlank { "Nessuno" }}")
            appendLine("Routine da completare: ${input.activeRoutines.filter { r -> input.todayChecks.none { it.routineId == r.id } }.joinToString(", ") { it.title }.ifBlank { "Nessuna" }}")
            appendLine("Dosi farmaci oggi: ${input.activeTreatments.sumOf { it.dailyFrequency }}")
            appendLine()
            appendPetsHomeGarage(input, now, horizonEnd)
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

private fun StringBuilder.appendPetsHomeGarage(
    input: PlanningContextInput,
    now: Long,
    horizonEnd: Long,
) {
    val lifeDocs = input.lifeAreaDocuments
    val msDay = 24L * 60 * 60 * 1000
    val petPastWindow = now - 180L * msDay
    val vehiclePastWindow = now - 365L * msDay
    val petNames = input.pets.associate { it.id to it.name }
    val vehNames = input.vehicles.associate { it.id to it.name }
    val euroFmt = NumberFormat.getCurrencyInstance(Locale.ITALY).apply {
        minimumFractionDigits = 0
        maximumFractionDigits = 2
    }

    if (input.pets.isNotEmpty()) {
        appendLine("## Animali")
        input.pets.take(20).forEach { p ->
            val bits = mutableListOf<String>()
            bits.add("specie: ${speciesLabelIt(p.species)}")
            p.breed?.takeIf { it.isNotBlank() }?.let { bits.add("razza: $it") }
            p.birthDate?.let { bits.add("nascita: ${PlanningContextBuilder.formatDate(it)}") }
            p.color?.takeIf { it.isNotBlank() }?.let { bits.add("colore: $it") }
            p.chipCode?.takeIf { it.isNotBlank() }?.let { bits.add("chip: $it") }
            p.notes?.takeIf { it.isNotBlank() }?.let {
                val short = if (it.length > 120) it.take(120) + "…" else it
                bits.add("note: $short")
            }
            appendLine("- ${p.name} — ${bits.joinToString(", ")}")
        }
    }

    val petEvWindow = input.petEvents.filter { ev ->
        ev.date in petPastWindow..horizonEnd ||
            (ev.nextDueDate?.let { due -> due in now..horizonEnd } == true)
    }.sortedByDescending { it.date }.take(28)

    if (petEvWindow.isNotEmpty()) {
        appendLine("## Eventi animali (finestra utile)")
        petEvWindow.forEach { ev ->
            val pet = petNames[ev.petId] ?: "animale"
            val next = ev.nextDueDate?.let { " — prossimo: ${PlanningContextBuilder.formatDate(it)}" }.orEmpty()
            val vet = ev.vetName?.takeIf { it.isNotBlank() }?.let { " — vet: $it" }.orEmpty()
            val cost = ev.cost?.let { c -> " — costo: ${euroFmt.format(c)}" }.orEmpty()
            val note = ev.notes?.trim()?.takeIf { it.isNotEmpty() }?.let {
                val s = if (it.length > 80) it.take(80) + "…" else it
                " — note: $s"
            }.orEmpty()
            appendLine(
                "- [${PlanningContextBuilder.formatDate(ev.date)}] ${ev.title} ($pet) — tipo: ${petEventTypeLabelIt(ev.eventType)}$next$vet$cost$note",
            )
            appendLifeAreaDocExtracts(lifeDocs, indent = "    ") { d -> PetEventAttachmentTag.matches(d.notes, ev.id) }
        }
    }

    if (input.homeItems.isNotEmpty()) {
        appendLine("## Casa (oggetti e manutenzione)")
        input.homeItems.take(28).forEach { h ->
            val cat = homeCategoryLabelIt(h.category)
            val bm = listOfNotNull(h.brand?.trim()?.takeIf { it.isNotEmpty() }, h.model?.trim()?.takeIf { it.isNotEmpty() })
                .joinToString(" ")
                .takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
            val sn = h.serialNumber?.trim()?.takeIf { it.isNotEmpty() }?.let { " — s/n: $it" }.orEmpty()
            val purchase = h.purchaseDate?.let { " — acquisto: ${PlanningContextBuilder.formatDate(it)}" }.orEmpty()
            val w = h.warrantyExpiryDate?.let { d ->
                val tag = when {
                    d < now -> " ⚠️ garanzia scaduta"
                    d in now..horizonEnd -> " 📅 garanzia in scadenza"
                    else -> ""
                }
                " — garanzia fino: ${PlanningContextBuilder.formatDate(d)}$tag"
            }.orEmpty()
            val s = h.nextServiceDate?.let { d ->
                val tag = if (d in now..horizonEnd) " 📅" else ""
                " — manutenzione: ${PlanningContextBuilder.formatDate(d)}$tag"
            }.orEmpty()
            val period = h.servicePeriodMonths?.let { " — periodicità: ogni $it mesi" }.orEmpty()
            val rem = if (h.reminderEnabled) " — promemoria: attivo" else ""
            val note = h.notes?.trim()?.takeIf { it.isNotEmpty() }?.let {
                val short = if (it.length > 100) it.take(100) + "…" else it
                " — note: $short"
            }.orEmpty()
            appendLine("- ${h.name} [$cat]$bm$sn$purchase$w$s$period$rem$note")
            appendLifeAreaDocExtracts(lifeDocs, indent = "    ") { d -> HomeItemAttachmentTag.matches(d.notes, h.id) }
        }
    }

    if (input.housePayments.isNotEmpty()) {
        appendLine("## Casa (scadenze e pagamenti)")
        input.housePayments.sortedBy { it.name }.take(40).forEach { p ->
            val bits = mutableListOf<String>()
            bits.add("tipo: ${housePaymentTypeLabelIt(p.typeRaw)}")
            p.subtypeRaw?.trim()?.takeIf { it.isNotEmpty() }?.let { bits.add("dettaglio: $it") }
            p.importo?.let { bits.add("importo: ${euroFmt.format(it)}") }
            p.giornoDiScadenzaMensile?.let { bits.add("giorno mensile: $it") }
            p.dataScadenza?.let { bits.add("scadenza annuale ref: ${PlanningContextBuilder.formatDate(it)}") }
            p.dataScadenzaContratto?.let { d ->
                val tag = when {
                    d < now -> " ⚠️ contratto passato"
                    d in now..horizonEnd -> " 📅"
                    else -> ""
                }
                bits.add("scadenza contratto: ${PlanningContextBuilder.formatDate(d)}$tag")
            }
            p.fornitore?.trim()?.takeIf { it.isNotEmpty() }?.let { bits.add("gestore: $it") }
            HousePaymentDeadlineCalculator.earliestDisplayDeadlineMillis(p, now)?.let { ed ->
                val tag = when {
                    ed < now -> " ⚠️"
                    ed in now..horizonEnd -> " 📅"
                    else -> ""
                }
                bits.add("prossima scadenza in agenda: ${PlanningContextBuilder.formatDate(ed)}$tag")
            }
            bits.add(if (p.reminderOn) "promemoria: sì" else "promemoria: no")
            p.note?.trim()?.takeIf { it.isNotEmpty() }?.let {
                val short = if (it.length > 100) it.take(100) + "…" else it
                bits.add("note: $short")
            }
            appendLine("- ${p.name} — ${bits.joinToString(", ")}")
            appendLifeAreaDocExtracts(lifeDocs, indent = "    ") { d -> HousePaymentAttachmentTag.matches(d.notes, p.id) }
        }
    }

    if (input.vehicles.isNotEmpty()) {
        appendLine("## Garage (veicoli)")
        input.vehicles.take(14).forEach { v ->
            val plate = v.licensePlate?.takeIf { it.isNotBlank() }?.let { " — targa $it" }.orEmpty()
            val bm = listOfNotNull(v.brand?.trim()?.takeIf { it.isNotEmpty() }, v.model?.trim()?.takeIf { it.isNotEmpty() })
                .joinToString(" ")
                .takeIf { it.isNotBlank() }?.let { " — $it" }.orEmpty()
            val year = v.year?.let { " — anno $it" }.orEmpty()
            val ins = v.insuranceExpiryDate?.let { d ->
                val tag = if (d in now..horizonEnd) " 📅" else ""
                " — assicurazione: ${PlanningContextBuilder.formatDate(d)}$tag"
            }.orEmpty()
            val rev = v.revisionExpiryDate?.let { d ->
                val tag = if (d in now..horizonEnd) " 📅" else ""
                " — revisione: ${PlanningContextBuilder.formatDate(d)}$tag"
            }.orEmpty()
            val tax = v.taxExpiryDate?.let { d ->
                val tag = if (d in now..horizonEnd) " 📅" else ""
                " — bollo: ${PlanningContextBuilder.formatDate(d)}$tag"
            }.orEmpty()
            val svc = v.nextServiceDate?.let { " — tagliando: ${PlanningContextBuilder.formatDate(it)}" }.orEmpty()
            val km = v.currentKm?.let { " — km: $it" }.orEmpty()
            val rem = if (v.reminderEnabled) " — promemoria scadenze: sì" else ""
            val note = v.notes?.trim()?.takeIf { it.isNotEmpty() }?.let {
                val short = if (it.length > 80) it.take(80) + "…" else it
                " — note: $short"
            }.orEmpty()
            appendLine("- ${v.name}$plate$bm$year$ins$rev$tax$svc$km$rem$note")
            appendLifeAreaDocExtracts(lifeDocs, indent = "    ") { d -> VehicleAttachmentTag.matches(d.notes, v.id) }
        }
    }

    val vehEvWindow = input.vehicleEvents.filter { it.date in vehiclePastWindow..horizonEnd }
        .sortedByDescending { it.date }.take(28)

    if (vehEvWindow.isNotEmpty()) {
        appendLine("## Interventi veicolo (ultimo anno + orizzonte)")
        vehEvWindow.forEach { ev ->
            val vn = vehNames[ev.vehicleId] ?: "veicolo"
            val km = ev.km?.let { " — ${it} km" }.orEmpty()
            val cost = ev.cost?.let { c -> " — costo: ${euroFmt.format(c)}" }.orEmpty()
            val garage = ev.garageName?.trim()?.takeIf { it.isNotEmpty() }?.let { " — officina: $it" }.orEmpty()
            val note = ev.notes?.trim()?.takeIf { it.isNotEmpty() }?.let {
                val s = if (it.length > 80) it.take(80) + "…" else it
                " — note: $s"
            }.orEmpty()
            appendLine(
                "- [${PlanningContextBuilder.formatDate(ev.date)}] ${ev.title} ($vn) — tipo: ${vehicleEventTypeLabelIt(ev.eventType)}$km$cost$garage$note",
            )
            appendLifeAreaDocExtracts(lifeDocs, indent = "    ") { d -> VehicleEventAttachmentTag.matches(d.notes, ev.id) }
        }
    }
}

private fun StringBuilder.appendLifeAreaDocExtracts(
    lifeDocs: List<KBDocument>,
    indent: String,
    matches: (KBDocument) -> Boolean,
) {
    val completed = lifeDocs.asSequence()
        .filter(matches)
        .filter { it.extractionStatusRaw == KBTextExtractionStatus.COMPLETED.rawValue }
        .filter { !it.extractedText.isNullOrBlank() }
    for (d in completed) {
        val body = HealthAiDocumentText.prepareExtractedTextForAi(d.extractedText)
        if (body.isBlank()) continue
        appendLine("${indent}Allegato «${d.title}» — testo estratto:")
        body.lineSequence().forEach { line ->
            if (line.isNotBlank()) {
                appendLine("$indent  $line")
            }
        }
    }
}
