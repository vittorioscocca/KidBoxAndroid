package it.vittorioscocca.kidbox.data.user

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBUserProfileDao
import it.vittorioscocca.kidbox.data.local.entity.KBUserProfileEntity
import it.vittorioscocca.kidbox.data.local.entity.canonicalMemberDisplayName
import it.vittorioscocca.kidbox.data.remote.family.FamilyMemberProfileRemoteStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await

@Singleton
class UserProfileRepository @Inject constructor(
    private val userProfileDao: KBUserProfileDao,
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val firestore: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val memberProfileRemoteStore: FamilyMemberProfileRemoteStore,
) {
    fun observe(uid: String): Flow<KBUserProfileEntity?> = userProfileDao.observeByUid(uid)

    suspend fun getByUid(uid: String): KBUserProfileEntity? = userProfileDao.getByUid(uid)

    /** Nome da mostrare per il membro corrente, come KBUserProfile su iOS. */
    suspend fun canonicalDisplayNameForCurrentUser(): String? {
        val uid = auth.currentUser?.uid ?: return null
        return userProfileDao.getByUid(uid)?.canonicalMemberDisplayName()
    }

    suspend fun ensureSeededFromAuth() {
        val user = auth.currentUser ?: return
        val uid = user.uid
        val now = System.currentTimeMillis()
        val existing = userProfileDao.getByUid(uid)
        if (existing == null) {
            userProfileDao.upsert(
                KBUserProfileEntity(
                    uid = uid,
                    email = user.email?.trim(),
                    displayName = user.displayName?.trim()?.takeIf { it.isNotEmpty() && it != "Utente" },
                    firstName = null,
                    lastName = null,
                    familyAddress = null,
                    createdAtEpochMillis = now,
                    updatedAtEpochMillis = now,
                ),
            )
            return
        }
        val newEmail = user.email?.trim()
        if (!newEmail.isNullOrEmpty() && existing.email.isNullOrBlank()) {
            userProfileDao.upsert(existing.copy(email = newEmail, updatedAtEpochMillis = now))
        }
    }

    /**
     * Come salvataggio profilo iOS: Room + `users/{uid}` + `members/{uid}` + riga locale [KBFamilyMemberEntity].
     */
    suspend fun saveLocalProfile(
        firstName: String,
        lastName: String,
        familyAddress: String,
    ) {
        val user = auth.currentUser ?: error("Non autenticato")
        val uid = user.uid
        val now = System.currentTimeMillis()
        val fn = firstName.trim()
        val ln = lastName.trim()
        val addr = familyAddress.trim()
        val full = "$fn $ln".trim()
        val displayName = if (full.isEmpty()) "Utente" else full
        val authEmail = user.email?.trim().orEmpty()
        val existing = userProfileDao.getByUid(uid)
        val entity = KBUserProfileEntity(
            uid = uid,
            email = when {
                authEmail.isNotEmpty() -> authEmail
                else -> existing?.email
            },
            displayName = displayName,
            firstName = fn.takeIf { it.isNotEmpty() },
            lastName = ln.takeIf { it.isNotEmpty() },
            familyAddress = addr.takeIf { it.isNotEmpty() },
            createdAtEpochMillis = existing?.createdAtEpochMillis ?: now,
            updatedAtEpochMillis = now,
        )
        userProfileDao.upsert(entity)

        firestore.collection("users").document(uid).set(
            mapOf(
                "firstName" to fn,
                "lastName" to ln,
                "displayName" to displayName,
                "familyAddress" to addr,
                "email" to (entity.email ?: ""),
                "updatedAt" to Timestamp.now(),
            ),
            SetOptions.merge(),
        ).await()

        if (displayName != "Utente") {
            val familyId = familyDao.observeAll().first().firstOrNull()?.id
            if (familyId != null) {
                memberProfileRemoteStore.upsertMyMemberProfileIfNeeded(familyId, displayName)
                val member = familyMemberDao.getById(uid)
                if (member != null && member.familyId == familyId) {
                    familyMemberDao.upsert(
                        member.copy(
                            displayName = displayName,
                            updatedAtEpochMillis = now,
                            updatedBy = uid,
                        ),
                    )
                }
            }
        }
    }

    /** Campi opzionali da `users/{uid}` per precompilare la UI (come iOS loadRemoteUserProfile). */
    suspend fun fetchRemoteProfileFields(uid: String): RemoteUserProfileFields? {
        if (auth.currentUser?.uid != uid) return null
        val snap = try {
            firestore.collection("users").document(uid).get().await()
        } catch (_: Exception) {
            return null
        }
        if (!snap.exists()) return null
        val d = snap.data.orEmpty()
        return RemoteUserProfileFields(
            firstName = (d["firstName"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
            lastName = (d["lastName"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
            familyAddress = (d["familyAddress"] as? String)?.trim()?.takeIf { it.isNotEmpty() },
        )
    }
}

data class RemoteUserProfileFields(
    val firstName: String?,
    val lastName: String?,
    val familyAddress: String?,
)
