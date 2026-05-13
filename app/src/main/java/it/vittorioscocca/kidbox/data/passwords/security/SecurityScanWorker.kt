package it.vittorioscocca.kidbox.data.passwords.security

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.vittorioscocca.kidbox.notifications.SecurityNotifier
import kotlinx.coroutines.flow.first

@HiltWorker
class SecurityScanWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val audit: SecurityAuditService,
    private val notifier: SecurityNotifier,
    private val preferences: PasswordSecurityPreferences,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val scan = audit.runFullScan()
        val previous = preferences.previousCompromisedIds.first()
        val current = scan.compromised.map { it.id }.toSet()
        val newCount = current.subtract(previous).size
        preferences.setPreviousCompromisedIds(current)
        preferences.setLastScanAt(System.currentTimeMillis())
        val pushEnabled = preferences.pushAlertsEnabled.first()
        if (newCount > 0 && pushEnabled) notifier.notifyNewBreaches(newCount)
        return Result.success()
    }
}
