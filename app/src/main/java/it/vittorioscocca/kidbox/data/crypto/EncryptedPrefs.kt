package it.vittorioscocca.kidbox.data.crypto

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import it.vittorioscocca.kidbox.util.KBLog
import java.security.KeyStore

/**
 * Factory robusta per [EncryptedSharedPreferences].
 *
 * Quando la MasterKey nell'Android Keystore non corrisponde più al file di prefs
 * cifrato (AEADBadTagException / Keystore VERIFICATION_FAILED — succede dopo restore
 * da backup, cambio lock-screen o corruzione del Keystore) la `create()` fallisce
 * in modo permanente. In quel caso elimina il file corrotto + la MasterKey e ricrea
 * lo store da zero, così l'app non crasha più ad ogni avvio.
 */
object EncryptedPrefs {

    private const val TAG = "EncryptedPrefs"
    // Alias di default usato da androidx.security (MasterKey.DEFAULT_MASTER_KEY_ALIAS), condiviso da tutti gli store.
    private const val MASTER_KEY_ALIAS = "_androidx_security_master_key_"
    private const val ANDROID_KEYSTORE = "AndroidKeyStore"

    private fun build(context: Context, fileName: String): SharedPreferences {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        return EncryptedSharedPreferences.create(
            context,
            fileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    private fun deleteMasterKey() {
        runCatching {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }.deleteEntry(MASTER_KEY_ALIAS)
        }
    }

    private fun deletePrefsFile(context: Context, fileName: String) {
        runCatching { context.deleteSharedPreferences(fileName) }.onFailure {
            runCatching { context.getSharedPreferences(fileName, Context.MODE_PRIVATE).edit().clear().commit() }
        }
    }

    /**
     * Crea [EncryptedSharedPreferences] per [fileName], con recovery automatico in caso
     * di Keystore disallineato. Se anche il recovery fallisce rilancia l'eccezione.
     */
    fun create(context: Context, fileName: String): SharedPreferences {
        return runCatching { build(context, fileName) }
            .recoverCatching { err ->
                KBLog.crypto.error("EncryptedSharedPreferences '$fileName' init failed, recovering: ${err.message}", TAG, err)
                deletePrefsFile(context, fileName)
                deleteMasterKey()
                build(context, fileName)
            }
            .getOrElse { err ->
                KBLog.crypto.error("EncryptedSharedPreferences '$fileName' recovery failed: ${err.message}", TAG, err)
                throw err
            }
    }

    /** Variante che non rilancia: ritorna null se neppure il recovery riesce. */
    fun createOrNull(context: Context, fileName: String): SharedPreferences? =
        runCatching { create(context, fileName) }.getOrNull()
}
