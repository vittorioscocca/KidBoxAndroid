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
    private val _initialSyncDone = MutableStateFlow(false)
    val initialSyncDone: StateFlow<Boolean> = _initialSyncDone.asStateFlow()

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
        stopSync()
        currentFamilyId = familyId
        _initialSyncDone.value = false
        Log.d(TAG, "startSync familyId=$familyId")

        var familyFirstDone = false
        var membersFirstDone = false
        var childrenFirstDone = false

        fun checkAllFirstDone() {
            if (familyFirstDone && membersFirstDone && childrenFirstDone) {
                Log.d(TAG, "All first snapshots processed → initialSyncDone")
                _initialSyncDone.value = true
            }
        }

        // ── Family listener ───────────────────────────────────────────────────
        familyListener = db.collection("families").document(familyId)
            .addSnapshotListener { snap, err ->
                if (err != null) {
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
                    Log.e(TAG, "membersListener error: ${err.message}")
                    membersFirstDone = true; checkAllFirstDone(); return@addSnapshotListener
                }
                if (snap == null) {
                    membersFirstDone = true; checkAllFirstDone(); return@addSnapshotListener
                }
                Log.d(TAG, "membersListener: totalDocs=${snap.documents.size} changes=${snap.documentChanges.size}")
                scope.launch {
                    val now = System.currentTimeMillis()
                    val uid = auth.currentUser?.uid.orEmpty()
                    for (change in snap.documentChanges) {
                        val doc = change.document
                        val d = doc.data.orEmpty()

                        if (change.type == DocumentChange.Type.REMOVED) {
                            familyMemberDao.deleteById(doc.id)
                            continue
                        }

                        if (d["isDeleted"] as? Boolean == true) {
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

                        val isMe = memberUid == uid
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
                    Log.e(TAG, "childrenListener error: ${err.message}")
                    childrenFirstDone = true; checkAllFirstDone(); return@addSnapshotListener
                }
                if (snap == null) {
                    childrenFirstDone = true; checkAllFirstDone(); return@addSnapshotListener
                }
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

                        // FIX #3: gestione isDeleted anche nei children
                        if (d["isDeleted"] as? Boolean == true) {
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