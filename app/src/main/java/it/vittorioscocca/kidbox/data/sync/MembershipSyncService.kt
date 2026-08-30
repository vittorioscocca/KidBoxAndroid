package it.vittorioscocca.kidbox.data.sync

import com.google.firebase.firestore.DocumentChange
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.ListenerRegistration
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyEntity
import it.vittorioscocca.kidbox.data.local.entity.KBFamilyMemberEntity
import it.vittorioscocca.kidbox.domain.family.ownershipUidFromFamilyFirestore
import it.vittorioscocca.kidbox.util.KBLog
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await

/**
 * Listener real-time su `users/{uid}/memberships`.
 *
 * Mirror della soluzione iOS al bug "famiglia creata su un device non appare
 * sull'altro": le tre scritture di creazione famiglia (`families/{fid}`,
 * `families/{fid}/members/{uid}`, `users/{uid}/memberships/{fid}`) **non sono
 * atomiche** su iOS, quindi un singolo `get()` può fallire a vedere la nuova
 * membership. Inoltre tutti i flussi di lettura attuali su Android sono
 * one-shot (`LoginViewModel.checkHasFamilyOnce`, `HomeViewModel
 * .bootstrapFromFirestore`) e si limitano al cold start.
 *
 * Questo servizio mantiene un `addSnapshotListener` persistente sulla
 * collection `users/{uid}/memberships`. Per ogni cambio:
 *
 * - **ADDED / MODIFIED**: scarica `families/{familyId}` + il proprio doc
 *   member, upserta in Room. Così la `FamilySwitcherViewModel.families`
 *   (che osserva `KBFamilyDao.observeAll()`) riflette in tempo reale le
 *   nuove famiglie create su un altro device dallo stesso utente.
 * - **REMOVED**: rimuove `KBFamilyEntity` da Room (cascade sui membri).
 *
 * NOTA: non cambia la famiglia attiva. Lo switch resta una decisione
 * dell'utente (via `FamilySwitcherViewModel.switchToFamily`) o del flusso
 * deep link (`NotificationDeepLinkRouter`).
 */
private const val TAG = "MembershipSyncService"

