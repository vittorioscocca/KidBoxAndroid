package it.vittorioscocca.kidbox.util

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import it.vittorioscocca.kidbox.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Rilevamento crash via euristica locale (marker `[CRASH]`/`TEST: crash` nei log), analisi dei
 * log con Gemini Nano sul telefono dove c'è, e upload opzionale su Firestore `crash_reports`.
 *
 * Come su iOS con Apple Intelligence: l'analisi "proattiva" (errori senza crash conclamato) gira
 * on-device, gratis, senza mandare i log a nessuno finché l'utente non acconsente. La vecchia
 * Cloud Function con Gemini nel cloud è stata tolta perché voleva una API key a pagamento.
 * Sui telefoni senza Gemini Nano resta solo il rilevamento euristico, come su iOS senza Apple
 * Intelligence. Gemini Nano gira solo con l'app in primo piano: in background si salta senza
 * segnare il giro, e si riprova al prossimo passaggio in primo piano.
 */
object CrashAnalyzer {

    data class IssueReport(
        val type: String,
        val severity: String,
        val category: String,
        val affectedModule: String,
        val summary: String,
        val detail: String,
        val firstOccurrence: String,
        val occurrences: Int,
    )

    data class ConsentPrompt(
        val issueCount: Int,
        val issues: List<IssueReport>,
        val rawLogs: String,
    )

    private const val MIN_LOG_BYTES = 2 * 1024
    private const val MAX_UPLOAD_LOG_BYTES = 50 * 1024
    private const val THROTTLE_MS = 6L * 60L * 60L * 1000L

    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _consentPrompt = MutableStateFlow<ConsentPrompt?>(null)
    val consentPrompt: StateFlow<ConsentPrompt?> = _consentPrompt.asStateFlow()

    private val _showConsentDialog = MutableStateFlow(false)
    val showConsentDialog: StateFlow<Boolean> = _showConsentDialog.asStateFlow()

    suspend fun analyzeIfNeeded(context: Context, force: Boolean = false) {
        if (FirebaseAuth.getInstance().currentUser == null) {
            KBLog.app.debug("CrashAnalyzer: skip (utente non autenticato)")
            return
        }

        val rawLogs = KBFileLogger.readLogs()
        val logBytes = rawLogs.toByteArray(Charsets.UTF_8).size
        if (logBytes < MIN_LOG_BYTES) {
            KBLog.app.debug("CrashAnalyzer: skip (log file $logBytes B < $MIN_LOG_BYTES B)")
            return
        }

        val crashInLogs = containsCrashMarkers(rawLogs)
        val pending = CrashReportPreferences.hasPendingCrashReport(context)
        val reporting = CrashReportPreferences.isReportingEnabled(context)
        val shouldUploadCrash = (crashInLogs || pending) && reporting
        val bypassThrottle = force || shouldUploadCrash

        if (!bypassThrottle) {
            val lastRun = CrashReportPreferences.lastAnalysisRunMillis(context)
            if (lastRun > 0 && System.currentTimeMillis() - lastRun < THROTTLE_MS) {
                KBLog.app.info(
                    "CrashAnalyzer: skip throttle (pending=$pending, crashInLogs=$crashInLogs, reporting=$reporting)",
                )
                return
            }
        }

        KBLog.app.info(
            "CrashAnalyzer: avvio analisi ($logBytes B, reporting=$reporting, crashInLogs=$crashInLogs, pending=$pending)",
        )

        if (shouldUploadCrash) {
            KBLog.app.info("CrashAnalyzer: crash pending → upload diretto")
            val issues = if (crashInLogs) {
                buildFallbackIssues(rawLogs)
            } else {
                listOf(pendingCrashIssue())
            }
            uploadToFirestore(context, issues, rawLogs)
            return
        }

        analyzeOnDevice(context, rawLogs)
    }

    // ── Gemini Nano sul telefono ─────────────────────────────────────────────

