package it.vittorioscocca.kidbox.ui.screens.settings.family

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.remote.family.FamilyLeaveService
import it.vittorioscocca.kidbox.data.remote.family.InviteRemoteStore
import it.vittorioscocca.kidbox.data.local.entity.canonicalMemberDisplayName
import it.vittorioscocca.kidbox.data.sync.FamilySyncCenter
import it.vittorioscocca.kidbox.data.sync.firstNonBlankString
import it.vittorioscocca.kidbox.data.sync.userProfileDisplayName
import it.vittorioscocca.kidbox.data.user.UserProfileRepository
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class FamilySettingsUiState(
    val isLoading: Boolean = true,
    val family: KBFamilyEntity? = null,
    val members: List<KBFamilyMemberEntity> = emptyList(),
    val children: List<KBChildEntity> = emptyList(),
    val isOwner: Boolean = false,
    val currentUid: String = "",
    val error: String? = null,
) {
    val canLeave: Boolean
        get() = members.size >= 2 || !isOwner
}

data class ChildInput(
    val id: String,
    val name: String,
    val birthDateEpochMillis: Long?,
)

@HiltViewModel
class FamilySettingsViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val childDao: KBChildDao,
    private val leaveService: FamilyLeaveService,
    private val familySyncCenter: FamilySyncCenter,
    private val userProfileRepository: UserProfileRepository,
) : ViewModel() {
    private val db = FirebaseFirestore.getInstance()
    private val inviteRemoteStore = InviteRemoteStore()
    private val _uiState = MutableStateFlow(FamilySettingsUiState())
    val uiState: StateFlow<FamilySettingsUiState> = _uiState.asStateFlow()

    // Reactive observation job — like iOS @Query
    private var observeJob: Job? = null
    private var observingFamilyId: String? = null

    // ── Public entry point called from screens ────────────────────────────────
    // Architecture: LOCAL-FIRST exactly like iOS
    //
    // FLOW:
    // 1. Read Room → show UI immediately (works offline, instant)
    // 2. If Room empty → bootstrap from Firestore once
    // 3. Start FamilySyncCenter → registers Firestore listeners
    //    → on first snapshot Firestore sends ALL docs as ADDED
    //    → FamilySyncCenter writes them to Room
    //    → Room Flow emits → UI updates automatically
    // 4. Cross-device sync: when device B adds a child, Firestore listener
    //    on device A receives ADDED/MODIFIED → writes to Room → UI updates
    //
    // NO manual bootstrap after step 2 — FamilySyncCenter handles everything
    fun startObserving() {
        val currentFamilyId = observingFamilyId
        if (currentFamilyId != null && observeJob?.isActive == true) {
            familySyncCenter.startSync(currentFamilyId)
            return
        }
        observeJob?.cancel()
        observeJob = viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            if (uid.isEmpty()) {
                _uiState.value = FamilySettingsUiState(isLoading = false)
                return@launch
            }

            // Step 1: read from Room (local-first, instant, works offline)
            var family = familyDao.observeAll().first().firstOrNull()

            // Step 2: Room empty → bootstrap from Firestore (first install only)
            if (family == null) {
                try {
                    family = bootstrapFromFirebase(uid)
                } catch (_: Exception) {
                    // No internet and no local data
                    _uiState.value = FamilySettingsUiState(isLoading = false, currentUid = uid)
                    return@launch
                }
                if (family == null) {
                    _uiState.value = FamilySettingsUiState(isLoading = false, currentUid = uid)
                    return@launch
                }
            }

            val fid = family!!.id
            observingFamilyId = fid

            // Step 3: start Firestore realtime sync
            // FamilySyncCenter writes to Room and signals initialSyncDone when first snapshot done
            familySyncCenter.startSync(fid)

            // Step 4: wait for FamilySyncCenter to finish first Firestore snapshot
            // This ensures Room is fully populated before the Flow starts emitting
            // Timeout 5s — if offline or slow, proceed with whatever Room has
            try {
                kotlinx.coroutines.withTimeout(5000) {
                    familySyncCenter.initialSyncDone.first { it }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) {
                // Offline or slow network — use Room as-is
            }

            // Step 5: observe Room reactively
            // Now Room is fully populated from Firestore first snapshot
            // All future changes update automatically via FamilySyncCenter listeners
            combine(
                familyDao.observeAll(),
                familyMemberDao.observeActiveByFamilyId(fid),
                childDao.observeByFamilyId(fid),
            ) { families, members, children ->
                val currentFamily = families.firstOrNull { it.id == fid }
                FamilySettingsUiState(
                    isLoading = false,
                    family = currentFamily,
                    members = members,
                    children = children,
                    isOwner = members.any { it.userId == uid && it.role.equals("owner", true) },
                    currentUid = uid,
                )
            }.collect { newState ->
                _uiState.value = newState
            }
        }
    }

    // Keep load() for backward compatibility
    fun load() = startObserving()

    // ── Bootstrap from Firestore when Room is empty ───────────────────────────
    private suspend fun bootstrapFromFirebase(requestUid: String): KBFamilyEntity? {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty().ifBlank { requestUid }
            val membershipDocs = db.collection("users")
                .document(uid)
                .collection("memberships")
                .get()
                .await()
                .documents

            val familyId = membershipDocs.firstOrNull()?.id ?: return null
            val familySnap = db.collection("families").document(familyId).get().await()
            if (!familySnap.exists()) return null

            val familyData = familySnap.data.orEmpty()
            val now = System.currentTimeMillis()
            val createdBy = familyData["ownerUid"] as? String ?: uid

            val family = KBFamilyEntity(
                id = familyId,
                name = (familyData["name"] as? String).orEmpty(),
                heroPhotoURL = familyData["heroPhotoURL"] as? String,
                heroPhotoLocalPath = null,
                heroPhotoUpdatedAtEpochMillis = null,
                heroPhotoScale = null,
                heroPhotoOffsetX = null,
                heroPhotoOffsetY = null,
                createdBy = createdBy,
                updatedBy = uid,
                createdAtEpochMillis = (familyData["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now,
                updatedAtEpochMillis = (familyData["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now,
                lastSyncAtEpochMillis = now,
                lastSyncError = null,
            )
            familyDao.upsert(family)

            // Bootstrap members
            val memberDocs = db.collection("families")
                .document(familyId).collection("members").get().await().documents
            memberDocs.forEach { doc ->
                val d = doc.data.orEmpty()
                if (d["isDeleted"] as? Boolean != true) {
                    val memberCreatedAt =
                        (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now
                    val memberUpdatedAt =
                        (d["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now
                    val memberUid = (d["uid"] as? String) ?: doc.id
                    val fromProfile =
                        if (memberUid == uid) userProfileRepository.getByUid(memberUid)?.canonicalMemberDisplayName()
                        else null
                    val displayName = fromProfile?.takeIf { it.isNotBlank() }
                        ?: d.firstNonBlankString("displayName", "name", "fullName")
                        ?: d.firstNonBlankString("email")
                        ?: run {
                            try {
                                val userDoc = db.collection("users").document(doc.id).get().await()
                                if (userDoc.exists()) userDoc.data.orEmpty().userProfileDisplayName() else null
                            } catch (_: Exception) {
                                null
                            }
                        }
                        ?: if (memberUid == uid) {
                            val u = FirebaseAuth.getInstance().currentUser
                            u?.displayName?.trim()?.takeIf { it.isNotEmpty() && it != "Utente" }
                                ?: u?.email?.trim()?.takeIf { it.isNotEmpty() }
                        } else {
                            null
                        }
                        ?: "Membro"
                    familyMemberDao.upsert(KBFamilyMemberEntity(
                        id = doc.id,
                        familyId = familyId,
                        userId = memberUid,
                        role = (d["role"] as? String) ?: "member",
                        displayName = displayName,
                        email = d.firstNonBlankString("email")
                            ?: FirebaseAuth.getInstance().currentUser?.takeIf { it.uid == memberUid }
                                ?.email?.trim()?.takeIf { it.isNotEmpty() },
                        photoURL = d["photoURL"] as? String,
                        createdAtEpochMillis = memberCreatedAt,
                        updatedAtEpochMillis = memberUpdatedAt,
                        updatedBy = (d["updatedBy"] as? String) ?: uid,
                        isDeleted = false,
                    ))
                }
            }

            // Bootstrap children
            val childDocs = db.collection("families")
                .document(familyId).collection("children").get().await().documents
            childDocs.forEach { doc ->
                val d = doc.data.orEmpty()
                if (d["isDeleted"] as? Boolean != true) {
                    childDao.upsert(KBChildEntity(
                        id = doc.id, familyId = familyId,
                        name = (d["name"] as? String)?.takeIf { it.isNotBlank() } ?: "Figlio",
                        birthDateEpochMillis = (d["birthDate"] as? com.google.firebase.Timestamp)?.toDate()?.time,
                        weightKg = null, heightCm = null,
                        createdBy = (d["createdBy"] as? String) ?: uid,
                        createdAtEpochMillis = (d["createdAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now,
                        updatedBy = (d["updatedBy"] as? String) ?: uid,
                        updatedAtEpochMillis = (d["updatedAt"] as? com.google.firebase.Timestamp)?.toDate()?.time ?: now,
                    ))
                }
            }

            family
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(error = "Errore sync: ${e.localizedMessage}")
            null
        }
    }

    // ── Write functions (unchanged logic, removed load() calls) ──────────────

    fun removeMember(member: KBFamilyMemberEntity) {
        val familyId = _uiState.value.family?.id ?: return
        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .collection("members").document(member.userId)
                    .set(mapOf("isDeleted" to true, "updatedAt" to FieldValue.serverTimestamp()),
                        com.google.firebase.firestore.SetOptions.merge()).await()
                familyMemberDao.deleteById(member.id)
                // Room change is picked up automatically by the reactive Flow
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun leaveFamily(onDone: () -> Unit) {
        val familyId = _uiState.value.family?.id ?: return
        viewModelScope.launch {
            try {
                leaveService.leaveFamily(familyId)
                observeJob?.cancel()
                observeJob = null
                observingFamilyId = null
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun joinWithCode(code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val familyId = inviteRemoteStore.resolveInvite(code.trim())
                inviteRemoteStore.addMember(familyId)
                // Reset so startObserving() re-bootstraps the new family
                observeJob?.cancel()
                observeJob = null
                observingFamilyId = null
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun saveFamilyName(name: String, onDone: () -> Unit) {
        val family = _uiState.value.family ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val updated = family.copy(name = name.trim(), updatedAtEpochMillis = System.currentTimeMillis())
                familyDao.upsert(updated)
                db.collection("families").document(family.id)
                    .set(mapOf("name" to updated.name, "updatedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp()),
                        com.google.firebase.firestore.SetOptions.merge()).await()
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage)
            }
        }
    }

    fun saveFamilyWithChildren(newName: String, childrenInputs: List<ChildInput>, onDone: () -> Unit) {
        val family = _uiState.value.family ?: return
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return
        viewModelScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val now = System.currentTimeMillis()

                // Update family in Room + Firestore
                familyDao.upsert(family.copy(name = trimmedName, updatedAtEpochMillis = now))
                db.collection("families").document(family.id)
                    .update("name", trimmedName, "updatedBy", uid, "updatedAt", FieldValue.serverTimestamp()).await()

                // Upsert children
                val existingIds = childDao.observeByFamilyId(family.id).first().map { it.id }.toSet()
                childrenInputs.filter { it.name.isNotBlank() }.forEach { input ->
                    val isExisting = input.id in existingIds
                    val entity = if (isExisting) {
                        (childDao.getById(input.id) ?: return@forEach).copy(
                            name = input.name.trim(),
                            birthDateEpochMillis = input.birthDateEpochMillis,
                            updatedBy = uid, updatedAtEpochMillis = now,
                        )
                    } else {
                        KBChildEntity(
                            id = input.id, familyId = family.id, name = input.name.trim(),
                            birthDateEpochMillis = input.birthDateEpochMillis,
                            weightKg = null, heightCm = null,
                            createdBy = uid, createdAtEpochMillis = now,
                            updatedBy = uid, updatedAtEpochMillis = now,
                        )
                    }
                    childDao.upsert(entity)
                    val firestoreData = mutableMapOf<String, Any?>(
                        "id" to entity.id, "familyId" to family.id,
                        "name" to entity.name,
                        "birthDate" to entity.birthDateEpochMillis?.let {
                            com.google.firebase.Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt())
                        },
                        "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp(),
                    )
                    if (!isExisting) {
                        firestoreData["createdBy"] = uid
                        firestoreData["createdAt"] = FieldValue.serverTimestamp()
                        firestoreData["isDeleted"] = false
                    }
                    db.collection("families").document(family.id)
                        .collection("children").document(entity.id)
                        .set(firestoreData, com.google.firebase.firestore.SetOptions.merge()).await()
                }
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage ?: "Errore salvataggio")
            }
        }
    }

    fun addChild(name: String, birthEpochMillis: Long?) {
        val family = _uiState.value.family ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
            val id = java.util.UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val child = KBChildEntity(
                id = id, familyId = family.id, name = name.trim(),
                birthDateEpochMillis = birthEpochMillis,
                weightKg = null, heightCm = null,
                createdBy = uid, createdAtEpochMillis = now,
                updatedBy = uid, updatedAtEpochMillis = now,
            )
            childDao.upsert(child)
            db.collection("families").document(family.id).collection("children").document(id)
                .set(mapOf(
                    "id" to child.id, "familyId" to family.id, "name" to child.name,
                    "birthDate" to child.birthDateEpochMillis?.let {
                        com.google.firebase.Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt())
                    },
                    "createdBy" to uid, "createdAt" to FieldValue.serverTimestamp(),
                    "updatedBy" to uid, "isDeleted" to false,
                    "updatedAt" to FieldValue.serverTimestamp(),
                ), com.google.firebase.firestore.SetOptions.merge()).await()
        }
    }

    fun saveChild(childId: String, name: String, birthEpochMillis: Long?, onDone: () -> Unit) {
        val family = _uiState.value.family ?: return
        viewModelScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val now = System.currentTimeMillis()
                val existing = childDao.getById(childId)
                val updated = existing?.copy(
                    name = name.trim(), birthDateEpochMillis = birthEpochMillis,
                    updatedBy = uid, updatedAtEpochMillis = now,
                ) ?: KBChildEntity(
                    id = childId, familyId = family.id, name = name.trim(),
                    birthDateEpochMillis = birthEpochMillis,
                    weightKg = null, heightCm = null,
                    createdBy = uid, createdAtEpochMillis = now,
                    updatedBy = uid, updatedAtEpochMillis = now,
                )
                childDao.upsert(updated)
                db.collection("families").document(family.id).collection("children").document(childId)
                    .set(mapOf(
                        "id" to updated.id, "familyId" to family.id, "name" to updated.name,
                        "birthDate" to updated.birthDateEpochMillis?.let {
                            com.google.firebase.Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt())
                        },
                        "updatedBy" to uid, "updatedAt" to FieldValue.serverTimestamp(),
                    ), com.google.firebase.firestore.SetOptions.merge()).await()
                onDone()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.localizedMessage ?: "Errore salvataggio figlio")
            }
        }
    }

    fun deleteChild(childId: String, onDone: () -> Unit) {
        val family = _uiState.value.family ?: return
        viewModelScope.launch {
            childDao.deleteById(childId)
            db.collection("families").document(family.id)
                .collection("children").document(childId).delete().await()
            onDone()
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}