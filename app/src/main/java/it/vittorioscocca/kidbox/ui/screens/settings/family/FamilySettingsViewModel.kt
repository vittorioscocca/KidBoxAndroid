package it.vittorioscocca.kidbox.ui.screens.settings.family

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import dagger.hilt.android.lifecycle.HiltViewModel
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBChildEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.data.local.entity.canonicalMemberDisplayName
import it.vittorioscocca.kidbox.data.remote.family.FamilyLeaveService
import it.vittorioscocca.kidbox.data.remote.family.FamilyFirestoreCreationRepository
import it.vittorioscocca.kidbox.data.remote.family.InitialChild
import it.vittorioscocca.kidbox.data.remote.family.InviteRemoteStore
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
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
    /** Salvataggio famiglia/figli in corso (non sovrascritto dal flow combine). */
    val isSavingFamily: Boolean = false,
    val savingMessage: String? = null,
) {
    val canLeave: Boolean
        get() = members.size >= 2 || !isOwner
}

data class ChildInput(
    val id: String,
    val name: String,
    val birthDateEpochMillis: Long?,
)

sealed interface LeaveScenario {
    data object MemberOnly : LeaveScenario
    data object OwnerAlone : LeaveScenario
    data class OwnerWithMembers(val otherMembers: List<KBFamilyMemberEntity>) : LeaveScenario
}

sealed interface LeaveDialogState {
    data object Hidden : LeaveDialogState
    data object ConfirmLeave : LeaveDialogState
    data object OwnerAlone : LeaveDialogState
    data class OwnerWithMembers(val otherMembers: List<KBFamilyMemberEntity>) : LeaveDialogState
    data object TransferOwnership : LeaveDialogState
}

