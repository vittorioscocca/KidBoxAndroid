package it.vittorioscocca.kidbox.data.chat.crypto

import android.content.Context
import android.util.Base64
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chat text encryption compatible with iOS ChatRemoteStore/NoteCryptoService.
 *
 * Wire format (same as iOS CryptoKit combined):
 *   base64( iv(12) + ciphertext + tag(16) )
 *
 * Key source:
 *   32-byte per-family key from FamilyKeyStore / FamilyKeychainStore.
 */
@Singleton
class ChatCryptoService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
) {
    private val transformation = "AES/GCM/NoPadding"

    fun encryptStringToBase64(
        plainText: String,
        familyId: String,
    ): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getFamilySecretKey(familyId))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(IV_SIZE_BYTES + cipherBytes.size)
        System.arraycopy(cipher.iv, 0, combined, 0, IV_SIZE_BYTES)
        System.arraycopy(cipherBytes, 0, combined, IV_SIZE_BYTES, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptStringFromBase64(
        encoded: String,
        familyId: String,
    ): String {
        val combined = Base64.decode(encoded, Base64.DEFAULT)
        require(combined.size > IV_SIZE_BYTES + TAG_SIZE_BYTES) {
            "Encrypted chat payload too small"
        }
        val iv = combined.copyOfRange(0, IV_SIZE_BYTES)
        val encrypted = combined.copyOfRange(IV_SIZE_BYTES, combined.size)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, getFamilySecretKey(familyId), GCMParameterSpec(TAG_SIZE_BITS, iv))
        val plainBytes = cipher.doFinal(encrypted)
        return plainBytes.toString(Charsets.UTF_8)
    }

    /**
     * Deterministic helper for cross-platform tests (Android <-> iOS).
     * Use the same IV on both platforms to compare exact `textEnc` output.
     */
    fun encryptStringWithIvForTest(
        plainText: String,
        familyId: String,
        iv: ByteArray,
    ): String {
        require(iv.size == IV_SIZE_BYTES) { "IV must be 12 bytes for AES-GCM" }
        val cipher = Cipher.getInstance(transformation)
        cipher.init(
            Cipher.ENCRYPT_MODE,
            getFamilySecretKey(familyId),
            GCMParameterSpec(TAG_SIZE_BITS, iv),
        )
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(IV_SIZE_BYTES + cipherBytes.size)
        System.arraycopy(iv, 0, combined, 0, IV_SIZE_BYTES)
        System.arraycopy(cipherBytes, 0, combined, IV_SIZE_BYTES, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    private fun getFamilySecretKey(familyId: String): SecretKeySpec {
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        require(uid.isNotBlank()) { "Not authenticated for chat crypto" }
        val keyBytes = FamilyKeyStore.loadFamilyKey(context, familyId, uid)
            ?: throw IllegalStateException("Family key missing for familyId=$familyId uid=$uid")
        require(keyBytes.size == KEY_SIZE_BYTES) { "Invalid family key length=${keyBytes.size}" }
        return SecretKeySpec(keyBytes, "AES")
    }

    companion object {
        private const val IV_SIZE_BYTES = 12
        private const val TAG_SIZE_BITS = 128
        private const val TAG_SIZE_BYTES = 16
        private const val KEY_SIZE_BYTES = 32
    }
}
