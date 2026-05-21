package it.vittorioscocca.kidbox.feature.passwords.autofill

import it.vittorioscocca.kidbox.util.KBLog

import android.app.PendingIntent
import android.app.assist.AssistStructure
import android.app.assist.AssistStructure.ViewNode
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.text.InputType
import android.view.View
import android.view.autofill.AutofillId
import android.view.autofill.AutofillValue
import android.widget.RemoteViews
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.data.passwords.AutoFillSnapshotItem
import it.vittorioscocca.kidbox.data.passwords.AutoFillSnapshotLoader
import java.util.Locale

/**
 * Autofill legacy (API 26+): un dataset per credenziale con [Dataset.setAuthentication],
 * sblocco in [KidBoxAutofillAuthActivity].
 */
class KidBoxAutofillService : AutofillService() {

    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }
        val callerPkg = structure.activityComponent.packageName.orEmpty()
        // MIUI / launcher: richieste autofill senza form login (es. “Minus One”) — non logare come errore.
        if (callerPkg in AutofillLowSignalPackages) {
            callback.onSuccess(null)
            return
        }
        val parsed = AutofillHeuristics.findUsernamePassword(structure)
        if (parsed.passwordId == null) {
            KBLog.security.debug("onFillRequest: no password field (pkg=$callerPkg)", TAG)
            callback.onSuccess(null)
            return
        }
        val deps = applicationContext.autofillEntryPoint()
        val uid = deps.firebaseAuth().currentUser?.uid.orEmpty()
        val familyId = resolveAutofillFamilyId(
            applicationContext,
            deps.familySessionPreferences(),
            uid,
        ).orEmpty()
        if (uid.isBlank()) {
            KBLog.security.debug("onFillRequest: no Firebase uid", TAG)
            callback.onSuccess(null)
            return
        }
        if (familyId.isBlank()) {
            KBLog.security.debug("onFillRequest: no familyId (prefs + keychain scan empty)", TAG)
            callback.onSuccess(null)
            return
        }
        val snapshot = AutoFillSnapshotLoader.load(applicationContext, familyId, uid, deps.autoFillSnapshotEncryptedStore())
        if (snapshot == null || snapshot.items.isEmpty()) {
            KBLog.security.debug("onFillRequest: snapshot empty or unreadable familyId=$familyId items=${snapshot?.items?.size ?: -1}", TAG)
            deps.passwordsRepository().scheduleAutofillSnapshotRebuild()
            callback.onSuccess(null)
            return
        }
        val pkg = callerPkg
        val requestHost = AutofillRequestHostResolver.resolve(structure, pkg, packageManager)
        val filtered = AutofillRequestHostResolver.filterSnapshotForHost(snapshot.items, requestHost)
            .take(MAX_AUTOFILL_DATASETS)
        // Chrome / siti nuovi: se il match dominio non trova nulla, offri comunque le ultime voci
        // (l’utente sblocca con biometria in auth). Evita che il sistema mostri solo Google.
        val displayItems = if (filtered.isNotEmpty()) {
            filtered
        } else {
            snapshot.items.take(MAX_AUTOFILL_DATASETS)
        }
        if (displayItems.isEmpty()) {
            callback.onSuccess(null)
            return
        }
        val responseBuilder = FillResponse.Builder()
        for ((index, item) in displayItems.withIndex()) {
            val presentation = RemoteViews(packageName, R.layout.autofill_dataset).apply {
                setTextViewText(R.id.autofill_label, item.title.ifBlank { item.username })
            }
            val authIntent = Intent(this, KidBoxAutofillAuthActivity::class.java).apply {
                action = KidBoxAutofillService.ACTION_AUTOFILL_AUTH
                putExtra(KidBoxAutofillService.EXTRA_ENTRY_ID, item.id)
                parsed.usernameId?.let {
                    putExtra(KidBoxAutofillService.EXTRA_USERNAME_AUTOFILL_ID, it)
                }
                putExtra(KidBoxAutofillService.EXTRA_PASSWORD_AUTOFILL_ID, parsed.passwordId)
            }
            // Request code univoco: hash(id) può collidere tra voci diverse.
            val sender = PendingIntent.getActivity(
                this,
                PI_REQ_AUTH_BASE + index,
                authIntent,
                PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            ).intentSender
            // Android 15+ / alcune ROM: build() fallisce se non c’è almeno un setValue/setField
            // (solo setAuthentication non popola i field id interni).
            val datasetBuilder = Dataset.Builder(presentation)
            parsed.usernameId?.let { userField ->
                datasetBuilder.setValue(userField, AutofillValue.forText(""))
            }
            datasetBuilder.setValue(parsed.passwordId!!, AutofillValue.forText(""))
            val dataset = datasetBuilder
                .setAuthentication(sender)
                .build()
            responseBuilder.addDataset(dataset)
        }
        val saveTypes = SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
        // parsed.passwordId è non-null qui (controllo all'inizio di onFillRequest).
        // requiredIds dice al sistema quale campo monitorare — senza MIUI ignora il save dialog.
        val saveInfoBuilder = SaveInfo.Builder(saveTypes, arrayOf(parsed.passwordId!!))
        parsed.usernameId?.let { saveInfoBuilder.setOptionalIds(arrayOf(it)) }
        responseBuilder.setSaveInfo(saveInfoBuilder.build())
        KBLog.security.info("onFillRequest: FillResponse pkg=$pkg host=${requestHost ?: "?"} datasets=${displayItems.size}", TAG)
        callback.onSuccess(responseBuilder.build())
    }

    override fun onSaveRequest(request: android.service.autofill.SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val pair = structure?.let { AutofillHeuristics.extractFilledUsernamePassword(it) }
        if (pair != null) {
            val i = Intent(this, KidBoxAutofillSaveActivity::class.java).apply {
                action = ACTION_AUTOFILL_SAVE
                putExtra(EXTRA_SAVE_USERNAME, pair.first)
                putExtra(EXTRA_SAVE_PASSWORD, pair.second)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            startActivity(i)
        }
        callback.onSuccess()
    }

    companion object {
        const val ACTION_AUTOFILL_AUTH = "it.vittorioscocca.kidbox.autofill.LEGACY_AUTH"
        const val ACTION_AUTOFILL_SAVE = "it.vittorioscocca.kidbox.autofill.LEGACY_SAVE"
        const val EXTRA_ENTRY_ID = "kidbox.autofill.entryId"
        const val EXTRA_USERNAME_AUTOFILL_ID = "kidbox.autofill.usernameAutofillId"
        const val EXTRA_PASSWORD_AUTOFILL_ID = "kidbox.autofill.passwordAutofillId"
        const val EXTRA_SAVE_USERNAME = "kidbox.autofill.saveUsername"
        const val EXTRA_SAVE_PASSWORD = "kidbox.autofill.savePassword"
        private const val TAG = "KidBoxAutofill"
        private const val MAX_AUTOFILL_DATASETS = 25
        private const val PI_REQ_AUTH_BASE = 0x4B42_4100
    }
}

