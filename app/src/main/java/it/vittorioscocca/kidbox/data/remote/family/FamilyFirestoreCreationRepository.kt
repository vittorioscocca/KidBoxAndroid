package it.vittorioscocca.kidbox.data.remote.family

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import kotlinx.coroutines.tasks.await
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "FamilyFirestoreCreation"

/** Figlio da creare insieme alla famiglia (Firestore + Room). */
data class InitialChild(
    val id: String,
    val name: String,
    val birthDateMillis: Long?,
)

@Singleton
class FamilyFirestoreCreationRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val familyDao: KBFamilyDao,
    private val childDao: KBChildDao,
) {

    private val db get() = FirebaseFirestore.getInstance()

    /**
     * Crea famiglia + membership + tutti i figli (uno `.set().await()` dopo l’altro).
     * Se un figlio fallisce, l’eccezione interrompe il flusso (nessun commit parziale oltre i precedenti await).
     */
    suspend fun createFamilyWithChildren(
        familyName: String,
        children: List<InitialChild>,
    ): String {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        val familyId = UUID.randomUUID().toString()
        val familyRef = db.collection("families").document(familyId)

        val batch1 = db.batch()
        batch1.set(
            familyRef,
            mapOf(
                "name" to familyName,
                "ownerUid" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        val memberDoc = mutableMapOf<String, Any>(
            "uid" to uid,
            "role" to "owner",
            "createdAt" to FieldValue.serverTimestamp(),
            "isDeleted" to false,
            "updatedAt" to FieldValue.serverTimestamp(),
            "updatedBy" to uid,
        )
        auth.currentUser?.displayName?.trim()?.takeIf { it.isNotEmpty() && it != "Utente" }?.let {
            memberDoc["displayName"] = it
        }
        auth.currentUser?.email?.trim()?.takeIf { it.isNotEmpty() }?.let {
            memberDoc["email"] = it
        }
        batch1.set(
            familyRef.collection("members").document(uid),
            memberDoc,
        )
        batch1.set(
            db.collection("users").document(uid)
                .collection("memberships").document(familyId),
            mapOf(
                "familyId" to familyId,
                "role" to "owner",
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch1.commit().await()

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

        val toSave = children.map { it.copy(name = it.name.trim()) }.filter { it.name.isNotEmpty() }
        for (child in toSave) {
            Log.d("DEBUG_SAVE", "Inviando figlio ${child.name} con ID ${child.id}")
            val childData = mutableMapOf<String, Any>(
                "name" to child.name,
                "isDeleted" to false,
                "createdBy" to uid,
                "createdAt" to FieldValue.serverTimestamp(),
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            )
            if (child.birthDateMillis != null) {
                childData["birthDate"] = Timestamp(
                    child.birthDateMillis / 1000,
                    ((child.birthDateMillis % 1000) * 1_000_000).toInt(),
                )
            }
            familyRef.collection("children").document(child.id).set(childData).await()
            childDao.upsert(
                KBChildEntity(
                    id = child.id,
                    familyId = familyId,
                    name = child.name,
                    birthDateEpochMillis = child.birthDateMillis,
                    weightKg = null,
                    heightCm = null,
                    createdBy = uid,
                    createdAtEpochMillis = now,
                    updatedBy = uid,
                    updatedAtEpochMillis = now,
                ),
            )
        }

        Log.i(TAG, "createFamilyWithChildren OK familyId=$familyId childrenWritten=${toSave.size}")
        return familyId
    }

    /** Compatibilità onboarding: un solo figlio. */
    suspend fun createFamilyWithInitialChild(
        familyName: String,
        childName: String,
        birthDateMillis: Long?,
    ): String {
        val childId = UUID.randomUUID().toString()
        return createFamilyWithChildren(
            familyName,
            listOf(InitialChild(id = childId, name = childName, birthDateMillis = birthDateMillis)),
        )
    }
}
