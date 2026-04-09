package it.vittorioscocca.kidbox.data.crypto

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Primitive crittografiche per inviti e wrapping della master key di famiglia.
 * Allineato 1:1 con iOS InviteCrypto.swift.
 *
 * - secret (input): blob casuale 32 bytes condiviso via QR (mai su Firestore)
 * - wrapKey: derivata con HKDF-SHA256(secret + salt + familyId)
 * - family master key: wrappata con AES-GCM (confidenzialità + integrità)
 */
object InviteCrypto {

    private val secureRandom = SecureRandom()

    // ── Randomness ────────────────────────────────────────────────────────────

    fun randomBytes(count: Int): ByteArray {
        val out = ByteArray(count)
        secureRandom.nextBytes(out)
        return out
    }

    // ── Hash ──────────────────────────────────────────────────────────────────

    /** SHA256(data) in Base64 standard — identico a iOS sha256Base64. */
    fun sha256Base64(data: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(data)
        return Base64.encodeToString(digest, Base64.NO_WRAP)
    }

    // ── KDF ───────────────────────────────────────────────────────────────────

    /**
     * Deriva una wrap key (32 bytes) con HKDF-SHA256.
     * Identico a iOS: HKDF(secret, salt, info="kidbox-wrap:{familyId}")
     */
    fun deriveWrapKey(secret: ByteArray, salt: ByteArray, familyId: String): ByteArray {
        // HKDF manual implementation (Android non ha HKDF built-in prima di API 33)
        // Step 1: Extract — PRK = HMAC-SHA256(salt, ikm)
        val prk = hmacSha256(salt, secret)
        // Step 2: Expand — T(1) = HMAC-SHA256(PRK, info || 0x01)
        val info = "kidbox-wrap:$familyId".toByteArray(Charsets.UTF_8)
        val t1Input = info + byteArrayOf(0x01)
        val okm = hmacSha256(prk, t1Input)
        // Take first 32 bytes (SHA256 output is already 32 bytes)
        return okm.copyOf(32)
    }

    private fun hmacSha256(key: ByteArray, data: ByteArray): ByteArray {
        val mac = javax.crypto.Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data)
    }

    // ── Wrap / Unwrap ─────────────────────────────────────────────────────────

    data class WrappedKey(
        val cipher: ByteArray,  // ciphertext
        val nonce: ByteArray,   // 12 bytes GCM nonce
        val tag: ByteArray,     // 16 bytes GCM tag
    )

    /**
     * Cifra (wrap) la family master key con AES-GCM.
     * Allineato a iOS: AES.GCM, nonce 12 bytes, tag 16 bytes.
     * Nota: Java AES/GCM produce cipher+tag concatenati — li splittiamo.
     */
    fun wrapFamilyKey(familyKeyBytes: ByteArray, wrapKeyBytes: ByteArray): WrappedKey {
        val nonce = randomBytes(12)
        val wrapKey = SecretKeySpec(wrapKeyBytes, "AES")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, wrapKey, GCMParameterSpec(128, nonce))
        val cipherWithTag = cipher.doFinal(familyKeyBytes)
        // AES/GCM output: ciphertext || tag (tag = last 16 bytes)
        val cipherLen = cipherWithTag.size - 16
        val cipherBytes = cipherWithTag.copyOf(cipherLen)
        val tagBytes = cipherWithTag.copyOfRange(cipherLen, cipherWithTag.size)
        return WrappedKey(cipher = cipherBytes, nonce = nonce, tag = tagBytes)
    }

    /**
     * Decifra (unwrap) la family master key.
     * Throws [AEADBadTagException] se il tag non corrisponde (dati manomessi o secret sbagliato).
     */
    @Throws(AEADBadTagException::class, Exception::class)
    fun unwrapFamilyKey(
        cipher: ByteArray,
        nonce: ByteArray,
        tag: ByteArray,
        wrapKeyBytes: ByteArray,
    ): ByteArray {
        val wrapKey = SecretKeySpec(wrapKeyBytes, "AES")
        val aesCipher = Cipher.getInstance("AES/GCM/NoPadding")
        aesCipher.init(Cipher.DECRYPT_MODE, wrapKey, GCMParameterSpec(128, nonce))
        // Java GCM expect ciphertext || tag concatenated
        val cipherWithTag = cipher + tag
        return aesCipher.doFinal(cipherWithTag)
    }

    // ── Helpers base64url ─────────────────────────────────────────────────────

    fun toBase64Url(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)

    fun fromBase64Url(s: String): ByteArray? = try {
        Base64.decode(s, Base64.NO_WRAP or Base64.URL_SAFE or Base64.NO_PADDING)
    } catch (_: Exception) { null }

    fun toBase64(data: ByteArray): String =
        Base64.encodeToString(data, Base64.NO_WRAP)

    fun fromBase64(s: String): ByteArray? = try {
        Base64.decode(s, Base64.NO_WRAP)
    } catch (_: Exception) { null }

    // ── Key generation ────────────────────────────────────────────────────────

    /** Genera una nuova family master key AES-256 (32 bytes). */
    fun generateFamilyKey(): ByteArray {
        val kg = KeyGenerator.getInstance("AES")
        kg.init(256, secureRandom)
        return kg.generateKey().encoded
    }
}