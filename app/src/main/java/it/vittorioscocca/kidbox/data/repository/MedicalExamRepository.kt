package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.mapper.toDomain
import it.vittorioscocca.kidbox.data.local.mapper.toEntity
import it.vittorioscocca.kidbox.data.remote.health.MedicalExamRemoteStore
import it.vittorioscocca.kidbox.data.remote.health.RemoteExamDto
import it.vittorioscocca.kidbox.domain.model.KBMedicalExam
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class MedicalExamRepository @Inject constructor(
    private val dao: KBMedicalExamDao,
    private val remote: MedicalExamRemoteStore,
) {
    private val auth = FirebaseAuth.getInstance()

    fun observe(familyId: String, childId: String): Flow<List<KBMedicalExam>> =
        dao.observeByFamilyAndChild(familyId, childId).map { list -> list.map { it.toDomain() } }

    suspend fun getById(id: String): KBMedicalExam? = withContext(Dispatchers.IO) {
        dao.getById(id)?.toDomain()
    }

    suspend fun upsert(exam: KBMedicalExam): KBMedicalExam = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val pending = exam.copy(
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

    suspend fun softDelete(exam: KBMedicalExam) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val deleted = exam.copy(
            isDeleted = true,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = 1,
        )
        dao.upsert(deleted.toEntity())
        runCatching { remote.softDelete(exam.familyId, exam.id, uid) }
    }
}

private fun KBMedicalExam.toRemoteDto() = RemoteExamDto(
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
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdAtEpochMillis = createdAtEpochMillis,
    createdBy = createdBy,
)