/** Contesti che ricevono spesso richieste autofill senza campi password (rumore MIUI / system UI). */
private val AutofillLowSignalPackages: Set<String> = setOf(
    "com.mi.globalminusscreen",
    "com.miui.globalminusscreen",
    "com.android.systemui",
)

/**
 * Chrome e altri browser non hanno un solo host via Digital Asset Links sul package del browser:
 * va letto [android.app.assist.AssistStructure.ViewNode.getWebDomain] (e simili) dall’[AssistStructure].
 */
private object AutofillRequestHostResolver {

    fun resolve(structure: AssistStructure, packageName: String, pm: android.content.pm.PackageManager): String? {
        webHostFromStructure(structure)?.let { return normalizeHost(it) }
        if (packageName.isNotBlank()) {
            NativeAppDomainResolver.resolveWebHostForPackage(packageName, pm)
                ?.let { return normalizeHost(it) }
        }
        return null
    }

    /**
     * Se l’host non è noto (es. WebView senza webDomain), mostra comunque voci senza dominio o,
     * in assenza, un sottoinsieme delle credenziali (l’utente sceglie e passa dalla biometria in auth).
     */
    fun filterSnapshotForHost(
        items: List<AutoFillSnapshotItem>,
        requestHost: String?,
    ): List<AutoFillSnapshotItem> {
        if (items.isEmpty()) return emptyList()
        if (requestHost.isNullOrBlank()) {
            val generic = items.filter { it.website.isNullOrBlank() }
            return if (generic.isNotEmpty()) generic else items
        }
        return items.filter { item ->
            when {
                item.website.isNullOrBlank() -> true
                else -> DomainMatcher.sameRegistrableDomain(requestHost, item.website)
            }
        }
    }

    private fun normalizeHost(host: String): String {
        val trimmed = host.trim()
        val bare = if (trimmed.contains("://")) {
            android.net.Uri.parse(trimmed).host?.trim().orEmpty().ifEmpty { trimmed }
        } else {
            trimmed
        }
        return bare.removePrefix("www.").lowercase()
    }

    private fun webHostFromStructure(structure: AssistStructure): String? {
        var found: String? = null
        val n = structure.windowNodeCount
        for (i in 0 until n) {
            traverse(structure.getWindowNodeAt(i).rootViewNode) { node ->
                val wd = node.webDomain
                if (!wd.isNullOrBlank()) found = wd
            }
        }
        return found
    }

    private fun traverse(node: AssistStructure.ViewNode?, visit: (AssistStructure.ViewNode) -> Unit) {
        if (node == null) return
        visit(node)
        for (i in 0 until node.childCount) traverse(node.getChildAt(i), visit)
    }
}

private object AutofillHeuristics {
    data class Parsed(val usernameId: AutofillId?, val passwordId: AutofillId?)

