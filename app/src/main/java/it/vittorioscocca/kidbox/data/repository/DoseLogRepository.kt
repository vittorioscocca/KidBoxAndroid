package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBDoseLogDao
import it.vittorioscocca.kidbox.data.local.mapper.toDomain
import it.vittorioscocca.kidbox.data.local.mapper.toEntity
import it.vittorioscocca.kidbox.data.remote.health.DoseLogRemoteStore
import it.vittorioscocca.kidbox.data.remote.health.RemoteDoseLogDto
import it.vittorioscocca.kidbox.domain.model.KBDoseLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class DoseLogRepository @Inject constructor(
    private val dao: KBDoseLogDao,
    private val remote: DoseLogRemoteStore,
) {
    private val auth = FirebaseAuth.getInstance()

    fun observeByTreatment(treatmentId: String): Flow<List<KBDoseLog>> =
        dao.observeByTreatment(treatmentId).map { list -> list.map { it.toDomain() } }

    fun observeByFamilyAndChild(familyId: String, childId: String): Flow<List<KBDoseLog>> =
        dao.observeByFamilyAndChild(familyId, childId).map { list -> list.map { it.toDomain() } }

    suspend fun markTaken(
        treatmentId: String,
        familyId: String,
        childId: String,
        dayNumber: Int,
        slotIndex: Int,
        scheduledTime: String,
        takenAtEpochMillis: Long = System.currentTimeMillis(),
    ): KBDoseLog = withContext(Dispatchers.IO) {
        val capped = takenAtEpochMillis.coerceAtMost(System.currentTimeMillis())
        upsertLog(
            treatmentId = treatmentId,
            familyId = familyId,
            childId = childId,
            dayNumber = dayNumber,
            slotIndex = slotIndex,
            scheduledTime = scheduledTime,
            taken = true,
            takenAtEpochMillis = capped,
        )
    }

    suspend fun markSkipped(
        treatmentId: String,
        familyId: String,
        childId: String,
        dayNumber: Int,
        slotIndex: Int,
        scheduledTime: String,
    ): KBDoseLog = withContext(Dispatchers.IO) {
        upsertLog(
            treatmentId = treatmentId,
            familyId = familyId,
            childId = childId,
            dayNumber = dayNumber,
            slotIndex = slotIndex,
            scheduledTime = scheduledTime,
            taken = false,
            takenAtEpochMillis = System.currentTimeMillis(),
        )
    }

    suspend fun clearLog(doseLogId: String) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "local"
        val existing = dao.getById(doseLogId) ?: return@withContext
        val deleted = existing.copy(
            isDeleted = true,
            updatedAtEpochMillis = System.currentTimeMillis(),
            updatedBy = uid,
            syncStatus = 1,
        )
        dao.upsert(deleted)
        runCatching { remote.softDelete(existing.familyId, existing.id, uid) }
    }

    private suspend fun upsertLog(
        treatmentId: String,
        familyId: String,
        childId: String,
        dayNumber: Int,
        slotIndex: Int,
        scheduledTime: String,
        taken: Boolean,
        takenAtEpochMillis: Long = System.currentTimeMillis(),
    ): KBDoseLog {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val docId = stableDoseLogDocumentId(treatmentId, dayNumber, slotIndex)
        val legacy = dao.getByTreatmentDaySlot(treatmentId, dayNumber, slotIndex)
        if (legacy != null && legacy.id != docId) {
            runCatching { remote.softDelete(familyId, legacy.id, uid) }
            dao.deleteAllForTreatmentDaySlot(treatmentId, dayNumber, slotIndex)
        }
        val existing = dao.getByTreatmentDaySlot(treatmentId, dayNumber, slotIndex)
        val atMillis = if (taken) takenAtEpochMillis else now
        val log = KBDoseLog(
            id = docId,
            familyId = familyId,
            childId = childId,
            treatmentId = treatmentId,
            dayNumber = dayNumber,
            slotIndex = slotIndex,
            scheduledTime = scheduledTime,
            takenAtEpochMillis = atMillis,
            taken = taken,
            isDeleted = false,
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStatus = 1,
            lastSyncError = null,
        )
        dao.upsert(log.toEntity())
        runCatching {
            remote.upsert(log.toRemoteDto())
            dao.upsert(log.copy(syncStatus = 0).toEntity())
        }.onFailure { err ->
            dao.upsert(log.copy(lastSyncError = err.message).toEntity())
        }
        return log
    }
}

/** Stesso id su tutti i dispositivi → stesso documento Firestore `doseLogs/{id}`. */
internal fun stableDoseLogDocumentId(treatmentId: String, dayNumber: Int, slotIndex: Int): String =
    "dose_${treatmentId}_${dayNumber}_$slotIndex"

private fun KBDoseLog.toRemoteDto() = RemoteDoseLogDto(
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
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdAtEpochMillis = createdAtEpochMillis,
)
