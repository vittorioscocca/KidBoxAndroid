package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.auth.FirebaseAuth
import it.vittorioscocca.kidbox.data.local.dao.KBPediatricProfileDao
import it.vittorioscocca.kidbox.data.local.mapper.decodeEmergencyContacts
import it.vittorioscocca.kidbox.data.local.mapper.encodeForStorage
import it.vittorioscocca.kidbox.data.local.mapper.toDomain
import it.vittorioscocca.kidbox.data.local.mapper.toEntity
import it.vittorioscocca.kidbox.data.remote.health.PediatricProfileRemoteStore
import it.vittorioscocca.kidbox.data.remote.health.RemotePediatricProfileDto
import it.vittorioscocca.kidbox.domain.model.KBEmergencyContact
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

@Singleton
class PediatricProfileRepository @Inject constructor(
    private val dao: KBPediatricProfileDao,
    private val remote: PediatricProfileRemoteStore,
) {
    private val auth = FirebaseAuth.getInstance()

    /** Observe Room for real-time UI updates (driven by [PediatricProfileSyncCenter]). */
    fun observe(familyId: String, childId: String): Flow<KBPediatricProfile?> =
        dao.observeByFamilyId(familyId).map { list ->
            list.firstOrNull { it.childId == childId }?.toDomain()
        }

    suspend fun loadOnce(childId: String): KBPediatricProfile? = withContext(Dispatchers.IO) {
        dao.getByChildId(childId)?.toDomain()
    }

    suspend fun decodeContacts(profile: KBPediatricProfile): List<KBEmergencyContact> =
        profile.decodeEmergencyContacts()

    /**
     * Persists the profile locally with [KBSyncState.PENDING_UPSERT], then pushes to
     * Firestore. On success marks [KBSyncState.SYNCED]; on failure leaves the pending state
     * so a future sync pass can retry.
     */
    suspend fun save(
        familyId: String,
        childId: String,
        bloodGroup: String?,
        allergies: String?,
        medicalNotes: String?,
        doctorName: String?,
        doctorPhone: String?,
        emergencyContacts: List<KBEmergencyContact>,
    ) = withContext(Dispatchers.IO) {
        val uid = auth.currentUser?.uid ?: "local"
        val now = System.currentTimeMillis()
        val contactsJson = emergencyContacts.encodeForStorage()

        val updated = KBPediatricProfile(
            id = childId,
            familyId = familyId,
            childId = childId,
            emergencyContactsJson = contactsJson,
            bloodGroup = bloodGroup?.takeIf { it.isNotBlank() && it != "Non specificato" },
            allergies = allergies?.takeIf { it.isNotBlank() },
            medicalNotes = medicalNotes?.takeIf { it.isNotBlank() },
            doctorName = doctorName?.takeIf { it.isNotBlank() },
            doctorPhone = doctorPhone?.takeIf { it.isNotBlank() },
            updatedAtEpochMillis = now,
            updatedBy = uid,
            syncStateRaw = 1, // PENDING_UPSERT
            lastSyncError = null,
        )
        dao.upsert(updated.toEntity())

        runCatching {
            remote.upsert(
                RemotePediatricProfileDto(
                    id = childId,
                    familyId = familyId,
                    childId = childId,
                    bloodGroup = updated.bloodGroup,
                    allergies = updated.allergies,
                    medicalNotes = updated.medicalNotes,
                    doctorName = updated.doctorName,
                    doctorPhone = updated.doctorPhone,
                    emergencyContactsJson = updated.emergencyContactsJson,
                    isDeleted = false,
                    updatedAtEpochMillis = now,
                    updatedBy = uid,
                )
            )
            dao.upsert(updated.copy(syncStateRaw = 0).toEntity()) // SYNCED
        }.onFailure { err ->
            dao.upsert(updated.copy(syncStateRaw = 1, lastSyncError = err.message).toEntity())
        }
    }
}