@Singleton
class MembershipSyncService @Inject constructor(
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val sessionPrefs: FamilySessionPreferences,
) {
    private val db get() = FirebaseFirestore.getInstance()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private var listener: ListenerRegistration? = null
    private var currentUid: String? = null

    /**
     * Avvia il listener su `users/{uid}/memberships`. Idempotente:
     * se è già attivo per lo stesso uid, non fa nulla; se è attivo per un uid
     * diverso, si riallinea (es. dopo un account switch).
     */
    fun start(uid: String) {
        if (uid.isBlank()) {
            KBLog.sync.warning("start ignored (empty uid)", TAG)
            return
        }
        if (currentUid == uid && listener != null) {
            KBLog.sync.debug("start: already listening uid=$uid", TAG)
            return
        }
        stop()
        currentUid = uid
        KBLog.sync.info("start listener uid=$uid", TAG)
        listener = db.collection("users")
            .document(uid)
            .collection("memberships")
            .addSnapshotListener { snap, err ->
                if (err != null) {
                    val code = (err as? FirebaseFirestoreException)?.code
                    if (code == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
                        // Non logghiamo come error: capita dopo logout / cambio account, è benigno.
                        KBLog.sync.warning("memberships PERMISSION_DENIED — stopping listener uid=$uid", TAG)
                        stop()
                        return@addSnapshotListener
                    }
                    KBLog.sync.error("memberships listener error: ${err.message}", TAG)
                    return@addSnapshotListener
                }
                if (snap == null) return@addSnapshotListener
                KBLog.sync.debug("memberships snapshot uid=$uid docs=${snap.documents.size} changes=${snap.documentChanges.size}", TAG)
                for (change in snap.documentChanges) {
                    val familyId = change.document.id.trim()
                    if (familyId.isEmpty()) continue
                    when (change.type) {
                        DocumentChange.Type.ADDED,
                        DocumentChange.Type.MODIFIED -> scope.launch {
                            runCatching { loadAndUpsertFamily(uid, familyId) }
                                .onFailure { e ->
                                    KBLog.sync.warning(
                                        "loadAndUpsertFamily failed familyId=$familyId: ${e.message}",
                                        TAG,
                                    )
                                }
                        }
                        DocumentChange.Type.REMOVED -> scope.launch {
                            runCatching { removeFamilyLocally(familyId) }
                                .onFailure { e ->
                                    KBLog.sync.warning(
                                        "removeFamilyLocally failed familyId=$familyId: ${e.message}",
                                        TAG,
                                    )
                                }
                        }
                    }
                }
            }
    }

    /** Ferma il listener e azzera l'uid corrente (chiamato su logout / access lost). */
    fun stop() {
        listener?.remove()
        listener = null
        currentUid = null
    }

    /**
     * Scarica `families/{familyId}` + `families/{familyId}/members/{uid}` e
     * li upserta in Room (preservando i campi locali della foto hero).
     */
    private suspend fun loadAndUpsertFamily(uid: String, familyId: String) {
        mutex.withLock {
            val familySnap = db.collection("families").document(familyId).get().await()
            if (!familySnap.exists()) {
                KBLog.sync.warning("loadAndUpsertFamily: families/$familyId non esiste, skip", TAG)
                return@withLock
            }
            val data = familySnap.data.orEmpty()
            if (data["isDeleted"] as? Boolean == true) {
                KBLog.sync.info("loadAndUpsertFamily: family marked isDeleted, removing local familyId=$familyId", TAG)
                // Il lock è già nostro: la variante con `withLock` qui si bloccherebbe.
                removeFamilyLocallyLocked(familyId)
                return@withLock
            }

            val now = System.currentTimeMillis()
            val local = familyDao.getById(familyId)
            val remoteUpdatedAt = (data["updatedAt"] as? com.google.firebase.Timestamp)
                ?.toDate()?.time
            val remoteCreatedAt = (data["createdAt"] as? com.google.firebase.Timestamp)
                ?.toDate()?.time
            val createdBy = ownershipUidFromFamilyFirestore(data) ?: local?.createdBy.orEmpty()

            // Stesso confronto sulle date usato da FamilySyncCenter: senza, la foto
            // delle famiglie mostrate nel selettore restava quella in cache anche
            // dopo che un altro membro l'aveva cambiata.
            val hero = resolveHeroPhotoFields(data, local, familyId)

            val entity = KBFamilyEntity(
                id = familyId,
                name = (data["name"] as? String).orEmpty(),
                heroPhotoURL = data["heroPhotoURL"] as? String,
                heroPhotoLocalPath = hero.localPath,
                heroPhotoUpdatedAtEpochMillis = hero.updatedAtEpochMillis,
                heroPhotoScale = hero.scale,
                heroPhotoOffsetX = hero.offsetX,
                heroPhotoOffsetY = hero.offsetY,
                createdBy = createdBy,
                updatedBy = (data["updatedBy"] as? String) ?: local?.updatedBy.orEmpty(),
                createdAtEpochMillis = remoteCreatedAt ?: local?.createdAtEpochMillis ?: now,
                updatedAtEpochMillis = remoteUpdatedAt ?: local?.updatedAtEpochMillis ?: now,
                lastSyncAtEpochMillis = now,
                lastSyncError = null,
            )
            familyDao.upsert(entity)
            KBLog.sync.info("loadAndUpsertFamily upserted familyId=$familyId name='${entity.name}'", TAG)

            // Upsert del proprio doc membro (best-effort: serve per non avere FK orfane
            // quando FamilySwitcher / Home query "members" prima che startSync gestisca
            // la famiglia). Le altre righe member arriveranno via startSync.
            //
            // ⚠️ Solo per la famiglia ATTIVA. `kb_family_members` ha come chiave
            // primaria l'`id`, che qui e in FamilySyncCenter è l'**uid** del
            // membro: chi appartiene a due famiglie ha quindi UNA sola riga, e
            // con `OnConflictStrategy.REPLACE` l'ultima scrittura vince
            // portandosi dietro il proprio `familyId`. Scrivendo anche le
            // famiglie non attive, questo servizio spostava la riga dell'utente
            // sull'altra famiglia: la famiglia aperta perdeva un membro a ogni
            // avvio, e tornava a posto solo col pull-to-refresh della Home
            // (`forceResync` → `reconcileMembersFromServer`). Verificato sui log
            // del 30/08/2026.
            //
            // Questo è un contenimento, NON la correzione: la vera soluzione è
            // una chiave primaria composita (familyId + userId) su
            // `kb_family_members`, che richiede una migrazione Room. Finché
            // resta così, ogni nuovo writer che usa `id = uid` reintroduce il
            // problema in silenzio.
            //
            // Se non c'è ancora una famiglia attiva (primo avvio) si scrive
            // comunque: non c'è nessuno stato da corrompere e la protezione
            // sulle FK orfane serve proprio lì.
            val activeFamilyId = sessionPrefs.getActiveFamilyId()?.trim().orEmpty()
            if (activeFamilyId.isNotEmpty() && activeFamilyId != familyId) {
                KBLog.sync.debug(
                    "loadAndUpsertFamily: salto la riga self-member, famiglia non attiva familyId=$familyId (attiva=$activeFamilyId)",
                    TAG,
                )
                return@withLock
            }

            runCatching {
                val memberSnap = db.collection("families")
                    .document(familyId)
                    .collection("members")
                    .document(uid)
                    .get()
                    .await()
                if (memberSnap.exists()) {
                    val d = memberSnap.data.orEmpty()
                    if (d["isDeleted"] as? Boolean != true) {
                        val member = KBFamilyMemberEntity(
                            id = uid,
                            familyId = familyId,
                            userId = uid,
                            role = (d["role"] as? String) ?: "member",
                            displayName = (d["displayName"] as? String)
                                ?: (d["name"] as? String)
                                ?: (d["fullName"] as? String)
                                ?: (d["email"] as? String)
                                ?: "Membro",
                            email = d["email"] as? String,
                            photoURL = d["photoURL"] as? String,
                            createdAtEpochMillis = (d["createdAt"] as? com.google.firebase.Timestamp)
                                ?.toDate()?.time ?: now,
                            updatedAtEpochMillis = (d["updatedAt"] as? com.google.firebase.Timestamp)
                                ?.toDate()?.time ?: now,
                            updatedBy = (d["updatedBy"] as? String) ?: uid,
                            isDeleted = false,
                        )
                        familyMemberDao.upsert(member)
                    }
                }
            }.onFailure { e ->
                KBLog.sync.warning("self member upsert failed familyId=$familyId: ${e.message}", TAG)
            }
        }
    }

    private suspend fun removeFamilyLocally(familyId: String) {
        mutex.withLock { removeFamilyLocallyLocked(familyId) }
    }

    /**
     * Uscita pulita: via la famiglia e, per cascata sulla foreign key, i suoi
     * membri — di una famiglia a cui non appartieni più non resta nulla in locale.
     *
     * Da chiamare con [mutex] GIÀ preso. Il `Mutex` di Kotlin non è rientrante:
     * la versione con lock chiamata da dentro `loadAndUpsertFamily` (che il lock
     * ce l'ha già) piantava la coroutine per sempre, e con lei ogni sync di
     * membership successiva.
     */
    private suspend fun removeFamilyLocallyLocked(familyId: String) {
        familyDao.deleteByFamilyId(familyId)
        KBLog.sync.info("removeFamilyLocally: familyId=$familyId rimosso da Room", TAG)
    }
}
