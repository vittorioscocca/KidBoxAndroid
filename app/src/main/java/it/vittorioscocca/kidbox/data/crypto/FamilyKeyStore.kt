package it.vittorioscocca.kidbox.data.crypto

import android.content.Context
import android.util.Base64
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

private const val TAG = "FamilyKeyStore"
private const val PREFS_FILE = "kidbox_family_keys"
private const val PREFS_FILE_FALLBACK = "kidbox_family_keys_fallback"
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
    private var fallbackPrefs: android.content.SharedPreferences? = null

    private fun getFallbackPrefs(context: Context): android.content.SharedPreferences {
        fallbackPrefs?.let { return it }
        return synchronized(this) {
            fallbackPrefs ?: context.getSharedPreferences(PREFS_FILE_FALLBACK, Context.MODE_PRIVATE).also {
                fallbackPrefs = it
                Log.w(TAG, "Using FALLBACK shared preferences for family keys")
            }
        }
    }

    private fun getPrefs(context: Context): android.content.SharedPreferences {
        prefs?.let { return it }
        return synchronized(this) {
            prefs ?: runCatching {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    PREFS_FILE,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
                )
            }.onFailure { err ->
                Log.e(TAG, "EncryptedSharedPreferences init failed: ${err.message}", err)
            }.getOrNull()?.also {
                prefs = it
            } ?: getFallbackPrefs(context)
        }
    }

    /** Salva la family master key (32 bytes). Equivalente a iOS saveFamilyKey. */
    fun saveFamilyKey(context: Context, keyBytes: ByteArray, familyId: String, userId: String) {
        require(keyBytes.size == 32) { "Family key deve essere 32 bytes, got ${keyBytes.size}" }
        val encoded = Base64.encodeToString(keyBytes, Base64.NO_WRAP)
        runCatching {
            getPrefs(context).edit().putString(prefKey(familyId, userId), encoded).apply()
            Log.d(TAG, "saveFamilyKey OK familyId=$familyId")
        }.onFailure { err ->
            Log.e(TAG, "saveFamilyKey failed familyId=$familyId: ${err.message}", err)
            runCatching {
                getFallbackPrefs(context).edit().putString(prefKey(familyId, userId), encoded).apply()
                Log.w(TAG, "saveFamilyKey stored in fallback prefs familyId=$familyId")
            }.onFailure {
                Log.e(TAG, "saveFamilyKey fallback failed familyId=$familyId: ${it.message}", it)
            }
        }
    }

    /** Carica la family master key. Restituisce null se non presente. */
    fun loadFamilyKey(context: Context, familyId: String, userId: String): ByteArray? {
        val encoded = runCatching {
            getPrefs(context).getString(prefKey(familyId, userId), null)
        }.getOrNull()
            ?: runCatching {
                getFallbackPrefs(context).getString(prefKey(familyId, userId), null)
            }.getOrNull()
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
        runCatching { getPrefs(context).edit().remove(prefKey(familyId, userId)).apply() }
        runCatching { getFallbackPrefs(context).edit().remove(prefKey(familyId, userId)).apply() }
        Log.d(TAG, "deleteFamilyKey familyId=$familyId")
    }

    /** Rimuove tutte le chiavi famiglia associate a questo utente (prefisso `fk_` + suffisso `_userId`). */
    fun deleteAllFamilyKeysForUser(context: Context, userId: String) {
        val suffix = "_$userId"
        fun deleteFromPrefs(p: android.content.SharedPreferences): Int {
            val editor = p.edit()
            var n = 0
            for (key in p.all.keys) {
                if (key.startsWith("fk_") && key.endsWith(suffix)) {
                    editor.remove(key)
                    n++
                }
            }
            editor.apply()
            return n
        }
        val removedEncrypted = runCatching { deleteFromPrefs(getPrefs(context)) }.getOrDefault(0)
        val removedFallback = runCatching { deleteFromPrefs(getFallbackPrefs(context)) }.getOrDefault(0)
        Log.d(TAG, "deleteAllFamilyKeysForUser removed=${removedEncrypted + removedFallback} userId=$userId")
    }

    /** Verifica la presenza della chiave senza restituirla (per log/debug). */
    fun hasFamilyKey(context: Context, familyId: String, userId: String): Boolean {
        return runCatching {
            getPrefs(context).contains(prefKey(familyId, userId))
        }.getOrDefault(false) || runCatching {
            getFallbackPrefs(context).contains(prefKey(familyId, userId))
        }.getOrDefault(false)
    }
}