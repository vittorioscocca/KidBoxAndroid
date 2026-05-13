package it.vittorioscocca.kidbox.data.passwords

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Allineato alla chiave iOS `kidbox.autofill.requireBiometricForQuickType` (default true). */
@Singleton
class AutoFillUserPreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    var requireBiometricAlways: Boolean
        get() = if (!prefs.contains(KEY_REQUIRE_BIOMETRIC)) true else prefs.getBoolean(KEY_REQUIRE_BIOMETRIC, true)
        set(value) {
            prefs.edit().putBoolean(KEY_REQUIRE_BIOMETRIC, value).apply()
        }

    companion object {
        private const val PREFS = "kidbox_prefs"
        private const val KEY_REQUIRE_BIOMETRIC = "kidbox.autofill.requireBiometricForQuickType"
    }
}
