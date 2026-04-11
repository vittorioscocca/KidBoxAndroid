package it.vittorioscocca.kidbox.data.remote.family

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyDao
import it.vittorioscocca.kidbox.data.local.dao.KBFamilyMemberDao
import it.vittorioscocca.kidbox.data.local.db.KidBoxDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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
) {
    companion object {
        private const val TAG = "FamilyLeaveService"
    }

    private val db = FirebaseFirestore.getInstance()

    suspend fun leaveFamily(familyId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not authenticated")
        Log.i(TAG, "leaveFamily start familyId=$familyId uid=$uid")

        // Stop listeners before wipe (mirrors iOS stopFamilyBundleRealtime)
        familySyncCenter.stopSync()
        Log.d(TAG, "leaveFamily sync stopped familyId=$familyId")

        // Small delay to let pending snapshots settle (mirrors iOS Task.sleep 150ms)
        kotlinx.coroutines.delay(150)

        // Fire-and-forget Firestore operations
        db.collection("families").document(familyId)
            .collection("members").document(uid).delete()
        db.collection("users").document(uid)
            .collection("memberships").document(familyId).delete().await()

        withContext(Dispatchers.IO) {
            val f = familyDao.deleteByFamilyId(familyId)
            val m = familyMemberDao.deleteByFamilyId(familyId)
            val c = childDao.deleteByFamilyId(familyId)
            Log.i(TAG, "leaveFamily wipe rows: family=$f members=$m children=$c familyId=$familyId")
        }
        Log.i(TAG, "leaveFamily local data wiped familyId=$familyId uid=$uid")
    }

    suspend fun transferOwnershipAndLeave(familyId: String, newOwnerUid: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not authenticated")
        Log.i(TAG, "transferOwnershipAndLeave start familyId=$familyId uid=$uid newOwnerUid=$newOwnerUid")
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
        Log.i(TAG, "transferOwnershipAndLeave batch committed familyId=$familyId uid=$uid newOwnerUid=$newOwnerUid")
        leaveFamily(familyId)
    }

    suspend fun deleteFamily(familyId: String) {
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Not authenticated")
        Log.i(TAG, "deleteFamily start familyId=$familyId uid=$uid")

        // Stop listeners before wipe (mirrors iOS stopFamilyBundleRealtime)
        familySyncCenter.stopSync()
        Log.d(TAG, "deleteFamily sync stopped familyId=$familyId")

        // Small delay to let pending snapshots settle (mirrors iOS Task.sleep 150ms)
        kotlinx.coroutines.delay(150)

        // Cloud Function fire-and-forget
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val functions = FirebaseFunctions.getInstance("europe-west1")
                functions.getHttpsCallable("deleteFamily")
                    .call(hashMapOf("familyId" to familyId))
                    .await()
                Log.i(TAG, "deleteFamily CF OK familyId=$familyId")
            } catch (e: Exception) {
                Log.w(TAG, "deleteFamily CF failed (non-fatal): ${e.message}")
            }
        }

        db.collection("users").document(uid)
            .collection("memberships").document(familyId).delete().await()

        withContext(Dispatchers.IO) {
            val f = familyDao.deleteByFamilyId(familyId)
            val m = familyMemberDao.deleteByFamilyId(familyId)
            val c = childDao.deleteByFamilyId(familyId)
            Log.i(TAG, "deleteFamily wipe rows: family=$f members=$m children=$c familyId=$familyId")
        }
        Log.i(TAG, "deleteFamily local data wiped familyId=$familyId uid=$uid")
    }
}