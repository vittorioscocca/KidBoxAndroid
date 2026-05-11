package it.vittorioscocca.kidbox.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.data.life.HousePaymentDeadlineCalculator
import it.vittorioscocca.kidbox.data.local.dao.HousePaymentDao
import it.vittorioscocca.kidbox.data.local.entity.HousePaymentEntity
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Allarme locale: notifica 3 giorni prima della prossima scadenza; al fuoco viene ricalcolata la successiva.
 */
@Singleton
class HousePaymentReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val housePaymentDao: HousePaymentDao,
) {
    private val alarmManager: AlarmManager
        get() = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun syncPayment(entity: HousePaymentEntity) {
        cancelForPayment(entity.id)
        if (entity.isDeleted || !entity.reminderOn) return

        val fire = HousePaymentDeadlineCalculator.nextReminderFireMillis(entity) ?: return
        val interval = fire - System.currentTimeMillis()
        if (interval < 5_000L) return

        val intent = baseIntent(
            paymentId = entity.id,
            familyId = entity.familyId,
            paymentName = entity.name,
        )
        val req = requestCode(entity.id)
        val pi = PendingIntent.getBroadcast(
            context,
            req,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, fire, pi)
    }

    fun cancelForPayment(paymentId: String) {
        val cancelIntent = Intent(context, HealthReminderReceiver::class.java).apply {
            data = alarmUri(paymentId)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode(paymentId),
            cancelIntent,
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE,
        ) ?: return
        alarmManager.cancel(pi)
        pi.cancel()
    }

    suspend fun rescheduleAfterAlarm(firedIntent: Intent) {
        val paymentId = firedIntent.getStringExtra(HealthReminderReceiver.EXTRA_HOUSE_PAYMENT_ID) ?: return
        val entity = housePaymentDao.getById(paymentId) ?: return
        syncPayment(entity)
    }

    private fun alarmUri(paymentId: String): Uri = Uri.parse("kidbox://house-payment/$paymentId")

    private fun baseIntent(
        paymentId: String,
        familyId: String,
        paymentName: String,
    ): Intent =
        Intent(context, HealthReminderReceiver::class.java).apply {
            data = alarmUri(paymentId)
            putExtra(HealthReminderReceiver.EXTRA_TYPE, HealthReminderReceiver.TYPE_HOUSE_PAYMENT)
            putExtra(HealthReminderReceiver.EXTRA_HOUSE_PAYMENT_ID, paymentId)
            putExtra(HealthReminderReceiver.EXTRA_FAMILY_ID, familyId)
            putExtra(HealthReminderReceiver.EXTRA_HOUSE_PAYMENT_NAME, paymentName)
        }

    companion object {
        fun requestCode(paymentId: String): Int = "kb_hpay|$paymentId".hashCode()
    }
}
