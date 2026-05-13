package it.vittorioscocca.kidbox.data.passwords.otp

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OtpSecureStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val gson = Gson()
    private val prefs by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "kidbox_otp_store",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun saveOtpConfig(credentialId: String, config: OtpConfig) {
        prefs.edit().putString(keyFor(credentialId), gson.toJson(config)).apply()
    }

    fun loadOtpConfig(credentialId: String): OtpConfig? {
        val raw = prefs.getString(keyFor(credentialId), null) ?: return null
        return runCatching { gson.fromJson(raw, OtpConfig::class.java) }.getOrNull()
    }

    fun deleteOtpConfig(credentialId: String) {
        prefs.edit().remove(keyFor(credentialId)).apply()
    }

    private fun keyFor(credentialId: String): String = "otp_$credentialId"
}
