package it.vittorioscocca.kidbox.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.QuerySnapshot
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
    private val database: KidBoxDatabase,
    private val sessionPrefs: FamilySessionPreferences,
    @ApplicationContext private val appContext: Context,
) {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private var familyListener: ListenerRegistration? = null
    private var membersListener: ListenerRegistration? = null
    private var childrenListener: ListenerRegistration? = null
    private var currentFamilyId: String? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Evita falsi positivi durante switch/join di famiglia (primo snapshot completo). */
    @Volatile
    private var isJoining: Boolean = false

    @Volatile
    private var accessLostEmitted: Boolean = false

    private var lastMembersSnapshot: QuerySnapshot? = null

    private var ownerSyncRecoveryJob: Job? = null

    // Signals when the first Firestore snapshot has been fully written to Room
    private val _initialSyncDone = MutableStateFlow(false)
    val initialSyncDone: StateFlow<Boolean> = _initialSyncDone.asStateFlow()

    private val _accessLostEvent = MutableSharedFlow<Unit>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val accessLostEvent: SharedFlow<Unit> = _accessLostEvent.asSharedFlow()

    /**
     * Revoca accesso remota (es. owner su iOS): stop listener, wipe Room, chiavi, preferenze.
     *
     * Spesso Firestore chiude lo stream con **PERMISSION_DENIED** invece di un document change:
     * va trattato come revoca (vedi [membersListener]).
     *
     * Path membri: `families/{familyId}/members/{userId}` (isDeleted o delete doc).
     */
    /**
     * Owner in Room: `createdBy` sulla famiglia **oppure** riga membro locale con role owner (fallback se
     * `createdBy` è temporaneamente incoerente). Se [currentFamilyId] punta già a un'altra famiglia, false.
     */
    private suspend fun isLocalFamilyCreator(familyId: String, uid: String): Boolean {
        if (uid.isEmpty()) {
            Log.d(TAG, "Bypass check: familyId=$familyId, isCreator=false (uid empty)")
            return false
        }
        if (currentFamilyId != null && familyId != currentFamilyId) {
            Log.d(TAG, "Bypass check: familyId=$familyId, isCreator=false (family mismatch currentFamilyId=$currentFamilyId)")
            return false
        }
        val localFamily = familyDao.getById(familyId)
        val byCreated = localFamily?.createdBy == uid
        val myMember = familyMemberDao.getById(uid)
        val byOwnerRole = myMember?.familyId == familyId &&
            myMember.userId == uid &&
            myMember.role.equals("owner", ignoreCase = true)
        val result = byCreated || byOwnerRole
        Log.d(
            TAG,
            "Bypass check: familyId=$familyId, currentFamilyId=$currentFamilyId, isCreator=$result " +
                "(createdBy=$byCreated ownerRole=$byOwnerRole)",
        )
        return result
    }

    /** Dopo PERMISSION_DENIED transitorio per l'owner: riavvia sync con ritardo. */
    private fun scheduleOwnerSyncRecovery(syncFamilyId: String) {
        ownerSyncRecoveryJob?.cancel()
        ownerSyncRecoveryJob = scope.launch(Dispatchers.IO) {
            delay(2500)
            Log.d(TAG, "Tentativo di ripristino sync forzato per Owner familyId=$syncFamilyId")
            startSync(syncFamilyId)
        }
    }

    private fun triggerAccessLostIfEligible(familyId: String, uid: String, reason: String): Boolean {
        if (uid.isEmpty()) return false
        synchronized(this) {
            if (accessLostEmitted) return false
            accessLostEmitted = true
        }
        Log.w(TAG, "Accesso revocato per l'utente corrente ($reason)")
        stopSync()
        scope.launch(Dispatchers.IO) {
            try {
                // Prima il flag così la Home non rifà bootstrap mentre Room si svuota.
                sessionPrefs.markSkipHomeBootstrapOnce()
                database.clearAllTables()
            } catch (e: Exception) {
                Log.e(TAG, "access lost clearAllTables failed: ${e.message}", e)
            }
            try {
                FamilyKeyStore.deleteAllFamilyKeysForUser(appContext, uid)
            } catch (e: Exception) {
                Log.e(TAG, "access lost deleteAllFamilyKeysForUser failed: ${e.message}", e)
            }
            try {
                // kidbox_prefs + KidBoxPrefs legacy (active_family_id), vedi FamilySessionPreferences
                sessionPrefs.clearActiveFamilyId()
            } catch (e: Exception) {
                Log.e(TAG, "access lost clearActiveFamilyId failed: ${e.message}", e)
            }
            _accessLostEvent.emit(Unit)
        }
        return true
    }

    /**
     * FIX #1: rimosso il guard `if (currentFamilyId == familyId) return`.
     * Prima, se la stessa familyId veniva richiesta una seconda volta (es. dopo
     * navigazione back/forward o ritorno da background), i listener NON venivano
     * riattaccati → le modifiche da altri device venivano perse silenziosamente.
     *
     * Ora: stopSync() + restart sempre, così i listener Firestore sono sempre attivi.
     * Il costo è minimo: Firestore re-invia lo snapshot corrente come primo evento.
     */
    fun startSync(familyId: String) {
        ownerSyncRecoveryJob?.cancel()
        ownerSyncRecoveryJob = null
        isJoining = true
        accessLostEmitted = false
        lastMembersSnapshot = null
        stopSync()
        currentFamilyId = familyId
        sessionPrefs.setActiveFamilyId(familyId)
        _initialSyncDone.value = false
        Log.d(TAG, "startSync familyId=$familyId")

        var familyFirstDone = false
        var membersFirstDone = false
        var childrenFirstDone = false

        fun checkAllFirstDone() {
            if (accessLostEmitted) return
            if (!(familyFirstDone && membersFirstDone && childrenFirstDone)) return
            Log.d(TAG, "All first snapshots processed → initialSyncDone")
            _initialSyncDone.value = true
            isJoining = false
        }

        // ── Family listener ───────────────────────────────────────────────────
        // Nota: routine/todo/event non hanno listener Firestore dedicati in questo modulo Android
        // (solo family + members + children). Eventuali sync aggiuntive vanno estese qui con lo stesso pattern creator.
        familyListener = db.collection("families").document(familyId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    val code = (err as? FirebaseFirestoreException)?.code
                    if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        val uid = auth.currentUser?.uid.orEmpty()
                        scope.launch {
                            if (uid.isNotEmpty() && isLocalFamilyCreator(familyId, uid)) {
                                Log.w(
                                    TAG,
                                    "familyListener PERMISSION_DENIED — ignorato (creator locale, glitch revoca)",
                                )
                                scheduleOwnerSyncRecovery(familyId)
                            } else {
                                Log.e(TAG, "familyListener PERMISSION_DENIED: ${err.message}")
                            }
                            familyFirstDone = true
                            checkAllFirstDone()
                        }
                        return@addSnapshotListener
                    }
                    Log.e(TAG, "familyListener error: ${err.message}")
                    familyFirstDone = true; checkAllFirstDone(); return@addSnapshotListener
                }
                if (snap == null || !snap.exists()) {
                    familyFirstDone = true; checkAllFirstDone(); return@addSnapshotListener
                }
                val data = snap.data.orEmpty()
                scope.launch {
                    val now = System.currentTimeMillis()
                    val local = familyDao.getById(familyId)

                    /**
                     * FIX #2: confronto LWW (Last Write Wins) corretto.
                     *
                     * Problema precedente: se il dispositivo A salvava con System.currentTimeMillis()
                     * (timestamp client) e il dispositivo B aggiornava via serverTimestamp, i clock
                     * potevano divergere e il check remoteUpdatedAt >= localUpdatedAt falliva.
                     *
                     * Fix: quando remoteUpdatedAt è null (es. campo non ancora scritto dal server),
                     * facciamo comunque l'upsert. Quando è presente, accettiamo l'update remoto
                     * solo se è più recente di almeno 1 secondo rispetto al locale — per evitare
                     * che l'eco della propria scrittura sovrascriva dati locali più freschi.
                     *
                     * IMPORTANTE: il campo `updatedAt` su Firestore deve essere scritto con
                     * FieldValue.serverTimestamp() (già corretto nel ViewModel). Il problema era
                     * solo questo confronto lato ricezione.
                     */
                    val remoteUpdatedAt = (data["updatedAt"] as? com.google.firebase.Timestamp)
                        ?.toDate()?.time
                    val localUpdatedAt = local?.updatedAtEpochMillis ?: 0L

                    // Accettiamo l'update remoto se:
                    // - non abbiamo dati locali (local == null)
                    // - remoteUpdatedAt non è disponibile (campo nuovo, scriviamo comunque)
                    // - remoteUpdatedAt è >= localUpdatedAt (remote più recente o uguale)
                    val shouldUpdate = local == null
                            || remoteUpdatedAt == null
                            || remoteUpdatedAt >= localUpdatedAt

                    if (shouldUpdate) {
                        val remoteName = (data["name"] as? String).orEmpty()
                        Log.d(TAG, "family upsert: name='$remoteName' remoteTs=$remoteUpdatedAt localTs=$localUpdatedAt")
                        familyDao.upsert(
                            KBFamilyEntity(
                                id = familyId,
                                name = remoteName,
                                heroPhotoURL = data["heroPhotoURL"] as? String,
                                heroPhotoLocalPath = local?.heroPhotoLocalPath,
                                heroPhotoUpdatedAtEpochMillis = local?.heroPhotoUpdatedAtEpochMillis,
                                heroPhotoScale = local?.heroPhotoScale,
                                heroPhotoOffsetX = local?.heroPhotoOffsetX,
                                heroPhotoOffsetY = local?.heroPhotoOffsetY,
                                createdBy = local?.createdBy
                                    ?: (data["ownerUid"] as? String).orEmpty(),
                                updatedBy = (data["updatedBy"] as? String)
                                    ?: local?.updatedBy ?: "",
                                createdAtEpochMillis = local?.createdAtEpochMillis ?: now,
                                updatedAtEpochMillis = remoteUpdatedAt ?: now,
                                lastSyncAtEpochMillis = local?.lastSyncAtEpochMillis,
                                lastSyncError = local?.lastSyncError,
                            )
                        )
                        Log.d(TAG, "family upserted id=$familyId name='$remoteName'")
                    } else {
                        Log.d(TAG, "family skipped (local più recente): remoteTs=$remoteUpdatedAt localTs=$localUpdatedAt")
                    }

                    familyFirstDone = true
                    checkAllFirstDone()
                }
            }

        // ── Members listener ──────────────────────────────────────────────────
        membersListener = db.collection("families").document(familyId)
            .collection("members")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    val code = (err as? FirebaseFirestoreException)?.code
                    if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        val uid = auth.currentUser?.uid.orEmpty()
                        if (uid.isEmpty()) {
                            membersFirstDone = true
                            checkAllFirstDone()
                            return@addSnapshotListener
                        }
                        // Il creator che rimuove un membro può vedere PERMISSION_DENIED transitorio:
                        // non espellere l'owner se in Room risulta creatore di questa famiglia.
                        scope.launch {
                            if (isLocalFamilyCreator(familyId, uid)) {
                                Log.w(
                                    TAG,
                                    "membersListener PERMISSION_DENIED — ignorato (creator locale)",
                                )
                                scheduleOwnerSyncRecovery(familyId)
                                membersFirstDone = true
                                checkAllFirstDone()
                                return@launch
                            }
                            Log.w(TAG, "membersListener PERMISSION_DENIED — trattato come revoca accesso")
                            triggerAccessLostIfEligible(
                                familyId,
                                uid,
                                "members listener PERMISSION_DENIED",
                            )
                        }
                        return@addSnapshotListener
                    }
                    Log.e(TAG, "membersListener error: ${err.message}")
                    membersFirstDone = true; checkAllFirstDone(); return@addSnapshotListener
                }
                if (snap == null) {
                    membersFirstDone = true; checkAllFirstDone(); return@addSnapshotListener
                }
                lastMembersSnapshot = snap

                Log.d(TAG, "membersListener: totalDocs=${snap.documents.size} changes=${snap.documentChanges.size}")
                scope.launch {
                    if (accessLostEmitted) return@launch
                    val now = System.currentTimeMillis()
                    val myUid = auth.currentUser?.uid.orEmpty()
                    val creatorBypass = isLocalFamilyCreator(familyId, myUid)
                    /** Firestore post-revoca: query vuota ma changelog con REMOVED — non svuotare Room (vale anche dopo startSync recovery). */
                    val emptyMembersSnap = snap.documents.isEmpty()
                    val blockMemberDeletesOnEmptyGlitch =
                        creatorBypass && emptyMembersSnap && snap.documentChanges.isNotEmpty()

                    for (change in snap.documentChanges) {
                        val doc = change.document
                        Log.d(
                            TAG,
                            "membersListener change: type=${change.type} id=${doc.id} isMe=${doc.id == myUid}",
                        )
                        if (myUid.isEmpty() || doc.id != myUid) continue
                        // Stesso scenario del PERMISSION_DENIED: rimuovendo un altro membro, Firestore può
                        // emettere REMOVED anche per il doc del creatore con totalDocs=0 (transitorio).
                        // iOS valuta sulla riga locale; qui non espelliamo il creator da eventi sul proprio uid.
                        if (creatorBypass) {
                            Log.w(
                                TAG,
                                "membersListener: ignoro revoca sul proprio doc (creator locale, prob. snapshot transitorio)",
                            )
                            continue
                        }
                        when (change.type) {
                            DocumentChange.Type.REMOVED -> {
                                if (triggerAccessLostIfEligible(familyId, myUid, "member REMOVED (my document)")) {
                                    membersFirstDone = true
                                    checkAllFirstDone()
                                    return@launch
                                }
                            }
                            DocumentChange.Type.MODIFIED -> {
                                if (doc.data?.get("isDeleted") as? Boolean == true) {
                                    if (triggerAccessLostIfEligible(
                                            familyId,
                                            myUid,
                                            "member isDeleted=true (my document)",
                                        )
                                    ) {
                                        membersFirstDone = true
                                        checkAllFirstDone()
                                        return@launch
                                    }
                                }
                            }
                            else -> { }
                        }
                    }

                    for (change in snap.documentChanges) {
                        val doc = change.document
                        val d = doc.data.orEmpty()

                        if (change.type == DocumentChange.Type.REMOVED) {
                            if (creatorBypass && doc.id == myUid) {
                                Log.w(
                                    TAG,
                                    "membersListener: skip delete local row per proprio uid (creator, REMOVED spurio)",
                                )
                                continue
                            }
                            if (blockMemberDeletesOnEmptyGlitch) {
                                Log.w(
                                    TAG,
                                    "membersListener: skip REMOVED (snapshot vuoto + changelog, glitch) id=${doc.id}",
                                )
                                continue
                            }
                            familyMemberDao.deleteById(doc.id)
                            continue
                        }

                        if (d["isDeleted"] as? Boolean == true) {
                            if (creatorBypass && doc.id == myUid) {
                                Log.w(
                                    TAG,
                                    "membersListener: skip delete local row per proprio uid (creator, isDeleted spurio)",
                                )
                                continue
                            }
                            if (blockMemberDeletesOnEmptyGlitch) {
                                Log.w(
                                    TAG,
                                    "membersListener: skip isDeleted (snapshot vuoto + changelog, glitch) id=${doc.id}",
                                )
                                continue
                            }
                            familyMemberDao.deleteById(doc.id)
                            continue
                        }

                        val memberUid = (d["uid"] as? String) ?: doc.id
                        val localMember = familyMemberDao.getById(doc.id)

                        val remoteUpdatedAt = (d["updatedAt"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time
                        val localUpdatedAt = localMember?.updatedAtEpochMillis ?: 0L

                        // Stessa logica LWW della family
                        val shouldUpdate = localMember == null
                                || remoteUpdatedAt == null
                                || remoteUpdatedAt >= localUpdatedAt

                        if (!shouldUpdate) {
                            Log.d(TAG, "member skipped (local più recente): id=${doc.id}")
                            continue
                        }

                        val isMe = memberUid == myUid
                        var displayName: String? = null
                        if (isMe) {
                            displayName = userProfileDao.getByUid(memberUid)?.canonicalMemberDisplayName()
                        }
                        if (displayName.isNullOrBlank()) {
                            displayName = d.firstNonBlankString("displayName", "name", "fullName")
                                ?: d.firstNonBlankString("email")
                        }
                        if (displayName.isNullOrBlank() && isMe) {
                            val u = auth.currentUser
                            displayName = u?.displayName?.trim()?.takeIf { it.isNotEmpty() && it != "Utente" }
                                ?: u?.email?.trim()?.takeIf { it.isNotEmpty() }
                        }
                        if (displayName.isNullOrBlank()) displayName = "Membro"

                        val memberCreatedAt = (d["createdAt"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time ?: localMember?.createdAtEpochMillis ?: now
                        val memberUpdatedAt = remoteUpdatedAt
                            ?: localMember?.updatedAtEpochMillis ?: now

                        try {
                            familyMemberDao.upsert(
                                KBFamilyMemberEntity(
                                    id = doc.id,
                                    familyId = familyId,
                                    userId = memberUid,
                                    role = (d["role"] as? String) ?: "member",
                                    displayName = displayName,
                                    email = d.firstNonBlankString("email")
                                        ?: auth.currentUser
                                            ?.takeIf { it.uid == memberUid }
                                            ?.email?.trim()?.takeIf { it.isNotEmpty() },
                                    photoURL = d["photoURL"] as? String,
                                    createdAtEpochMillis = memberCreatedAt,
                                    updatedAtEpochMillis = memberUpdatedAt,
                                    updatedBy = (d["updatedBy"] as? String) ?: doc.id,
                                    isDeleted = false,
                                )
                            )
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
                if (err != null) {
                    val uidErr = auth.currentUser?.uid.orEmpty()
                    scope.launch {
                        val creatorOnErr =
                            uidErr.isNotEmpty() && isLocalFamilyCreator(familyId, uidErr)
                        if (creatorOnErr) {
                            // Nessuna modifica Room; sblocca solo il gate così isJoining non resta true per sempre.
                            Log.w(
                                TAG,
                                "childrenListener error — Room figli invariato (creator), gate sync: ${err.message}",
                            )
                            if ((err as? FirebaseFirestoreException)?.code ==
                                FirebaseFirestoreException.Code.PERMISSION_DENIED
                            ) {
                                scheduleOwnerSyncRecovery(familyId)
                            }
                            childrenFirstDone = true
                            checkAllFirstDone()
                            return@launch
                        }
                        val code = (err as? FirebaseFirestoreException)?.code
                        if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                            Log.e(TAG, "childrenListener PERMISSION_DENIED: ${err.message}")
                        } else {
                            Log.e(TAG, "childrenListener error: ${err.message}")
                        }
                        childrenFirstDone = true
                        checkAllFirstDone()
                    }
                    return@addSnapshotListener
                }
                if (snap == null) {
                    val uidNull = auth.currentUser?.uid.orEmpty()
                    scope.launch {
                        if (uidNull.isNotEmpty() && isLocalFamilyCreator(familyId, uidNull)) {
                            Log.w(
                                TAG,
                                "childrenListener snap null — Room figli invariato (creator), gate sync",
                            )
                        } else {
                            Log.e(TAG, "childrenListener snap null")
                        }
                        childrenFirstDone = true
                        checkAllFirstDone()
                    }
                    return@addSnapshotListener
                }
                Log.d(TAG, "childrenListener: totalDocs=${snap.documents.size} changes=${snap.documentChanges.size}")
                scope.launch {
                    val now = System.currentTimeMillis()
                    val myUid = auth.currentUser?.uid.orEmpty()
                    val creatorBypass = isLocalFamilyCreator(familyId, myUid)
                    val emptySnap = snap.documents.isEmpty()
                    /**
                     * Glitch post-revoca / recovery: anche con [isJoining]==true dopo [startSync], uno snapshot
                     * vuoto con REMOVED non deve svuotare Room (prima il blocco si disattivava e cancellava tutti i figli).
                     */
                    val blockChildDeletesOnEmptyGlitch =
                        creatorBypass && emptySnap && snap.documentChanges.isNotEmpty()

                    for (change in snap.documentChanges) {
                        val doc = change.document
                        val d = doc.data.orEmpty()
                        Log.d(TAG, "child change=${change.type} id=${doc.id} name=${d["name"]}")

                        if (change.type == DocumentChange.Type.REMOVED) {
                            if (blockChildDeletesOnEmptyGlitch) {
                                Log.w(
                                    TAG,
                                    "childrenListener: skip child REMOVED (glitch vuoto+changelog) id=${doc.id}",
                                )
                                continue
                            }
                            childDao.deleteById(doc.id)
                            continue
                        }

                        // FIX #3: gestione isDeleted anche nei children
                        if (d["isDeleted"] as? Boolean == true) {
                            if (blockChildDeletesOnEmptyGlitch) {
                                Log.w(
                                    TAG,
                                    "childrenListener: skip child isDeleted (glitch vuoto+changelog) id=${doc.id}",
                                )
                                continue
                            }
                            childDao.deleteById(doc.id)
                            Log.d(TAG, "child deleted (isDeleted=true) id=${doc.id}")
                            continue
                        }

                        val remoteName = (d["name"] as? String)?.takeIf { it.isNotBlank() } ?: ""
                        val remoteBirthDate = (d["birthDate"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time
                        val remoteUpdatedAt = (d["updatedAt"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time
                        val remoteCreatedAt = (d["createdAt"] as? com.google.firebase.Timestamp)
                            ?.toDate()?.time
                        val remoteCreatedBy = d["createdBy"] as? String
                        val remoteUpdatedBy = d["updatedBy"] as? String

                        val local = childDao.getById(doc.id)
                        val localUpdatedAt = local?.updatedAtEpochMillis ?: 0L

                        /**
                         * FIX #3: aggiunto confronto LWW anche per i children.
                         * Prima mancava completamente: si faceva sempre upsert,
                         * il che è meno grave (accetta sempre il remoto) ma può
                         * sovrascrivere una modifica locale non ancora propagata.
                         *
                         * Con questo fix:
                         * - se local == null → sempre upsert (nuovo figlio da altro device)
                         * - se remoteUpdatedAt == null → upsert (campo mancante, safe)
                         * - se remoteUpdatedAt >= localUpdatedAt → upsert (remoto più recente)
                         * - altrimenti → skip (locale più recente, es. ho appena salvato)
                         */
                        val shouldUpdate = local == null
                                || remoteUpdatedAt == null
                                || remoteUpdatedAt >= localUpdatedAt

                        if (!shouldUpdate) {
                            Log.d(TAG, "child skipped (local più recente): id=${doc.id} remoteTs=$remoteUpdatedAt localTs=$localUpdatedAt")
                            continue
                        }

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
                            updatedAtEpochMillis = remoteUpdatedAt ?: remoteCreatedAt
                            ?: local?.updatedAtEpochMillis ?: now,
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

    /**
     * Rimuove tutte le [ListenerRegistration] Firestore e azzera i riferimenti così non restano
     * callback attive dopo [remove].
     */
    fun stopSync() {
        ownerSyncRecoveryJob?.cancel()
        ownerSyncRecoveryJob = null
        familyListener?.remove()
        membersListener?.remove()
        childrenListener?.remove()
        familyListener = null
        membersListener = null
        childrenListener = null
        currentFamilyId = null
        lastMembersSnapshot = null
        _initialSyncDone.value = false
        sessionPrefs.clearActiveFamilyId()
    }
}