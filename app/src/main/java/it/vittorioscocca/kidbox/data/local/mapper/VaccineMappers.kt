package it.vittorioscocca.kidbox.data.local.mapper

import it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity
import it.vittorioscocca.kidbox.domain.model.KBVaccine

// ── Vaccine kind (allineato a iOS `VaccineType.rawValue`) ───────────────────

enum class KBVaccineType(val rawValue: String) {
    ESAVALENTE("esavalente"),
    PNEUMOCOCCO("pneumococco"),
    MENINGOCOCCO_B("meningococcoB"),
    MPR("mpr"),
    VARICELLA("varicella"),
    MENINGOCOCCO_ACWY("meningococcoACWY"),
    HPV("hpv"),
    INFLUENZA("influenza"),
    ALTRO("altro"),
    ;

    val displayName: String
        get() = when (this) {
            ESAVALENTE -> "Esavalente"
            PNEUMOCOCCO -> "Pneumococco"
            MENINGOCOCCO_B -> "Meningococco B"
            MPR -> "MPR"
            VARICELLA -> "Varicella"
            MENINGOCOCCO_ACWY -> "Meningococco ACWY"
            HPV -> "HPV"
            INFLUENZA -> "Influenza"
            ALTRO -> "Altro"
        }

    companion object {
        fun fromRaw(value: String?): KBVaccineType {
            if (value.isNullOrBlank()) return ALTRO
            entries.firstOrNull { it.rawValue == value }?.let { return it }
            // Legacy Android / vecchi valori
            return when (value) {
                "Obbligatorio", "Raccomandato" -> ALTRO
                else -> ALTRO
            }
        }
    }
}

// ── Stato (allineato a iOS `VaccineStatus.rawValue`) ─────────────────────────

enum class KBVaccineStatus(val rawValue: String) {
    ADMINISTERED("administered"),
    SCHEDULED("scheduled"),
    PLANNED("planned"),
    OVERDUE("overdue"),
    SKIPPED("skipped"),
    ;

    val displayName: String
        get() = when (this) {
            ADMINISTERED -> "Somministrato"
            SCHEDULED -> "Programmato"
            PLANNED -> "Da programmare"
            OVERDUE -> "In ritardo"
            SKIPPED -> "Non eseguito"
        }

    companion object {
        fun fromRaw(value: String?): KBVaccineStatus = when (value) {
            "administered" -> ADMINISTERED
            "scheduled" -> SCHEDULED
            "planned" -> PLANNED
            "overdue" -> OVERDUE
            "skipped" -> SKIPPED
            // Legacy
            "Programmato", "SCHEDULED" -> SCHEDULED
            "Somministrato" -> ADMINISTERED
            "Non eseguito" -> SKIPPED
            null, "" -> SCHEDULED
            else -> SCHEDULED
        }
    }
}

/** Titolo lista / notifiche: come iOS (nome commerciale o tipo). */
fun KBVaccine.displayTitle(): String {
    val c = commercialName?.trim().orEmpty()
    if (c.isNotEmpty()) return c
    return KBVaccineType.fromRaw(vaccineTypeRaw).displayName
}

// ── Stato derivato per UI (ritardo da data programmata) ─────────────────────

fun KBVaccine.computedStatus(): KBVaccineStatus {
    val stored = KBVaccineStatus.fromRaw(statusRaw)
    if (stored == KBVaccineStatus.SKIPPED) return KBVaccineStatus.SKIPPED
    if (administeredDateEpochMillis != null || stored == KBVaccineStatus.ADMINISTERED) {
        return KBVaccineStatus.ADMINISTERED
    }
    if (stored == KBVaccineStatus.PLANNED) return KBVaccineStatus.PLANNED
    if (stored == KBVaccineStatus.SCHEDULED) {
        val sd = scheduledDateEpochMillis
        if (sd != null && sd < System.currentTimeMillis()) return KBVaccineStatus.OVERDUE
        return KBVaccineStatus.SCHEDULED
    }
    if (stored == KBVaccineStatus.OVERDUE) return KBVaccineStatus.OVERDUE
    return stored
}

// ── Entity ↔ Domain ─────────────────────────────────────────────────────────

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
