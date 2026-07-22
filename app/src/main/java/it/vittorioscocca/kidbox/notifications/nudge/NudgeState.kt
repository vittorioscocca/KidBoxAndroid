package it.vittorioscocca.kidbox.notifications.nudge

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Memoria locale del motore di nudge: cosa è già scattato e quando.
 *
 * Gemello di `NudgeState.swift`. Sta in `SharedPreferences` e NON su Firestore,
 * di proposito: sono dati di frequenza, non di contenuto. Metterli sul server
 * significherebbe costruire per ogni utente un registro di "cosa gli abbiamo
 * detto e quando", cioè il profilo per-utente che l'impianto analytics evita
 * apposta. Il prezzo è che cambiando dispositivo la sequenza riparte:
 * accettabile, ed è comunque frenata dal cooldown globale e dal tetto
 * trimestrale.
 */
object NudgeState {

    private const val PREFS = "kb_nudge_prefs"
    private const val KEY_INSTALL = "installEpochMillis"
    private const val KEY_FIRES = "fires"
    private const val KEY_OPTED_OUT = "optedOut"

    private const val DAY_MS = 24L * 60 * 60 * 1000

    data class Fire(val campaignId: String, val atMillis: Long)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Prima data conosciuta di questa installazione, usata da `firstDelayDays`.
     *
     * Si registra alla prima chiamata: per chi era già installato al momento
     * dell'aggiornamento vale la data dell'update, non quella vera. È corretto
     * così — un utente di sei mesi non deve ricevere "benvenuto, invita la
     * famiglia" il giorno dopo l'aggiornamento come se si fosse appena iscritto.
     */
    fun installMillis(context: Context): Long {
        val p = prefs(context)
        val existing = p.getLong(KEY_INSTALL, 0L)
        if (existing > 0L) return existing
        val now = System.currentTimeMillis()
        p.edit().putLong(KEY_INSTALL, now).apply()
        return now
    }

    /**
     * Interruttore utente, indipendente dal permesso di sistema. Chi lo spegne
     * non riceve più nudge ma continua a ricevere le notifiche che ha chiesto
     * (promemoria, chat): sono cose diverse e vanno separate.
     */
    fun isOptedOut(context: Context): Boolean = prefs(context).getBoolean(KEY_OPTED_OUT, false)

    fun setOptedOut(context: Context, value: Boolean) {
        prefs(context).edit().putBoolean(KEY_OPTED_OUT, value).apply()
    }

    fun fires(context: Context): List<Fire> {
        val raw = prefs(context).getString(KEY_FIRES, null) ?: return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Fire(o.getString("campaignId"), o.getLong("at"))
            }
        }.getOrDefault(emptyList())
    }

    fun setFires(context: Context, value: List<Fire>) {
        // Oltre un anno non serve a nessuna decisione: il tetto più lungo è
        // trimestrale.
        val cutoff = System.currentTimeMillis() - 365 * DAY_MS
        val arr = JSONArray()
        value.filter { it.atMillis > cutoff }.forEach {
            arr.put(JSONObject().put("campaignId", it.campaignId).put("at", it.atMillis))
        }
        prefs(context).edit().putString(KEY_FIRES, arr.toString()).apply()
    }

    fun recordFire(context: Context, campaignId: String, atMillis: Long) {
        setFires(context, fires(context) + Fire(campaignId, atMillis))
    }

    fun fireCount(context: Context, campaignId: String): Int =
        fires(context).count { it.campaignId == campaignId }

    fun lastFire(context: Context, campaignId: String): Long? =
        fires(context).filter { it.campaignId == campaignId }.maxOfOrNull { it.atMillis }

    fun lastFireAny(context: Context): Long? = fires(context).maxOfOrNull { it.atMillis }

    fun firesInLastDays(context: Context, days: Int): Int {
        val cutoff = System.currentTimeMillis() - days * DAY_MS
        return fires(context).count { it.atMillis > cutoff }
    }
}
