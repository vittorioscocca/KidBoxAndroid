package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.VehicleDao
import it.vittorioscocca.kidbox.data.local.entity.VehicleEntity
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Allarmi locali annuali per scadenze veicolo (stesso giorno ogni anno, ore 9 Europe/Rome).
 * Due slot: giorno scadenza e 7 giorni prima. Dopo ogni allarme viene schedulato il successivo (+1 anno).
 */
@Singleton
class VehicleDeadlineReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleDao: VehicleDao,
) {

    private val zone: ZoneId = ZoneId.of("Europe/Rome")
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun syncVehicle(entity: VehicleEntity) {
        cancelForVehicle(entity.id)
        if (entity.isDeleted || !entity.reminderEnabled) return

        val name = entity.name
        val fid = entity.familyId
        val vid = entity.id

        data class Kind(val key: String, val deadlineMillis: Long?, val dueLabel: String, val weekLabel: String)

        listOf(
            Kind("insurance", entity.insuranceExpiryDate, "Assicurazione", "Assicurazione (tra 7 giorni)"),
            Kind("revision", entity.revisionExpiryDate, "Revisione", "Revisione (tra 7 giorni)"),
            Kind("tax", entity.taxExpiryDate, "Bollo", "Bollo (tra 7 giorni)"),
            Kind("service", entity.nextServiceDate, "Tagliando", "Tagliando (tra 7 giorni)"),
        ).forEach { kind ->
            val deadline = kind.deadlineMillis ?: return@forEach
            val dueAnchor = startOfDayRomeMillis(deadline)
            val earlyLocal = Instant.ofEpochMilli(dueAnchor).atZone(zone).toLocalDate().minusDays(7)
            val earlyAnchor = earlyLocal.atStartOfDay(zone).toInstant().toEpochMilli()

            scheduleInitial(
                vehicleId = vid,
                familyId = fid,
                vehicleName = name,
                kindKey = kind.key,
                slot = SLOT_DUE,
                titlePrefix = kind.dueLabel,
                anchorDayMillis = dueAnchor,
            )
            scheduleInitial(
                vehicleId = vid,
                familyId = fid,
                vehicleName = name,
                kindKey = kind.key,
                slot = SLOT_WEEK,
                titlePrefix = kind.weekLabel,
                anchorDayMillis = earlyAnchor,
            )
        }
    }

    fun cancelForVehicle(vehicleId: String) {
        KIND_KEYS.forEach { kind ->
            listOf(SLOT_DUE, SLOT_WEEK).forEach { slot ->
                val cancelIntent = Intent(context, HealthReminderReceiver::class.java).apply {
                    data = alarmUri(vehicleId, kind, slot)
                }
                val pi = PendingIntent.getBroadcast(
                    context,
                    requestCode(vehicleId, kind, slot),
                    cancelIntent,
                    PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
                ) ?: return@forEach
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    suspend fun rescheduleAfterAlarm(firedIntent: Intent) {
        val vehicleId = firedIntent.getStringExtra(HealthReminderReceiver.EXTRA_VEHICLE_ID) ?: return
        val entity = vehicleDao.getById(vehicleId)
        if (entity == null || entity.isDeleted || !entity.reminderEnabled) return

        val kindKey = firedIntent.getStringExtra(HealthReminderReceiver.EXTRA_VEHICLE_KIND) ?: return
        val slot = firedIntent.getStringExtra(HealthReminderReceiver.EXTRA_VEHICLE_SLOT) ?: return
        val oldAnchor = firedIntent.getLongExtra(HealthReminderReceiver.EXTRA_VEHICLE_ANCHOR_DAY_MILLIS, 0L)
        if (oldAnchor <= 0L) return

        val newDate = Instant.ofEpochMilli(oldAnchor).atZone(zone).toLocalDate().plusYears(1)
        val newAnchor = newDate.atStartOfDay(zone).toInstant().toEpochMilli()
        val nextFire = nextNineAmMillisAfter(newAnchor, System.currentTimeMillis())

        val vehicleName = entity.name
        val titlePrefix = firedIntent.getStringExtra(HealthReminderReceiver.EXTRA_VEHICLE_TITLE_PREFIX)
            ?: defaultTitlePrefix(kindKey, slot)

        val intent = baseIntent(
            vehicleId = vehicleId,
            familyId = entity.familyId,
            vehicleName = vehicleName,
            kindKey = kindKey,
            slot = slot,
            titlePrefix = titlePrefix,
            anchorDayMillis = newAnchor,
        )
        val req = requestCode(vehicleId, kindKey, slot)
        val pi = PendingIntent.getBroadcast(
            context,
            req,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFire, pi)
    }

    private fun scheduleInitial(
        vehicleId: String,
        familyId: String,
        vehicleName: String,
        kindKey: String,
        slot: String,
        titlePrefix: String,
        anchorDayMillis: Long,
    ) {
        val nextFire = nextNineAmMillisAfter(anchorDayMillis, System.currentTimeMillis())
        val intent = baseIntent(
            vehicleId = vehicleId,
            familyId = familyId,
            vehicleName = vehicleName,
            kindKey = kindKey,
            slot = slot,
            titlePrefix = titlePrefix,
            anchorDayMillis = anchorDayMillis,
        )
        val req = requestCode(vehicleId, kindKey, slot)
        val pi = PendingIntent.getBroadcast(
            context,
            req,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextFire, pi)
    }

    private fun alarmUri(vehicleId: String, kindKey: String, slot: String): Uri =
        Uri.parse("kidbox://vehicle-deadline/$vehicleId/$kindKey/$slot")

    private fun baseIntent(
        vehicleId: String,
        familyId: String,
        vehicleName: String,
        kindKey: String,
        slot: String,
        titlePrefix: String,
        anchorDayMillis: Long,
    ): Intent =
        Intent(context, HealthReminderReceiver::class.java).apply {
            data = alarmUri(vehicleId, kindKey, slot)
            putExtra(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_VEHICLE_DEADLINE)
            putExtra(HealthReminderReceiver.EXTRA_VEHICLE_ID, vehicleId)
            putExtra(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
            putExtra(HealthReminderReceiver.EXTRA_VEHICLE_NAME, vehicleName)
            putExtra(HealthReminderReceiver.EXTRA_VEHICLE_KIND, kindKey)
            putExtra(HealthReminderReceiver.EXTRA_VEHICLE_SLOT, slot)
            putExtra(HealthReminderReceiver.EXTRA_VEHICLE_TITLE_PREFIX, titlePrefix)
            putExtra(HealthReminderReceiver.EXTRA_VEHICLE_ANCHOR_DAY_MILLIS, anchorDayMillis)
        }

    private fun startOfDayRomeMillis(deadlineMillis: Long): Long {
        val d = Instant.ofEpochMilli(deadlineMillis).atZone(zone).toLocalDate()
        return d.atStartOfDay(zone).toInstant().toEpochMilli()
    }

    private fun nextNineAmMillisAfter(anchorStartOfDayMillis: Long, strictlyAfter: Long): Long {
        var date = Instant.ofEpochMilli(anchorStartOfDayMillis).atZone(zone).toLocalDate()
        while (true) {
            val nine = date.atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
            if (nine > strictlyAfter) return nine
            date = date.plusYears(1)
        }
    }

    private fun defaultTitlePrefix(kindKey: String, slot: String): String {
        val base = when (kindKey) {
            "insurance" -> "Assicurazione"
            "revision" -> "Revisione"
            "tax" -> "Bollo"
            "service" -> "Tagliando"
            else -> "Scadenza"
        }
        return if (slot == SLOT_WEEK) "$base (tra 7 giorni)" else base
    }

    companion object {
        private const val SLOT_DUE = "due"
        private const val SLOT_WEEK = "week"
        private val KIND_KEYS = listOf("insurance", "revision", "tax", "service")

        fun requestCode(vehicleId: String, kindKey: String, slot: String): Int =
            "kb_vrem|$vehicleId|$kindKey|$slot".hashCode()
    }
}
