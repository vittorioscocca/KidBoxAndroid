package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.PetDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.mapper.toDomain
import it.vittorioscocca.kidbox.data.local.mapper.toEntity
import it.vittorioscocca.kidbox.data.remote.health.RemoteTreatmentDto
import it.vittorioscocca.kidbox.data.remote.health.TreatmentRemoteStore
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
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
    private val petDao: PetDao,
    @ApplicationContext private val appContext: Context,
) {
    private val auth = FirebaseAuth.getInstance()

    fun observe(familyId: String, childId: String): Flow<List<KBTreatment>> =
        dao.observeByFamilyAndChild(familyId, childId).map { list -> list.map { it.toDomain() } }

    fun observeByFamilyAndPet(familyId: String, petId: String): Flow<List<KBTreatment>> =
        dao.observeByFamilyAndPet(familyId, petId).map { list -> list.map { it.toDomain() } }

    fun observeForSubject(familyId: String, childId: String, petId: String): Flow<List<KBTreatment>> =
        if (petId.isNotBlank()) observeByFamilyAndPet(familyId, petId) else observe(familyId, childId)

    suspend fun getById(id: String): KBTreatment? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    suspend fun listByFamilyAndChild(familyId: String, childId: String): List<KBTreatment> = withContext(Dispatchers.IO) {
        dao.listByFamilyAndChild(familyId, childId).map { it.toDomain() }
    }

    suspend fun listByFamilyAndPet(familyId: String, petId: String): List<KBTreatment> = withContext(Dispatchers.IO) {
        dao.listByFamilyAndPet(familyId, petId).map { it.toDomain() }
    }

    suspend fun upsert(treatment: KBTreatment): KBTreatment = withContext(Dispatchers.IO) {
        val isNew = dao.getById(treatment.id) == null
        val isFirstUse = isNew && dao.countByFamilyId(treatment.familyId) == 0
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
            val syncReminder = isPediatricHealthSubject(pending.familyId, pending.childId) ||
                isPetHealthSubject(pending.familyId, pending.petId)
            remote.upsert(pending.toRemoteDto(), syncReminder)
            dao.upsert(pending.copy(syncStateRaw = 0).toEntity())
        }.onFailure { err ->
            dao.upsert(pending.copy(lastSyncError = err.message).toEntity())
        }

        if (isNew) {
            AppAnalytics.contentCreated(appContext, "health")
            if (isFirstUse) {
                AppAnalytics.featureFirstUse(appContext, feature = "health")
            }
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

    private suspend fun isPetHealthSubject(familyId: String, petId: String): Boolean {
        if (petId.isBlank()) return false
        val pet = petDao.getById(petId) ?: return false
        return pet.familyId == familyId && !pet.isDeleted
    }
}

private fun KBTreatment.toRemoteDto() = RemoteTreatmentDto(
    id = id,
    familyId = familyId,
    childId = childId,
    petId = petId,
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
    intervalBetweenDosesDays = intervalBetweenDosesDays,
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
