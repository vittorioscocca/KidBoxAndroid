package it.vittorioscocca.kidbox.feature.passwords.autofill

import it.vittorioscocca.kidbox.util.KBLog

import android.content.Intent
import android.os.Bundle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.credentials.CreatePasswordRequest
import androidx.credentials.CreatePasswordResponse
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.data.local.entity.PasswordEntryEntity
import it.vittorioscocca.kidbox.domain.model.KBVisibilityScope
import java.util.UUID
import kotlinx.coroutines.runBlocking

/**
 * Flusso "salva password" dal Credential Manager ([BeginCreatePasswordCredentialRequest]).
 */
@AndroidEntryPoint
class KidBoxCredentialPasswordCreateActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val createReq = PendingIntentHandler.retrieveProviderCreateCredentialRequest(intent)
        val calling = createReq?.callingRequest
        if (calling !is CreatePasswordRequest) {
            finish()
            return
        }
        val username = calling.id
        val password = calling.password
        val deps = applicationContext.autofillEntryPoint()
        val uid = deps.firebaseAuth().currentUser?.uid.orEmpty()
        val familyId = resolveAutofillFamilyId(
            applicationContext,
            deps.familySessionPreferences(),
            uid,
        ).orEmpty()
        if (uid.isBlank() || familyId.isBlank()) {
            cancelSave()
            return
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Salvare in KidBox?")
            .setMessage("Account: $username")
            .setNegativeButton("Annulla") { _, _ -> cancelSave() }
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
                    val out = Intent()
                    PendingIntentHandler.setCreateCredentialResponse(out, CreatePasswordResponse())
                    setResult(RESULT_OK, out)
                    finish()
                }.onFailure { e ->
                    KBLog.security.error("save failed", TAG, e)
                    cancelSave()
                }
            }
            .setOnCancelListener { cancelSave() }
            .show()
    }

    private fun cancelSave() {
        setResult(RESULT_CANCELED)
        finish()
    }

    companion object {
        private const val TAG = "KidBoxCredPasswordCreate"
    }
}
