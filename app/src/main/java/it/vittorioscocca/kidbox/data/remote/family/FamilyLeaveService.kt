package it.vittorioscocca.kidbox.data.remote.family

import it.vittorioscocca.kidbox.util.KBLog

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
import it.vittorioscocca.kidbox.notifications.HousePaymentReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import javax.inject.Inject

class FamilyLeaveService @Inject constructor(
    private val database: KidBoxDatabase,
    private val familyDao: KBFamilyDao,
    private val familyMemberDao: KBFamilyMemberDao,
    private val childDao: KBChildDao,
    private val familySyncCenter: it.vittorioscocca.kidbox.data.sync.FamilySyncCenter,
    private val housePaymentReminderScheduler: HousePaymentReminderScheduler,
) {
    companion object {
        private const val TAG = "FamilyLeaveService"
    }

    private val db get() = FirebaseFirestore.getInstance()

    /**
     * Uscita volontaria del **membro non proprietario**. Non invocare per il owner: cancella
     * `users/{uid}/memberships/{familyId}` e il doc `members/{uid}` — se l'UID è il owner su Firestore,
     * l'operazione è bloccata (doppio controllo rispetto alla UI).
     *
     * **Non** usare per la revoca di un terzo da parte dell'owner: quella è solo
     * `members/{targetId}.update(isDeleted)` in [FamilySettingsViewModel.removeMember], senza leave/deleteFamily.
     */
    suspend fun leaveFamily(familyId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not authenticated")
        KBLog.data.info("leaveFamily start familyId=$familyId uid=$uid", TAG)

        val familySnap = try {
            db.collection("families").document(familyId).get().await()
        } catch (e: Exception) {
            KBLog.data.error("leaveFamily: lettura famiglia fallita, abort per evitare rimozione errata memberships", TAG, e)
            throw e
        }
        if (familySnap.exists()) {
            val data = familySnap.data.orEmpty()
            val ownerUid = (data["ownerUid"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val createdBy = (data["createdBy"] as? String)?.trim()?.takeIf { it.isNotEmpty() }
            val canonicalOwner = ownerUid ?: createdBy
            if (canonicalOwner != null && canonicalOwner == uid) {
                KBLog.data.error("leaveFamily BLOCCATO: uid è owner su Firestore (ownerUid=$ownerUid createdBy=$createdBy). Usa trasferimento o deleteFamily.", TAG)
                error(
                    "Il proprietario non può uscire con questa azione. Usa «Trasferisci proprietà» o elimina la famiglia.",
                )
            }
        }

        // Stop listeners before wipe (mirrors iOS stopFamilyBundleRealtime)
        familySyncCenter.stopSync()
        KBLog.data.debug("leaveFamily sync stopped familyId=$familyId", TAG)

        // Small delay to let pending snapshots settle (mirrors iOS Task.sleep 150ms)
        kotlinx.coroutines.delay(150)

        // Fire-and-forget Firestore operations
        db.collection("families").document(familyId)
            .collection("members").document(uid).delete()
        db.collection("users").document(uid)
            .collection("memberships").document(familyId).delete().await()

        withContext(Dispatchers.IO) {
            database.petDao().deleteAllByFamily(familyId)
            database.petEventDao().deleteAllByFamily(familyId)
            database.housePaymentDao().listIdsByFamily(familyId).forEach { pid ->
                housePaymentReminderScheduler.cancelForPayment(pid)
            }
            database.housePaymentDao().deleteAllByFamily(familyId)
            database.homeItemDao().deleteAllByFamily(familyId)
            database.vehicleDao().deleteAllByFamily(familyId)
            database.vehicleEventDao().deleteAllByFamily(familyId)
            database.geofenceDao().deleteAllByFamily(familyId)
            val f = familyDao.deleteByFamilyId(familyId)
            val m = familyMemberDao.deleteByFamilyId(familyId)
            val c = childDao.deleteByFamilyId(familyId)
            KBLog.data.info("leaveFamily wipe rows: family=$f members=$m children=$c familyId=$familyId", TAG)
        }
        KBLog.data.info("leaveFamily local data wiped familyId=$familyId uid=$uid", TAG)
    }

    suspend fun transferOwnershipAndLeave(familyId: String, newOwnerUid: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not authenticated")
        KBLog.data.info("transferOwnershipAndLeave start familyId=$familyId uid=$uid newOwnerUid=$newOwnerUid", TAG)
        val batch = db.batch()
        batch.update(
            db.collection("families").document(familyId),
            mapOf(
                "ownerUid" to newOwnerUid,
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.update(
            db.collection("families").document(familyId)
                .collection("members").document(newOwnerUid),
            mapOf(
                "role" to "owner",
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.update(
            db.collection("families").document(familyId)
                .collection("members").document(uid),
            mapOf(
                "role" to "member",
                "updatedBy" to uid,
                "updatedAt" to FieldValue.serverTimestamp(),
            ),
        )
        batch.commit().await()
        KBLog.data.info("transferOwnershipAndLeave batch committed familyId=$familyId uid=$uid newOwnerUid=$newOwnerUid", TAG)
        leaveFamily(familyId)
    }

    /**
     * Eliminazione famiglia (solo owner, ultimo membro in UI). Chiamare solo da quel flusso —
     * **mai** dalla revoca di un singolo membro ([FamilySettingsViewModel.removeMember] non invoca questo metodo).
     */
    suspend fun deleteFamily(familyId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not authenticated")
        KBLog.data.info("deleteFamily start familyId=$familyId uid=$uid", TAG)

        val activeMembers = familyMemberDao.observeActiveByFamilyId(familyId).first()
        if (activeMembers.size > 1) {
            KBLog.data.error("deleteFamily BLOCCATO: ${activeMembers.size} membri attivi in Room (>1) — Cloud Function non invocata", TAG)
            error(
                "Impossibile eliminare la famiglia: risultano ancora altri membri. Sincronizza e riprova.",
            )
        }

        // Stop listeners before wipe (mirrors iOS stopFamilyBundleRealtime)
        familySyncCenter.stopSync()
        KBLog.data.debug("deleteFamily sync stopped familyId=$familyId", TAG)

        // Small delay to let pending snapshots settle (mirrors iOS Task.sleep 150ms)
        kotlinx.coroutines.delay(150)

        // Cloud Function fire-and-forget
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val functions = FirebaseFunctions.getInstance("europe-west1")
                functions.getHttpsCallable("deleteFamily")
                    .call(hashMapOf("familyId" to familyId))
                    .await()
                KBLog.data.info("deleteFamily CF OK familyId=$familyId", TAG)
            } catch (e: Exception) {
                KBLog.data.warning("deleteFamily CF failed (non-fatal): ${e.message}", TAG)
            }
        }

        db.collection("users").document(uid)
            .collection("memberships").document(familyId).delete().await()

        withContext(Dispatchers.IO) {
            database.petDao().deleteAllByFamily(familyId)
            database.petEventDao().deleteAllByFamily(familyId)
            database.housePaymentDao().listIdsByFamily(familyId).forEach { pid ->
                housePaymentReminderScheduler.cancelForPayment(pid)
            }
            database.housePaymentDao().deleteAllByFamily(familyId)
            database.homeItemDao().deleteAllByFamily(familyId)
            database.vehicleDao().deleteAllByFamily(familyId)
            database.vehicleEventDao().deleteAllByFamily(familyId)
            database.geofenceDao().deleteAllByFamily(familyId)
            val f = familyDao.deleteByFamilyId(familyId)
            val m = familyMemberDao.deleteByFamilyId(familyId)
            val c = childDao.deleteByFamilyId(familyId)
            KBLog.data.info("deleteFamily wipe rows: family=$f members=$m children=$c familyId=$familyId", TAG)
        }
        KBLog.data.info("deleteFamily local data wiped familyId=$familyId uid=$uid", TAG)
    }
}