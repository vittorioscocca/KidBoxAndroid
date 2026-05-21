package it.vittorioscocca.kidbox.feature.passwords.autofill

import it.vittorioscocca.kidbox.util.KBLog

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.credentials.GetCredentialResponse
import androidx.credentials.PasswordCredential
import androidx.credentials.provider.PendingIntentHandler
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.data.passwords.AutoFillSnapshotLoader
import it.vittorioscocca.kidbox.data.passwords.AutoFillUserPreferences
import javax.inject.Inject

/**
 * Activity invocata dal Credential Manager dopo la scelta di una voce password:
 * biometria (se richiesta) → [PendingIntentHandler.setGetCredentialResponse].
 */
@AndroidEntryPoint
class KidBoxCredentialPasswordGetActivity : FragmentActivity() {

    @Inject
    lateinit var autoFillUserPreferences: AutoFillUserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val getRequest = PendingIntentHandler.retrieveProviderGetCredentialRequest(intent)
        if (getRequest == null) {
            finish()
            return
        }
        val entryId = intent.getStringExtra(KidBoxCredentialProviderService.EXTRA_ENTRY_ID).orEmpty()
        if (entryId.isBlank()) {
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
        val snapshot = AutoFillSnapshotLoader.load(applicationContext, familyId, uid, deps.autoFillSnapshotEncryptedStore())
        val item = snapshot?.items?.firstOrNull { it.id == entryId }
        if (item == null) {
            finish()
            return
        }

        fun deliver() {
            val result = Intent()
            val cred = PasswordCredential(item.username, item.password)
            PendingIntentHandler.setGetCredentialResponse(result, GetCredentialResponse(cred))
            setResult(RESULT_OK, result)
            finish()
        }

        if (!autoFillUserPreferences.requireBiometricAlways) {
            deliver()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val biometricPrompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    KBLog.security.warning("biometric error $errorCode $errString", TAG)
                    finish()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    deliver()
                }

                override fun onAuthenticationFailed() {
                    finish()
                }
            },
        )
        val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle("KidBox")
            .setSubtitle("Sblocca per inserire la password")
            .setAllowedAuthenticators(allowed)
            .build()
        runCatching { biometricPrompt.authenticate(info) }.onFailure {
            KBLog.security.warning("biometric authenticate failed: ${it.message}", TAG)
            finish()
        }
    }

    companion object {
        private const val TAG = "KidBoxCredPasswordGet"
    }
}
