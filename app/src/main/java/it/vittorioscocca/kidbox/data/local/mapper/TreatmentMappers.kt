package it.vittorioscocca.kidbox.data.local.mapper

import it.vittorioscocca.kidbox.data.local.entity.KBDoseLogEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.domain.model.KBDoseLog
import it.vittorioscocca.kidbox.domain.model.KBTreatment

fun KBTreatmentEntity.toDomain(): KBTreatment = KBTreatment(
    id = id,
    familyId = familyId,
    childId = childId,
    prescribingVisitId = prescribingVisitId,
    drugName = drugName,
    activeIngredient = activeIngredient,
    dosageValue = dosageValue,
    dosageUnit = dosageUnit,
    isLongTerm = isLongTerm,
    durationDays = durationDays,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    dailyFrequency = dailyFrequency,
    scheduleTimesData = scheduleTimesData,
    isActive = isActive,
    notes = notes,
    reminderEnabled = reminderEnabled,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdBy = createdBy,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    syncStateRaw = syncStateRaw,
)

fun KBTreatment.toEntity(): KBTreatmentEntity = KBTreatmentEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    prescribingVisitId = prescribingVisitId,
    drugName = drugName,
    activeIngredient = activeIngredient,
    dosageValue = dosageValue,
    dosageUnit = dosageUnit,
    isLongTerm = isLongTerm,
    durationDays = durationDays,
    startDateEpochMillis = startDateEpochMillis,
    endDateEpochMillis = endDateEpochMillis,
    dailyFrequency = dailyFrequency,
    scheduleTimesData = scheduleTimesData,
    isActive = isActive,
    notes = notes,
    reminderEnabled = reminderEnabled,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdBy = createdBy,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
    syncStateRaw = syncStateRaw,
)

fun KBDoseLogEntity.toDomain(): KBDoseLog = KBDoseLog(
    id = id,
    familyId = familyId,
    childId = childId,
    treatmentId = treatmentId,
    dayNumber = dayNumber,
    slotIndex = slotIndex,
    scheduledTime = scheduledTime,
    takenAtEpochMillis = takenAtEpochMillis,
    taken = taken,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
)

fun KBDoseLog.toEntity(): KBDoseLogEntity = KBDoseLogEntity(
    id = id,
    familyId = familyId,
    childId = childId,
    treatmentId = treatmentId,
    dayNumber = dayNumber,
    slotIndex = slotIndex,
    scheduledTime = scheduledTime,
    takenAtEpochMillis = takenAtEpochMillis,
    taken = taken,
    isDeleted = isDeleted,
    createdAtEpochMillis = createdAtEpochMillis,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    syncStatus = syncStatus,
    lastSyncError = lastSyncError,
)

fun KBTreatment.scheduleTimesList(): List<String> =
    scheduleTimesData.split(",").map { it.trim() }.filter { it.isNotEmpty() }

fun KBTreatment.totalDoses(): Int =
    if (isLongTerm) -1 else dailyFrequency * durationDays
