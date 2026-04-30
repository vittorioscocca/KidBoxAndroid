package it.vittorioscocca.kidbox.data.local.mapper

import it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity
import it.vittorioscocca.kidbox.domain.model.KBVaccine

// ── Enums ─────────────────────────────────────────────────────────────────────

enum class KBVaccineType(val rawValue: String) {
    MANDATORY("Obbligatorio"),
    RECOMMENDED("Raccomandato");

    companion object {
        fun fromRaw(value: String?): KBVaccineType? = entries.firstOrNull { it.rawValue == value }
    }
}

enum class KBVaccineStatus(val rawValue: String) {
    SCHEDULED("Programmato"),
    ADMINISTERED("Somministrato"),
    OVERDUE("In ritardo"),
    SKIPPED("Non eseguito");

    companion object {
        fun fromRaw(value: String?): KBVaccineStatus? = entries.firstOrNull { it.rawValue == value }
    }
}

// ── Computed status ────────────────────────────────────────────────────────────

fun KBVaccine.computedStatus(): KBVaccineStatus {
    if (administeredDateEpochMillis != null) return KBVaccineStatus.ADMINISTERED
    if (statusRaw == KBVaccineStatus.SKIPPED.rawValue) return KBVaccineStatus.SKIPPED
    val today = System.currentTimeMillis()
    if (scheduledDateEpochMillis != null && scheduledDateEpochMillis < today) return KBVaccineStatus.OVERDUE
    return KBVaccineStatus.SCHEDULED
}

// ── Entity ↔ Domain ───────────────────────────────────────────────────────────

fun KBVaccineEntity.toDomain(): KBVaccine = KBVaccine(
    id = id,
    familyId = familyId,
    childId = childId,
    name = name,
    vaccineTypeRaw = vaccineTypeRaw,
    statusRaw = statusRaw,
    commercialName = commercialName,
    doseNumber = doseNumber,
    totalDoses = totalDoses,
    administeredDateEpochMillis = administeredDateEpochMillis,
    scheduledDateEpochMillis = scheduledDateEpochMillis,
    lotNumber = lotNumber,
    doctorName = doctorName,
    location = location,
    administeredBy = administeredBy,
    administrationSiteRaw = administrationSiteRaw,
    notes = notes,
    reminderOn = reminderOn,
    nextDoseDateEpochMillis = nextDoseDateEpochMillis,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdBy = createdBy,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)

fun KBVaccine.toEntity(): KBVaccineEntity = KBVaccineEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    name = name,
    vaccineTypeRaw = vaccineTypeRaw,
    statusRaw = statusRaw,
    commercialName = commercialName,
    doseNumber = doseNumber,
    totalDoses = totalDoses,
    administeredDateEpochMillis = administeredDateEpochMillis,
    scheduledDateEpochMillis = scheduledDateEpochMillis,
    lotNumber = lotNumber,
    doctorName = doctorName,
    location = location,
    administeredBy = administeredBy,
    administrationSiteRaw = administrationSiteRaw,
    notes = notes,
    reminderOn = reminderOn,
    nextDoseDateEpochMillis = nextDoseDateEpochMillis,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdBy = createdBy,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
)
