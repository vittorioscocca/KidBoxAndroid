package it.vittorioscocca.kidbox.data.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.google.android.play.core.appupdate.AppUpdateManagerFactory
import com.google.android.play.core.install.model.UpdateAvailability
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Chiede a Google Play se esiste una versione più recente di KidBox.
 *
 * Non serve tenere da nessuna parte il numero dell'ultima versione pubblicata: la
 * risposta arriva da Play, che considera anche i rollout graduali (un utente che non
 * è ancora nella percentuale di rilascio non vede l'aggiornamento, quindi non lo
 * infastidiamo con un banner che non potrebbe soddisfare).
 *
 * Funziona solo su installazioni provenienti dal Play Store: in debug o da sideload
 * `appUpdateInfo` fallisce e [checkForUpdate] restituisce `null`.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)

    /**
     * Restituisce il versionCode disponibile su Play se c'è un aggiornamento da proporre,
     * `null` se non c'è, se l'utente ha già scelto "Non ora" per quella versione di recente,
     * o se Play non risponde (build non installata dallo Store, offline, ecc.).
     */
    suspend fun checkForUpdate(): Int? {
        val info = runCatching {
            AppUpdateManagerFactory.create(context).appUpdateInfo.await()
        }.getOrNull() ?: return null

        if (info.updateAvailability() != UpdateAvailability.UPDATE_AVAILABLE) return null

        val availableVersion = info.availableVersionCode()
        return if (isSnoozed(availableVersion)) null else availableVersion
    }

    /** "Non ora": non riproponiamo la stessa versione per [SNOOZE_DAYS] giorni. */
    fun snooze(versionCode: Int) {
        prefs.edit()
            .putInt(KEY_SNOOZED_VERSION, versionCode)
            .putLong(KEY_SNOOZED_AT, System.currentTimeMillis())
            .apply()
    }

    private fun isSnoozed(versionCode: Int): Boolean {
        if (prefs.getInt(KEY_SNOOZED_VERSION, -1) != versionCode) return false
        val snoozedAt = prefs.getLong(KEY_SNOOZED_AT, 0L)
        val elapsed = System.currentTimeMillis() - snoozedAt
        return elapsed in 0 until SNOOZE_MILLIS
    }

    /** Apre la scheda di KidBox su Play, con fallback al browser se l'app Play non c'è. */
    fun openPlayStore() {
        val marketIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("market://details?id=${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val opened = runCatching { context.startActivity(marketIntent) }.isSuccess
        if (opened) return

        val webIntent = Intent(
            Intent.ACTION_VIEW,
            Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(webIntent) }
    }

    private companion object {
        private const val KEY_SNOOZED_VERSION = "update_snoozed_version"
        private const val KEY_SNOOZED_AT = "update_snoozed_at"
        private const val SNOOZE_DAYS = 3L
        private const val SNOOZE_MILLIS = SNOOZE_DAYS * 24 * 60 * 60 * 1000
    }
}
