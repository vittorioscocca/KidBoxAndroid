package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.mapper.toDomain
import it.vittorioscocca.kidbox.data.local.mapper.toEntity
import it.vittorioscocca.kidbox.data.remote.health.RemoteTreatmentDto
import it.vittorioscocca.kidbox.data.remote.health.TreatmentRemoteStore
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class TreatmentRepository @Inject constructor(
    private val dao: KBTreatmentDao,
    private val remote: TreatmentRemoteStore,
    private val childDao: KBChildDao,
) {
    private val auth = FirebaseAuth.getInstance()

    fun observe(familyId: String, childId: String): Flow<List<KBTreatment>> =
        dao.observeByFamilyAndChild(familyId, childId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): KBTreatment? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    suspend fun listByFamilyAndChild(familyId: String, childId: String): List<KBTreatment> = withContext(Dispatchers.IO) {
        dao.listByFamilyAndChild(familyId, childId).map { it.toDomain() }
    }

    suspend fun upsert(treatment: KBTreatment): KBTreatment = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val pending = treatment.copy(
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = 1,
            lastSyncError = null,
        )
        dao.upsert(pending.toEntity())

        runCatching {
            val syncReminder = isPediatricHealthSubject(pending.familyId, pending.childId)
            remote.upsert(pending.toRemoteDto(), syncReminder)
            dao.upsert(pending.copy(syncStateRaw = 0).toEntity())
        }.onFailure { err ->
            dao.upsert(pending.copy(lastSyncError = err.message).toEntity())
        }
        pending
    }

    suspend fun softDelete(treatment: KBTreatment) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val deleted = treatment.copy(
            isDeleted = true,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = 1,
        )
        dao.upsert(deleted.toEntity())
        runCatching { remote.softDelete(treatment.familyId, treatment.id, uid) }
    }

    private suspend fun isPediatricHealthSubject(familyId: String, childId: String): Boolean {
        val row = childDao.getById(childId) ?: return false
        return row.familyId == familyId
    }
}

private fun KBTreatment.toRemoteDto() = RemoteTreatmentDto(
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
    scheduleTimes = scheduleTimesList(),
    isActive = isActive,
    notes = notes,
    reminderEnabled = reminderEnabled,
    isDeleted = isDeleted,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdAtEpochMillis = createdAtEpochMillis,
    createdBy = createdBy,
)
