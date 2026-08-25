package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.local.dao.VehicleDao
import it.vittorioscocca.kidbox.data.local.entity.VehicleEntity
import it.vittorioscocca.kidbox.data.vehicles.VehicleReminderOffsets
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Allarmi locali annuali per scadenze veicolo (stesso giorno ogni anno, ore 9 Europe/Rome).
 * Fino a 3 avvisi per scadenza, scelti dall'utente tra: giorno stesso, 2 giorni prima,
 * 1 settimana prima (`VehicleEntity.reminderOffsetsJson`). Dopo ogni allarme viene
 * schedulato il successivo (+1 anno).
 */
@Singleton
class VehicleDeadlineReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val vehicleDao: VehicleDao,
    private val alarmRegistry: ReminderAlarmRegistry,
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
        val offsets = VehicleReminderOffsets.decode(entity.reminderOffsetsJson)

        data class Kind(val key: String, val deadlineMillis: Long?, val label: String)

        listOf(
            Kind("insurance", entity.insuranceExpiryDate, "Assicurazione"),
            Kind("revision", entity.revisionExpiryDate, "Revisione"),
            Kind("tax", entity.taxExpiryDate, "Bollo"),
            Kind("service", entity.nextServiceDate, "Tagliando"),
        ).forEach { kind ->
            val deadline = kind.deadlineMillis ?: return@forEach
            val dueAnchor = startOfDayRomeMillis(deadline)

            offsets.offsets(kind.key).forEach { days ->
                val fireLocal = Instant.ofEpochMilli(dueAnchor).atZone(zone).toLocalDate().minusDays(days.toLong())
                val fireAnchor = fireLocal.atStartOfDay(zone).toInstant().toEpochMilli()
                scheduleInitial(
                    vehicleId = vid,
                    familyId = fid,
                    vehicleName = name,
                    kindKey = kind.key,
                    slot = offsetSlot(days),
                    titlePrefix = kind.label,
                    anchorDayMillis = fireAnchor,
                )
            }
        }
    }

    fun cancelForVehicle(vehicleId: String) {
        KIND_KEYS.forEach { kind ->
            VehicleReminderOffsets.ALLOWED_OFFSETS.map { offsetSlot(it) }.forEach { slot ->
                alarmRegistry.forget(ReminderAlarmRegistry.vehicleKey(vehicleId, kind, slot))
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
            ?: defaultTitlePrefix(kindKey)

        armAlarm(
            vehicleId = vehicleId,
            familyId = entity.familyId,
            vehicleName = vehicleName,
            kindKey = kindKey,
            slot = slot,
            titlePrefix = titlePrefix,
            anchorDayMillis = newAnchor,
            fireAtMillis = nextFire,
        )
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
        armAlarm(
            vehicleId = vehicleId,
            familyId = familyId,
            vehicleName = vehicleName,
            kindKey = kindKey,
            slot = slot,
            titlePrefix = titlePrefix,
            anchorDayMillis = anchorDayMillis,
            fireAtMillis = nextNineAmMillisAfter(anchorDayMillis, System.currentTimeMillis()),
        )
    }

    private fun armAlarm(
        vehicleId: String,
        familyId: String,
        vehicleName: String,
        kindKey: String,
        slot: String,
        titlePrefix: String,
        anchorDayMillis: Long,
        fireAtMillis: Long,
    ) {
        alarmRegistry.arm(
            ReminderAlarmRegistry.AlarmSpec(
                key = ReminderAlarmRegistry.vehicleKey(vehicleId, kindKey, slot),
                target = ReminderAlarmRegistry.Target.HEALTH,
                requestCode = requestCode(vehicleId, kindKey, slot),
                fireAtMillis = fireAtMillis,
                dataUri = alarmUri(vehicleId, kindKey, slot).toString(),
                stringExtras = mapOf(
                    HealthReminderReceiver.EXTRA_TYPE to HealthReminderReceiver.TYPE_VEHICLE_DEADLINE,
                    HealthReminderReceiver.EXTRA_VEHICLE_ID to vehicleId,
                    HealthReminderReceiver.EXTRA_FAMILY_ID to familyId,
                    HealthReminderReceiver.EXTRA_VEHICLE_NAME to vehicleName,
                    HealthReminderReceiver.EXTRA_VEHICLE_KIND to kindKey,
                    HealthReminderReceiver.EXTRA_VEHICLE_SLOT to slot,
                    HealthReminderReceiver.EXTRA_VEHICLE_TITLE_PREFIX to titlePrefix,
                ),
                longExtras = mapOf(
                    HealthReminderReceiver.EXTRA_VEHICLE_ANCHOR_DAY_MILLIS to anchorDayMillis,
                ),
            ),
        )
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

    private fun defaultTitlePrefix(kindKey: String): String = when (kindKey) {
        "insurance" -> "Assicurazione"
        "revision" -> "Revisione"
        "tax" -> "Bollo"
        "service" -> "Tagliando"
        else -> "Scadenza"
    }

    private fun offsetSlot(days: Int): String = "offset$days"

    companion object {
        private val KIND_KEYS = listOf("insurance", "revision", "tax", "service")

        fun requestCode(vehicleId: String, kindKey: String, slot: String): Int =
            "kb_vrem|$vehicleId|$kindKey|$slot".hashCode()
    }
}
