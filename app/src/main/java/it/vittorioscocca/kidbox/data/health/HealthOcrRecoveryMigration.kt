package it.vittorioscocca.kidbox.data.health

import android.content.Context
import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG_HEALTH_OCR_RECOVERY = "HealthOcrRecovery"

@Singleton
class HealthOcrRecoveryMigration @Inject constructor(
    private val healthAttachmentService: HealthAttachmentService,
) {
    fun runIfNeeded(context: Context) {
        val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)
        val alreadyRan = prefs.getBoolean(PREF_OCR_RECOVERY_V1_DONE, false)
        if (alreadyRan) return

        val activeFamilyId = prefs.getString("active_family_id", null)?.trim().orEmpty()
        val targetFamilyId = activeFamilyId
        if (targetFamilyId.isBlank()) {
            Log.i(TAG_HEALTH_OCR_RECOVERY, "OCR recovery skipped: no family available yet")
            return
        }

        Log.i(
            TAG_HEALTH_OCR_RECOVERY,
            "Starting one-shot OCR recovery for familyId=$targetFamilyId",
        )
        healthAttachmentService.enqueueBackfillHealthExtraction(targetFamilyId)
        prefs.edit().putBoolean(PREF_OCR_RECOVERY_V1_DONE, true).apply()
        Log.i(TAG_HEALTH_OCR_RECOVERY, "One-shot OCR recovery marked as completed")
    }

    companion object {
        private const val PREF_OCR_RECOVERY_V1_DONE = "kb_health_ocr_recovery_v1_done"
    }
}
