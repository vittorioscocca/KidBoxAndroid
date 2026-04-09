package it.vittorioscocca.kidbox.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.dao.KBUserProfileDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.local.entity.canonicalMemberDisplayName
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch

private const val TAG = "FamilySyncCenter"

@Singleton
class FamilySyncCenter @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val childDao: KBChildDao,
    private val userProfileDao: KBUserProfileDao,
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var familyListener: ListenerRegistration? = null
    private var membersListener: ListenerRegistration? = null
    private var childrenListener: ListenerRegistration? = null
    private var currentFamilyId: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Signals when the first Firestore snapshot has been fully written to Room
    // ViewModel waits on this before starting the reactive Flow
    private val _initialSyncDone = MutableStateFlow(false)
    val initialSyncDone: StateFlow<Boolean> = _initialSyncDone.asStateFlow()

    fun startSync(familyId: String) {
        if (currentFamilyId == familyId) return
        stopSync()
        currentFamilyId = familyId
        _initialSyncDone.value = false
        Log.d(TAG, "startSync familyId=$familyId")

        // Track when all three first snapshots have been processed
        var familyFirstDone = false
        var membersFirstDone = false
        var childrenFirstDone = false

        fun checkAllFirstDone() {
            if (familyFirstDone && membersFirstDone && childrenFirstDone) {
                Log.d(TAG, "All first snapshots processed, signaling initialSyncDone")
                _initialSyncDone.value = true
            }
        }

        // ── Family listener ───────────────────────────────────────────────────
        familyListener = db.collection("families").document(familyId)
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "familyListener error: ${err.message}"); familyFirstDone = true; checkAllFirstDone(); return@addSnapshotListener }
                if (snap == null || !snap.exists()) { familyFirstDone = true; checkAllFirstDone(); return@addSnapshotListener }
                val data = snap.data.orEmpty()
                scope.launch {
                    val now = System.currentTimeMillis()
                    val local = familyDao.getById(familyId)
                    val remoteUpdatedAt = (data["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: 0L
                    val localUpdatedAt = local?.updatedAtEpochMillis ?: 0L
                    if (remoteUpdatedAt >= localUpdatedAt) {
                        familyDao.upsert(KBFamilyEntity(
                            id = familyId,
                            name = (data["name"] as? String).orEmpty(),
                            heroPhotoURL = data["heroPhotoURL"] as? String,
                            heroPhotoLocalPath = local?.heroPhotoLocalPath,
                            heroPhotoUpdatedAtEpochMillis = local?.heroPhotoUpdatedAtEpochMillis,
                            heroPhotoScale = local?.heroPhotoScale,
                            heroPhotoOffsetX = local?.heroPhotoOffsetX,
                            heroPhotoOffsetY = local?.heroPhotoOffsetY,
                            createdBy = local?.createdBy ?: (data["ownerUid"] as? String).orEmpty(),
                            updatedBy = (data["updatedBy"] as? String).orEmpty(),
                            createdAtEpochMillis = local?.createdAtEpochMillis ?: now,
                            updatedAtEpochMillis = remoteUpdatedAt.takeIf { it > 0 } ?: now,
                            lastSyncAtEpochMillis = now,
                            lastSyncError = null,
                        ))
                    }
                    familyFirstDone = true
                    checkAllFirstDone()
                }
            }

        // ── Members listener ──────────────────────────────────────────────────
        membersListener = db.collection("families").document(familyId)
            .collection("members")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "membersListener error: ${err.message}"); membersFirstDone = true; checkAllFirstDone(); return@addSnapshotListener }
                if (snap == null) { membersFirstDone = true; checkAllFirstDone(); return@addSnapshotListener }
                Log.d(TAG, "membersListener: changes=${snap.documentChanges.size}")
                scope.launch {
                    val now = System.currentTimeMillis()
                    for (change in snap.documentChanges) {
                        val doc = change.document
                        val d = doc.data.orEmpty()
                        val isDeleted = d["isDeleted"] as? Boolean ?: false
                        if (change.type == DocumentChange.Type.REMOVED || isDeleted) {
                            familyMemberDao.deleteById(doc.id)
                            continue
                        }
                        // Wait for family to exist in Room before inserting member (foreign key)
                        var familyExists = familyDao.getById(familyId) != null
                        if (!familyExists) {
                            var retries = 0
                            while (!familyExists && retries < 50) {
                                kotlinx.coroutines.delay(100)
                                familyExists = familyDao.getById(familyId) != null
                                retries++
                            }
                        }
                        if (!familyExists) {
                            Log.e(TAG, "family not in Room, skipping member id=${doc.id}")
                            continue
                        }

                        val localMember = familyMemberDao.getById(doc.id)
                        val memberUid = (d["uid"] as? String) ?: doc.id
                        val isMe = memberUid == auth.currentUser?.uid
                        Log.d(
                            TAG,
                            "member raw id=${doc.id} displayName=${d["displayName"]} role=${d["role"]} keys=${d.keys}",
                        )
                        // Come iOS SyncCenter: per l'utente corrente il nome canonico è KBUserProfile (Room).
                        var displayName: String? = null
                        if (isMe) {
                            displayName = userProfileDao.getByUid(memberUid)?.canonicalMemberDisplayName()
                        }
                        if (displayName.isNullOrBlank()) {
                            displayName = d.firstNonBlankString("displayName", "name", "fullName")
                                ?: d.firstNonBlankString("email")
                        }
                        if (displayName.isNullOrBlank()) {
                            displayName = try {
                                val userDoc = db.collection("users").document(doc.id).get().await()
                                if (userDoc.exists()) userDoc.data.orEmpty().userProfileDisplayName() else null
                            } catch (e: Exception) {
                                Log.w(TAG, "users/${doc.id} read failed: ${e.message}")
                                null
                            }
                        }
                        if (displayName.isNullOrBlank() && isMe) {
                            val u = auth.currentUser
                            displayName = u?.displayName?.trim()?.takeIf { it.isNotEmpty() && it != "Utente" }
                                ?: u?.email?.trim()?.takeIf { it.isNotEmpty() }
                        }
                        if (displayName.isNullOrBlank()) {
                            displayName = localMember?.displayName?.trim()?.takeIf { it.isNotEmpty() }
                        }
                        if (displayName.isNullOrBlank()) {
                            displayName = "Membro"
                        }
                        val remoteCreatedAt =
                            (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        val remoteUpdatedAt =
                            (d["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        try {
                            familyMemberDao.upsert(KBFamilyMemberEntity(
                                id = doc.id, familyId = familyId,
                                userId = memberUid,
                                role = (d["role"] as? String) ?: "member",
                                displayName = displayName,
                                email = d.firstNonBlankString("email")
                                    ?: auth.currentUser?.takeIf { it.uid == memberUid }?.email?.trim()
                                        ?.takeIf { it.isNotEmpty() },
                                photoURL = d["photoURL"] as? String,
                                createdAtEpochMillis = remoteCreatedAt
                                    ?: localMember?.createdAtEpochMillis
                                    ?: now,
                                updatedAtEpochMillis = remoteUpdatedAt
                                    ?: localMember?.updatedAtEpochMillis
                                    ?: now,
                                updatedBy = (d["updatedBy"] as? String) ?: doc.id,
                                isDeleted = false,
                            ))
                            Log.d(TAG, "member upserted id=${doc.id} name=$displayName")
                        } catch (e: Exception) {
                            Log.e(TAG, "member upsert FAILED id=${doc.id}: ${e.message}")
                        }
                    }
                    membersFirstDone = true
                    checkAllFirstDone()
                }
            }

        // ── Children listener ─────────────────────────────────────────────────
        childrenListener = db.collection("families").document(familyId)
            .collection("children")
            .addSnapshotListener { snap, err ->
                if (err != null) { Log.e(TAG, "childrenListener error: ${err.message}"); childrenFirstDone = true; checkAllFirstDone(); return@addSnapshotListener }
                if (snap == null) { childrenFirstDone = true; checkAllFirstDone(); return@addSnapshotListener }
                Log.d(TAG, "childrenListener: totalDocs=${snap.documents.size} changes=${snap.documentChanges.size}")
                scope.launch {
                    val now = System.currentTimeMillis()
                    for (change in snap.documentChanges) {
                        val doc = change.document
                        val d = doc.data.orEmpty()
                        Log.d(TAG, "child change=${change.type} id=${doc.id} name=${d["name"]}")

                        if (change.type == DocumentChange.Type.REMOVED) {
                            childDao.deleteById(doc.id)
                            continue
                        }
                        val remoteName = (d["name"] as? String)?.takeIf { it.isNotBlank() } ?: ""
                        val remoteBirthDate = (d["birthDate"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        val remoteUpdatedAt = (d["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        val remoteCreatedAt = (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time
                        val remoteCreatedBy = d["createdBy"] as? String
                        val remoteUpdatedBy = d["updatedBy"] as? String
                        val remoteIsDeleted = d["isDeleted"] as? Boolean ?: false

                        if (remoteIsDeleted) {
                            childDao.deleteById(doc.id)
                            continue
                        }

                        // Wait for family to exist in Room before inserting child
                        // (foreign key constraint: child.familyId must exist in kb_families)
                        var familyExists = familyDao.getById(familyId) != null
                        if (!familyExists) {
                            Log.w(TAG, "family not yet in Room, waiting...")
                            var retries = 0
                            while (!familyExists && retries < 50) {
                                kotlinx.coroutines.delay(100)
                                familyExists = familyDao.getById(familyId) != null
                                retries++
                            }
                            Log.d(TAG, "family wait done: exists=$familyExists after $retries retries")
                        }

                        if (!familyExists) {
                            Log.e(TAG, "family still not in Room after wait, skipping child id=${doc.id}")
                            continue
                        }

                        val local = childDao.getById(doc.id)
                        val entity = KBChildEntity(
                            id = doc.id,
                            familyId = familyId,
                            name = remoteName.ifBlank { local?.name ?: "Figlio" },
                            birthDateEpochMillis = remoteBirthDate ?: local?.birthDateEpochMillis,
                            weightKg = null,
                            heightCm = null,
                            createdBy = remoteCreatedBy ?: local?.createdBy ?: "remote",
                            createdAtEpochMillis = remoteCreatedAt ?: local?.createdAtEpochMillis ?: now,
                            updatedBy = remoteUpdatedBy ?: local?.updatedBy,
                            updatedAtEpochMillis = remoteUpdatedAt ?: remoteCreatedAt ?: local?.updatedAtEpochMillis,
                        )
                        try {
                            childDao.upsert(entity)
                            Log.d(TAG, "child upserted id=${doc.id} name=$remoteName familyId=$familyId")
                        } catch (e: Exception) {
                            Log.e(TAG, "child upsert FAILED id=${doc.id}: ${e.message}")
                        }
                    }
                    childrenFirstDone = true
                    checkAllFirstDone()
                    val roomChildren = childDao.observeByFamilyId(familyId).first()
                    Log.d(TAG, "After sync: Room has ${roomChildren.size} children")
                }
            }
    }

    fun stopSync() {
        familyListener?.remove()
        membersListener?.remove()
        childrenListener?.remove()
        familyListener = null
        membersListener = null
        childrenListener = null
        currentFamilyId = null
        _initialSyncDone.value = false
    }
}