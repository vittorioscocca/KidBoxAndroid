package it.vittorioscocca.kidbox.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
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
        Log.d(TAG_DOC_CRYPTO, "encrypt start bytes=${plainBytes.size} familyId=$familyId")
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getFamilySecretKey(familyId))
        val encrypted = cipher.doFinal(plainBytes)
        val iv = cipher.iv
        // iOS CryptoKit combined format: nonce(12) + ciphertext + tag(16)
        val out = ByteBuffer.allocate(iv.size + encrypted.size)
            .put(iv)
            .put(encrypted)
            .array()
        Log.d(TAG_DOC_CRYPTO, "encrypt ok outBytes=${out.size} ivBytes=${iv.size} familyId=$familyId")
        return out
    }

    fun decrypt(
        combined: ByteArray,
        familyId: String,
    ): ByteArray {
        Log.d(TAG_DOC_CRYPTO, "decrypt start combinedBytes=${combined.size} familyId=$familyId")
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
            Log.d(TAG_DOC_CRYPTO, "decrypt format=android_prefixed ivBytes=${ivBytes.size} encBytes=${encryptedBytes.size}")
            ivBytes to encryptedBytes
        } else {
            val ivBytes = combined.copyOfRange(0, 12)
            val encryptedBytes = combined.copyOfRange(12, combined.size)
            Log.d(TAG_DOC_CRYPTO, "decrypt format=cryptokit_combined ivBytes=${ivBytes.size} encBytes=${encryptedBytes.size}")
            ivBytes to encryptedBytes
        }

        val cipher = Cipher.getInstance(transformation)
        val spec = javax.crypto.spec.GCMParameterSpec(128, iv)
        cipher.init(Cipher.DECRYPT_MODE, getFamilySecretKey(familyId), spec)
        val plain = cipher.doFinal(encrypted)
        Log.d(TAG_DOC_CRYPTO, "decrypt ok plainBytes=${plain.size} familyId=$familyId")
        return plain
    }

    private fun getFamilySecretKey(familyId: String): SecretKeySpec {
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        require(uid.isNotBlank()) { "Not authenticated for document crypto" }
        val keyBytes = FamilyKeyStore.loadFamilyKey(context, familyId, uid)
            ?: throw IllegalStateException("Family key missing for familyId=$familyId uid=$uid")
        require(keyBytes.size == 32) { "Invalid family key length=${keyBytes.size}" }
        return SecretKeySpec(keyBytes, "AES")
    }
}
