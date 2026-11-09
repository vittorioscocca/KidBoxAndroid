package it.vittorioscocca.kidbox.data.crypto

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Cassaforte della chiave di famiglia, v2: la parte crittografica pura (JVM,
 * niente Android), testata sul vettore scritto dalla web app.
 *
 * ⚠️ Costanti IDENTICHE a web (`familyKeyVault.js`) e iOS (`FamilyKeyVault.swift`),
 * piano `internal/cifratura-chiave-di-riserva-v2.md` sezione 3.3. Un carattere
 * diverso = chiave diversa = dati illeggibili, senza un errore.
 */
object FamilyKeyVaultCrypto {

    const val CONTEXT = "kidbox-vault-v2"
    const val VERSION = 2
    const val INFO_SLOT_RECOVERY = "kidbox-vault-v2:slot:recoveryCode"
    const val INFO_SLOT_PASSKEY = "kidbox-vault-v2:slot:passkey"
    const val INFO_RK_ID = "kidbox-vault-v2:rk-id"
    const val INFO_FAMILY = "kidbox-vault-v2:family"
    const val AAD_SLOT_PREFIX = "kidbox-vault-v2:rk:"          // + uid
    const val AAD_FAMILY_PREFIX = "kidbox-vault-v2:family:"    // + uid + ":" + familyId
    const val PRF_INPUT = "kidbox-vault-v2:prf"
    /** Base32 di Crockford: niente I, L, O, U. 24 caratteri = 120 bit. */
    const val CODE_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"
    const val CODE_LENGTH = 24

    class WrongSecretException : Exception("wrong secret")

    data class Sealed(val cipher: String, val nonce: String, val tag: String)

    private val random = SecureRandom()

    // ── codice di recupero ──────────────────────────────────────────────

    fun generateRecoveryCode(): String {
        val bytes = ByteArray(15).also { random.nextBytes(it) }
        val sb = StringBuilder()
        var value = 0
        var bits = 0
        for (b in bytes) {
            value = (value shl 8) or (b.toInt() and 0xff)
            bits += 8
            while (bits >= 5) {
                sb.append(CODE_ALPHABET[(value shr (bits - 5)) and 31])
                bits -= 5
            }
            value = value and ((1 shl bits) - 1)
        }
        return formatRecoveryCode(sb.toString())
    }

    /** Forma canonica: maiuscole, senza spazi né trattini, O→0, I/L→1. `null` se non valido. */
    fun normalizeRecoveryCode(input: String): String? {
        val s = input.uppercase()
            .filterNot { it.isWhitespace() || it == '-' }
            .replace('O', '0').replace('I', '1').replace('L', '1')
        if (s.length != CODE_LENGTH || s.any { it !in CODE_ALPHABET }) return null
        return s
    }

    fun formatRecoveryCode(canonical: String): String = canonical.chunked(4).joinToString("-")

    // ── derivazioni ─────────────────────────────────────────────────────

