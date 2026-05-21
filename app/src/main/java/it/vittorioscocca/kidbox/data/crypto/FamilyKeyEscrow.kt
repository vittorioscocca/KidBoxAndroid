package it.vittorioscocca.kidbox.data.crypto

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
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
        KBLog.crypto.info("key missing locally, trying escrow recovery familyId=$familyId", TAG)
        val recovered = recover(familyId, userId) ?: run {
            KBLog.crypto.error("escrow recovery failed (no backup or bad data) familyId=$familyId", TAG)
            return false
        }
        return try {
            FamilyKeyStore.saveFamilyKey(context, recovered, familyId, userId)
            val ok = FamilyKeyStore.loadFamilyKey(context, familyId, userId) != null
            if (ok) {
                KBLog.crypto.info("escrow recovery OK familyId=$familyId", TAG)
                backup(context, familyId, userId)
            } else {
                KBLog.crypto.error("escrow recovery save did not persist familyId=$familyId", TAG)
            }
            ok
        } catch (e: Exception) {
            KBLog.crypto.error("save after escrow failed familyId=$familyId: ${e.message}", TAG, e)
            false
        }
    }

    /**
     * Best-effort: cifra la family key con la chiave escrow e scrive su Firestore.
     */
    suspend fun backup(context: Context, familyId: String, userId: String) {
        if (familyId.isBlank() || userId.isBlank()) return
        val keyBytes = FamilyKeyStore.loadFamilyKey(context, familyId, userId) ?: run {
            KBLog.crypto.warning("backup skipped: no local family key familyId=$familyId", TAG)
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
            KBLog.crypto.info("backup OK familyId=$familyId userId=$userId", TAG)
        } catch (e: Exception) {
            KBLog.crypto.error("backup failed familyId=$familyId: ${e.message}", TAG, e)
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
                KBLog.crypto.info("recover: no backup doc familyId=$familyId", TAG)
                return null
            }
            val cipherB64 = d["cipher"] as? String
            val nonceB64 = d["nonce"] as? String
            val tagB64 = d["tag"] as? String
            val cipher = cipherB64?.let { InviteCrypto.fromBase64(it) }
            val nonce = nonceB64?.let { InviteCrypto.fromBase64(it) }
            val tag = tagB64?.let { InviteCrypto.fromBase64(it) }
            if (cipher == null || nonce == null || tag == null) {
                KBLog.crypto.info("recover: malformed backup familyId=$familyId", TAG)
                return null
            }
            val escrowWrap = InviteCrypto.deriveEscrowWrapKey(userId, familyId)
            InviteCrypto.unwrapFamilyKey(cipher, nonce, tag, escrowWrap)
        } catch (e: Exception) {
            KBLog.crypto.error("recover failed familyId=$familyId: ${e.message}", TAG, e)
            null
        }
    }
}
