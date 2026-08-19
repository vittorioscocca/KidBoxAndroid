package it.vittorioscocca.kidbox.data.repository

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.local.mapper.decodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.encodeStringList
import it.vittorioscocca.kidbox.data.local.mapper.toDomain
import it.vittorioscocca.kidbox.data.local.mapper.toEntity
import it.vittorioscocca.kidbox.data.remote.health.MedicalVisitRemoteStore
import it.vittorioscocca.kidbox.data.remote.health.RemoteMedicalVisitDto
import it.vittorioscocca.kidbox.domain.model.KBMedicalVisit
import it.vittorioscocca.kidbox.util.analytics.AppAnalytics
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class MedicalVisitRepository @Inject constructor(
    private val dao: KBMedicalVisitDao,
    private val remote: MedicalVisitRemoteStore,
    @ApplicationContext private val appContext: Context,
) {
    private val auth = FirebaseAuth.getInstance()

    /** Observe Room for real-time UI updates (driven by [MedicalVisitSyncCenter]). */
    fun observe(familyId: String, childId: String): Flow<List<KBMedicalVisit>> =
        dao.observeByFamilyAndChild(familyId, childId).map { list ->
            list.map { it.toDomain() }
        }

    suspend fun loadOnce(visitId: String): KBMedicalVisit? = withContext(Dispatchers.IO) {
        dao.getById(visitId)?.toDomain()
    }

    suspend fun listRecentVisitsForChild(familyId: String, childId: String, limit: Int = 30): List<KBMedicalVisit> =
        withContext(Dispatchers.IO) {
            dao.listRecentForChild(familyId, childId, limit).map { it.toDomain() }
        }

    suspend fun softDeleteById(visitId: String) {
        val v = loadOnce(visitId) ?: return
        delete(visitId, v.familyId)
    }

    /**
     * Persist locally with PENDING_UPSERT, push to Firestore, then mark SYNCED.
     */
    suspend fun save(visit: KBMedicalVisit) = withContext(Dispatchers.IO) {
        val isNew = dao.getById(visit.id) == null
        val isFirstUse = isNew && dao.countByFamilyId(visit.familyId) == 0
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val pending = visit.copy(
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = 1, // PENDING_UPSERT
            lastSyncError = null,
        )
        dao.upsert(pending.toEntity())

        runCatching {
            remote.upsert(pending.toRemoteDto())
            dao.upsert(pending.copy(syncStateRaw = 0).toEntity()) // SYNCED
        }.onFailure { err ->
            dao.upsert(pending.copy(syncStateRaw = 1, lastSyncError = err.message).toEntity())
        }

        if (isNew) {
            AppAnalytics.contentCreated(appContext, "health")
            if (isFirstUse) {
                AppAnalytics.featureFirstUse(appContext, feature = "health")
            }
        }
    }

    /**
     * Soft-delete: mark as deleted locally and push to Firestore.
     */
    suspend fun delete(visitId: String, familyId: String) = withContext(Dispatchers.IO) {
        val existing = dao.getById(visitId) ?: return@withContext
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val deleted = existing.copy(
            isDeleted = true,
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = 1,
        )
        dao.upsert(deleted)
        runCatching { remote.upsert(deleted.toDomain().toRemoteDto()) }
    }
}

private fun KBMedicalVisit.toRemoteDto() = RemoteMedicalVisitDto(
    // Keep both keys coherent (legacy + current) to avoid Android/iOS drift.
    id = id,
    familyId = familyId,
    childId = childId,
    dateEpochMillis = dateEpochMillis,
    doctorName = doctorName,
    doctorSpecializationRaw = doctorSpecializationRaw,
    reason = reason,
    diagnosis = diagnosis,
    recommendations = recommendations,
    notes = notes,
    visitStatusRaw = visitStatusRaw,
    nextVisitDateEpochMillis = nextVisitDateEpochMillis,
    nextVisitReason = nextVisitReason,
    reminderOn = reminderOn,
    nextVisitReminderOn = nextVisitReminderOn,
    linkedTreatmentIdsJson = linkedTreatmentIdsJson,
    linkedExamIdsJson = normalizedExamIdsJson(),
    prescribedExamsJson = normalizedExamIdsJson(),
    asNeededDrugsJson = asNeededDrugsJson ?: "[]",
    therapyTypesJson = therapyTypesJson,
    photoUrlsJson = photoUrlsJson,
    isDeleted = isDeleted,
    updatedAtEpochMillis = updatedAtEpochMillis,
    updatedBy = updatedBy,
    createdAtEpochMillis = createdAtEpochMillis,
    createdBy = createdBy,
)

private fun KBMedicalVisit.normalizedExamIdsJson(): String {
    val merged = (decodeStringList(linkedExamIdsJson) + decodeStringList(prescribedExamsJson))
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()
    return encodeStringList(merged)
}
