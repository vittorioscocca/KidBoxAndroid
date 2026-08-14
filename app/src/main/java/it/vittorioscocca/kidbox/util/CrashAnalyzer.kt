package it.vittorioscocca.kidbox.util

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
 * Rilevamento crash via euristica locale (marker `[CRASH]`/`TEST: crash` nei log) e upload
 * opzionale su Firestore collection "crash_reports".
 *
 * Non usa più una Cloud Function basata su Gemini per l'analisi "proattiva" (log senza crash
 * conclamati): richiedeva una API key a pagamento. Su iOS l'equivalente gira on-device via
 * Apple Intelligence (gratis); su Android non c'è un modello on-device comparabile per copertura
 * di device, quindi qui resta solo il rilevamento euristico — identico a quello che scatta anche
 * su iOS in assenza di Apple Intelligence.
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

        // Nessuna analisi "proattiva" AI-assisted su Android (nessun modello on-device
        // comparabile ad Apple Intelligence con copertura device sufficiente, e niente
        // servizi cloud a pagamento). Il rilevamento crash euristico sopra resta attivo.
        KBLog.app.info("CrashAnalyzer: nessun crash rilevato nei log — skip (nessuna analisi AI su Android)")
        CrashReportPreferences.markAnalysisRun(context)
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
