package it.vittorioscocca.kidbox.feature.passwords.autofill

import it.vittorioscocca.kidbox.util.KBLog

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.IntentCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import dagger.hilt.android.AndroidEntryPoint
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.passwords.AutoFillSnapshotLoader
import it.vittorioscocca.kidbox.data.passwords.AutoFillUserPreferences
import javax.inject.Inject
import android.service.autofill.Dataset
import android.service.autofill.FillResponse

/**
 * Dopo autenticazione, restituisce un [FillResponse] con valori in chiaro per il dataset selezionato.
 */
@AndroidEntryPoint
class KidBoxAutofillAuthActivity : FragmentActivity() {

    @Inject
    lateinit var autoFillUserPreferences: AutoFillUserPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val entryId = intent.getStringExtra(KidBoxAutofillService.EXTRA_ENTRY_ID).orEmpty()
        val usernameId = IntentCompat.getParcelableExtra(
            intent,
            KidBoxAutofillService.EXTRA_USERNAME_AUTOFILL_ID,
            AutofillId::class.java,
        )
        val passwordId = IntentCompat.getParcelableExtra(
            intent,
            KidBoxAutofillService.EXTRA_PASSWORD_AUTOFILL_ID,
            AutofillId::class.java,
        )
        if (entryId.isBlank() || passwordId == null) {
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
            val presentation = RemoteViews(packageName, R.layout.autofill_dataset).apply {
                setTextViewText(R.id.autofill_label, item.title.ifBlank { item.username })
            }
            val dataset = Dataset.Builder(presentation).apply {
                usernameId?.let { setValue(it, AutofillValue.forText(item.username)) }
                setValue(passwordId, AutofillValue.forText(item.password))
            }.build()
            val response = FillResponse.Builder().addDataset(dataset).build()
            val reply = Intent().putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, response)
            setResult(Activity.RESULT_OK, reply)
            finish()
        }

        if (!autoFillUserPreferences.requireBiometricAlways) {
            deliver()
            return
        }

        val executor = ContextCompat.getMainExecutor(this)
        val prompt = BiometricPrompt(
            this,
            executor,
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    KBLog.security.warning("bio err $errorCode $errString", TAG)
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
            .setSubtitle("Sblocca per compilare")
            .setAllowedAuthenticators(allowed)
            .build()
        runCatching { prompt.authenticate(info) }.onFailure {
            KBLog.security.warning("bio failed ${it.message}", TAG)
            finish()
        }
    }

    companion object {
        private const val TAG = "KidBoxAutofillAuth"
    }
}