@HiltViewModel
class FamilySettingsViewModel @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val childDao: KBChildDao,
    private val leaveService: FamilyLeaveService,
    private val familySyncCenter: FamilySyncCenter,
    private val userProfileRepository: UserProfileRepository,
    private val creationRepository: FamilyFirestoreCreationRepository,
) : ViewModel() {
    companion object {
        private const val TAG = "FamilySettingsVM"
    }

    /** Sempre [FirebaseFirestore.getInstance] — mai un `val` fisso. */
    private val db get() = FirebaseFirestore.getInstance()
    private val inviteRemoteStore = InviteRemoteStore()
    private val _uiState = MutableStateFlow(FamilySettingsUiState())
    val uiState: StateFlow<FamilySettingsUiState> = _uiState.asStateFlow()
    private val _leaveDialogState = MutableStateFlow<LeaveDialogState>(LeaveDialogState.Hidden)
    val leaveDialogState: StateFlow<LeaveDialogState> = _leaveDialogState.asStateFlow()
    private val _navigateAwayAfterLeave = MutableStateFlow(false)
    val navigateAwayAfterLeave: StateFlow<Boolean> = _navigateAwayAfterLeave.asStateFlow()

    private var observeJob: Job? = null
    private var observingFamilyId: String? = null

    init {
        viewModelScope.launch {
            familySyncCenter.accessLostEvent.collect {
                handleAccessRevokedByRemote()
            }
        }
    }

    /**
     * Revoca accesso da altro device (listener → PERMISSION_DENIED / wipe).
     * Reset UI senza mostrare l'errore Firestore: [onLeaveDone] riavvia il flusso verso onboarding.
     */
    private fun handleAccessRevokedByRemote() {
        observeJob?.cancel()
        observeJob = null
        observingFamilyId = null
        dismissLeaveDialog()
        val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        _uiState.value = FamilySettingsUiState(
            isLoading = false,
            family = null,
            members = emptyList(),
            children = emptyList(),
            isOwner = false,
            currentUid = uid,
            error = null,
        )
        _navigateAwayAfterLeave.value = true
        Log.i(TAG, "handleAccessRevokedByRemote: stato resettato, navigateAwayAfterLeave=true")
    }

    private fun isPermissionDenied(t: Throwable): Boolean {
        var e: Throwable? = t
        while (e != null) {
            val fs = e as? FirebaseFirestoreException
            if (fs?.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) return true
            e = e.cause
        }
        return false
    }

    /**
     * Dopo una scrittura Firestore che cambia i permessi: invalida il token, attende la propagazione
     * nell'SDK, poi [terminate] e [clearPersistence]. L'ordine è obbligatorio (non pulire con client attivo).
     * Errori su terminate/clearPersistence vengono loggati ma non bloccano il ripristino del sync.
     *
     * @return true se [getIdToken] è riuscito.
     */
    private suspend fun refreshTokenThenResetFirestoreCache(): Boolean {
        var tokenRefreshOk = false
        try {
            FirebaseAuth.getInstance().currentUser?.getIdToken(true)?.await()
            tokenRefreshOk = true
            Log.d(TAG, "refreshTokenThenResetFirestoreCache: token rinfrescato")
        } catch (e: Exception) {
            Log.e(TAG, "refreshTokenThenResetFirestoreCache: errore rinfresco token", e)
        }
        delay(3000)
        try {
            FirebaseFirestore.getInstance().terminate().await()
            FirebaseFirestore.getInstance().clearPersistence().await()
            Log.d(TAG, "refreshTokenThenResetFirestoreCache: terminate + clearPersistence OK")
            delay(1000)
        } catch (e: Exception) {
            Log.e(TAG, "refreshTokenThenResetFirestoreCache: terminate/clearPersistence fallito — continuo", e)
        }
        return tokenRefreshOk
    }

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

            var family = familyDao.observeAll().first().firstOrNull()

            if (family == null) {
                try {
                    family = bootstrapFromFirebase(uid)
                } catch (_: Exception) {
                    _uiState.value = FamilySettingsUiState(isLoading = false, currentUid = uid)
                    return@launch
                }
                if (family == null) {
                    try {
                        kotlinx.coroutines.withTimeout(8000) {
                            familySyncCenter.initialSyncDone.first { it }
                        }
                        family = familyDao.observeAll().first().firstOrNull()
                    } catch (_: Exception) { }
                    if (family == null) {
                        _uiState.value = FamilySettingsUiState(isLoading = false, currentUid = uid)
                        return@launch
                    }
                }
            }

            val fid = family!!.id
            observingFamilyId = fid
            familySyncCenter.startSync(fid)

            try {
                kotlinx.coroutines.withTimeout(5000) {
                    familySyncCenter.initialSyncDone.first { it }
                }
            } catch (_: kotlinx.coroutines.TimeoutCancellationException) { }

            combine(
                familyDao.observeById(fid),
                familyMemberDao.observeActiveByFamilyId(fid),
                childDao.observeByFamilyId(fid),
            ) { family, members, children ->
                val isOwnerFromMembers =
                    members.any { it.userId == uid && it.role.equals("owner", true) }
                val isOwnerFromFamily = family?.createdBy == uid
                val isOwner = isOwnerFromMembers || isOwnerFromFamily
                FamilySettingsUiState(
                    isLoading = false,
                    family = family,
                    members = members,
                    children = children,
                    isOwner = isOwner,
                    currentUid = uid,
                )
            }.collect { newState ->
                val prev = _uiState.value
                _uiState.value = newState.copy(
                    isSavingFamily = prev.isSavingFamily,
                    savingMessage = prev.savingMessage,
                    error = prev.error,
                )
            }
        }
    }

    fun load() = startObserving()

    private var lastRefreshMs = 0L
    fun refreshSync() {
        val fid = observingFamilyId ?: return
        val now = System.currentTimeMillis()
        if (now - lastRefreshMs < 10_000L) return
        lastRefreshMs = now
        familySyncCenter.startSync(fid)
    }

    private suspend fun bootstrapFromFirebase(
        requestUid: String,
        allowRecoveryOnPermissionDenied: Boolean = true,
    ): KBFamilyEntity? {
        return try {
            val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty().ifBlank { requestUid }
            var membershipDocs = emptyList<com.google.firebase.firestore.DocumentSnapshot>()
            repeat(3) { attempt ->
                membershipDocs = db.collection("users")
                    .document(uid)
                    .collection("memberships")
                    .get()
                    .await()
                    .documents
                if (membershipDocs.isNotEmpty()) return@repeat
                if (attempt < 2) kotlinx.coroutines.delay(1000)
            }

            val candidateFamilyIds = mutableListOf<String>()
            membershipDocs
                .asSequence()
                .mapNotNull { doc ->
                    doc.id.takeIf { it.isNotBlank() }?.also { candidateFamilyIds.add(it) }
                    (doc.data?.get("familyId") as? String)?.trim()?.takeIf { it.isNotEmpty() }
                }
                .forEach { candidateFamilyIds.add(it) }

            if (candidateFamilyIds.isEmpty()) {
                Log.w(TAG, "bootstrapFromFirebase: memberships vuote/incoerenti, fallback members collectionGroup")
                val memberDocs = db.collectionGroup("members")
                    .whereEqualTo("uid", uid)
                    .get()
                    .await()
                    .documents
                memberDocs
                    .filter { it.data?.get("isDeleted") as? Boolean != true }
                    .mapNotNull { it.reference.parent.parent?.id }
                    .forEach { candidateFamilyIds.add(it) }
            }
            val distinctCandidates = candidateFamilyIds
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
            if (distinctCandidates.isEmpty()) return null

            var selectedFamilyId: String? = null
            var selectedFamilySnap: com.google.firebase.firestore.DocumentSnapshot? = null
            for (candidateId in distinctCandidates) {
                try {
                    val myMemberSnap = db.collection("families")
                        .document(candidateId)
                        .collection("members")
                        .document(uid)
                        .get()
                        .await()
                    if (!myMemberSnap.exists() || myMemberSnap.data?.get("isDeleted") as? Boolean == true) {
                        Log.w(TAG, "bootstrapFromFirebase: skip familyId=$candidateId (member missing/deleted)")
                        continue
                    }
                    val familySnap = db.collection("families").document(candidateId).get().await()
                    if (!familySnap.exists()) continue
                    val familyData = familySnap.data.orEmpty()
                    if (familyData["isDeleted"] as? Boolean == true) {
                        Log.w(TAG, "bootstrapFromFirebase: skip familyId=$candidateId (family deleted)")
                        continue
                    }
                    selectedFamilyId = candidateId
                    selectedFamilySnap = familySnap
                    break
                } catch (e: FirebaseFirestoreException) {
                    if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        Log.w(TAG, "bootstrapFromFirebase: skip familyId=$candidateId (PERMISSION_DENIED)")
                        continue
                    }
                    throw e
                }
            }
            val familyId = selectedFamilyId ?: return null
            val familyData = selectedFamilySnap?.data.orEmpty()
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
                updatedBy = (familyData["updatedBy"] as? String) ?: uid,
                createdAtEpochMillis = (familyData["createdAt"] as? com.google.firebase.Timestamp)
                    ?.toDate()?.time ?: now,
                updatedAtEpochMillis = (familyData["updatedAt"] as? com.google.firebase.Timestamp)
                    ?.toDate()?.time ?: now,
                lastSyncAtEpochMillis = null,
                lastSyncError = null,
            )
            familyDao.upsert(family)

            val memberDocs = db.collection("families")
                .document(familyId).collection("members").get().await().documents
            memberDocs.forEach { doc ->
                val d = doc.data.orEmpty()
                if (d["isDeleted"] as? Boolean != true) {
                    val memberUid = (d["uid"] as? String) ?: doc.id
                    val fromProfile = if (memberUid == uid)
                        userProfileRepository.getByUid(memberUid)?.canonicalMemberDisplayName()
                    else null
                    val displayName = fromProfile?.takeIf { it.isNotBlank() }
                        ?: d.firstNonBlankString("displayName", "name", "fullName")
                        ?: d.firstNonBlankString("email")
                        ?: run {
                            try {
                                val userDoc = db.collection("users").document(doc.id).get().await()
                                if (userDoc.exists()) userDoc.data.orEmpty().userProfileDisplayName() else null
                            } catch (_: Exception) { null }
                        }
                        ?: if (memberUid == uid) {
                            val u = FirebaseAuth.getInstance().currentUser
                            u?.displayName?.trim()?.takeIf { it.isNotEmpty() && it != "Utente" }
                                ?: u?.email?.trim()?.takeIf { it.isNotEmpty() }
                        } else null
                            ?: "Membro"
                    familyMemberDao.upsert(KBFamilyMemberEntity(
                        id = doc.id,
                        familyId = familyId,
                        userId = memberUid,
                        role = (d["role"] as? String) ?: "member",
                        displayName = displayName,
                        email = (d["email"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
                            ?: FirebaseAuth.getInstance().currentUser
                                ?.takeIf { it.uid == memberUid }
                                ?.email?.trim()?.takeIf { it.isNotEmpty() },
                        photoURL = d["photoURL"] as? String,
                        createdAtEpochMillis = (d["createdAt"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time ?: now,
                        updatedAtEpochMillis = (d["updatedAt"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time ?: now,
                        updatedBy = (d["updatedBy"] as? String) ?: uid,
                        isDeleted = false,
                    ))
                }
            }

            val childDocs = db.collection("families")
                .document(familyId).collection("children").get().await().documents
            childDocs.forEach { doc ->
                val d = doc.data.orEmpty()
                if (d["isDeleted"] as? Boolean != true) {
                    childDao.upsert(KBChildEntity(
                        id = doc.id,
                        familyId = familyId,
                        name = (d["name"] as? String)?.takeIf { it.isNotBlank() } ?: "Figlio",
                        birthDateEpochMillis = (d["birthDate"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time,
                        weightKg = null,
                        heightCm = null,
                        createdBy = (d["createdBy"] as? String) ?: uid,
                        createdAtEpochMillis = (d["createdAt"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time ?: now,
                        updatedBy = (d["updatedBy"] as? String) ?: uid,
                        updatedAtEpochMillis = (d["updatedAt"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time ?: now,
                    ))
                }
            }

            family
        } catch (e: Exception) {
            if (isPermissionDenied(e)) {
                Log.w(TAG, "bootstrapFromFirebase: PERMISSION_DENIED — revoca accesso, nessun popup errore")
                if (allowRecoveryOnPermissionDenied) {
                    Log.w(TAG, "bootstrapFromFirebase: tentativo recovery token/cache e retry")
                    refreshTokenThenResetFirestoreCache()
                    return bootstrapFromFirebase(
                        requestUid = requestUid,
                        allowRecoveryOnPermissionDenied = false,
                    )
                }
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                _uiState.value = FamilySettingsUiState(isLoading = false, currentUid = uid, error = null)
            } else {
                _uiState.value = _uiState.value.copy(error = "Errore sync: ${e.localizedMessage}")
            }
            null
        }
    }

    /**
     * Revoca solo il membro indicato: **solo** `families/{familyId}/members/{memberDocId}` con
     * `isDeleted` + `updatedAt`. Non aggiorna mai `families/{familyId}` (nessun merge sul doc padre).
     * Operazione atomica lato app: nessuna chiamata a [FamilyLeaveService.leaveFamily] né
     * [FamilyLeaveService.deleteFamily] — solo sync dopo reset cache.
     */
    fun removeMember(member: KBFamilyMemberEntity) {
        val myUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
        Log.d(
            "DEBUG_REVOCA",
            "Operatore (Owner): $myUid, Target (Da rimuovere): ${member.userId}, docId=${member.id}",
        )
        if (member.userId == myUid || member.id == myUid) {
            Log.w(TAG, "removeMember: rifiutato — target coincide con l'operatore")
            return
        }
        val familyId = _uiState.value.family?.id ?: return
        // Path members: id documento = doc.id in sync (mai confondere con l'operatore).
        val targetMemberDocId = member.id
        viewModelScope.launch {
            try {
                db.collection("families").document(familyId)
                    .collection("members").document(targetMemberDocId)
                    .update(
                        mapOf(
                            "isDeleted" to true,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                    ).await()
                familyMemberDao.deleteById(member.id)

                familySyncCenter.stopSync()
                Log.d(TAG, "removeMember: stopSync dopo revoca familyId=$familyId")
                refreshTokenThenResetFirestoreCache()
                Log.d(TAG, "Reset Firestore completato con nuovo token. Riattivazione Sync.")
                familySyncCenter.startSync(familyId)
            } catch (e: Exception) {
                if (!isPermissionDenied(e)) {
                    _uiState.value = _uiState.value.copy(error = e.localizedMessage)
                } else {
                    Log.w(TAG, "removeMember: PERMISSION_DENIED — ignorato (possibile revoca)")
                }
            }
        }
    }

    fun leaveFamily() {
        val familyId = _uiState.value.family?.id ?: return
        viewModelScope.launch {
            try {
                Log.i(TAG, "leaveFamily start familyId=$familyId uid=${_uiState.value.currentUid}")
                leaveService.leaveFamily(familyId)
                Log.d(TAG, "leaveFamily service completed familyId=$familyId")
                observeJob?.cancel()
                observeJob = null
                observingFamilyId = null
                Log.d(TAG, "leaveFamily observers cleared familyId=$familyId")
                dismissLeaveDialog()
                _navigateAwayAfterLeave.value = true
            } catch (e: Exception) {
                Log.e(TAG, "leaveFamily failed familyId=$familyId err=${e.message}", e)
                if (!isPermissionDenied(e)) {
                    _uiState.value = _uiState.value.copy(error = e.localizedMessage)
                }
            }
        }
    }

    fun checkLeaveScenario(): LeaveScenario {
        val state = _uiState.value
        val currentUid = state.currentUid
        val currentMember = state.members.firstOrNull { it.userId == currentUid }
        val isOwnerFromFamily = state.family?.createdBy == currentUid
        val isOwner =
            currentMember?.role.equals("owner", ignoreCase = true) || isOwnerFromFamily
        Log.d(TAG, "checkLeaveScenario: members=${state.members.size} isOwner=$isOwner")
        if (!isOwner) {
            Log.d(TAG, "checkLeaveScenario -> MemberOnly uid=$currentUid members=${state.members.size}")
            return LeaveScenario.MemberOnly
        }
        // Lista vuota (glitch sync post-revoca / permessi): mai OwnerAlone → la UI non deve proporre deleteFamily.
        if (state.members.isEmpty()) {
            if (isOwnerFromFamily) {
                Log.d(
                    TAG,
                    "checkLeaveScenario: forzato scenario con membri per evitare eliminazione accidentale durante glitch",
                )
            }
            Log.w(
                TAG,
                "checkLeaveScenario -> OwnerWithMembers(empty) uid=$currentUid (lista vuota, mai OwnerAlone)",
            )
            return LeaveScenario.OwnerWithMembers(emptyList())
        }
        val otherMembers = state.members.filter { it.userId != currentUid }
        if (otherMembers.isEmpty()) {
            Log.d(TAG, "checkLeaveScenario -> OwnerAlone uid=$currentUid members=${state.members.size}")
            return LeaveScenario.OwnerAlone
        }
        Log.d(TAG, "checkLeaveScenario -> OwnerWithMembers uid=$currentUid others=${otherMembers.size}")
        return LeaveScenario.OwnerWithMembers(otherMembers)
    }

    fun transferOwnershipAndLeave(newOwnerUid: String) {
        val familyId = _uiState.value.family?.id ?: return
        viewModelScope.launch {
            try {
                Log.i(TAG, "transferOwnershipAndLeave start familyId=$familyId uid=${_uiState.value.currentUid} newOwnerUid=$newOwnerUid")
                familySyncCenter.stopSync()
                Log.d(TAG, "transferOwnershipAndLeave sync stopped familyId=$familyId")
                leaveService.transferOwnershipAndLeave(familyId, newOwnerUid)
                Log.d(TAG, "transferOwnershipAndLeave service completed familyId=$familyId")
                observeJob?.cancel()
                observeJob = null
                observingFamilyId = null
                dismissLeaveDialog()
                _navigateAwayAfterLeave.value = true
            } catch (e: Exception) {
                Log.e(TAG, "transferOwnershipAndLeave failed familyId=$familyId newOwnerUid=$newOwnerUid err=${e.message}", e)
                if (!isPermissionDenied(e)) {
                    _uiState.value = _uiState.value.copy(error = e.localizedMessage)
                }
            }
        }
    }

    fun deleteFamily() {
        val state = _uiState.value
        val familyId = state.family?.id ?: return

        if (state.members.filter { !it.isDeleted }.size > 1) {
            Log.e(
                TAG,
                "SABOTAGGIO EVITATO: Tentativo di cancellare famiglia con membri attivi nello stato UI.",
            )
            return
        }

        viewModelScope.launch {
            Log.e(TAG, "!!! INVOCATA PROCEDURA DI ELIMINAZIONE TOTALE FAMIGLIA !!! ID: $familyId")
            try {
                val activeInDb = familyMemberDao.observeActiveByFamilyId(familyId).first()
                if (activeInDb.size <= 1) {
                    Log.i(TAG, "Cancellazione autorizzata: unico membro rimasto nel DB locale.")
                    Log.i(TAG, "deleteFamily start familyId=$familyId uid=${_uiState.value.currentUid}")
                    leaveService.deleteFamily(familyId)
                    familyDao.deleteAll()
                    Log.d(TAG, "deleteFamily service completed familyId=$familyId")
                    observeJob?.cancel()
                    observeJob = null
                    observingFamilyId = null
                    dismissLeaveDialog()
                    _uiState.value = FamilySettingsUiState(
                        isLoading = false,
                        currentUid = _uiState.value.currentUid,
                    )
                    _navigateAwayAfterLeave.value = true
                } else {
                    Log.e(
                        TAG,
                        "ERRORE CRITICO: Il database locale dice che ci sono ancora ${activeInDb.size} membri. Cancellazione interrotta.",
                    )
                    _uiState.value = _uiState.value.copy(
                        error = "Impossibile eliminare: sincronizzazione incompleta.",
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Fallimento durante il controllo di sicurezza eliminazione", e)
                if (!isPermissionDenied(e)) {
                    _uiState.value = _uiState.value.copy(error = e.localizedMessage)
                }
            }
        }
    }

    fun onLeaveButtonTapped() {
        if (!familySyncCenter.initialSyncDone.value) {
            Log.w(TAG, "onLeaveButtonTapped bloccato: sync in corso")
            return
        }
        val scenario = checkLeaveScenario()
        val next = when (scenario) {
            LeaveScenario.MemberOnly -> LeaveDialogState.ConfirmLeave
            LeaveScenario.OwnerAlone -> LeaveDialogState.OwnerAlone
            is LeaveScenario.OwnerWithMembers -> LeaveDialogState.OwnerWithMembers(scenario.otherMembers)
        }
        _leaveDialogState.value = next
        Log.d(TAG, "onLeaveButtonTapped scenario=$scenario dialogState=$next")
    }

    fun dismissLeaveDialog() {
        Log.d(TAG, "dismissLeaveDialog from=${_leaveDialogState.value}")
        _leaveDialogState.value = LeaveDialogState.Hidden
    }

    fun showTransferOwnershipDialog() {
        if (_leaveDialogState.value is LeaveDialogState.OwnerWithMembers) {
            _leaveDialogState.value = LeaveDialogState.TransferOwnership
            Log.d(TAG, "showTransferOwnershipDialog -> TransferOwnership")
        } else {
            Log.w(TAG, "showTransferOwnershipDialog ignored from=${_leaveDialogState.value}")
        }
    }

    fun clearError() {
        Log.d(TAG, "clearError previous=${_uiState.value.error}")
        _uiState.value = _uiState.value.copy(error = null)
    }

    fun resetNavigateAway() {
        Log.d(TAG, "resetNavigateAway")
        _navigateAwayAfterLeave.value = false
    }

    fun joinWithCode(code: String, onDone: () -> Unit) {
        viewModelScope.launch {
            try {
                val familyId = inviteRemoteStore.resolveInvite(code.trim())
                inviteRemoteStore.addMember(familyId)
                observeJob?.cancel()
                observeJob = null
                observingFamilyId = null
                onDone()
            } catch (e: Exception) {
                if (!isPermissionDenied(e)) {
                    _uiState.value = _uiState.value.copy(error = e.localizedMessage)
                } else {
                    Log.w(TAG, "joinWithCode: PERMISSION_DENIED — ignorato")
                }
            }
        }
    }

    fun saveFamilyName(name: String, onDone: () -> Unit) {
        val family = _uiState.value.family ?: return
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val now = System.currentTimeMillis()
                val updated = family.copy(name = name.trim(), updatedAtEpochMillis = now)
                familyDao.upsert(updated)
                db.collection("families").document(family.id)
                    .set(
                        mapOf(
                            "name" to updated.name,
                            "updatedBy" to uid,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                onDone()
            } catch (e: Exception) {
                if (!isPermissionDenied(e)) {
                    _uiState.value = _uiState.value.copy(error = e.localizedMessage)
                } else {
                    Log.w(TAG, "saveFamilyName: PERMISSION_DENIED — ignorato")
                }
            }
        }
    }

    /** Room + Firestore per nome famiglia e figli. Usa [fs] dopo clearPersistence/terminate (es. [freshDb]). */
    private suspend fun saveFamilyAndChildrenToFirestore(
        fs: FirebaseFirestore,
        family: KBFamilyEntity,
        trimmedName: String,
        childrenInputs: List<ChildInput>,
        uid: String,
        now: Long,
    ) {
        familyDao.upsert(family.copy(name = trimmedName, updatedAtEpochMillis = now))
        fs.collection("families").document(family.id)
            .update(
                "name", trimmedName,
                "updatedBy", uid,
                "updatedAt", FieldValue.serverTimestamp(),
            ).await()

        val existingIds = childDao.getChildrenByFamilyId(family.id).map { it.id }.toSet()
        childrenInputs.filter { it.name.isNotBlank() }.forEach { input ->
            val isExisting = input.id in existingIds
            Log.d("DEBUG_SAVE", "Inviando figlio ${input.name.trim()} con ID ${input.id}")
            Log.d(TAG, "saveFamilyWithChildren child id=${input.id} existing=$isExisting name=${input.name.trim()}")
            val entity = if (isExisting) {
                (childDao.getById(input.id)?.copy(
                    name = input.name.trim(),
                    birthDateEpochMillis = input.birthDateEpochMillis,
                    updatedBy = uid,
                    updatedAtEpochMillis = now,
                )) ?: KBChildEntity(
                    id = input.id, familyId = family.id, name = input.name.trim(),
                    birthDateEpochMillis = input.birthDateEpochMillis,
                    weightKg = null, heightCm = null,
                    createdBy = uid, createdAtEpochMillis = now,
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
            fs.collection("families").document(family.id)
                .collection("children").document(input.id)
                .set(
                    mapOf(
                        "id" to entity.id,
                        "familyId" to family.id,
                        "name" to entity.name,
                        "birthDate" to entity.birthDateEpochMillis?.let {
                            com.google.firebase.Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt())
                        },
                        "createdBy" to entity.createdBy,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "isDeleted" to false,
                    ),
                    com.google.firebase.firestore.SetOptions.merge(),
                ).await()
        }
    }

    fun saveFamilyWithChildren(newName: String, childrenInputs: List<ChildInput>, onDone: () -> Unit) {
        val family = _uiState.value.family
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                isSavingFamily = true,
                savingMessage = "Sincronizzazione in corso…",
                error = null,
            )
            try {
                if (family == null) {
                    Log.i(TAG, "Starting new family creation because state.family was null")
                    val list = childrenInputs
                        .filter { it.name.isNotBlank() }
                        .map {
                            InitialChild(
                                id = it.id,
                                name = it.name.trim(),
                                birthDateMillis = it.birthDateEpochMillis,
                            )
                        }
                    Log.d(
                        TAG,
                        "createFamilyWithChildren: ${list.size} figlio/i da salvare: " +
                            list.joinToString { "${it.name}(${it.id.take(8)}…)" },
                    )
                    if (list.isEmpty()) {
                        _uiState.value = _uiState.value.copy(error = "Aggiungi almeno un figlio con nome.")
                        return@launch
                    }
                    val newFamilyId = creationRepository.createFamilyWithChildren(trimmedName, list)
                    familySyncCenter.startSync(newFamilyId)
                    onDone()
                    return@launch
                }

                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val now = System.currentTimeMillis()
                Log.i(TAG, "saveFamilyWithChildren start familyId=${family.id} children=${childrenInputs.size}")

                familySyncCenter.stopSync()
                Log.d(TAG, "saveFamilyWithChildren stopSync done familyId=${family.id}")

                val fs = FirebaseFirestore.getInstance()
                for (attempt in 1..5) {
                    try {
                        saveFamilyAndChildrenToFirestore(fs, family, trimmedName, childrenInputs, uid, now)
                        break
                    } catch (e: Exception) {
                        if (isPermissionDenied(e) && attempt < 5) {
                            Log.w(
                                TAG,
                                "saveFamilyWithChildren PERMISSION_DENIED attempt=$attempt/5, retry in 2000ms",
                            )
                            delay(2000)
                        } else {
                            throw e
                        }
                    }
                }

                Log.i(TAG, "saveFamilyWithChildren write OK familyId=${family.id}")
                refreshTokenThenResetFirestoreCache()
                Log.d(TAG, "Reset Firestore completato con nuovo token. Riattivazione Sync.")
                familySyncCenter.startSync(family.id)
                onDone()
            } catch (e: Exception) {
                Log.e(TAG, "saveFamilyWithChildren failed familyId=${family?.id} err=${e.message}", e)
                val fid = family?.id
                if (!fid.isNullOrEmpty()) {
                    runCatching { familySyncCenter.startSync(fid) }
                        .onFailure { t ->
                            Log.e(TAG, "saveFamilyWithChildren: startSync dopo errore fallito", t)
                        }
                }
                val msg = when {
                    isPermissionDenied(e) ->
                        "Salvataggio non riuscito dopo 5 tentativi. Riprova tra qualche secondo."
                    else -> e.localizedMessage ?: "Errore salvataggio"
                }
                _uiState.value = _uiState.value.copy(error = msg)
            } finally {
                _uiState.value = _uiState.value.copy(
                    isSavingFamily = false,
                    savingMessage = null,
                )
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
                id = id,
                familyId = family.id,
                name = name.trim(),
                birthDateEpochMillis = birthEpochMillis,
                weightKg = null,
                heightCm = null,
                createdBy = uid,
                createdAtEpochMillis = now,
                updatedBy = uid,
                updatedAtEpochMillis = now,
            )
            childDao.upsert(child)
            db.collection("families").document(family.id)
                .collection("children").document(id)
                .set(
                    mapOf(
                        "id" to child.id,
                        "familyId" to family.id,
                        "name" to child.name,
                        "birthDate" to child.birthDateEpochMillis?.let {
                            com.google.firebase.Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt())
                        },
                        "createdBy" to uid,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedBy" to uid,
                        "updatedAt" to FieldValue.serverTimestamp(),
                        "isDeleted" to false,
                    ),
                    com.google.firebase.firestore.SetOptions.merge()
                ).await()
        }
    }

    fun saveChild(childId: String, name: String, birthEpochMillis: Long?, onDone: () -> Unit) {
        val family = _uiState.value.family ?: return
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            try {
                val uid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                val now = System.currentTimeMillis()
                Log.i(TAG, "saveChild start familyId=${family.id} childId=$childId")
                val existing = childDao.getById(childId)
                val updated = existing?.copy(
                    name = name.trim(),
                    birthDateEpochMillis = birthEpochMillis,
                    updatedBy = uid,
                    updatedAtEpochMillis = now,
                ) ?: KBChildEntity(
                    id = childId,
                    familyId = family.id,
                    name = name.trim(),
                    birthDateEpochMillis = birthEpochMillis,
                    weightKg = null,
                    heightCm = null,
                    createdBy = uid,
                    createdAtEpochMillis = now,
                    updatedBy = uid,
                    updatedAtEpochMillis = now,
                )
                childDao.upsert(updated)
                db.collection("families").document(family.id)
                    .collection("children").document(childId)
                    .set(
                        mapOf(
                            "id" to updated.id,
                            "familyId" to family.id,
                            "name" to updated.name,
                            "birthDate" to updated.birthDateEpochMillis?.let {
                                com.google.firebase.Timestamp(it / 1000, ((it % 1000) * 1_000_000).toInt())
                            },
                            "updatedBy" to uid,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                Log.i(TAG, "saveChild completed familyId=${family.id} childId=$childId")
                onDone()
            } catch (e: Exception) {
                Log.e(TAG, "saveChild failed familyId=${family.id} childId=$childId err=${e.message}", e)
                if (!isPermissionDenied(e)) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = e.localizedMessage ?: "Errore salvataggio figlio",
                    )
                }
            } finally {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun deleteChild(childId: String, onDone: () -> Unit) {
        val family = _uiState.value.family ?: return
        viewModelScope.launch {
            try {
                childDao.deleteById(childId)
                db.collection("families").document(family.id)
                    .collection("children").document(childId)
                    .set(
                        mapOf(
                            "isDeleted" to true,
                            "updatedAt" to FieldValue.serverTimestamp(),
                        ),
                        com.google.firebase.firestore.SetOptions.merge()
                    ).await()
                onDone()
            } catch (e: Exception) {
                if (!isPermissionDenied(e)) {
                    _uiState.value = _uiState.value.copy(error = e.localizedMessage)
                } else {
                    Log.w(TAG, "deleteChild: PERMISSION_DENIED — ignorato")
                }
            }
        }
    }

    override fun onCleared() {
        observeJob?.cancel()
        super.onCleared()
    }
}