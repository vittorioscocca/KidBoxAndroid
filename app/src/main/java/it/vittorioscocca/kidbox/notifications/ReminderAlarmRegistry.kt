package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.util.KBLog
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "ReminderAlarmRegistry"
private const val PREFS_NAME = "kb_reminder_alarms"

/**
 * Registro device-locale degli alarm di promemoria armati da questo dispositivo.
 *
 * `AlarmManager` azzera tutti gli alarm al reboot e non offre un modo per
 * elencare quelli pendenti (a differenza di `UNUserNotificationCenter` su iOS,
 * dove le notifiche locali sopravvivono al riavvio). I promemoria KidBox sono
 * *del device* — un elemento arrivato dal sync non deve generare avvisi qui —
 * quindi il ripristino NON può rileggere Room: ricostruirebbe anche i
 * promemoria di elementi creati altrove.
 *
 * Questo registro risolve entrambe le cose: ogni scheduler passa da [arm], che
 * arma l'alarm e ne persiste la ricetta completa (target, action, requestCode,
 * extra, istante di fuoco). Al BOOT_COMPLETED [restoreAll] riemette esattamente
 * ciò che questo device aveva armato, e nient'altro.
 *
 * Veicoli, pagamenti casa e password restano fuori: hanno già un ripristino da
 * Room in [BootReceiver] e una semantica famiglia-wide, non device-locale.
 */
