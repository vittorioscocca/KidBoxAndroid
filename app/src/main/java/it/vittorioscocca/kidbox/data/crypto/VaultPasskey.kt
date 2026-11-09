package it.vittorioscocca.kidbox.data.crypto

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import androidx.credentials.CreatePublicKeyCredentialRequest
import androidx.credentials.CreatePublicKeyCredentialResponse
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetPublicKeyCredentialOption
import androidx.credentials.PublicKeyCredential
import androidx.credentials.exceptions.CreateCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialCancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.security.SecureRandom

/**
 * La cerimonia WebAuthn della cassaforte su Android (piano v2, sezione 3.4):
 * Credential Manager con l'estensione PRF, stesso RP ID (`kidboxapp.com`,
 * Digital Asset Links in `assetlinks.json` con `get_login_creds`) e stesso
 * input PRF del web. Una passkey creata qui apre la cassaforte anche dal
 * browser, e viceversa, se il gestore delle passkey la sincronizza.
 *
 * Restituisce solo (credentialId, uscita PRF): la crittografia è in
 * [FamilyKeyVault]. Senza PRF → [UnsupportedException]; annullata → [CancelledException].
 */
object VaultPasskey {
    const val RP_ID = "kidboxapp.com"

    class UnsupportedException(reason: String) : Exception("passkey non disponibile: $reason")
    class CancelledException : Exception("annullata")

    data class Result(val credentialId: String, val prf: ByteArray)

    private val random = SecureRandom()

    /** Le passkey col Credential Manager servono Android 9+. */
    fun maybeSupported(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P

    private fun challenge() = FamilyKeyVaultCrypto.b64Url(ByteArray(32).also { random.nextBytes(it) })
    private fun prfInput() = FamilyKeyVaultCrypto.b64Url(FamilyKeyVaultCrypto.PRF_INPUT.toByteArray(Charsets.UTF_8))
    private fun prfExt() = JSONObject().put("prf", JSONObject().put("eval", JSONObject().put("first", prfInput())))

    fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    private fun prfFirst(json: JSONObject): ByteArray? =
        json.optJSONObject("clientExtensionResults")?.optJSONObject("prf")?.optJSONObject("results")
            ?.optString("first")?.takeIf { it.isNotBlank() }?.let { FamilyKeyVaultCrypto.unb64Url(it) }

    /** Crea una passkey per `uid` e ne ricava l'uscita PRF. */
    suspend fun create(context: Context, uid: String, name: String, displayName: String): Result {
        if (!maybeSupported()) throw UnsupportedException("android")
        val activity = context.findActivity() ?: throw UnsupportedException("activity")
        val req = JSONObject()
            .put("challenge", challenge())
            .put("rp", JSONObject().put("id", RP_ID).put("name", "KidBox"))
            .put("user", JSONObject()
                .put("id", FamilyKeyVaultCrypto.b64Url(uid.toByteArray(Charsets.UTF_8)))
                .put("name", name).put("displayName", displayName))
            .put("pubKeyCredParams", JSONArray()
                .put(JSONObject().put("type", "public-key").put("alg", -7))
                .put(JSONObject().put("type", "public-key").put("alg", -257)))
            .put("authenticatorSelection", JSONObject()
                .put("residentKey", "required").put("requireResidentKey", true)
                .put("userVerification", "required"))
            .put("timeout", 120000)
            .put("extensions", prfExt())
        val resp = try {
            CredentialManager.create(activity)
                .createCredential(activity, CreatePublicKeyCredentialRequest(req.toString())) as? CreatePublicKeyCredentialResponse
        } catch (e: CreateCredentialCancellationException) {
            throw CancelledException()
        } ?: throw UnsupportedException("risposta")
        val json = JSONObject(resp.registrationResponseJson)
        val credentialId = json.optString("rawId").ifBlank { json.optString("id") }
        if (credentialId.isBlank()) throw UnsupportedException("rawId")
        prfFirst(json)?.takeIf { it.size >= 32 }?.let { return Result(credentialId, it) }
        val enabled = json.optJSONObject("clientExtensionResults")?.optJSONObject("prf")?.opt("enabled")
        if (enabled == false) throw UnsupportedException("prf")
        // Molti gestori dicono solo `enabled` alla creazione: l'uscita arriva con un'asserzione.
        return assert(context, listOf(credentialId))
    }

    /** Asserzione con una delle passkey registrate: quale è stata usata e la sua uscita PRF. */
    suspend fun assert(context: Context, allowIds: List<String>): Result {
        if (!maybeSupported()) throw UnsupportedException("android")
        if (allowIds.isEmpty()) throw UnsupportedException("nessuna")
        val activity = context.findActivity() ?: throw UnsupportedException("activity")
        val req = JSONObject()
            .put("challenge", challenge())
            .put("rpId", RP_ID)
            .put("userVerification", "required")
            .put("timeout", 120000)
            .put("allowCredentials", JSONArray().apply {
                allowIds.forEach { put(JSONObject().put("type", "public-key").put("id", it)) }
            })
            .put("extensions", prfExt())
        val cred = try {
            CredentialManager.create(activity).getCredential(
                activity, GetCredentialRequest(listOf(GetPublicKeyCredentialOption(req.toString()))),
            ).credential
        } catch (e: GetCredentialCancellationException) {
            throw CancelledException()
        }
        val pk = cred as? PublicKeyCredential ?: throw UnsupportedException("tipo")
        val json = JSONObject(pk.authenticationResponseJson)
        val credentialId = json.optString("rawId").ifBlank { json.optString("id") }
        val prf = prfFirst(json)?.takeIf { it.size >= 32 } ?: throw UnsupportedException("prf")
        return Result(credentialId, prf)
    }
}
