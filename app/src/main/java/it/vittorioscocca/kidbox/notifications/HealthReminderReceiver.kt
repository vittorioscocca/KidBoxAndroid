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
import dagger.hilt.android.EntryPointAccessors
import it.vittorioscocca.kidbox.MainActivity
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.local.mapper.toDomain
import it.vittorioscocca.kidbox.util.KBLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class HealthReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        ensureChannel(context)

        val type = intent.getStringExtra(EXTRA_TYPE) ?: TYPE_VISIT_REMINDER

        when (type) {
            TYPE_TREATMENT_SENTINEL -> {
                // Sentinella: non mostra nulla. Scatta un minuto dopo l'ultima dose
                // della finestra corrente e serve solo a estenderla, altrimenti una
                // cura lunga smetterebbe di avvisare dopo la fine della finestra.
                val treatmentId = intent.getStringExtra(EXTRA_TREATMENT_ID).orEmpty()
                if (treatmentId.isEmpty()) return
                val pendingResult = goAsync()
                val appCtx = context.applicationContext
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        extendTreatmentWindow(appCtx, treatmentId)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            TYPE_TREATMENT_REMINDER -> {
                val treatmentId = intent.getStringExtra(EXTRA_TREATMENT_ID).orEmpty()
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
                val childId = intent.getStringExtra(EXTRA_CHILD_ID).orEmpty()
                val dayOffset = intent.getIntExtra(EXTRA_DAY_OFFSET, 0)
                val slotIndex = intent.getIntExtra(EXTRA_SLOT_INDEX, 0)
                // Prima la chiave (tradotta ora, alla consegna), poi la frase già
                // composta: gli alarm armati da una versione precedente hanno solo
                // quella, e sopravvivono all'aggiornamento dell'app.
                val title = KBNotificationText.title(context, intent)
                    ?: intent.getStringExtra(EXTRA_TITLE)
                    ?: context.getString(R.string.treatment_reminder_notification_title)
                val body = KBNotificationText.body(context, intent)
                    ?: intent.getStringExtra(EXTRA_BODY)
                    ?: context.getString(R.string.treatment_reminder_body_fallback)

                val deepLink = Intent(context, MainActivity::class.java).apply {
                    // NEW_TASK necessario: parte da un BroadcastReceiver (AlarmManager), un
                    // contesto non-Activity. Senza, con l'app in background Android può aprire
                    // un secondo task invece di riportare avanti quello esistente — vedi lo
                    // stesso fix in KidBoxFirebaseMessagingService.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                    putExtra("push_type", TYPE_TREATMENT_REMINDER)
                    putExtra("push_treatment_id", treatmentId)
                    putExtra("push_family_id", familyId)
                    putExtra("push_child_id", childId)
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
            TYPE_WALLET_REMINDER -> {
                val ticketId = intent.getStringExtra(EXTRA_WALLET_TICKET_ID).orEmpty()
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
                val deepLink = Intent(context, MainActivity::class.java).apply {
                    // NEW_TASK necessario: parte da un BroadcastReceiver (AlarmManager), un
                    // contesto non-Activity. Senza, con l'app in background Android può aprire
                    // un secondo task invece di riportare avanti quello esistente — vedi lo
                    // stesso fix in KidBoxFirebaseMessagingService.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                    putExtra("push_type", "wallet_ticket_reminder")
                    putExtra("push_family_id", familyId)
                    putExtra("ticketId", ticketId)
                }
                val notifId = ticketId.hashCode()
                val pendingIntent = PendingIntent.getActivity(
                    context, notifId, deepLink,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_HEALTH_REMINDERS)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(
                        KBNotificationText.title(context, intent)
                            ?: title.ifBlank { context.getString(R.string.wallet_ticket_reminder_title_fallback) },
                    )
                    .setContentText(
                        KBNotificationText.body(context, intent)
                            ?: body.ifBlank { context.getString(R.string.wallet_ticket_reminder_body_fallback) },
                    )
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .build()
                runCatching { NotificationManagerCompat.from(context).notify(notifId, notification) }
            }
            TYPE_WALLET_DOCUMENT_REMINDER -> {
                val documentId = intent.getStringExtra(EXTRA_WALLET_DOCUMENT_ID).orEmpty()
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
                val deepLink = Intent(context, MainActivity::class.java).apply {
                    // NEW_TASK necessario: parte da un BroadcastReceiver (AlarmManager), un
                    // contesto non-Activity. Senza, con l'app in background Android può aprire
                    // un secondo task invece di riportare avanti quello esistente — vedi lo
                    // stesso fix in KidBoxFirebaseMessagingService.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                    putExtra("push_type", "wallet_document_reminder")
                    putExtra("push_family_id", familyId)
                    putExtra("documentId", documentId)
                }
                val notifId = documentId.hashCode()
                val pendingIntent = PendingIntent.getActivity(
                    context, notifId, deepLink,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_HEALTH_REMINDERS)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(
                        KBNotificationText.title(context, intent)
                            ?: title.ifBlank { context.getString(R.string.wallet_document_reminder_title_fallback) },
                    )
                    .setContentText(
                        KBNotificationText.body(context, intent)
                            ?: body.ifBlank { context.getString(R.string.wallet_document_reminder_body_fallback) },
                    )
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .build()
                runCatching { NotificationManagerCompat.from(context).notify(notifId, notification) }
            }
            TYPE_PASSWORD_EXPIRY -> {
                val entryId = intent.getStringExtra(EXTRA_PASSWORD_ENTRY_ID).orEmpty()
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
                val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val body = intent.getStringExtra(EXTRA_BODY).orEmpty()
                val deepLink = Intent(context, MainActivity::class.java).apply {
                    // NEW_TASK necessario: parte da un BroadcastReceiver (AlarmManager), un
                    // contesto non-Activity. Senza, con l'app in background Android può aprire
                    // un secondo task invece di riportare avanti quello esistente — vedi lo
                    // stesso fix in KidBoxFirebaseMessagingService.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                    putExtra("push_type", TYPE_PASSWORD_EXPIRY)
                    putExtra("push_family_id", familyId)
                    putExtra("push_entry_id", entryId)
                }
                val notifId = entryId.hashCode()
                val pendingIntent = PendingIntent.getActivity(
                    context, notifId, deepLink,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_HEALTH_REMINDERS)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(
                        KBNotificationText.title(context, intent)
                            ?: title.ifBlank { context.getString(R.string.password_expiry_reminder_title) },
                    )
                    .setContentText(KBNotificationText.body(context, intent) ?: body)
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
                    // NEW_TASK necessario: parte da un BroadcastReceiver (AlarmManager), un
                    // contesto non-Activity. Senza, con l'app in background Android può aprire
                    // un secondo task invece di riportare avanti quello esistente — vedi lo
                    // stesso fix in KidBoxFirebaseMessagingService.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                    putExtra("push_type", TYPE_VACCINE_REMINDER)
                    putExtra("push_vaccine_id", vaccineId)
                    putExtra("push_family_id", familyId)
                    putExtra("push_child_id", childId)
                }
                val notifId = vaccineId.hashCode()
                val pendingIntent = PendingIntent.getActivity(
                    context, notifId, deepLink,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
                val notification = NotificationCompat.Builder(context, CHANNEL_ID_HEALTH_REMINDERS)
                    .setSmallIcon(R.mipmap.ic_launcher)
                    .setContentTitle(context.getString(R.string.vaccine_reminder_notification_title))
                    .setContentText(
                        KBNotificationText.body(context, intent)
                            ?: body.ifBlank { context.getString(R.string.vaccine_reminder_body_fallback) },
                    )
                    .setAutoCancel(true)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setContentIntent(pendingIntent)
                    .build()
                runCatching { NotificationManagerCompat.from(context).notify(notifId, notification) }
            }
            TYPE_VEHICLE_DEADLINE -> {
                val vehicleId = intent.getStringExtra(EXTRA_VEHICLE_ID).orEmpty()
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
                val vehicleName = intent.getStringExtra(EXTRA_VEHICLE_NAME).orEmpty()
                val kindKey = intent.getStringExtra(EXTRA_VEHICLE_KIND).orEmpty()
                val slot = intent.getStringExtra(EXTRA_VEHICLE_SLOT).orEmpty()
                val offsetDays = if (slot.startsWith("offset")) slot.removePrefix("offset").toIntOrNull() else null
                val kindLabel = when (kindKey) {
                    "insurance" -> context.getString(R.string.vehicles_insurance)
                    "revision" -> context.getString(R.string.vehicles_inspection)
                    "tax" -> context.getString(R.string.vehicles_road_tax)
                    "service" -> context.getString(R.string.vehicles_next_service)
                    else -> null
                }
                val body = when (offsetDays) {
                    0 -> context.getString(R.string.vehicles_reminder_body_same_day)
                    2 -> context.getString(R.string.vehicles_reminder_body_2d)
                    7 -> context.getString(R.string.vehicles_reminder_body_1w)
                    else -> context.getString(R.string.vehicles_reminder_body_same_day)
                }
                val reminderWord = context.getString(R.string.vehicles_reminder)
                val title = when {
                    kindLabel != null && vehicleName.isNotBlank() -> "$reminderWord: $kindLabel: $vehicleName"
                    vehicleName.isNotBlank() -> "$reminderWord Garage: $vehicleName"
                    else -> "$reminderWord Garage"
                }

                val deepLink = Intent(context, MainActivity::class.java).apply {
                    // NEW_TASK necessario: parte da un BroadcastReceiver (AlarmManager), un
                    // contesto non-Activity. Senza, con l'app in background Android può aprire
                    // un secondo task invece di riportare avanti quello esistente — vedi lo
                    // stesso fix in KidBoxFirebaseMessagingService.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                    putExtra("push_type", TYPE_VEHICLE_DEADLINE)
                    putExtra("push_family_id", familyId)
                    putExtra("push_vehicle_id", vehicleId)
                }
                val notifId = ("$vehicleId|$slot|$kindKey").hashCode()
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

                val pendingResult = goAsync()
                val appCtx = context.applicationContext
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val ep = EntryPointAccessors.fromApplication(
                            appCtx,
                            VehicleReminderEntryPoint::class.java,
                        )
                        ep.vehicleDeadlineReminderScheduler().rescheduleAfterAlarm(intent)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            TYPE_HOUSE_PAYMENT -> {
                val paymentId = intent.getStringExtra(EXTRA_HOUSE_PAYMENT_ID).orEmpty()
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
                val paymentName = intent.getStringExtra(EXTRA_HOUSE_PAYMENT_NAME).orEmpty()
                val title = context.getString(R.string.house_payment_reminder_title)
                val body = if (paymentName.isNotBlank()) {
                    context.getString(R.string.house_payment_reminder_body, paymentName)
                } else {
                    context.getString(R.string.house_payment_reminder_body_fallback)
                }
                val deepLink = Intent(context, MainActivity::class.java).apply {
                    // NEW_TASK necessario: parte da un BroadcastReceiver (AlarmManager), un
                    // contesto non-Activity. Senza, con l'app in background Android può aprire
                    // un secondo task invece di riportare avanti quello esistente — vedi lo
                    // stesso fix in KidBoxFirebaseMessagingService.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
                    putExtra("push_type", TYPE_HOUSE_PAYMENT)
                    putExtra("push_family_id", familyId)
                    putExtra("push_house_payment_id", paymentId)
                }
                val notifId = paymentId.hashCode()
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

                val pendingResult = goAsync()
                val appCtx = context.applicationContext
                CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
                    try {
                        val ep = EntryPointAccessors.fromApplication(
                            appCtx,
                            VehicleReminderEntryPoint::class.java,
                        )
                        ep.housePaymentReminderScheduler().rescheduleAfterAlarm(intent)
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            else -> {
                val body = intent.getStringExtra(EXTRA_TITLE).orEmpty()
                val familyId = intent.getStringExtra(EXTRA_FAMILY_ID).orEmpty()
                val childId = intent.getStringExtra(EXTRA_CHILD_ID).orEmpty()
                val isExam = type == TYPE_EXAM_REMINDER
                val notifTitle = KBNotificationText.title(context, intent)
                    ?: context.getString(if (isExam) R.string.exam_reminder_notification_title else R.string.visit_reminder_notification_title)
                val notifBody = KBNotificationText.body(context, intent) ?: body.ifBlank {
                    context.getString(if (isExam) R.string.exam_reminder_body_fallback else R.string.visit_reminder_body_fallback)
                }

                val deepLink = Intent(context, MainActivity::class.java).apply {
                    // NEW_TASK necessario: parte da un BroadcastReceiver (AlarmManager), un
                    // contesto non-Activity. Senza, con l'app in background Android può aprire
                    // un secondo task invece di riportare avanti quello esistente — vedi lo
                    // stesso fix in KidBoxFirebaseMessagingService.
                    addFlags(
                        Intent.FLAG_ACTIVITY_NEW_TASK or
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                            Intent.FLAG_ACTIVITY_CLEAR_TOP,
                    )
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

    /**
     * Ricarica la cura da Room e allunga la finestra di alarm. La cura può essere
     * stata sospesa, chiusa o cancellata da un altro membro dopo l'ultima
     * schedulazione: in quel caso non si ri-arma nulla.
     */
    private suspend fun extendTreatmentWindow(appCtx: Context, treatmentId: String) {
        runCatching {
            val ep = EntryPointAccessors.fromApplication(
                appCtx,
                VehicleReminderEntryPoint::class.java,
            )
            val entity = ep.treatmentDao().getById(treatmentId) ?: return
            if (entity.isDeleted || !entity.isActive || !entity.reminderEnabled) return
            val subjectName = if (entity.petId.isNotBlank()) {
                ep.petDao().getById(entity.petId)?.name
            } else {
                ep.childDao().getById(entity.childId)?.name
            }.orEmpty()
            ep.treatmentNotificationManager().rescheduleIfNeeded(entity.toDomain(), subjectName)
        }.onFailure {
            KBLog.app.error("sentinella: estensione finestra fallita treatmentId=$treatmentId", "HealthReminderReceiver", it)
        }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID_HEALTH_REMINDERS) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID_HEALTH_REMINDERS,
                context.getString(R.string.health_garage_reminder_channel_name),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply { description = context.getString(R.string.health_garage_reminder_channel_description) },
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
        const val TYPE_PASSWORD_EXPIRY = "password_expiry_reminder"
        const val EXTRA_PASSWORD_ENTRY_ID = "extra_password_entry_id"
        const val TYPE_VISIT_REMINDER = "visit_reminder"
        const val TYPE_EXAM_REMINDER = "exam_reminder"
        const val TYPE_TREATMENT_REMINDER = "treatment_reminder"
        const val TYPE_TREATMENT_SENTINEL = "treatment_sentinel"
        const val TYPE_VACCINE_REMINDER = "vaccine_reminder"
        const val TYPE_WALLET_REMINDER = "wallet_reminder"
        const val EXTRA_WALLET_TICKET_ID = "extra_wallet_ticket_id"
        const val TYPE_WALLET_DOCUMENT_REMINDER = "wallet_document_reminder"
        const val EXTRA_WALLET_DOCUMENT_ID = "extra_wallet_document_id"
        const val TYPE_VEHICLE_DEADLINE = "vehicle_deadline_reminder"
        const val TYPE_HOUSE_PAYMENT = "house_payment_reminder"
        const val EXTRA_HOUSE_PAYMENT_ID = "extra_house_payment_id"
        const val EXTRA_HOUSE_PAYMENT_NAME = "extra_house_payment_name"
        const val EXTRA_VEHICLE_ID = "extra_vehicle_id"
        const val EXTRA_VEHICLE_NAME = "extra_vehicle_name"
        const val EXTRA_VEHICLE_KIND = "extra_vehicle_kind"
        const val EXTRA_VEHICLE_SLOT = "extra_vehicle_slot"
        const val EXTRA_VEHICLE_TITLE_PREFIX = "extra_vehicle_title_prefix"
        const val EXTRA_VEHICLE_ANCHOR_DAY_MILLIS = "extra_vehicle_anchor_day_millis"
    }
}
