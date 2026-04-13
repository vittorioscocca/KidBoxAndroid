package it.vittorioscocca.kidbox.data.remote.family

import android.content.Context
import android.util.Base64
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import it.vittorioscocca.kidbox.data.crypto.InviteCrypto
import kotlinx.coroutines.tasks.await
import java.util.Date
import java.util.UUID

private const val TAG = "InviteWrapService"

/**
 * Allineato 1:1 con iOS InviteWrapService.
 *
 * DIFFERENZA rispetto alla versione di Cursor:
 * Cursor scriveva solo secretHash + kdfSalt su Firestore.
 * iOS scrive ANCHE wrappedKeyCipher, wrappedKeyNonce, wrappedKeyTag
 * (la family master key cifrata con AES-GCM).
 * Senza questi campi, JoinWrapService non può recuperare la master key.
 *
 * Flusso:
 * 1) Assicura che esista una family master key (FamilyKeyStore, 32 bytes AES)
 * 2) Genera secret (32 bytes) + salt (16 bytes) per HKDF
 * 3) Deriva wrapKey con HKDF(secret+salt+familyId) e wrappa la master key con AES-GCM
 * 4) Salva su Firestore SOLO hash del secret + salt + wrappedKey (NO secret in chiaro)
 * 5) Costruisce il QR payload con secret URL-safe base64
 */
class InviteWrapService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private val db get() = FirebaseFirestore.getInstance()
    data class Result(
        val inviteId: String,
        val secretBase64url: String,
        val qrPayload: String,
        val expiresAt: Date,
    )

    suspend fun createInvite(
        context: Context,
        familyId: String,
        ttlSeconds: Long = 24 * 3600,
    ): Result {
        val uid = auth.currentUser?.uid ?: error("Not authenticated")
        require(familyId.isNotBlank()) { "familyId vuoto" }

        // 1) Assicura master key in FamilyKeyStore
        val familyKeyBytes: ByteArray = FamilyKeyStore.loadFamilyKey(context, familyId, uid)
            ?: run {
                val newKey = InviteCrypto.generateFamilyKey()
                FamilyKeyStore.saveFamilyKey(context, newKey, familyId, uid)
                Log.i(TAG, "Family master key creata familyId=$familyId")
                newKey
            }

        // 2) Genera secret + salt
        val inviteId = UUID.randomUUID().toString()
        val secret = InviteCrypto.randomBytes(32)
        val salt = InviteCrypto.randomBytes(16)

        // 3) Wrappa la master key
        val wrapKeyBytes = InviteCrypto.deriveWrapKey(secret, salt, familyId)
        val wrapped = InviteCrypto.wrapFamilyKey(familyKeyBytes, wrapKeyBytes)

        val now = Date()
        val expiresAt = Date(now.time + ttlSeconds * 1000L)
        val secretHash = InviteCrypto.sha256Base64(secret)

        // 4) Scrivi su Firestore (NO secret in chiaro)
        db.collection("families")
            .document(familyId)
            .collection("invites")
            .document(inviteId)
            .set(
                mapOf(
                    "createdAt" to Timestamp(now),
                    "createdBy" to uid,
                    "expiresAt" to Timestamp(expiresAt),
                    "secretHash" to secretHash,
                    "kdfSalt" to InviteCrypto.toBase64(salt),
                    "wrappedKeyCipher" to InviteCrypto.toBase64(wrapped.cipher),
                    "wrappedKeyNonce" to InviteCrypto.toBase64(wrapped.nonce),
                    "wrappedKeyTag" to InviteCrypto.toBase64(wrapped.tag),
                    "usedAt" to null,
                    "usedBy" to null,
                ),
            ).await()

        Log.i(TAG, "Invite created inviteId=$inviteId familyId=$familyId")

        // 5) QR payload con secret URL-safe (non su Firestore)
        val secretB64url = InviteCrypto.toBase64Url(secret)
        val qrPayload = "kidbox://join?familyId=$familyId&inviteId=$inviteId&secret=$secretB64url"

        return Result(
            inviteId = inviteId,
            secretBase64url = secretB64url,
            qrPayload = qrPayload,
            expiresAt = expiresAt,
        )
    }
}