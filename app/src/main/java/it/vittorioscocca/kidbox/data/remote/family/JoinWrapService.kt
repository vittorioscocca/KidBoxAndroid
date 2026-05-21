package it.vittorioscocca.kidbox.data.remote.family

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyEscrow
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import it.vittorioscocca.kidbox.data.crypto.InviteCrypto
import kotlinx.coroutines.tasks.await
import java.util.Date

private const val TAG = "JoinWrapService"

sealed class JoinInviteError(message: String) : Exception(message) {
    object InvalidPayload : JoinInviteError("QR non valido.")
    object Expired        : JoinInviteError("Invito scaduto.")
    object InvalidSecret  : JoinInviteError("Invito non valido.")
    object AlreadyUsed    : JoinInviteError("Invito già utilizzato.")
}

class JoinWrapService(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    private val db get() = FirebaseFirestore.getInstance()
    data class ParsedPayload(
        val familyId: String,
        val inviteId: String,
        val secret: ByteArray,
    )

    fun parse(payload: String): ParsedPayload? {
        return try {
            val trimmed = payload.trim()
            val uri = android.net.Uri.parse(trimmed)
            if (uri.scheme != "kidbox" || uri.host != "join") {
                KBLog.data.debug("parse failed: wrong scheme/host", TAG)
                return null
            }
            val familyId = uri.getQueryParameter("familyId")?.takeIf { it.isNotBlank() }
                ?: return null
            val inviteId = uri.getQueryParameter("inviteId")?.takeIf { it.isNotBlank() }
                ?: return null
            val secretStr = uri.getQueryParameter("secret")?.takeIf { it.isNotBlank() }
                ?: return null
            // Secret in Base64 URL-safe (no padding), decodifica allineata a iOS
            val secret = InviteCrypto.fromBase64Url(secretStr) ?: return null

            KBLog.data.info("parse OK familyId=$familyId inviteId=$inviteId", TAG)
            ParsedPayload(familyId, inviteId, secret)
        } catch (e: Exception) {
            KBLog.data.debug("parse exception: ${e.message}", TAG)
            null
        }
    }

    suspend fun join(context: Context, qrPayload: String) {
        val uid = auth.currentUser?.uid
            ?: throw JoinInviteError.InvalidPayload.also {
                KBLog.data.error("join failed: not authenticated", TAG)
            }

        val parsed = parse(qrPayload)
            ?: throw JoinInviteError.InvalidPayload.also {
                KBLog.data.error("join failed: invalid payload", TAG)
            }

        val familyId = parsed.familyId
        val inviteId = parsed.inviteId
        val secret = parsed.secret

        val docRef = db.collection("families")
            .document(familyId)
            .collection("invites")
            .document(inviteId)

        KBLog.data.info("join start familyId=$familyId inviteId=$inviteId", TAG)

        var inviteData: Map<String, Any>? = null
        db.runTransaction { txn ->
            val snap = txn.get(docRef)
            val d = snap.data ?: throw JoinInviteError.InvalidPayload

            val expiresAt = d["expiresAt"] as? Timestamp
            if (expiresAt != null && expiresAt.toDate().before(Date())) {
                throw JoinInviteError.Expired
            }

            if (d["usedAt"] != null) {
                throw JoinInviteError.AlreadyUsed
            }

            val expectedHash = d["secretHash"] as? String ?: ""
            val actualHash = InviteCrypto.sha256Base64(secret)
            if (actualHash != expectedHash) {
                throw JoinInviteError.InvalidSecret
            }

            txn.update(docRef, mapOf(
                "usedAt" to Timestamp(Date()),
                "usedBy" to uid,
            ))

            inviteData = d
        }.await()

        val d = inviteData ?: throw JoinInviteError.InvalidPayload

        val saltB64   = d["kdfSalt"] as? String ?: throw JoinInviteError.InvalidPayload
        val cipherB64 = d["wrappedKeyCipher"] as? String ?: throw JoinInviteError.InvalidPayload
        val nonceB64  = d["wrappedKeyNonce"] as? String ?: throw JoinInviteError.InvalidPayload
        val tagB64    = d["wrappedKeyTag"] as? String ?: throw JoinInviteError.InvalidPayload

        val salt   = InviteCrypto.fromBase64(saltB64)   ?: throw JoinInviteError.InvalidPayload
        val cipher = InviteCrypto.fromBase64(cipherB64) ?: throw JoinInviteError.InvalidPayload
        val nonce  = InviteCrypto.fromBase64(nonceB64)  ?: throw JoinInviteError.InvalidPayload
        val tag    = InviteCrypto.fromBase64(tagB64)    ?: throw JoinInviteError.InvalidPayload

        val wrapKeyBytes = InviteCrypto.deriveWrapKey(secret, salt, familyId)
        val familyKeyBytes = try {
            InviteCrypto.unwrapFamilyKey(cipher, nonce, tag, wrapKeyBytes)
        } catch (e: Exception) {
            KBLog.data.error("unwrap failed familyId=$familyId: ${e.message}", TAG)
            throw JoinInviteError.InvalidSecret
        }

        FamilyKeyStore.saveFamilyKey(context, familyKeyBytes, familyId, uid)
        KBLog.data.info("master key saved familyId=$familyId", TAG)

        FamilyKeyEscrow.backupRawKey(familyKeyBytes, familyId, uid)

        try {
            docRef.delete().await()
            KBLog.data.debug("invite deleted inviteId=$inviteId", TAG)
        } catch (e: Exception) {
            KBLog.data.debug("invite delete failed (best effort): ${e.message}", TAG)
        }

        if (FamilyKeyStore.hasFamilyKey(context, familyId, uid)) {
            KBLog.data.info("keychain verify OK familyId=$familyId", TAG)
        } else {
            KBLog.data.error("keychain verify FAILED familyId=$familyId", TAG)
        }
    }

    fun extractCode(payload: String): String? =
        JoinPayloadParser.extractInviteCode(payload)
}