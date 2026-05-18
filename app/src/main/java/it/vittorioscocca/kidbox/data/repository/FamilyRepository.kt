package it.vittorioscocca.kidbox.data.repository

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.tasks.await

@Singleton
class FamilyRepository @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
) {
    private val firestore get() = FirebaseFirestore.getInstance()

    /**
     * Crea una nuova famiglia su Firestore + indice membership + Room.
     * Non cambia la famiglia attiva in sessione.
     */
    suspend fun createNewFamily(name: String, creatorUid: String): Result<String> = runCatching {
        val familyId = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val trimmedName = name.trim()
        require(trimmedName.isNotEmpty()) { "Nome famiglia obbligatorio" }

        val familyRef = firestore.collection("families").document(familyId)
        val batch = firestore.batch()

        batch.set(
            familyRef,
            mapOf(
                "name" to trimmedName,
                "ownerUid" to creatorUid,
                "createdBy" to creatorUid,
                "updatedBy" to creatorUid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )

        batch.set(
            familyRef.collection("members").document(creatorUid),
            mapOf(
                "uid" to creatorUid,
                "userId" to creatorUid,
                "familyId" to familyId,
                "role" to "owner",
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )

        batch.set(
            firestore.collection("users")
                .document(creatorUid)
                .collection("memberships")
                .document(familyId),
            mapOf(
                "familyId" to familyId,
                "role" to "owner",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )

        batch.commit().await()

        familyDao.upsert(
            KBFamilyEntity(
                id = familyId,
                name = trimmedName,
                heroPhotoURL = null,
                heroPhotoLocalPath = null,
                heroPhotoUpdatedAtEpochMillis = null,
                heroPhotoScale = null,
                heroPhotoOffsetX = null,
                heroPhotoOffsetY = null,
                createdBy = creatorUid,
                updatedBy = creatorUid,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                lastSyncAtEpochMillis = now,
                lastSyncError = null,
            ),
        )

        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        familyMemberDao.upsert(
            KBFamilyMemberEntity(
                id = creatorUid,
                familyId = familyId,
                userId = creatorUid,
                role = "owner",
                displayName = user?.displayName ?: user?.email ?: "Proprietario",
                email = user?.email,
                photoURL = user?.photoUrl?.toString(),
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                updatedBy = creatorUid,
                isDeleted = false,
            ),
        )

        familyId
    }
}
