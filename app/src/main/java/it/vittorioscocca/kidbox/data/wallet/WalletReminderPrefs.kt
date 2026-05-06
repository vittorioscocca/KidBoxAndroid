package it.vittorioscocca.kidbox.data.wallet

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Local preference aligned with iOS `kb_notifyOnWalletReminder` (UNUserNotificationCenter). */
@Singleton
class WalletReminderPrefs @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun isReminderEnabled(): Boolean =
        prefs.getBoolean(KEY_NOTIFY_ON_WALLET_REMINDER, true)

    fun setReminderEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_NOTIFY_ON_WALLET_REMINDER, enabled).apply()
    }

    companion object {
        private const val PREFS_NAME = "kidbox_wallet_prefs"
        const val KEY_NOTIFY_ON_WALLET_REMINDER = "kb_notifyOnWalletReminder"
    }
}
