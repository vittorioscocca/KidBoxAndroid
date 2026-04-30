package it.vittorioscocca.kidbox.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R

class HealthReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ensureChannel(context)

        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_VISIT_REMINDER

        when (type) {
            TYPE_TREATMENT_SENTINEL -> {
                // Sentinel: do NOT post a notification. The TreatmentNotificationManager
                // rescheduleIfNeeded is called from the ViewModel/session restore path instead,
                // since receivers cannot inject Hilt singletons reliably across process death.
                return
            }
            TYPE_TREATMENT_REMINDER -> {
                val treatmentId = intent.getStringExtra(EXTRA_TREATMENT_ID).orEmpty()
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
                val childId = intent.getStringExtra(EXTRA_CHILD_ID).orEmpty()
                val dayOffset = intent.getIntExtra(EXTRA_DAY_OFFSET, 0)
                val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, 0)
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Promemoria cura"
                val body = intent.getStringExtra(EXTRA_BODY) ?: "Orario di somministrazione"

                val deepLink = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("kb_deeplink_type", TYPE_TREATMENT_REMINDER)
                    putExtra("kb_treatmentId", treatmentId)
                    putExtra("kb_familyId", familyId)
                    putExtra("kb_childId", childId)
                    putExtra("kb_dayOffset", dayOffset)
                    putExtra("kb_slotIndex", slotIndex)
                }
                val notifId = "$treatmentId:$dayOffset:$slotIndex".hashCode()
                val pendingIntent = PendingIntent.getActivity(
                    context, notifId, deepLink,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_HEALTH_REMINDERS)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(title)
                    .setContentText(body)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .build()
                runCatching { NotificationManagerCompat.from(context).notify(notifId, notification) }
            }
            TYPE_VACCINE_REMINDER -> {
                val vaccineId = intent.getStringExtra(EXTRA_VACCINE_ID).orEmpty()
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
                val childId = intent.getStringExtra(EXTRA_CHILD_ID).orEmpty()
                val body = intent.getStringExtra(EXTRA_TITLE).orEmpty()

                val deepLink = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("kb_deeplink_type", TYPE_VACCINE_REMINDER)
                    putExtra("kb_vaccineId", vaccineId)
                    putExtra("kb_familyId", familyId)
                    putExtra("kb_childId", childId)
                }
                val notifId = vaccineId.hashCode()
                val pendingIntent = PendingIntent.getActivity(
                    context, notifId, deepLink,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_HEALTH_REMINDERS)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle("Promemoria vaccino")
                    .setContentText(body.ifBlank { "Vaccino in programma domani" })
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .build()
                runCatching { NotificationManagerCompat.from(context).notify(notifId, notification) }
            }
            else -> {
                val body = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
                val childId = intent.getStringExtra(EXTRA_CHILD_ID).orEmpty()
                val isExam = type == TYPE_EXAM_REMINDER
                val notifTitle = if (isExam) "Promemoria esame" else "Promemoria visita"
                val notifBody = body.ifBlank { if (isExam) "Esame in programma" else "Visita medica" }

                val deepLink = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    putExtra("push_type", type)
                    putExtra("push_family_id", familyId)
                    putExtra("push_child_id", childId)
                    if (isExam) {
                        putExtra("push_exam_id", intent.getStringExtra(EXTRA_EXAM_ID).orEmpty())
                    } else {
                        putExtra("push_visit_id", intent.getStringExtra(EXTRA_VISIT_ID).orEmpty())
                    }
                }
                val notifId = if (isExam) {
                    intent.getStringExtra(EXTRA_EXAM_ID).orEmpty().hashCode()
                } else {
                    intent.getStringExtra(EXTRA_VISIT_ID).orEmpty().hashCode()
                }
                val pendingIntent = PendingIntent.getActivity(
                    context, notifId, deepLink,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_HEALTH_REMINDERS)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(notifTitle)
                    .setContentText(notifBody)
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .build()
                runCatching { NotificationManagerCompat.from(context).notify(notifId, notification) }
            }
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID_HEALTH_REMINDERS) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_HEALTH_REMINDERS,
                "Promemoria salute",
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = "Promemoria locali visite, esami e cure" },
        )
    }

    companion object {
        const val CHANNEL_ID_HEALTH_REMINDERS = "health_visit_reminders"
        const val EXTRA_TYPE = "extra_type"
        const val EXTRA_VISIT_ID = "extra_visit_id"
        const val EXTRA_EXAM_ID = "extra_exam_id"
        const val EXTRA_TREATMENT_ID = "extra_treatment_id"
        const val EXTRA_DAY_OFFSET = "extra_day_offset"
        const val EXTRA_SLOT_INDEX = "extra_slot_index"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_BODY = "extra_body"
        const val EXTRA_FAMILY_ID = "extra_family_id"
        const val EXTRA_CHILD_ID = "extra_child_id"
        const val EXTRA_VACCINE_ID = "extra_vaccine_id"
        const val TYPE_VISIT_REMINDER = "visit_reminder"
        const val TYPE_EXAM_REMINDER = "exam_reminder"
        const val TYPE_TREATMENT_REMINDER = "treatment_reminder"
        const val TYPE_TREATMENT_SENTINEL = "treatment_sentinel"
        const val TYPE_VACCINE_REMINDER = "vaccine_reminder"
    }
}