    fun findUsernamePassword(structure: AssistStructure): Parsed {
        var username: AutofillId? = null
        var password: AutofillId? = null
        val n = structure.windowNodeCount
        for (i in 0 until n) {
            traverse(structure.getWindowNodeAt(i).rootViewNode) { node ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && node.isFocused && node.autofillId != null) {
                    val it = node.inputType
                    val variation = it and InputType.TYPE_MASK_VARIATION
                    val inputClass = it and InputType.TYPE_MASK_CLASS
                    if (password == null && (
                            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                            )
                    ) {
                        password = node.autofillId
                    }
                    if (username == null && inputClass == InputType.TYPE_CLASS_TEXT) {
                        if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                        ) {
                            username = node.autofillId
                        }
                    }
                }
                val hints = node.autofillHints
                if (!hints.isNullOrEmpty()) {
                    for (h in hints) {
                        when (h) {
                            View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS ->
                                if (node.autofillId != null) username = node.autofillId
                            View.AUTOFILL_HINT_PASSWORD ->
                                if (node.autofillId != null) password = node.autofillId
                        }
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && node.autofillId != null) {
                    val hi = node.htmlInfo
                    if (hi != null) {
                        var autocomplete = ""
                        var typeAttr = ""
                        var nameLike = ""
                        for (pair in hi.attributes ?: emptyList()) {
                            when (pair.first.lowercase(Locale.ROOT)) {
                                "autocomplete" -> autocomplete = pair.second.lowercase(Locale.ROOT)
                                "type" -> typeAttr = pair.second.lowercase(Locale.ROOT)
                                "name", "id" -> nameLike = pair.second.lowercase(Locale.ROOT)
                            }
                        }
                        if (password == null) {
                            val looksPassword = typeAttr == "password" ||
                                autocomplete.contains("password") ||
                                autocomplete.contains("current-password") ||
                                nameLike.contains("password") || nameLike.contains("passwd")
                            if (looksPassword) password = node.autofillId
                        }
                        if (username == null) {
                            val looksUser = typeAttr == "email" ||
                                autocomplete.contains("email") ||
                                autocomplete.contains("username") ||
                                autocomplete.contains("login") ||
                                nameLike.contains("email") || nameLike.contains("user") ||
                                nameLike.contains("login") || nameLike.contains("account")
                            if (looksUser) username = node.autofillId
                        }
                    }
                }
                val hint = node.hint?.lowercase().orEmpty()
                if (username == null && (
                        hint.contains("user") || hint.contains("email") || hint.contains("mail") ||
                            hint.contains("login") || hint.contains("utente") || hint.contains("account") ||
                            hint.contains("accesso") || hint.contains("posta")
                        )
                ) {
                    if (node.autofillId != null) username = node.autofillId
                }
                if (password == null && hint.contains("pass")) {
                    if (node.autofillId != null) password = node.autofillId
                }
                val idEntry = node.idEntry?.lowercase().orEmpty()
                if (username == null && (
                        idEntry.contains("user") || idEntry.contains("email") || idEntry.contains("mail") ||
                            idEntry.contains("login") || idEntry.contains("account")
                        )
                ) {
                    if (node.autofillId != null) username = node.autofillId
                }
                if (password == null && idEntry.contains("pass")) {
                    if (node.autofillId != null) password = node.autofillId
                }
                val it = node.inputType
                val variation = it and InputType.TYPE_MASK_VARIATION
                val inputClass = it and InputType.TYPE_MASK_CLASS
                if (username == null && inputClass == InputType.TYPE_CLASS_TEXT) {
                    if (variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
                        variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS
                    ) {
                        if (node.autofillId != null) username = node.autofillId
                    }
                }
                if (password == null && (
                        variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                            variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                            variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        )
                ) {
                    if (node.autofillId != null) password = node.autofillId
                }
            }
        }
        return Parsed(username, password)
    }

    fun extractFilledUsernamePassword(structure: AssistStructure): Pair<String, String>? {
        var user: String? = null
        var pass: String? = null
        val n = structure.windowNodeCount
        for (i in 0 until n) {
            traverse(structure.getWindowNodeAt(i).rootViewNode) { node ->
                val av = node.autofillValue ?: return@traverse
                if (!av.isText) return@traverse
                val text = av.textValue.toString()
                val hints = node.autofillHints
                if (!hints.isNullOrEmpty()) {
                    for (h in hints) {
                        when (h) {
                            View.AUTOFILL_HINT_USERNAME, View.AUTOFILL_HINT_EMAIL_ADDRESS -> user = text
                            View.AUTOFILL_HINT_PASSWORD -> pass = text
                        }
                    }
                }
                val hint = node.hint?.lowercase().orEmpty()
                if (user == null && (hint.contains("user") || hint.contains("email"))) user = text
                if (pass == null && hint.contains("pass")) pass = text
            }
        }
        val u = user?.trim().orEmpty()
        val p = pass?.trim().orEmpty()
        return if (u.isNotEmpty() && p.isNotEmpty()) u to p else null
    }

    private fun traverse(node: ViewNode?, visit: (ViewNode) -> Unit) {
        if (node == null) return
        visit(node)
        for (i in 0 until node.childCount) {
            traverse(node.getChildAt(i), visit)
        }
    }
}
