package it.vittorioscocca.kidbox.data.docintel

import java.io.File
import java.util.UUID

/**
 * Document Intelligence — gemello di `DocumentIntelligenceModels.swift`.
 *
 * L'AI legge un documento caricato col «+» dell'assistente, capisce cos'è
 * (bolletta, referto, analisi, ricetta…) e propone le azioni nelle sezioni
 * giuste; la scheda di conferma le lascia correggere prima di salvarle.
 */
data class DocIntelAction(
    val id: String = UUID.randomUUID().toString(),
    /**
     * expense_add, event_add, todo_add, health_reminder, note_add, vehicle_event,
     * medical_visit, exam_add, treatment_add, house_payment, vaccine_add, rename_document.
     */
    val type: String,
    val summary: String? = null,
    val title: String? = null,
    val body: String? = null,
    val notes: String? = null,
    val amount: Double? = null,
    /** ISO8601, come le restituisce l'AI. */
    val date: String? = null,
    val endDate: String? = null,
    val isAllDay: Boolean? = null,
    /** Persona dei dati sanitari: `childId` di un figlio o `userId` di un adulto. */
    val childId: String? = null,
    val vehicleId: String? = null,
    val vehicleEventType: String? = null,
    val doctorName: String? = null,
    val vaccineType: String? = null,
    val renameTo: String? = null,
    val diagnosis: String? = null,
    val recommendations: String? = null,
    val resultText: String? = null,
    val location: String? = null,
    val activeIngredient: String? = null,
    val dosageValue: Double? = null,
    val dosageUnit: String? = null,
    val dailyFrequency: Int? = null,
    val durationDays: Int? = null,
    val isLongTerm: Boolean? = null,
    val paymentType: String? = null,
    val subtype: String? = null,
    val provider: String? = null,
) {
    /** Azioni che riguardano una persona (figlio o adulto) di Salute. */
    val needsPerson: Boolean
        get() = type in setOf("medical_visit", "exam_add", "treatment_add", "vaccine_add")

    /** Azioni a cui l'originale del documento si allega come file. */
    val takesAttachment: Boolean
        get() = type in ATTACH_ORDER

    companion object {
        /** Prima scheda che riceve il file, nello stesso ordine di iOS. */
        val ATTACH_ORDER = listOf("house_payment", "medical_visit", "exam_add", "treatment_add")
    }
}

data class DocIntelResult(
    val documentType: String?,
    /** bill, medical_report, lab_results, prescription, receipt, vehicle, school, insurance, other. */
    val category: String?,
    val suggestedTitle: String?,
    val actions: List<DocIntelAction>,
)

/** Persona di Salute: figlio (`childId`) o adulto (`userId`). */
data class DocIntelPerson(val id: String, val name: String, val isAdult: Boolean)

data class DocIntelVehicle(val id: String, val label: String)

/** Cosa serve alla scheda di conferma: risultato, persone, veicoli e il file da allegare. */
data class DocIntelPayload(
    val result: DocIntelResult,
    val sourceFile: File,
    val persons: List<DocIntelPerson>,
    val vehicles: List<DocIntelVehicle>,
)