    /** In memoria: dopo un «non adesso» del modello non si riprova a ogni ritorno in primo piano. */
    @Volatile private var lastOnDeviceAttemptMillis = 0L
    private const val ON_DEVICE_RETRY_MS = 10L * 60L * 1000L

    private suspend fun analyzeOnDevice(context: Context, rawLogs: String) {
        // Il dialog del consenso è già aperto: aspetta la risposta, non si rianalizza.
        if (_consentPrompt.value != null) return
        val now = System.currentTimeMillis()
        if (now - lastOnDeviceAttemptMillis < ON_DEVICE_RETRY_MS) return
        val significant = CrashLogAnalysis.significantLines(rawLogs)
        if (significant.isEmpty()) {
            KBLog.app.info("CrashAnalyzer: nessuna riga ERROR/CRASH → niente da analizzare")
            CrashReportPreferences.markAnalysisRun(context)
            return
        }
        if (!isInForeground()) {
            // Gemini Nano rifiuta le richieste in background: si riprova in primo piano.
            KBLog.app.info("CrashAnalyzer: app non in primo piano → analisi rimandata")
            return
        }
        lastOnDeviceAttemptMillis = now
        when (val outcome = OnDeviceCrashModel.analyze { budget -> CrashLogAnalysis.buildPrompt(significant, budget) }) {
            is OnDeviceCrashModel.Outcome.Text -> {
                val result = CrashLogAnalysis.parse(outcome.text)
                if (result == null) {
                    KBLog.app.warning("CrashAnalyzer: risposta del modello non leggibile")
                    CrashReportPreferences.markAnalysisRun(context)
                } else if (result.hasIssues) {
                    requestPermissionAndUpload(context, result.issues, rawLogs)
                } else {
                    KBLog.app.info("CrashAnalyzer: nessun problema rilevato nei log")
                    CrashReportPreferences.markAnalysisRun(context)
                }
            }
            OnDeviceCrashModel.Outcome.Unavailable -> {
                KBLog.app.info("CrashAnalyzer: Gemini Nano non disponibile su questo telefono → solo euristica")
                CrashReportPreferences.markAnalysisRun(context)
            }
            OnDeviceCrashModel.Outcome.NotNow ->
                KBLog.app.info("CrashAnalyzer: Gemini Nano occupato o in quota → riprovo più avanti")
            is OnDeviceCrashModel.Outcome.Failed -> {
                KBLog.app.warning("CrashAnalyzer: analisi on-device non riuscita: ${outcome.message}")
                CrashReportPreferences.markAnalysisRun(context)
            }
        }
    }

    /** Come `requestPermissionAndUpload` di iOS: si chiede una volta, poi vale la scelta. */
    private suspend fun requestPermissionAndUpload(context: Context, issues: List<IssueReport>, rawLogs: String) {
        when {
            CrashReportPreferences.isReportingEnabled(context) -> uploadToFirestore(context, issues, rawLogs)
            CrashReportPreferences.hasBeenAsked(context) -> {
                KBLog.app.debug("CrashAnalyzer: skip upload (report automatici disattivati)")
                CrashReportPreferences.markAnalysisRun(context)
            }
            else -> {
                // Il dialog lo mostra MainActivity; «Invia» e «No» passano da onConsentSend/onConsentDecline.
                _consentPrompt.value = ConsentPrompt(issues.size, issues, rawLogs)
                _showConsentDialog.value = true
            }
        }
    }

    /**
     * Lo dice MainActivity (onResume/onPause). Il controllo di sistema da solo non
     * basta: provato su un Redmi Note 15, in onResume il processo risultava ancora
     * non in primo piano e l'analisi veniva rimandata per sempre.
     */
    @Volatile private var activityVisible = false

    fun setActivityVisible(visible: Boolean) {
        activityVisible = visible
    }

