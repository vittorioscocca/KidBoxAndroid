package it.vittorioscocca.kidbox.data.crypto

import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * AES-GCM in formato **CryptoKit combined** (12 byte nonce + ciphertext + 16 byte tag),
 * con HKDF-SHA256 per `visibility == private` (solo creatore), allineato a iOS [PasswordCypher].
 *
 * Usato da [PasswordCypher] dopo aver caricato la family key; esposto per test JVM senza Android Context.
 */
internal object PasswordCryptoEngine {

    private val hkdfInfoOnlyCreator = "KidBox.Password.v1.onlyCreator".toByteArray(Charsets.UTF_8)
    private const val AES_GCM = "AES/GCM/NoPadding"

    sealed class PasswordCryptoException(message: String) : Exception(message) {
        data object NotCreatorForPrivateEntry : PasswordCryptoException("Not creator for private password entry")
    }

    fun encrypt(plaintext: String, familyKey32: ByteArray, visibility: String, createdBy: String): ByteArray {
        require(familyKey32.size == 32) { "family key must be 32 bytes" }
        val key = wrappingKey(familyKey32, visibility, createdBy)
        return aesGcmEncryptCombined(plaintext.toByteArray(Charsets.UTF_8), key)
    }

    fun decrypt(ciphertext: ByteArray, familyKey32: ByteArray, visibility: String, createdBy: String, decryptingUid: String): String {
        require(familyKey32.size == 32) { "family key must be 32 bytes" }
        val vis = KBVisibilityScope.normalizedPassword(visibility)
        if (vis == KBVisibilityScope.ONLY_CREATOR && decryptingUid != createdBy) {
            throw PasswordCryptoException.NotCreatorForPrivateEntry
        }
        val key = wrappingKey(familyKey32, visibility, createdBy)
        val plain = aesGcmDecryptCombined(ciphertext, key)
        return plain.toString(Charsets.UTF_8)
    }

    private fun wrappingKey(familyKey32: ByteArray, visibility: String, createdBy: String): SecretKeySpec {
        val vis = KBVisibilityScope.normalizedPassword(visibility)
        if (vis == KBVisibilityScope.ONLY_CREATOR) {
            val salt = createdBy.toByteArray(Charsets.UTF_8)
            val derived = hkdfSha256(familyKey32, salt, hkdfInfoOnlyCreator, 32)
            return SecretKeySpec(derived, "AES")
        }
        return SecretKeySpec(familyKey32, "AES")
    }

    /** RFC 5869 HKDF-SHA256 (Extract + Expand). */
    private fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, outLen: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val saltKey = if (salt.isEmpty()) ByteArray(32) { 0 } else salt
        mac.init(SecretKeySpec(saltKey, "HmacSHA256"))
        val prk = mac.doFinal(ikm)

        val hashLen = 32
        val n = (outLen + hashLen - 1) / hashLen
        val okm = ByteArray(outLen)
        var offset = 0
        var tPrev = ByteArray(0)
        for (i in 1..n) {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val input = ByteArray(tPrev.size + info.size + 1)
            tPrev.copyInto(input, 0)
            info.copyInto(input, tPrev.size)
            input[input.size - 1] = i.toByte()
            val t = mac.doFinal(input)
            val copyLen = minOf(t.size, outLen - offset)
            t.copyInto(okm, offset, 0, copyLen)
            offset += copyLen
            tPrev = t
        }
        return okm
    }

    private fun aesGcmEncryptCombined(plain: ByteArray, key: SecretKeySpec): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val encrypted = cipher.doFinal(plain)
        val iv = cipher.iv
        val out = ByteBuffer.allocate(iv.size + encrypted.size)
            .put(iv)
            .put(encrypted)
            .array()
        return out
    }

    private fun aesGcmDecryptCombined(combined: ByteArray, key: SecretKeySpec): ByteArray {
        if (combined.size < 12 + 16) throw IllegalArgumentException("Encrypted payload too small")

        val prefixedIvSize = ByteBuffer
            .wrap(combined, 0, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .int

        val (iv, enc) = if (
            prefixedIvSize in 8..32 &&
            combined.size > (4 + prefixedIvSize + 16)
        ) {
            val ivStart = 4
            val encStart = ivStart + prefixedIvSize
            combined.copyOfRange(ivStart, encStart) to combined.copyOfRange(encStart, combined.size)
        } else {
            combined.copyOfRange(0, 12) to combined.copyOfRange(12, combined.size)
        }

        val cipher = Cipher.getInstance(AES_GCM)
        val spec = GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, key, spec)
        return cipher.doFinal(enc)
    }
}
