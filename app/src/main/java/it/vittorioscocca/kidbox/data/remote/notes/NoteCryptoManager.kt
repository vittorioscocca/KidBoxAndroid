package it.vittorioscocca.kidbox.data.remote.notes

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

@Singleton
class NoteCryptoManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
) {
    private val transformation = "AES/GCM/NoPadding"

    fun encryptToBase64(
        plainText: String,
        familyId: String,
    ): String {
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.ENCRYPT_MODE, getFamilySecretKey(familyId))
        val cipherBytes = cipher.doFinal(plainText.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(cipher.iv.size + cipherBytes.size)
        System.arraycopy(cipher.iv, 0, combined, 0, cipher.iv.size)
        System.arraycopy(cipherBytes, 0, combined, cipher.iv.size, cipherBytes.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decryptFromBase64(
        encoded: String,
        familyId: String,
    ): String {
        val combined = Base64.decode(encoded, Base64.DEFAULT)
        require(combined.size > 12 + 16) { "Encrypted note payload too small" }
        val iv = combined.copyOfRange(0, 12)
        val encrypted = combined.copyOfRange(12, combined.size)
        val cipher = Cipher.getInstance(transformation)
        cipher.init(Cipher.DECRYPT_MODE, getFamilySecretKey(familyId), GCMParameterSpec(128, iv))
        val plainBytes = cipher.doFinal(encrypted)
        return plainBytes.toString(Charsets.UTF_8)
    }

    private fun getFamilySecretKey(familyId: String): SecretKeySpec {
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        require(uid.isNotBlank()) { "Not authenticated for note crypto" }
        val keyBytes = FamilyKeyStore.loadFamilyKey(context, familyId, uid)
            ?: throw IllegalStateException("Family key missing for familyId=$familyId uid=$uid")
        require(keyBytes.size == 32) { "Invalid family key length=${keyBytes.size}" }
        return SecretKeySpec(keyBytes, "AES")
    }
}
