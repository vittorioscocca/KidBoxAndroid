package it.vittorioscocca.kidbox.data.local.mapper

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.domain.model.KBExamStatus
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam

fun examStatusFromRaw(raw: String?): KBExamStatus {
    val normalized = raw?.trim()
    return when (normalized) {
        "Eseguita" -> KBExamStatus.DONE // legacy/feminine wording
        else -> KBExamStatus.values().firstOrNull { it.rawValue == normalized } ?: KBExamStatus.PENDING
    }
}

fun KBMedicalExamEntity.toDomain(): KBMedicalExam = KBMedicalExam(
    id = id,
    familyId = familyId,
    childId = childId,
    name = name,
    isUrgent = isUrgent,
    deadlineEpochMillis = deadlineEpochMillis,
    preparation = preparation,
    notes = notes,
    location = location,
    statusRaw = statusRaw,
    resultText = resultText,
    resultDateEpochMillis = resultDateEpochMillis,
    prescribingVisitId = prescribingVisitId,
    reminderOn = reminderOn,
    isDeleted = isDeleted,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdBy = createdBy,
)

fun KBMedicalExam.toEntity(): KBMedicalExamEntity = KBMedicalExamEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    name = name,
    isUrgent = isUrgent,
    deadlineEpochMillis = deadlineEpochMillis,
    preparation = preparation,
    notes = notes,
    location = location,
    statusRaw = statusRaw,
    resultText = resultText,
    resultDateEpochMillis = resultDateEpochMillis,
    prescribingVisitId = prescribingVisitId,
    reminderOn = reminderOn,
    isDeleted = isDeleted,
    syncStateRaw = syncStateRaw,
    lastSyncError = lastSyncError,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdBy = createdBy,
)