@Singleton
class ReminderAlarmRegistry @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    /** Receiver di destinazione dell'alarm. Il nome della classe non viene persistito. */
    enum class Target { TODO, HEALTH }

    data class AlarmSpec(
        /** Chiave stabile del promemoria: identifica il record nel registro. */
        val key: String,
        val target: Target,
        val requestCode: Int,
        val fireAtMillis: Long,
        val action: String? = null,
        /** Alcuni scheduler distinguono gli intent per `data` invece che per `action`. */
        val dataUri: String? = null,
        val stringExtras: Map<String, String?> = emptyMap(),
        val intExtras: Map<String, Int> = emptyMap(),
        val longExtras: Map<String, Long> = emptyMap(),
    )

    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    /** Arma l'alarm e ne registra la ricetta. Un istante già passato non viene armato. */
    fun arm(spec: AlarmSpec) {
        if (spec.fireAtMillis <= System.currentTimeMillis()) {
            forget(spec.key)
            return
        }
        setAlarm(spec)
        prefs.edit().putString(spec.key, spec.toJson()).apply()
    }

    /** Dimentica il record: l'annullamento dell'alarm resta in carico allo scheduler. */
    fun forget(key: String) {
        prefs.edit().remove(key).apply()
    }

    /**
     * Annulla e dimentica **tutti** i promemoria registrati. Serve al logout:
     * gli alarm già armati sopravvivono alla cancellazione del database locale,
     * e senza questo continuerebbero a scattare per l'account precedente.
     */
    fun cancelAll() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        var cancelled = 0
        for ((key, raw) in prefs.all) {
            val spec = (raw as? String)?.let { parse(key, it) } ?: continue
            val target = when (spec.target) {
                Target.TODO -> TodoReminderReceiver::class.java
                Target.HEALTH -> HealthReminderReceiver::class.java
            }
            val intent = Intent(context, target).apply {
                spec.action?.let { action = it }
                spec.dataUri?.let { data = Uri.parse(it) }
            }
            val pi = PendingIntent.getBroadcast(
                context,
                spec.requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
            ) ?: continue
            alarmManager.cancel(pi)
            pi.cancel()
            cancelled++
        }
        prefs.edit().clear().apply()
        KBLog.app.info("cancelAll — $cancelled alarm annullati, registro svuotato", TAG)
    }

    /** Ri-arma tutti i promemoria ancora futuri, scartando quelli già scaduti. */
    fun restoreAll() {
        val now = System.currentTimeMillis()
        val editor = prefs.edit()
        var restored = 0
        var pruned = 0
        for ((key, raw) in prefs.all) {
            val spec = (raw as? String)?.let { parse(key, it) }
            if (spec == null || spec.fireAtMillis <= now) {
                editor.remove(key)
                pruned++
                continue
            }
            runCatching { setAlarm(spec) }
                .onSuccess { restored++ }
                .onFailure { KBLog.app.error("restore failed key=$key", TAG, it) }
        }
        editor.apply()
        KBLog.app.info("restoreAll — $restored alarm ri-armati, $pruned record scaduti rimossi", TAG)
    }

    // ── Interni ────────────────────────────────────────────────────────────────

    private fun setAlarm(spec: AlarmSpec) {
        val target = when (spec.target) {
            Target.TODO -> TodoReminderReceiver::class.java
            Target.HEALTH -> HealthReminderReceiver::class.java
        }
        val intent = Intent(context, target).apply {
            spec.action?.let { action = it }
            spec.dataUri?.let { data = Uri.parse(it) }
            spec.stringExtras.forEach { (k, v) -> putExtra(k, v) }
            spec.intExtras.forEach { (k, v) -> putExtra(k, v) }
            spec.longExtras.forEach { (k, v) -> putExtra(k, v) }
        }
        val pi = PendingIntent.getBroadcast(
            context,
            spec.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        ExactAlarmScheduler.scheduleRtcWakeupAllowWhileIdle(context, spec.fireAtMillis, pi)
    }

    private fun AlarmSpec.toJson(): String = JSONObject().apply {
        put("t", target.name)
        put("rc", requestCode)
        put("f", fireAtMillis)
        action?.let { put("a", it) }
        dataUri?.let { put("d", it) }
        put(
            "s",
            JSONObject().apply {
                stringExtras.forEach { (k, v) -> put(k, v ?: JSONObject.NULL) }
            },
        )
        put("i", JSONObject().apply { intExtras.forEach { (k, v) -> put(k, v) } })
        put("l", JSONObject().apply { longExtras.forEach { (k, v) -> put(k, v) } })
    }.toString()

    private fun parse(key: String, raw: String): AlarmSpec? = runCatching {
        val json = JSONObject(raw)
        val strings = mutableMapOf<String, String?>()
        json.optJSONObject("s")?.let { obj ->
            obj.keys().forEach { k -> strings[k] = if (obj.isNull(k)) null else obj.getString(k) }
        }
        val ints = mutableMapOf<String, Int>()
        json.optJSONObject("i")?.let { obj ->
            obj.keys().forEach { k -> ints[k] = obj.getInt(k) }
        }
        val longs = mutableMapOf<String, Long>()
        json.optJSONObject("l")?.let { obj ->
            obj.keys().forEach { k -> longs[k] = obj.getLong(k) }
        }
        AlarmSpec(
            key = key,
            target = Target.valueOf(json.getString("t")),
            requestCode = json.getInt("rc"),
            fireAtMillis = json.getLong("f"),
            action = json.optString("a").takeIf { it.isNotEmpty() },
            dataUri = json.optString("d").takeIf { it.isNotEmpty() },
            stringExtras = strings,
            intExtras = ints,
            longExtras = longs,
        )
    }.getOrNull()

    companion object {
        fun todoKey(todoId: String) = "todo:$todoId"
        fun visitKey(reminderKey: String) = "visit:$reminderKey"
        fun examKey(examId: String) = "exam:$examId"
        fun vaccineKey(vaccineId: String) = "vaccine:$vaccineId"
        fun walletTicketKey(ticketId: String, offsetMinutes: Long) = "wallet:$ticketId:$offsetMinutes"
        fun walletDocumentKey(documentId: String) = "walletdoc:$documentId"
        fun treatmentKey(treatmentId: String, dayOffset: Int, slotIndex: Int) =
            "treatment:$treatmentId:$dayOffset:$slotIndex"
        fun treatmentSentinelKey(treatmentId: String) = "treatmentsentinel:$treatmentId"
        fun vehicleKey(vehicleId: String, kindKey: String, slot: String) = "vehicle:$vehicleId:$kindKey:$slot"
        fun housePaymentKey(paymentId: String) = "housepayment:$paymentId"
        fun passwordExpiryKey(entryId: String, days: Int) = "passwordexpiry:$entryId:$days"
    }
}
