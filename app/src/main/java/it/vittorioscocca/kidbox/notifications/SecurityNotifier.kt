package it.vittorioscocca.kidbox.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.MainActivity
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SecurityNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    fun notifyNewBreaches(newCount: Int) {
        if (newCount <= 0) return
        ensureChannel()
        val intent = Intent(context, MainActivity::class.java).apply {
            // NEW_TASK necessario: parte da un contesto non-Activity (ApplicationContext).
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP,
            )
            putExtra("push_type", "password_security_summary")
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            991100,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("KidBox - Password a rischio")
            .setContentText("$newCount nuove password trovate in data breach.")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()
        manager.notify(991101, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Sicurezza Password",
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }

    companion object {
        const val CHANNEL_ID = "password_security"
    }
}
