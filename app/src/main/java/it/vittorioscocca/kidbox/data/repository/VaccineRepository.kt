package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBVaccineDao
import it.vittorioscocca.kidbox.data.local.mapper.toDomain
import it.vittorioscocca.kidbox.data.local.mapper.toEntity
import it.vittorioscocca.kidbox.data.remote.health.RemoteVaccineDto
import it.vittorioscocca.kidbox.data.remote.health.VaccineRemoteStore
import it.vittorioscocca.kidbox.domain.model.KBVaccine
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class VaccineRepository @Inject constructor(
    private val dao: KBVaccineDao,
    private val remote: VaccineRemoteStore,
) {
    private val auth = FirebaseAuth.getInstance()

    fun observe(familyId: String, childId: String): Flow<List<KBVaccine>> =
        dao.observeByFamilyAndChild(familyId, childId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): KBVaccine? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    suspend fun upsert(vaccine: KBVaccine): KBVaccine = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val pending = vaccine.copy(
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = 1,
            lastSyncError = null,
        )
        dao.upsert(pending.toEntity())

        runCatching {
            remote.upsert(pending.toRemoteDto())
            dao.upsert(pending.copy(syncStateRaw = 0).toEntity())
        }.onFailure { err ->
            dao.upsert(pending.copy(lastSyncError = err.message).toEntity())
        }
        pending
    }

    suspend fun softDelete(vaccine: KBVaccine) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val deleted = vaccine.copy(
            isDeleted = true,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = 1,
        )
        dao.upsert(deleted.toEntity())
        runCatching { remote.softDelete(vaccine.familyId, vaccine.id, uid) }
    }
}

private fun KBVaccine.toRemoteDto() = RemoteVaccineDto(
    id = id,
    familyId = familyId,
    childId = childId,
    name = name,
    vaccineTypeRaw = vaccineTypeRaw,
    statusRaw = statusRaw,
    scheduledDateEpochMillis = scheduledDateEpochMillis,
    administeredDateEpochMillis = administeredDateEpochMillis,
    doctorName = doctorName,
    location = location,
    lotNumber = lotNumber,
    notes = notes,
    reminderOn = reminderOn,
    nextDoseDateEpochMillis = nextDoseDateEpochMillis,
    isDeleted = isDeleted,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdAtEpochMillis = createdAtEpochMillis,
    createdBy = createdBy,
)