    /** HKDF-SHA256 (RFC 5869). Salt vuoto = 32 byte a zero (SecretKeySpec rifiuta la chiave vuota). */
    fun hkdf(ikm: ByteArray, salt: ByteArray, info: String, length: Int = 32): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        val out = ByteArray(length)
        var t = ByteArray(0)
        var pos = 0
        var counter = 1
        while (pos < length) {
            mac.update(t)
            mac.update(info.toByteArray(Charsets.UTF_8))
            mac.update(counter.toByte())
            t = mac.doFinal()
            val n = minOf(t.size, length - pos)
            System.arraycopy(t, 0, out, pos, n)
            pos += n
            counter++
        }
        return out
    }

    fun rkId(rk: ByteArray): String = b64Url(hkdf(rk, ByteArray(0), INFO_RK_ID, 16))

    fun slotKey(type: String, ikm: ByteArray, salt: ByteArray): ByteArray =
        hkdf(ikm, salt, if (type == "recoveryCode") INFO_SLOT_RECOVERY else INFO_SLOT_PASSKEY)

    fun familyWrapKey(rk: ByteArray): ByteArray = hkdf(rk, ByteArray(0), INFO_FAMILY)

    // ── AES-GCM con AAD, ciphertext e tag separati ──────────────────────

    fun seal(plain: ByteArray, key: ByteArray, aad: String): Sealed {
        val nonce = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
        c.updateAAD(aad.toByteArray(Charsets.UTF_8))
        val out = c.doFinal(plain)
        val cipher = out.copyOfRange(0, out.size - 16)
        val tag = out.copyOfRange(out.size - 16, out.size)
        return Sealed(b64(cipher), b64(nonce), b64(tag))
    }

    fun open(s: Sealed, key: ByteArray, aad: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, unb64(s.nonce)))
        c.updateAAD(aad.toByteArray(Charsets.UTF_8))
        return c.doFinal(unb64(s.cipher) + unb64(s.tag))
    }

    // ── slot e copia v2 ─────────────────────────────────────────────────

    /** Apre lo slot del codice: la RK, o [WrongSecretException]. */
    fun openRecoverySlot(uid: String, code: String, salt: String, sealed: Sealed, expectedRkId: String?): ByteArray {
        val canonical = normalizeRecoveryCode(code) ?: throw WrongSecretException()
        val rk = runCatching {
            open(sealed, slotKey("recoveryCode", canonical.toByteArray(Charsets.UTF_8), unb64(salt)), AAD_SLOT_PREFIX + uid)
        }.getOrNull() ?: throw WrongSecretException()
        if (rk.size != 32) throw WrongSecretException()
        if (expectedRkId != null && rkId(rk) != expectedRkId) throw WrongSecretException()
        return rk
    }

    /** Chiude la RK nello slot del codice: (salt base64, sigillo). */
    fun sealRecoverySlot(uid: String, code: String, rk: ByteArray): Pair<String, Sealed> {
        val canonical = normalizeRecoveryCode(code) ?: throw WrongSecretException()
        val salt = ByteArray(16).also { random.nextBytes(it) }
        val key = slotKey("recoveryCode", canonical.toByteArray(Charsets.UTF_8), salt)
        return b64(salt) to seal(rk, key, AAD_SLOT_PREFIX + uid)
    }

    /** Chiude la RK in uno slot qualunque (passkey: `ikm` = uscita PRF). */
    fun sealSlot(uid: String, type: String, ikm: ByteArray, rk: ByteArray): Pair<String, Sealed> {
        val salt = ByteArray(16).also { random.nextBytes(it) }
        return b64(salt) to seal(rk, slotKey(type, ikm, salt), AAD_SLOT_PREFIX + uid)
    }

    /** Apre uno slot qualunque: la RK, o [WrongSecretException]. */
    fun openSlot(uid: String, type: String, ikm: ByteArray, salt: String, sealed: Sealed, expectedRkId: String?): ByteArray {
        val rk = runCatching { open(sealed, slotKey(type, ikm, unb64(salt)), AAD_SLOT_PREFIX + uid) }
            .getOrNull() ?: throw WrongSecretException()
        if (rk.size != 32) throw WrongSecretException()
        if (expectedRkId != null && rkId(rk) != expectedRkId) throw WrongSecretException()
        return rk
    }

    /** Id dello slot di una passkey, uguale al web: `passkey_` + b64url(sha256(rawId)[0..16]). */
    fun passkeySlotId(rawId: ByteArray): String =
        "passkey_" + b64Url(java.security.MessageDigest.getInstance("SHA-256").digest(rawId).copyOfRange(0, 16))

    fun unb64Url(s: String): ByteArray = Base64.getUrlDecoder().decode(s.trimEnd('='))

    fun sealFamilyKeyV2(uid: String, familyId: String, familyKey: ByteArray, rk: ByteArray): Sealed =
        seal(familyKey, familyWrapKey(rk), "$AAD_FAMILY_PREFIX$uid:$familyId")

    fun openFamilyKeyV2(uid: String, familyId: String, sealed: Sealed, rk: ByteArray): ByteArray? =
        runCatching { open(sealed, familyWrapKey(rk), "$AAD_FAMILY_PREFIX$uid:$familyId") }
            .getOrNull()?.takeIf { it.size == 32 }

    fun newRk(): ByteArray = ByteArray(32).also { random.nextBytes(it) }

    fun b64(b: ByteArray): String = Base64.getEncoder().encodeToString(b)
    fun unb64(s: String): ByteArray = Base64.getDecoder().decode(s)
    fun b64Url(b: ByteArray): String = Base64.getUrlEncoder().withoutPadding().encodeToString(b)
}
