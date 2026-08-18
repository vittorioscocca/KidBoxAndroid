package it.vittorioscocca.kidbox.data.remote

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import it.vittorioscocca.kidbox.data.crypto.MissingFamilyKeyException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_DOC_CRYPTO = "KB_Doc_Crypto"

@Singleton
class DocumentCryptoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
) {
    private val transformation = "AES/GCM/NoPadding"

    fun encrypt(
        plainBytes: ByteArray,
        familyId: String,
    ): ByteArray {
        KBLog.data.debug("encrypt start bytes=${plainBytes.size} familyId=$familyId", TAG_DOC_CRYPTO)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getFamilySecretKey(familyId))
        val encrypted = cipher.doFinal(plainBytes)
        val iv = cipher.iv
        // iOS CryptoKit combined format: nonce(12) + ciphertext + tag(16)
        val out = ByteBuffer.allocate(iv.size + encrypted.size)
            .put(iv)
            .put(encrypted)
            .array()
        KBLog.data.debug("encrypt ok outBytes=${out.size} ivBytes=${iv.size} familyId=$familyId", TAG_DOC_CRYPTO)
        return out
    }

    fun decrypt(
        combined: ByteArray,
        familyId: String,
    ): ByteArray {
        KBLog.data.debug("decrypt start combinedBytes=${combined.size} familyId=$familyId", TAG_DOC_CRYPTO)
        if (combined.size < 12 + 16) {
            throw IllegalArgumentException("Encrypted payload too small")
        }

        // Compat mode:
        // 1) Android format: [4-byte ivSize][iv][cipher+tag]
        // 2) iOS CryptoKit combined: [12-byte nonce][cipher][16-byte tag]
        val prefixedIvSize = ByteBuffer
            .wrap(combined, 0, 4)
            .order(ByteOrder.BIG_ENDIAN)
            .int

        val (iv, encrypted) = if (
            prefixedIvSize in 8..32 &&
            combined.size > (4 + prefixedIvSize + 16)
        ) {
            val ivStart = 4
            val encStart = ivStart + prefixedIvSize
            val ivBytes = combined.copyOfRange(ivStart, encStart)
            val encryptedBytes = combined.copyOfRange(encStart, combined.size)
            KBLog.data.debug("decrypt format=android_prefixed ivBytes=${ivBytes.size} encBytes=${encryptedBytes.size}", TAG_DOC_CRYPTO)
            ivBytes to encryptedBytes
        } else {
            val ivBytes = combined.copyOfRange(0, 12)
            val encryptedBytes = combined.copyOfRange(12, combined.size)
            KBLog.data.debug("decrypt format=cryptokit_combined ivBytes=${ivBytes.size} encBytes=${encryptedBytes.size}", TAG_DOC_CRYPTO)
            ivBytes to encryptedBytes
        }

        val cipher = Cipher.getInstance(transformation)
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getFamilySecretKey(familyId), spec)
        val plain = cipher.doFinal(encrypted)
        KBLog.data.debug("decrypt ok plainBytes=${plain.size} familyId=$familyId", TAG_DOC_CRYPTO)
        return plain
    }

    private fun getFamilySecretKey(familyId: String): SecretKeySpec {
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        require(uid.isNotBlank()) { "Not authenticated for document crypto" }
        val keyBytes = FamilyKeyStore.loadFamilyKey(context, familyId, uid)
            ?: throw MissingFamilyKeyException(familyId)
        require(keyBytes.size == 32) { "Invalid family key length=${keyBytes.size}" }
        return SecretKeySpec(keyBytes, "AES")
    }
}
