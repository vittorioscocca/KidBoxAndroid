package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.mapper.scheduleTimesList
import it.vittorioscocca.kidbox.domain.model.KBTreatment
import it.vittorioscocca.kidbox.domain.model.slotLabelFor
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
        Log.d(TAG, "cancelled all alarms for treatmentId=$treatmentId (${entries.size} entries)")
    }

    fun cancelSlot(treatmentId: String, dayOffset: Int, slotIndex: Int) {
        cancelAlarmIntent(treatmentId, dayOffset, slotIndex)
        removeSingleEntry(treatmentId, dayOffset, slotIndex)
        Log.d(TAG, "cancelled slot treatmentId=$treatmentId day=$dayOffset slot=$slotIndex")
    }

    fun rescheduleIfNeeded(treatment: KBTreatment, childName: String) {
        val now = System.currentTimeMillis()
        val pending = getEntries(treatment.id).mapNotNull { parseEntry(it) }
            .count { (_, _, _, fireMillis) -> fireMillis > now }
        if (pending <= RESCHEDULE_THRESHOLD) {
            val latest = getEntries(treatment.id).mapNotNull { parseEntry(it) }
                .maxOfOrNull { (_, _, _, fireMillis) -> fireMillis }
            scheduleWindow(treatment, childName, windowStartMillis = latest?.plus(86_400_000L))
        }
    }

    fun rescheduleAllActive() {
        // Called on app cold start and BOOT_COMPLETED — no familyId filter here;
        // individual families populate treatments lazily from sync, so we only
        // reschedule what's already in the SharedPrefs key set.
        // Real reschedule happens via TreatmentRepository observers in the UI.
        // This method is intentionally a no-op stub: the sync centers + ViewModel
        // cascade do the work when the family session is re-established.
        Log.d(TAG, "rescheduleAllActive called (delegated to sync centers on session restore)")
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
        while (currentDay <= endDay) {
            val dayOffset = ChronoUnit.DAYS.between(
                java.time.Instant.ofEpochMilli(startDay).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
                java.time.Instant.ofEpochMilli(currentDay).atZone(java.time.ZoneId.systemDefault()).toLocalDate(),
            ).toInt()

            for ((slotIndex, timeStr) in times.withIndex()) {
                val fireMillis = buildFireMillis(currentDay, timeStr) ?: continue
                if (fireMillis <= now) continue

                val slotLabel = slotLabelFor(slotIndex)
                val dosageStr = treatment.dosageValue.formatted()
                val body = "$slotLabel · $dosageStr ${treatment.dosageUnit} per $childName"
                scheduleAlarm(treatment, dayOffset, slotIndex, timeStr, fireMillis, body)
                recordEntry(treatment.id, dayOffset, slotIndex, fireMillis)
                if (fireMillis > lastFireMillis) lastFireMillis = fireMillis
            }
            currentDay += 86_400_000L
        }

        if (lastFireMillis > 0L) {
            scheduleSentinel(treatment.id, lastFireMillis + 60_000L)
        }
        Log.d(TAG, "scheduled window treatmentId=${treatment.id} start=$windowStartDay end=$endDay")
    }

    private fun scheduleAlarm(
        treatment: KBTreatment,
        dayOffset: Int,
        slotIndex: Int,
        scheduledTime: String,
        fireMillis: Long,
        body: String,
    ) {
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = "kb.health.treatment_reminder.${treatment.id}.$dayOffset.$slotIndex"
            putExtra(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_TREATMENT_REMINDER)
            putExtra(HealthReminderReceiver.EXTRA_TREATMENT_ID, treatment.id)
            putExtra(HealthReminderReceiver.EXTRA_FAMILY_ID, treatment.familyId)
            putExtra(HealthReminderReceiver.EXTRA_CHILD_ID, treatment.childId)
            putExtra(HealthReminderReceiver.EXTRA_DAY_OFFSET, dayOffset)
            putExtra(HealthReminderReceiver.EXTRA_SLOT_INDEX, slotIndex)
            putExtra(HealthReminderReceiver.EXTRA_TITLE, "💊 ${treatment.drugName}")
            putExtra(HealthReminderReceiver.EXTRA_BODY, body)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            alarmRequestCode(treatment.id, dayOffset, slotIndex),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMillis, pi)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMillis, pi)
        }
    }

    private fun scheduleSentinel(treatmentId: String, fireMillis: Long) {
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = "kb.health.treatment_sentinel.$treatmentId"
            putExtra(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_TREATMENT_SENTINEL)
            putExtra(HealthReminderReceiver.EXTRA_TREATMENT_ID, treatmentId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            "sentinel:$treatmentId".hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (alarmManager.canScheduleExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMillis, pi)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMillis, pi)
            }
        } else {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fireMillis, pi)
        }
    }

    private fun cancelSentinel(treatmentId: String) {
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = "kb.health.treatment_sentinel.$treatmentId"
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
        val intent = Intent(context, HealthReminderReceiver::class.java).apply {
            action = "kb.health.treatment_reminder.$treatmentId.$dayOffset.$slotIndex"
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
