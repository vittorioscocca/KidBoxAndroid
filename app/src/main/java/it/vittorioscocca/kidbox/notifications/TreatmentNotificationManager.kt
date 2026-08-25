package it.vittorioscocca.kidbox.notifications

import it.vittorioscocca.kidbox.util.KBLog

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.TreatmentSchedulePeriod
import it.vittorioscocca.kidbox.domain.model.schedulePeriodForTime
import java.time.temporal.ChronoUnit
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "TreatmentNotifMgr"
private const val WINDOW_DAYS = 7
private const val RESCHEDULE_THRESHOLD = 2
private const val PREFS_NAME = "kb_treatment_alarms"

@Singleton
class TreatmentNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val alarmRegistry: ReminderAlarmRegistry,
) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs by lazy { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    fun schedule(treatment: KBTreatment, childName: String) {
        cancel(treatment.id)
        scheduleWindow(treatment, childName, windowStartMillis = null)
    }

    fun cancel(treatmentId: String) {
        val entries = getEntries(treatmentId)
        for (entry in entries) {
            val (_, dayOffset, slotIndex, _) = parseEntry(entry) ?: continue
            cancelAlarmIntent(treatmentId, dayOffset, slotIndex)
        }
        cancelSentinel(treatmentId)
        removeAllEntries(treatmentId)
        KBLog.app.debug("cancelled all alarms for treatmentId=$treatmentId (${entries.size} entries)", TAG)
    }

    fun cancelSlot(treatmentId: String, dayOffset: Int, slotIndex: Int) {
        cancelAlarmIntent(treatmentId, dayOffset, slotIndex)
        removeSingleEntry(treatmentId, dayOffset, slotIndex)
        KBLog.app.debug("cancelled slot treatmentId=$treatmentId day=$dayOffset slot=$slotIndex", TAG)
    }

    /**
     * Estende la finestra scorrevole quando restano pochi alarm pendenti.
     * Invocata dalla sentinella in [HealthReminderReceiver] appena l'ultimo
     * alarm della finestra corrente è scattato: senza questo aggancio una cura
     * lunga smetterebbe di avvisare dopo [WINDOW_DAYS] giorni.
     */
    fun rescheduleIfNeeded(treatment: KBTreatment, childName: String) {
        val now = System.currentTimeMillis()
        val entries = pruneFiredEntries(treatment.id, now)
        val pending = entries.count { it.fireMillis > now }
        if (pending > RESCHEDULE_THRESHOLD) return
        val latest = entries.maxOfOrNull { it.fireMillis }
        scheduleWindow(treatment, childName, windowStartMillis = latest?.plus(86_400_000L))
    }

    private fun scheduleWindow(
        treatment: KBTreatment,
        childName: String,
        windowStartMillis: Long?,
    ) {
        val now = System.currentTimeMillis()
        val startDay = toLocalDateMillis(treatment.startDateEpochMillis)
        val todayDay = toLocalDateMillis(now)
        val windowStartDay = if (windowStartMillis != null) {
            toLocalDateMillis(windowStartMillis)
        } else {
            maxOf(startDay, todayDay)
        }
        val endDay = if (treatment.isLongTerm || treatment.endDateEpochMillis == null) {
            windowStartDay + (WINDOW_DAYS - 1) * 86_400_000L
        } else {
            minOf(
                windowStartDay + (WINDOW_DAYS - 1) * 86_400_000L,
                toLocalDateMillis(treatment.endDateEpochMillis),
            )
        }

        val times = treatment.scheduleTimesList()
        var lastFireMillis = 0L

        var currentDay = windowStartDay
        dayLoop@ while (currentDay <= endDay) {
            val dayOffset = ChronoUnit.DAYS.between(
                java.time.Instant.ofEpochMilli(startDay).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                java.time.Instant.ofEpochMilli(currentDay).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            ).toInt()

            val intervalN = treatment.intervalBetweenDosesDays
            if (intervalN > 0) {
                if (dayOffset < 0 || dayOffset % intervalN != 0) {
                    currentDay += 86_400_000L
                    continue@dayLoop
                }
                val timeStr = times.firstOrNull()
                if (timeStr == null) {
                    currentDay += 86_400_000L
                    continue@dayLoop
                }
                val fireMillis = buildFireMillis(currentDay, timeStr)
                if (fireMillis == null) {
                    currentDay += 86_400_000L
                    continue@dayLoop
                }
                if (fireMillis > now) {
                    val slotLabel = localizedSlotLabel(timeStr, 0)
                    val dosageStr = treatment.dosageValue.formatted()
                    val body = context.getString(R.string.treatment_reminder_body_format, slotLabel, dosageStr, treatment.dosageUnit, childName)
                    scheduleAlarm(treatment, dayOffset, 0, timeStr, fireMillis, body)
                    recordEntry(treatment.id, dayOffset, 0, fireMillis)
                    if (fireMillis > lastFireMillis) lastFireMillis = fireMillis
                }
            } else {
                for ((slotIndex, timeStr) in times.withIndex()) {
                    val fireMillis = buildFireMillis(currentDay, timeStr) ?: continue
                    if (fireMillis <= now) continue

                    val slotLabel = localizedSlotLabel(timeStr, slotIndex)
                    val dosageStr = treatment.dosageValue.formatted()
                    val body = context.getString(R.string.treatment_reminder_body_format, slotLabel, dosageStr, treatment.dosageUnit, childName)
                    scheduleAlarm(treatment, dayOffset, slotIndex, timeStr, fireMillis, body)
                    recordEntry(treatment.id, dayOffset, slotIndex, fireMillis)
                    if (fireMillis > lastFireMillis) lastFireMillis = fireMillis
                }
            }
            currentDay += 86_400_000L
        }

        if (lastFireMillis > 0L) {
            scheduleSentinel(treatment.id, lastFireMillis + 60_000L)
        }
        KBLog.app.debug("scheduled window treatmentId=${treatment.id} start=$windowStartDay end=$endDay", TAG)
    }

    /** Etichetta fascia oraria localizzata per il corpo della notifica (non tocca `labelIt`, usato nella UI). */
    private fun localizedSlotLabel(scheduledTime: String, slotIndexFallback: Int): String =
        when (schedulePeriodForTime(scheduledTime, slotIndexFallback)) {
            TreatmentSchedulePeriod.MATTINA -> context.getString(R.string.treatment_period_morning)
            TreatmentSchedulePeriod.PRANZO -> context.getString(R.string.treatment_period_lunch)
            TreatmentSchedulePeriod.SERA -> context.getString(R.string.treatment_period_evening)
            TreatmentSchedulePeriod.NOTTE -> context.getString(R.string.treatment_period_night)
            null -> context.getString(R.string.treatment_dose_n, slotIndexFallback + 1)
        }

    private fun scheduleAlarm(
        treatment: KBTreatment,
        dayOffset: Int,
        slotIndex: Int,
        scheduledTime: String,
        fireMillis: Long,
        body: String,
    ) {
        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.treatmentKey(treatment.id, dayOffset, slotIndex),
                target = ReminderAlarmRegistry.Target.HEALTH,
                requestCode = alarmRequestCode(treatment.id, dayOffset, slotIndex),
                fireAtMillis = fireMillis,
                action = reminderAction(treatment.id, dayOffset, slotIndex),
                stringExtras = mapOf(
                    HealthReminderReceiver.EXTRA_TYPE to HealthReminderReceiver.TYPE_TREATMENT_REMINDER,
                    HealthReminderReceiver.EXTRA_TREATMENT_ID to treatment.id,
                    HealthReminderReceiver.EXTRA_FAMILY_ID to treatment.familyId,
                    HealthReminderReceiver.EXTRA_CHILD_ID to treatment.childId,
                    HealthReminderReceiver.EXTRA_TITLE to "💊 ${treatment.drugName}",
                    HealthReminderReceiver.EXTRA_BODY to body,
                ),
                intExtras = mapOf(
                    HealthReminderReceiver.EXTRA_DAY_OFFSET to dayOffset,
                    HealthReminderReceiver.EXTRA_SLOT_INDEX to slotIndex,
                ),
            ),
        )
    }

    private fun scheduleSentinel(treatmentId: String, fireMillis: Long) {
        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.treatmentSentinelKey(treatmentId),
                target = ReminderAlarmRegistry.Target.HEALTH,
                requestCode = "sentinel:$treatmentId".hashCode(),
                fireAtMillis = fireMillis,
                action = sentinelAction(treatmentId),
                stringExtras = mapOf(
                    HealthReminderReceiver.EXTRA_TYPE to HealthReminderReceiver.TYPE_TREATMENT_SENTINEL,
                    HealthReminderReceiver.EXTRA_TREATMENT_ID to treatmentId,
                ),
            ),
        )
    }

    private fun cancelSentinel(treatmentId: String) {
        alarmRegistry.forget(ReminderAlarmRegistry.treatmentSentinelKey(treatmentId))
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = sentinelAction(treatmentId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            "sentinel:$treatmentId".hashCode(),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun cancelAlarmIntent(treatmentId: String, dayOffset: Int, slotIndex: Int) {
        alarmRegistry.forget(ReminderAlarmRegistry.treatmentKey(treatmentId, dayOffset, slotIndex))
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = reminderAction(treatmentId, dayOffset, slotIndex)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            alarmRequestCode(treatmentId, dayOffset, slotIndex),
            intent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    private fun reminderAction(treatmentId: String, dayOffset: Int, slotIndex: Int) =
        "kb.health.treatment_reminder.$treatmentId.$dayOffset.$slotIndex"

    private fun sentinelAction(treatmentId: String) = "kb.health.treatment_sentinel.$treatmentId"

    /** Svuota il registro delle dosi armate. Usato al logout. */
    fun clearAllRecords() {
        prefs.edit().clear().apply()
    }

    // ── SharedPreferences helpers ──────────────────────────────────────────────

    private fun prefsKey(treatmentId: String) = "entries_$treatmentId"

    private fun getEntries(treatmentId: String): Set<String> =
        prefs.getStringSet(prefsKey(treatmentId), emptySet()) ?: emptySet()

    private fun recordEntry(treatmentId: String, dayOffset: Int, slotIndex: Int, fireMillis: Long) {
        val key = prefsKey(treatmentId)
        val current = prefs.getStringSet(key, mutableSetOf()) ?: mutableSetOf()
        val entry = "$treatmentId|$dayOffset|$slotIndex|$fireMillis"
        prefs.edit().putStringSet(key, current + entry).apply()
    }

    private fun removeSingleEntry(treatmentId: String, dayOffset: Int, slotIndex: Int) {
        val key = prefsKey(treatmentId)
        val current = getEntries(treatmentId).toMutableSet()
        current.removeAll { it.startsWith("$treatmentId|$dayOffset|$slotIndex|") }
        prefs.edit().putStringSet(key, current).apply()
    }

    /**
     * Scarta i record degli alarm già scattati e restituisce quelli superstiti.
     * Senza questa potatura lo `StringSet` di una cura a lungo termine
     * crescerebbe senza limite, un record per ogni dose mai schedulata.
     */
    private fun pruneFiredEntries(treatmentId: String, now: Long): List<EntryParts> {
        val parsed = getEntries(treatmentId).mapNotNull { parseEntry(it) }
        val survivors = parsed.filter { it.fireMillis > now }
        if (survivors.size != parsed.size) {
            prefs.edit()
                .putStringSet(
                    prefsKey(treatmentId),
                    survivors.map { "${it.treatmentId}|${it.dayOffset}|${it.slotIndex}|${it.fireMillis}" }.toSet(),
                )
                .apply()
        }
        return survivors
    }

    private fun removeAllEntries(treatmentId: String) {
        prefs.edit().remove(prefsKey(treatmentId)).apply()
    }

    data class EntryParts(val treatmentId: String, val dayOffset: Int, val slotIndex: Int, val fireMillis: Long)

    private fun parseEntry(entry: String): EntryParts? {
        val parts = entry.split("|")
        if (parts.size < 4) return null
        return EntryParts(
            treatmentId = parts[0],
            dayOffset = parts[1].toIntOrNull() ?: return null,
            slotIndex = parts[2].toIntOrNull() ?: return null,
            fireMillis = parts[3].toLongOrNull() ?: return null,
        )
    }

    // ── Utilities ──────────────────────────────────────────────────────────────

    private fun alarmRequestCode(treatmentId: String, dayOffset: Int, slotIndex: Int): Int =
        "tr:$treatmentId:d$dayOffset:s$slotIndex".hashCode()

    private fun buildFireMillis(dayStartMillis: Long, timeStr: String): Long? {
        val parts = timeStr.split(":").mapNotNull { it.toIntOrNull() }
        if (parts.size < 2) return null
        val cal = Calendar.getInstance().apply {
            timeInMillis = dayStartMillis
            set(Calendar.HOUR_OF_DAY, parts[0])
            set(Calendar.MINUTE, parts[1])
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    private fun toLocalDateMillis(epochMillis: Long): Long {
        val cal = Calendar.getInstance().apply {
            timeInMillis = epochMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }
}

private fun Double.formatted(): String =
    if (this % 1.0 == 0.0) "%.0f".format(this) else "%.1f".format(this)
