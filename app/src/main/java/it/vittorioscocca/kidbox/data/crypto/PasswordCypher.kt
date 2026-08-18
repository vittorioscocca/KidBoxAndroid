package it.vittorioscocca.kidbox.data.crypto

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cifratura stringhe per password/gruppi: AES-GCM (stesso layout di [it.vittorioscocca.kidbox.data.remote.DocumentCryptoManager])
 * e chiave da [FamilyKeyStore], come iOS `FamilyKeychainStore` + [PasswordCypher.swift].
 *
 * Per `visibility == private` (solo creatore): HKDF-SHA256 con salt = UTF-8 di [createdBy] e
 * info `KidBox.Password.v1.onlyCreator` — vedi [PasswordCryptoEngine].
 *
 * @param familyKeyUserId se non null/non vuoto, usato al posto di `FirebaseAuth.currentUser.uid`
 * per caricare la family key (test o bootstrap).
 */
@Singleton
class PasswordCypher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val auth: FirebaseAuth,
) {
    fun encrypt(
        plaintext: String,
        familyId: String,
        visibility: String,
        createdBy: String,
        familyKeyUserId: String? = null,
    ): ByteArray {
        val keyBytes = loadFamilyKey32(familyId, familyKeyUserId)
        return PasswordCryptoEngine.encrypt(plaintext, keyBytes, visibility, createdBy)
    }

    fun decrypt(
        ciphertext: ByteArray,
        familyId: String,
        visibility: String,
        createdBy: String,
        familyKeyUserId: String? = null,
    ): String {
        val uidForKey = resolvedUid(familyKeyUserId)
        val keyBytes = loadFamilyKey32(familyId, familyKeyUserId)
        return PasswordCryptoEngine.decrypt(ciphertext, keyBytes, visibility, createdBy, decryptingUid = uidForKey)
    }

    private fun resolvedUid(familyKeyUserId: String?): String {
        val u = familyKeyUserId?.trim().orEmpty()
        if (u.isNotEmpty()) return u
        return auth.currentUser?.uid?.trim().orEmpty().ifBlank {
            error("Not authenticated for password crypto")
        }
    }

    private fun loadFamilyKey32(familyId: String, familyKeyUserId: String?): ByteArray {
        val uid = resolvedUid(familyKeyUserId)
        return FamilyKeyStore.loadFamilyKey(context, familyId, uid)
            ?: throw MissingFamilyKeyException(familyId)
    }
}
