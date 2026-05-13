package it.vittorioscocca.kidbox.data.crypto

import android.content.Context
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

private const val TAG = "FamilyKeyEscrow"
private const val ESCROW_VERSION = 1

/**
 * Backup / recovery della family master key su Firestore, allineato a iOS
 * [FamilyKeyEscrowService]: `families/{familyId}/memberKeyBackups/{userId}` con
 * campi `cipher`, `nonce`, `tag` (Base64), `updatedAt`, `version`.
 */
object FamilyKeyEscrow {

    private val db get() = FirebaseFirestore.getInstance()

    /**
     * Se la chiave non è in [FamilyKeyStore], prova recupero dall'escrow e salva in locale.
     * Idempotente: se la chiave c'è già, ritorna subito `true`.
     */
    suspend fun ensureFamilyKeyAvailable(context: Context, familyId: String, userId: String): Boolean {
        if (familyId.isBlank() || userId.isBlank()) return false
        if (FamilyKeyStore.loadFamilyKey(context, familyId, userId) != null) return true
        Log.i(TAG, "key missing locally, trying escrow recovery familyId=$familyId")
        val recovered = recover(familyId, userId) ?: run {
            Log.e(TAG, "escrow recovery failed (no backup or bad data) familyId=$familyId")
            return false
        }
        return try {
            FamilyKeyStore.saveFamilyKey(context, recovered, familyId, userId)
            val ok = FamilyKeyStore.loadFamilyKey(context, familyId, userId) != null
            if (ok) {
                Log.i(TAG, "escrow recovery OK familyId=$familyId")
                backup(context, familyId, userId)
            } else {
                Log.e(TAG, "escrow recovery save did not persist familyId=$familyId")
            }
            ok
        } catch (e: Exception) {
            Log.e(TAG, "save after escrow failed familyId=$familyId: ${e.message}", e)
            false
        }
    }

    /**
     * Best-effort: cifra la family key con la chiave escrow e scrive su Firestore.
     */
    suspend fun backup(context: Context, familyId: String, userId: String) {
        if (familyId.isBlank() || userId.isBlank()) return
        val keyBytes = FamilyKeyStore.loadFamilyKey(context, familyId, userId) ?: run {
            Log.w(TAG, "backup skipped: no local family key familyId=$familyId")
            return
        }
        backupRawKey(keyBytes, familyId, userId)
    }

    suspend fun backupRawKey(keyBytes: ByteArray, familyId: String, userId: String) {
        if (familyId.isBlank() || userId.isBlank()) return
        require(keyBytes.size == 32) { "Family key must be 32 bytes" }
        try {
            val escrowWrap = InviteCrypto.deriveEscrowWrapKey(userId, familyId)
            val wrapped = InviteCrypto.wrapFamilyKey(keyBytes, escrowWrap)
            val data = mapOf(
                "cipher" to InviteCrypto.toBase64(wrapped.cipher),
                "nonce" to InviteCrypto.toBase64(wrapped.nonce),
                "tag" to InviteCrypto.toBase64(wrapped.tag),
                "updatedAt" to FieldValue.serverTimestamp(),
                "version" to ESCROW_VERSION,
            )
            db.collection("families").document(familyId)
                .collection("memberKeyBackups").document(userId)
                .set(data)
                .await()
            Log.i(TAG, "backup OK familyId=$familyId userId=$userId")
        } catch (e: Exception) {
            Log.e(TAG, "backup failed familyId=$familyId: ${e.message}", e)
        }
    }

    suspend fun recover(familyId: String, userId: String): ByteArray? {
        if (familyId.isBlank() || userId.isBlank()) return null
        return try {
            val snap = db.collection("families").document(familyId)
                .collection("memberKeyBackups").document(userId)
                .get()
                .await()
            val d = snap.data ?: run {
                Log.i(TAG, "recover: no backup doc familyId=$familyId")
                return null
            }
            val cipherB64 = d["cipher"] as? String
            val nonceB64 = d["nonce"] as? String
            val tagB64 = d["tag"] as? String
            val cipher = cipherB64?.let { InviteCrypto.fromBase64(it) }
            val nonce = nonceB64?.let { InviteCrypto.fromBase64(it) }
            val tag = tagB64?.let { InviteCrypto.fromBase64(it) }
            if (cipher == null || nonce == null || tag == null) {
                Log.i(TAG, "recover: malformed backup familyId=$familyId")
                return null
            }
            val escrowWrap = InviteCrypto.deriveEscrowWrapKey(userId, familyId)
            InviteCrypto.unwrapFamilyKey(cipher, nonce, tag, escrowWrap)
        } catch (e: Exception) {
            Log.e(TAG, "recover failed familyId=$familyId: ${e.message}", e)
            null
        }
    }
}
