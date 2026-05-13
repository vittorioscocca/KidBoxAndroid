package it.vittorioscocca.kidbox.feature.passwords.autofill

import android.os.Bundle
import android.util.Log
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.util.UUID
import kotlinx.coroutines.runBlocking

/**
 * Conferma salvataggio credenziale dopo [KidBoxAutofillService.onSaveRequest].
 */
@AndroidEntryPoint
class KidBoxAutofillSaveActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val username = intent.getStringExtra(KidBoxAutofillService.EXTRA_SAVE_USERNAME).orEmpty()
        val password = intent.getStringExtra(KidBoxAutofillService.EXTRA_SAVE_PASSWORD).orEmpty()
        if (username.isBlank() || password.isBlank()) {
            finish()
            return
        }
        val deps = applicationContext.autofillEntryPoint()
        val uid = deps.firebaseAuth().currentUser?.uid.orEmpty()
        val familyId = resolveAutofillFamilyId(
            applicationContext,
            deps.familySessionPreferences(),
            uid,
        ).orEmpty()
        if (uid.isBlank() || familyId.isBlank()) {
            finish()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Salvare in KidBox?")
            .setMessage("Account: $username")
            .setNegativeButton("Annulla") { _, _ -> finish() }
            .setPositiveButton("Salva") { _, _ ->
                runCatching {
                    val cypher = deps.passwordCypher()
                    val dao = deps.passwordEntryDao()
                    val repo = deps.passwordsRepository()
                    val now = System.currentTimeMillis()
                    val id = UUID.randomUUID().toString()
                    val vis = KBVisibilityScope.FAMILY
                    val entity = PasswordEntryEntity(
                        id = id,
                        familyId = familyId,
                        createdBy = uid,
                        visibility = vis,
                        visibilityMemberIdsJson = "[]",
                        groupId = null,
                        titleCipher = cypher.encrypt(username, familyId, vis, uid, uid),
                        usernameCipher = cypher.encrypt(username, familyId, vis, uid, uid),
                        passwordCipher = cypher.encrypt(password, familyId, vis, uid, uid),
                        websiteCipher = null,
                        notesCipher = null,
                        otpConfigCipher = null,
                        iconURL = null,
                        lastUsedAtEpochMillis = null,
                        passwordUpdatedAtEpochMillis = now,
                        expiresAtEpochMillis = null,
                        createdAtEpochMillis = now,
                        updatedAtEpochMillis = now,
                        deletedAtEpochMillis = null,
                        isFavorite = false,
                        syncStateRaw = 1,
                        lastSyncError = null,
                    )
                    runBlocking {
                        dao.upsert(entity)
                        repo.pushUpsertEntry(entity)
                    }
                    repo.scheduleAutofillSnapshotRebuild()
                    finish()
                }.onFailure { e ->
                    Log.e(TAG, "save failed", e)
                    finish()
                }
            }
            .setOnCancelListener { finish() }
            .show()
    }

    companion object {
        private const val TAG = "KidBoxAutofillSave"
    }
}
