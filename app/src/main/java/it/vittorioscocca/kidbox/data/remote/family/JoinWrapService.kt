package it.vittorioscocca.kidbox.data.remote.family

import android.content.Context
import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
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

/**
 * Allineato 1:1 con iOS JoinWrapService.
 *
 * Flusso (identico a iOS):
 * 1) Parsa il QR payload (kidbox://join?familyId=&inviteId=&secret=)
 * 2) Verifica autenticazione
 * 3) Firestore transaction: valida invite (expiry/used/secretHash) + marca used
 * 4) Unwrap family key (HKDF + AES-GCM)
 * 5) Salva in FamilyKeyStore
 * 6) Elimina invite document (best effort)
 */
class JoinWrapService(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance(),
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
) {
    data class ParsedPayload(
        val familyId: String,
        val inviteId: String,
        val secret: ByteArray,
    )

    /**
     * Parsa il QR payload.
     * Formato atteso: kidbox://join?familyId=...&inviteId=...&secret=...
     * Restituisce null se il payload non è valido.
     */
    fun parse(payload: String): ParsedPayload? {
        return try {
            val uri = android.net.Uri.parse(payload)
            if (uri.scheme != "kidbox" || uri.host != "join") {
                Log.d(TAG, "parse failed: wrong scheme/host")
                return null
            }
            val familyId = uri.getQueryParameter("familyId")?.takeIf { it.isNotBlank() }
                ?: return null
            val inviteId = uri.getQueryParameter("inviteId")?.takeIf { it.isNotBlank() }
                ?: return null
            val secretStr = uri.getQueryParameter("secret")?.takeIf { it.isNotBlank() }
                ?: return null
            val secret = InviteCrypto.fromBase64Url(secretStr) ?: return null

            Log.i(TAG, "parse OK familyId=$familyId inviteId=$inviteId")
            ParsedPayload(familyId, inviteId, secret)
        } catch (e: Exception) {
            Log.d(TAG, "parse exception: ${e.message}")
            null
        }
    }

    /**
     * Consuma l'invito, recupera la family master key, la salva in FamilyKeyStore.
     * Identico al flusso iOS join(usingQRPayload:).
     */
    suspend fun join(context: Context, qrPayload: String) {
        val uid = auth.currentUser?.uid
            ?: throw JoinInviteError.InvalidPayload.also {
                Log.e(TAG, "join failed: not authenticated")
            }

        val parsed = parse(qrPayload)
            ?: throw JoinInviteError.InvalidPayload.also {
                Log.e(TAG, "join failed: invalid payload")
            }

        val familyId = parsed.familyId
        val inviteId = parsed.inviteId
        val secret = parsed.secret

        val docRef = db.collection("families")
            .document(familyId)
            .collection("invites")
            .document(inviteId)

        Log.i(TAG, "join start familyId=$familyId inviteId=$inviteId")

        // Transaction: valida + marca used
        var inviteData: Map<String, Any>? = null
        db.runTransaction { txn ->
            val snap = txn.get(docRef)
            val d = snap.data ?: throw JoinInviteError.InvalidPayload

            // Scaduto?
            val expiresAt = d["expiresAt"] as? Timestamp
            if (expiresAt != null && expiresAt.toDate().before(Date())) {
                throw JoinInviteError.Expired
            }

            // Già usato?
            if (d["usedAt"] != null) {
                throw JoinInviteError.AlreadyUsed
            }

            // Verifica secret hash
            val expectedHash = d["secretHash"] as? String ?: ""
            val actualHash = InviteCrypto.sha256Base64(secret)
            if (actualHash != expectedHash) {
                throw JoinInviteError.InvalidSecret
            }

            // Marca used
            txn.update(docRef, mapOf(
                "usedAt" to Timestamp(Date()),
                "usedBy" to uid,
            ))

            inviteData = d
        }.await()

        val d = inviteData ?: throw JoinInviteError.InvalidPayload

        // Unwrap family key
        val saltB64    = d["kdfSalt"] as? String ?: throw JoinInviteError.InvalidPayload
        val cipherB64  = d["wrappedKeyCipher"] as? String ?: throw JoinInviteError.InvalidPayload
        val nonceB64   = d["wrappedKeyNonce"] as? String ?: throw JoinInviteError.InvalidPayload
        val tagB64     = d["wrappedKeyTag"] as? String ?: throw JoinInviteError.InvalidPayload

        val salt   = InviteCrypto.fromBase64(saltB64)   ?: throw JoinInviteError.InvalidPayload
        val cipher = InviteCrypto.fromBase64(cipherB64) ?: throw JoinInviteError.InvalidPayload
        val nonce  = InviteCrypto.fromBase64(nonceB64)  ?: throw JoinInviteError.InvalidPayload
        val tag    = InviteCrypto.fromBase64(tagB64)    ?: throw JoinInviteError.InvalidPayload

        val wrapKeyBytes = InviteCrypto.deriveWrapKey(secret, salt, familyId)
        val familyKeyBytes = try {
            InviteCrypto.unwrapFamilyKey(cipher, nonce, tag, wrapKeyBytes)
        } catch (e: Exception) {
            Log.e(TAG, "unwrap failed familyId=$familyId: ${e.message}")
            throw JoinInviteError.InvalidSecret
        }

        // Salva in FamilyKeyStore
        FamilyKeyStore.saveFamilyKey(context, familyKeyBytes, familyId, uid)
        Log.i(TAG, "master key saved familyId=$familyId")

        // Elimina invite (best effort)
        try {
            docRef.delete().await()
            Log.d(TAG, "invite deleted inviteId=$inviteId")
        } catch (e: Exception) {
            Log.d(TAG, "invite delete failed (best effort): ${e.message}")
        }

        // Verifica presenza chiave
        if (FamilyKeyStore.hasFamilyKey(context, familyId, uid)) {
            Log.i(TAG, "keychain verify OK familyId=$familyId")
        } else {
            Log.e(TAG, "keychain verify FAILED familyId=$familyId")
        }
    }

    /** Estrae il &code= dal QR payload (equivalente a iOS JoinPayloadParser.extractCode). */
    fun extractCode(payload: String): String? {
        return try {
            android.net.Uri.parse(payload).getQueryParameter("code")?.takeIf { it.isNotBlank() }
        } catch (_: Exception) { null }
    }
}