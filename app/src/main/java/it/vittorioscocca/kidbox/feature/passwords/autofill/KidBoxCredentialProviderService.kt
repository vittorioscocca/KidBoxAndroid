package it.vittorioscocca.kidbox.feature.passwords.autofill

import it.vittorioscocca.kidbox.util.KBLog

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.os.OutcomeReceiver
import androidx.credentials.exceptions.ClearCredentialException
import androidx.credentials.provider.Action
import androidx.credentials.provider.BeginCreateCredentialResponse
import androidx.credentials.provider.BeginCreatePasswordCredentialRequest
import androidx.credentials.provider.BeginGetCredentialRequest
import androidx.credentials.provider.BeginGetCredentialResponse
import androidx.credentials.provider.BeginGetPasswordOption
import androidx.credentials.provider.BeginGetPublicKeyCredentialOption
import androidx.credentials.provider.CreateEntry
import androidx.credentials.provider.CredentialProviderService
import androidx.credentials.provider.PasswordCredentialEntry
import androidx.credentials.provider.ProviderClearCredentialStateRequest
import it.vittorioscocca.kidbox.data.passwords.AutoFillSnapshotItem
import it.vittorioscocca.kidbox.data.passwords.AutoFillSnapshotLoader
import java.time.Instant

/**
 * Credential Manager provider (Android 14+). Le voci usano [PendingIntent] verso
 * [KidBoxCredentialPasswordGetActivity] che applica biometria e restituisce [androidx.credentials.PasswordCredential].
 */
class KidBoxCredentialProviderService : CredentialProviderService() {