    private fun isInForeground(): Boolean {
        if (activityVisible) return true
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance <= ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun containsCrashMarkers(logs: String): Boolean =
        logs.contains("[CRASH]") ||
            logs.contains("TEST: crash") ||
            logs.contains("RuntimeException") && logs.contains("NotesHomeScreen")

    private fun pendingCrashIssue(): IssueReport = IssueReport(
        type = "crash",
        severity = "critical",
        category = "ui",
        affectedModule = "NotesHomeScreen",
        summary = "Crash segnalato; log locali già troncati",
        detail = "kb_crash_report_pending era attivo",
        firstOccurrence = "",
        occurrences = 1,
    )

    private fun buildFallbackIssues(rawLogs: String): List<IssueReport> {
        val crashLines = rawLogs.lineSequence()
            .filter { line ->
                line.contains("[CRASH]") || line.contains("TEST: crash")
            }
            .toList()
        val excerpt = crashLines.takeLast(5).joinToString(" | ")
        val module = crashLines.lastOrNull()?.let { line ->
            Regex("""\[([^:]+):""").find(line)?.groupValues?.getOrNull(1)
        } ?: "NotesHomeScreen"
        return listOf(
            IssueReport(
                type = "crash",
                severity = "critical",
                category = "ui",
                affectedModule = module,
                summary = "Crash rilevato nei log dell'app",
                detail = excerpt.ifEmpty { "Segnale di crash presente nel file di log" },
                firstOccurrence = "",
                occurrences = maxOf(1, crashLines.size),
            ),
        )
    }

    fun dismissConsentPrompt() {
        _consentPrompt.value = null
        _showConsentDialog.value = false
    }

    fun onConsentSend(context: Context) {
        val prompt = _consentPrompt.value ?: return
        CrashReportPreferences.setReportingEnabled(context, true)
        CrashReportPreferences.setHasBeenAsked(context, true)
        dismissConsentPrompt()
        uploadScope.launch {
            uploadToFirestore(context, prompt.issues, prompt.rawLogs)
        }
    }

    fun onConsentDecline(context: Context) {
        CrashReportPreferences.setReportingEnabled(context, false)
        CrashReportPreferences.setHasBeenAsked(context, true)
        dismissConsentPrompt()
        CrashReportPreferences.markAnalysisRun(context)
    }

    private suspend fun uploadToFirestore(
        context: Context,
        issues: List<IssueReport>,
        rawLogs: String,
    ) {
        val truncatedLogs = truncateLogs(rawLogs, MAX_UPLOAD_LOG_BYTES)
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: "anonymous"
        val payload = hashMapOf(
            "platform" to "android",
            "appVersion" to BuildConfig.VERSION_NAME,
            "osVersion" to Build.VERSION.RELEASE,
            "device" to "${Build.MANUFACTURER} ${Build.MODEL}",
            "issues" to issues.map { issue ->
                hashMapOf(
                    "type" to issue.type,
                    "severity" to issue.severity,
                    "category" to issue.category,
                    "affectedModule" to issue.affectedModule,
                    "summary" to issue.summary,
                    "detail" to issue.detail,
                    "firstOccurrence" to issue.firstOccurrence,
                    "occurrences" to issue.occurrences,
                )
            },
            "rawLogs" to truncatedLogs,
            "createdAt" to FieldValue.serverTimestamp(),
            "status" to "new",
            "userId" to uid,
        )
        try {
            FirebaseFirestore.getInstance()
                .collection("crash_reports")
                .add(payload)
                .await()
            KBFileLogger.clearLogs()
            CrashReportPreferences.markAnalysisRun(context)
            CrashReportPreferences.clearPendingCrashReport(context)
            KBLog.app.info("Crash report inviato: ${issues.size} issues")
        } catch (e: Exception) {
            KBLog.app.error("CrashAnalyzer: upload Firestore fallito", e)
        }
    }

    private fun truncateLogs(raw: String, maxBytes: Int): String {
        if (raw.toByteArray(Charsets.UTF_8).size <= maxBytes) return raw
        var lines = raw.split('\n').toMutableList()
        while (lines.isNotEmpty()) {
            val candidate = lines.joinToString("\n")
            if (candidate.toByteArray(Charsets.UTF_8).size <= maxBytes) return candidate
            lines.removeAt(0)
        }
        return raw.take(maxBytes)
    }
}
