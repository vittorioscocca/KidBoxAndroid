package it.vittorioscocca.kidbox.data.local.mapper

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import org.json.JSONArray

// ── Visit Status ─────────────────────────────────────────────────────────────

enum class KBVisitStatus(val rawValue: String, val displayLabel: String) {
    PENDING("pending", "In attesa"),
    BOOKED("booked", "Prenotate"),
    COMPLETED("completed", "Eseguite"),
    RESULT_AVAILABLE("result_available", "Risultato disponibile"),
    UNKNOWN_STATUS("unknown", "Senza stato");

    companion object {
        fun fromRaw(raw: String?): KBVisitStatus =
            entries.firstOrNull { it.rawValue == raw } ?: UNKNOWN_STATUS
    }
}

// ── Doctor Specialization ─────────────────────────────────────────────────────

enum class KBDoctorSpecialization(val rawValue: String) {
    PEDIATRA("Pediatra"),
    DERMATOLOGO("Dermatologo"),
    CARDIOLOGO("Cardiologo"),
    NEUROLOGO("Neurologo"),
    ORTOPEDICO("Ortopedico"),
    OCULISTA("Oculista"),
    OTORINOLARINGOIATRA("Otorinolaringoiatra"),
    PSICOLOGO("Psicologo"),
    ALTRO("Altro");

    companion object {
        fun fromRaw(raw: String?): KBDoctorSpecialization? =
            entries.firstOrNull { it.rawValue == raw }
    }
}

// ── Entity ↔ Domain ───────────────────────────────────────────────────────────

fun KBMedicalVisitEntity.toDomain(): KBMedicalVisit = KBMedicalVisit(
    id = id,
    familyId = familyId,
    childId = childId,
    dateEpochMillis = dateEpochMillis,
    doctorName = doctorName,
    doctorSpecializationRaw = doctorSpecializationRaw,
    travelDetailsJson = travelDetailsJson,
    reason = reason,
    diagnosis = diagnosis,
    recommendations = recommendations,
    linkedTreatmentIdsJson = linkedTreatmentIdsJson,
    linkedExamIdsJson = linkedExamIdsJson,
    asNeededDrugsJson = asNeededDrugsJson,
    therapyTypesJson = therapyTypesJson,
    prescribedExamsJson = prescribedExamsJson,
    photoUrlsJson = photoUrlsJson,
    notes = notes,
    nextVisitDateEpochMillis = nextVisitDateEpochMillis,
    nextVisitReason = nextVisitReason,
    visitStatusRaw = visitStatusRaw,
    reminderOn = reminderOn,
    nextVisitReminderOn = nextVisitReminderOn,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdBy = createdBy,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

fun KBMedicalVisit.toEntity(): KBMedicalVisitEntity = KBMedicalVisitEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    dateEpochMillis = dateEpochMillis,
    doctorName = doctorName,
    doctorSpecializationRaw = doctorSpecializationRaw,
    travelDetailsJson = travelDetailsJson,
    reason = reason,
    diagnosis = diagnosis,
    recommendations = recommendations,
    linkedTreatmentIdsJson = linkedTreatmentIdsJson,
    linkedExamIdsJson = linkedExamIdsJson,
    asNeededDrugsJson = asNeededDrugsJson,
    therapyTypesJson = therapyTypesJson,
    prescribedExamsJson = prescribedExamsJson,
    photoUrlsJson = photoUrlsJson,
    notes = notes,
    nextVisitDateEpochMillis = nextVisitDateEpochMillis,
    nextVisitReason = nextVisitReason,
    visitStatusRaw = visitStatusRaw,
    reminderOn = reminderOn,
    nextVisitReminderOn = nextVisitReminderOn,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdBy = createdBy,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

// ── JSON helpers ──────────────────────────────────────────────────────────────

fun encodeStringList(list: List<String>): String {
    val arr = JSONArray()
    list.forEach { arr.put(it) }
    return arr.toString()
}

fun decodeStringList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).map { arr.getString(it) }
    }.getOrElse { emptyList() }
}
