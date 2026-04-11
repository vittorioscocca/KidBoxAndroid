package it.vittorioscocca.kidbox.data.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val TAG = "FamilyKeyStore"
private const val PREFS_FILE = "kidbox_family_keys"
private fun prefKey(familyId: String, userId: String) = "fk_${familyId}_$userId"

/**
 * Equivalente Android di iOS FamilyKeychainStore.
 * Salva/carica la family master key in EncryptedSharedPreferences,
 * che usa Android Keystore per proteggere la chiave di cifratura delle prefs.
 *
 * API identica a iOS:
 *   saveFamilyKey(keyBytes, familyId, userId)
 *   loadFamilyKey(familyId, userId): ByteArray?
 *   deleteFamilyKey(familyId, userId)
 */
object FamilyKeyStore {

    @Volatile
    private var prefs: android.content.SharedPreferences? = null

    private fun getPrefs(context: Context): android.content.SharedPreferences {
        prefs?.let { return it }
        return synchronized(this) {
            prefs ?: run {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                val p = EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
                prefs = p
                p
            }
        }
    }

    /** Salva la family master key (32 bytes). Equivalente a iOS saveFamilyKey. */
    fun saveFamilyKey(context: Context, keyBytes: ByteArray, familyId: String, userId: String) {
        require(keyBytes.size == 32) { "Family key deve essere 32 bytes, got ${keyBytes.size}" }
        val encoded = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        getPrefs(context).edit().putString(prefKey(familyId, userId), encoded).apply()
        Log.d(TAG, "saveFamilyKey OK familyId=$familyId")
    }

    /** Carica la family master key. Restituisce null se non presente. */
    fun loadFamilyKey(context: Context, familyId: String, userId: String): ByteArray? {
        val encoded = getPrefs(context).getString(prefKey(familyId, userId), null)
            ?: return null
        return try {
            Base64.decode(encoded, Base64.NO_WRAP)
        } catch (e: Exception) {
            Log.e(TAG, "loadFamilyKey decode failed familyId=$familyId: ${e.message}")
            null
        }
    }

    /** Elimina la family master key (es. dopo leave family). */
    fun deleteFamilyKey(context: Context, familyId: String, userId: String) {
        getPrefs(context).edit().remove(prefKey(familyId, userId)).apply()
        Log.d(TAG, "deleteFamilyKey familyId=$familyId")
    }

    /** Rimuove tutte le chiavi famiglia associate a questo utente (prefisso `fk_` + suffisso `_userId`). */
    fun deleteAllFamilyKeysForUser(context: Context, userId: String) {
        val p = getPrefs(context)
        val suffix = "_$userId"
        val editor = p.edit()
        var n = 0
        for (key in p.all.keys) {
            if (key.startsWith("fk_") && key.endsWith(suffix)) {
                editor.remove(key)
                n++
            }
        }
        editor.apply()
        Log.d(TAG, "deleteAllFamilyKeysForUser removed=$n userId=$userId")
    }

    /** Verifica la presenza della chiave senza restituirla (per log/debug). */
    fun hasFamilyKey(context: Context, familyId: String, userId: String): Boolean {
        return getPrefs(context).contains(prefKey(familyId, userId))
    }
}