    override fun onBeginGetCredentialRequest(
        request: BeginGetCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginGetCredentialResponse, androidx.credentials.exceptions.GetCredentialException>,
    ) {
        val deps = applicationContext.autofillEntryPoint()
        val uid = deps.firebaseAuth().currentUser?.uid.orEmpty()
        val familyId = resolveAutofillFamilyId(
            applicationContext,
            deps.familySessionPreferences(),
            uid,
        ).orEmpty()
        if (uid.isBlank()) {
            // Firebase Auth non ancora ripristinato (cold start) oppure utente non loggato.
            // Mostriamo comunque l'Action così KidBox è visibile nel selettore di Chrome.
            KBLog.security.debug("onBeginGetCredentialRequest: uid blank — returning fallback action", TAG)
            callback.onResult(BeginGetCredentialResponse(emptyList(), listOf(openKidBoxAction()), emptyList(), null))
            return
        }
        if (familyId.isBlank()) {
            KBLog.security.debug("onBeginGetCredentialRequest: no familyId — returning fallback action", TAG)
            callback.onResult(BeginGetCredentialResponse(emptyList(), listOf(openKidBoxAction()), emptyList(), null))
            return
        }
        val snapshot = AutoFillSnapshotLoader.load(applicationContext, familyId, uid, deps.autoFillSnapshotEncryptedStore())
        if (snapshot == null) {
            KBLog.security.debug("onBeginGetCredentialRequest: snapshot null familyId=$familyId — scheduling rebuild", TAG)
            deps.passwordsRepository().scheduleAutofillSnapshotRebuild()
            callback.onResult(BeginGetCredentialResponse(emptyList(), listOf(openKidBoxAction()), emptyList(), null))
            return
        }
        if (snapshot.items.isEmpty()) {
            KBLog.security.debug("onBeginGetCredentialRequest: snapshot empty familyId=$familyId — scheduling rebuild", TAG)
            deps.passwordsRepository().scheduleAutofillSnapshotRebuild()
            callback.onResult(BeginGetCredentialResponse(emptyList(), emptyList(), emptyList(), null))
            return
        }

        val calling = request.callingAppInfo
        val requestHost = resolveRequestHost(calling?.packageName.orEmpty(), calling?.origin)

        for (option in request.beginGetCredentialOptions) {
            if (option is BeginGetPublicKeyCredentialOption) {
                KBLog.security.debug("Ignoring passkey begin-get option (v2)", TAG)
            }
        }

        val passwordOption = request.beginGetCredentialOptions.filterIsInstance<BeginGetPasswordOption>().firstOrNull()
        if (passwordOption == null) {
            // Chrome su siti con passkey manda solo BeginGetPublicKeyCredentialOption.
            // PasswordCredentialEntry richiede BeginGetPasswordOption, quindi non possiamo
            // creare voci password dirette. Restituiamo un'Action visibile nel selettore
            // così l'utente sa che KidBox è disponibile e può aprirlo per copiare la password.
            KBLog.security.info("onBeginGetCredentialRequest: no BeginGetPasswordOption — caller=${calling?.packageName} origin=${calling?.origin} types=${
                    request.beginGetCredentialOptions.joinToString { it.type }
                }", TAG)
            callback.onResult(BeginGetCredentialResponse(emptyList(), listOf(openKidBoxAction()), emptyList(), null))
            return
        }

        val allowed: Collection<String> = passwordOption.allowedUserIds
        fun domainOk(item: AutoFillSnapshotItem): Boolean = when {
            requestHost.isNullOrBlank() -> true
            item.website.isNullOrBlank() -> true
            else -> DomainMatcher.sameRegistrableDomain(requestHost, item.website)
        }
        fun userOk(item: AutoFillSnapshotItem): Boolean =
            credentialAllowedUser(username = item.username, allowed = allowed)

        var filtered = snapshot.items.filter { userOk(it) && domainOk(it) }
            .take(MAX_CREDENTIAL_PROVIDER_ENTRIES)
        if (filtered.isEmpty()) {
            filtered = snapshot.items.filter { domainOk(it) }.take(MAX_CREDENTIAL_PROVIDER_ENTRIES)
        }
        if (filtered.isEmpty()) {
            filtered = snapshot.items.take(MAX_CREDENTIAL_PROVIDER_ENTRIES)
        }

        val entries = mutableListOf<PasswordCredentialEntry>()
        for (item in filtered) {
            val intent = Intent(this, KidBoxCredentialPasswordGetActivity::class.java).apply {
                action = ACTION_GET_PASSWORD
                putExtra(EXTRA_ENTRY_ID, item.id)
            }
            val pi = PendingIntent.getActivity(
                this,
                item.id.hashCode(),
                intent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            entries.add(
                PasswordCredentialEntry(
                    context = this@KidBoxCredentialProviderService,
                    username = item.username,
                    pendingIntent = pi,
                    beginGetPasswordOption = passwordOption,
                    displayName = item.title.ifBlank { item.username },
                    lastUsedTime = Instant.EPOCH,
                    isAutoSelectAllowed = false,
                ),
            )
        }
        callback.onResult(BeginGetCredentialResponse(entries, emptyList(), emptyList(), null))
    }

    override fun onBeginCreateCredentialRequest(
        request: androidx.credentials.provider.BeginCreateCredentialRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<BeginCreateCredentialResponse, androidx.credentials.exceptions.CreateCredentialException>,
    ) {
        when (request) {
            is BeginCreatePasswordCredentialRequest -> {
                val intent = Intent(this, KidBoxCredentialPasswordCreateActivity::class.java).apply {
                    action = ACTION_CREATE_PASSWORD
                }
                val pi = PendingIntent.getActivity(
                    this,
                    PI_REQ_CREATE,
                    intent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                )
                val entry = CreateEntry(
                    accountName = "KidBox",
                    pendingIntent = pi,
                    description = "Salvare in KidBox?",
                    lastUsedTime = Instant.EPOCH,
                    icon = null,
                    passwordCredentialCount = 1,
                    publicKeyCredentialCount = null,
                    totalCredentialCount = 1,
                    isAutoSelectAllowed = false,
                )
                callback.onResult(BeginCreateCredentialResponse(listOf(entry), null))
            }
            else -> {
                // TODO v2 passkey support
                callback.onResult(BeginCreateCredentialResponse())
            }
        }
    }

    override fun onClearCredentialStateRequest(
        request: ProviderClearCredentialStateRequest,
        cancellationSignal: CancellationSignal,
        callback: OutcomeReceiver<Void?, ClearCredentialException>,
    ) {
        AutoFillSnapshotLoader.clearMemoryCache()
        callback.onResult(null)
    }

    private fun resolveRequestHost(packageName: String, origin: String?): String? {
        val o = origin?.trim().orEmpty()
        if (o.isNotBlank()) {
            val host = runCatching {
                val uri = Uri.parse(o)
                uri.host?.lowercase()
            }.getOrNull()
            if (!host.isNullOrBlank()) return host.removePrefix("www.")
        }
        if (packageName.isNotBlank()) {
            val linked = NativeAppDomainResolver.resolveWebHostForPackage(packageName, packageManager)
            if (!linked.isNullOrBlank()) return linked.removePrefix("www.")
        }
        return null
    }

    private fun openKidBoxAction(): Action {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
            ?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            ?: Intent()
        val pi = PendingIntent.getActivity(
            this,
            PI_REQ_ACTION_OPEN,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Action(title = "KidBox", pendingIntent = pi)
    }

    companion object {
        private const val TAG = "KidBoxCredentialProvider"
        private const val MAX_CREDENTIAL_PROVIDER_ENTRIES = 40
        const val ACTION_GET_PASSWORD = "it.vittorioscocca.kidbox.autofill.CM_GET_PASSWORD"
        const val ACTION_CREATE_PASSWORD = "it.vittorioscocca.kidbox.autofill.CM_CREATE_PASSWORD"
        const val EXTRA_ENTRY_ID = "kidbox.autofill.entryId"
        private const val PI_REQ_CREATE = 91002
        private const val PI_REQ_ACTION_OPEN = 91003
    }
}

private fun credentialAllowedUser(username: String, allowed: Collection<String>): Boolean {
    if (allowed.isEmpty()) return true
    val u = username.trim()
    if (u.isEmpty()) return false
    return allowed.any { candidate ->
        val c = candidate.trim()
        c.isNotEmpty() && c.equals(u, ignoreCase = true)
    }
}
