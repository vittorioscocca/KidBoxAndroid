package it.vittorioscocca.kidbox.data.remote.family

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.Timestamp
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FamilyFirestoreCreationRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val familyDao: KBFamilyDao,
) {

    suspend fun createFamilyWithInitialChild(
        familyName: String,
        childName: String,
        birthDateMillis: Long?,
    ): String {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val familyId = UUID.randomUUID().toString()
        val childId = UUID.randomUUID().toString()
        val familyRef = firestore.collection("families").document(familyId)

        val batch1 = firestore.batch()
        batch1.set(
            familyRef,
            mapOf(
                "name" to familyName,
                "ownerUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch1.set(
            familyRef.collection("members").document(uid),
            mapOf(
                "uid" to uid,
                "role" to "owner",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch1.set(
            firestore.collection("users").document(uid)
                .collection("memberships").document(familyId),
            mapOf(
                "familyId" to familyId,
                "role" to "owner",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch1.commit().await()

        val childData = mutableMapOf<String, Any>(
            "name" to childName,
            "isDeleted" to false,
            "createdAt" to FieldValue.serverTimestamp(),
            "updatedBy" to uid,
            "updatedAt" to FieldValue.serverTimestamp(),
        )
        if (birthDateMillis != null) {
            childData["birthDate"] = Timestamp(
                birthDateMillis / 1000,
                ((birthDateMillis % 1000) * 1_000_000).toInt(),
            )
        }
        familyRef.collection("children").document(childId).set(childData).await()

        val now = System.currentTimeMillis()
        familyDao.upsert(
            KBFamilyEntity(
                id = familyId,
                name = familyName,
                heroPhotoURL = null,
                heroPhotoUpdatedAtEpochMillis = null,
                heroPhotoScale = null,
                heroPhotoOffsetX = null,
                heroPhotoOffsetY = null,
                createdBy = uid,
                updatedBy = uid,
                createdAtEpochMillis = now,
                updatedAtEpochMillis = now,
                lastSyncAtEpochMillis = now,
                lastSyncError = null,
            ),
        )
        return familyId
    }
}
