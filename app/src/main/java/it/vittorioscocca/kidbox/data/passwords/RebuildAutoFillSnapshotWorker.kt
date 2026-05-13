package it.vittorioscocca.kidbox.data.passwords

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import it.vittorioscocca.kidbox.data.crypto.FamilyKeyStore
import it.vittorioscocca.kidbox.data.crypto.PasswordCypher
import it.vittorioscocca.kidbox.data.local.FamilySessionPreferences
import it.vittorioscocca.kidbox.data.local.dao.PasswordEntryDao
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RebuildAutoFillSnapshot"
private const val UNIQUE = "kidbox_autofill_snapshot_rebuild"

@HiltWorker
class RebuildAutoFillSnapshotWorker @AssistedInject constructor(
    @Assisted private val appContext: Context,
    @Assisted workerParams: WorkerParameters,
    private val entryDao: PasswordEntryDao,
    private val passwordCypher: PasswordCypher,
    private val auth: FirebaseAuth,
    private val familySessionPreferences: FamilySessionPreferences,
    private val encryptedStore: AutoFillSnapshotEncryptedStore,
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val uid = auth.currentUser?.uid?.trim().orEmpty()
        val familyId = familySessionPreferences.getActiveFamilyId()?.trim().orEmpty()
        if (uid.isBlank() || familyId.isBlank()) {
            encryptedStore.delete()
            return Result.success()
        }
        val familyKey = FamilyKeyStore.loadFamilyKey(appContext, familyId, uid)
        if (familyKey == null) {
            encryptedStore.delete()
            return Result.success()
        }

        return runCatching {
            val entities = entryDao.listVisibleForAutofill(familyId, uid)
            val items = buildList {
                for (e in entities) {
                    runCatching {
                        val title = passwordCypher.decrypt(e.titleCipher, e.familyId, e.visibility, e.createdBy, uid)
                        val username = e.usernameCipher?.let { passwordCypher.decrypt(it, e.familyId, e.visibility, e.createdBy, uid) }.orEmpty()
                        val password = passwordCypher.decrypt(e.passwordCipher, e.familyId, e.visibility, e.createdBy, uid)
                        val websiteRaw = e.websiteCipher?.let { passwordCypher.decrypt(it, e.familyId, e.visibility, e.createdBy, uid) }
                        val host = AutoFillWebsiteHost.normalizedHost(websiteRaw)
                        val otp = e.otpConfigCipher?.let { cipher ->
                            val json = passwordCypher.decrypt(cipher, e.familyId, e.visibility, e.createdBy, uid)
                            runCatching {
                                val o = org.json.JSONObject(json)
                                AutoFillOtpPayload(
                                    secret = o.getString("secret"),
                                    digits = o.optInt("digits", 6),
                                    period = o.optInt("period", 30),
                                    algorithm = o.optString("algorithm", "SHA1"),
                                )
                            }.getOrNull()
                        }
                        add(
                            AutoFillSnapshotItem(
                                id = e.id,
                                title = title,
                                username = username,
                                password = password,
                                website = host,
                                visibility = e.visibility,
                                owner = e.createdBy,
                                otp = otp,
                            ),
                        )
                    }.onFailure { err ->
                        Log.d(TAG, "skip entry id=${e.id}: ${err.message}")
                    }
                }
            }
            val snapshot = AutoFillSnapshot(version = 1, updatedAt = Instant.now(), items = items)
            val json = AutoFillSnapshotJson.toJson(snapshot).toByteArray(Charsets.UTF_8)
            val blob = AutoFillSnapshotFamilyCipher.encryptCombined(json, familyKey)
            encryptedStore.writeFamilyEncryptedBlob(blob)
            AutoFillSnapshotLoader.clearMemoryCache()
            Result.success()
        }.getOrElse { err ->
            Log.w(TAG, "rebuild failed: ${err.message}")
            Result.retry()
        }
    }
}

@Singleton
class AutoFillSnapshotScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    fun enqueue() {
        val wm = WorkManager.getInstance(context)
        val builder = OneTimeWorkRequestBuilder<RebuildAutoFillSnapshotWorker>()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            builder.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
        }
        val req = builder.build()
        wm.enqueueUniqueWork(UNIQUE, ExistingWorkPolicy.REPLACE, req)
    }
}
