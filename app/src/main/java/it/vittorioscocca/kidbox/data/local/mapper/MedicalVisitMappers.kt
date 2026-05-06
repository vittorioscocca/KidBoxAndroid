package it.vittorioscocca.kidbox.data.local.mapper

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.domain.model.KBAsNeededDrug
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.domain.model.KBTherapyType
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

// ── Visit Status ─────────────────────────────────────────────────────────────

enum class KBVisitStatus(val rawValue: String, val displayLabel: String, val wizardChipLabel: String) {
    PENDING("pending", "In attesa", "In attesa"),
    BOOKED("booked", "Prenotata", "Prenotata"),
    COMPLETED("completed", "Eseguite", "Eseguita"),
    RESULT_AVAILABLE("result_available", "Risultato disponibile", "Risultati"),
    UNKNOWN_STATUS("unknown", "Senza stato", "—");

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

fun encodeAsNeededDrugs(list: List<KBAsNeededDrug>): String {
    val arr = JSONArray()
    for (d in list) {
        arr.put(
            JSONObject().apply {
                put("id", d.id)
                put("drugName", d.drugName)
                put("dosageValue", d.dosageValue)
                put("dosageUnit", d.dosageUnit)
                d.instructions?.takeIf { it.isNotBlank() }?.let { put("instructions", it) }
            },
        )
    }
    return arr.toString()
}

fun decodeAsNeededDrugs(raw: String?): List<KBAsNeededDrug> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            KBAsNeededDrug(
                id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                drugName = o.optString("drugName"),
                dosageValue = o.optDouble("dosageValue", 0.0),
                dosageUnit = o.optString("dosageUnit", "mg"),
                instructions = o.optString("instructions").takeIf { it.isNotBlank() },
            )
        }
    }.getOrElse { emptyList() }
}

fun encodeTherapyTypes(types: List<KBTherapyType>): String {
    val arr = JSONArray()
    types.distinct().forEach { arr.put(it.rawValue) }
    return arr.toString()
}

fun decodeTherapyTypes(raw: String?): List<KBTherapyType> {
    if (raw.isNullOrBlank()) return emptyList()
    return runCatching {
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            val s = arr.optString(i)
            KBTherapyType.entries.firstOrNull { it.rawValue == s }
        }
    }.getOrElse { emptyList() }
}
