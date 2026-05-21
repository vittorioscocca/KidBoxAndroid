package it.vittorioscocca.kidbox.util

import android.content.Context
import android.os.Build
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.GenerationConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
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
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * Analisi log (Gemini Nano on-device se disponibile, altrimenti Cloud Function)
 * e upload opzionale su Firestore collection "crash_reports".
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

    private data class AnalysisResponse(
        val hasIssues: Boolean,
        val issues: List<IssueReport>,
    )

    private const val MIN_LOG_BYTES = 2 * 1024
    private const val MAX_UPLOAD_LOG_BYTES = 50 * 1024
    private const val THROTTLE_MS = 6L * 60L * 60L * 1000L

    private val functions = FirebaseFunctions.getInstance("europe-west1")
    private val uploadScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _consentPrompt = MutableStateFlow<ConsentPrompt?>(null)
    val consentPrompt: StateFlow<ConsentPrompt?> = _consentPrompt.asStateFlow()

    private val _showConsentDialog = MutableStateFlow(false)
    val showConsentDialog: StateFlow<Boolean> = _showConsentDialog.asStateFlow()

    suspend fun analyzeIfNeeded(context: Context) {
        if (FirebaseAuth.getInstance().currentUser == null) return

        val rawLogs = KBFileLogger.readLogs()
        if (rawLogs.toByteArray(Charsets.UTF_8).size < MIN_LOG_BYTES) return

        val lastRun = CrashReportPreferences.lastAnalysisRunMillis(context)
        if (lastRun > 0 && System.currentTimeMillis() - lastRun < THROTTLE_MS) return

        val parsed = when {
            isGeminiNanoAvailable() -> analyzeWithNano(rawLogs)
            else -> null
        } ?: analyzeWithCloudFunction(rawLogs)

        if (parsed == null) {
            CrashReportPreferences.markAnalysisRun(context)
            return
        }

        if (!parsed.hasIssues || parsed.issues.isEmpty()) {
            CrashReportPreferences.markAnalysisRun(context)
            return
        }

        handlePermissionAndUpload(context, parsed.issues, rawLogs)
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

    private fun isGeminiNanoAvailable(): Boolean {
        return try {
            GenerativeModel(
                modelName = "gemini-nano",
                generationConfig = GenerationConfig.Builder().build(),
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun analyzeWithNano(rawLogs: String): AnalysisResponse? =
        withContext(Dispatchers.Default) {
            try {
                val model = GenerativeModel(
                    modelName = "gemini-nano",
                    generationConfig = GenerationConfig.Builder().build(),
                )
                val response = model.generateContent(buildPrompt(rawLogs))
                parseAnalysisResponse(response.text ?: return@withContext null)
            } catch (e: Exception) {
                KBLog.app.warning("CrashAnalyzer: Nano non disponibile: ${e.message}")
                null
            }
        }

    private suspend fun analyzeWithCloudFunction(rawLogs: String): AnalysisResponse? =
        withContext(Dispatchers.IO) {
            try {
                val result = functions
                    .getHttpsCallable("analyzeLogs")
                    .call(mapOf("logs" to rawLogs))
                    .await()
                val text = when (val data = result.getData()) {
                    is String -> data
                    is Map<*, *> -> data["text"] as? String ?: data["result"] as? String
                    else -> null
                } ?: return@withContext null
                parseAnalysisResponse(text)
            } catch (e: Exception) {
                KBLog.app.error("CrashAnalyzer: Cloud Function fallita", e)
                null
            }
        }

    private suspend fun handlePermissionAndUpload(
        context: Context,
        issues: List<IssueReport>,
        rawLogs: String,
    ) {
        if (CrashReportPreferences.isReportingEnabled(context)) {
            uploadToFirestore(context, issues, rawLogs)
            return
        }
        if (CrashReportPreferences.hasBeenAsked(context)) {
            CrashReportPreferences.markAnalysisRun(context)
            return
        }
        _consentPrompt.value = ConsentPrompt(
            issueCount = issues.size,
            issues = issues,
            rawLogs = rawLogs,
        )
        _showConsentDialog.value = true
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
            KBLog.app.info("Crash report inviato: ${issues.size} issues")
        } catch (e: Exception) {
            KBLog.app.error("CrashAnalyzer: upload Firestore fallito", e)
            CrashReportPreferences.markAnalysisRun(context)
        }
    }

    private fun buildPrompt(rawLogs: String): String = """
        Sei un analizzatore di log per l'app KidBox Android.
        Analizza i log e rispondi SOLO con JSON valido:
        {
          "hasIssues": true/false,
          "issues": [
            {
              "type": "crash|error|malfunction|warning",
              "severity": "critical|high|medium|low",
              "category": "sync|auth|data|ui|ai|storage|navigation",
              "affectedModule": "nome classe o funzione",
              "summary": "descrizione breve max 120 caratteri in italiano",
              "detail": "causa tecnica probabile",
              "firstOccurrence": "timestamp",
              "occurrences": numero
            }
          ]
        }
        Log: $rawLogs
    """.trimIndent()

    private fun parseAnalysisResponse(text: String): AnalysisResponse? {
        return try {
            val trimmed = text.trim()
            val start = trimmed.indexOf('{')
            val end = trimmed.lastIndexOf('}')
            if (start < 0 || end <= start) return null
            val json = JSONObject(trimmed.substring(start, end + 1))
            val hasIssues = json.optBoolean("hasIssues", false)
            val issuesArray = json.optJSONArray("issues")
                ?: return AnalysisResponse(hasIssues, emptyList())
            val issues = buildList {
                for (i in 0 until issuesArray.length()) {
                    val item = issuesArray.optJSONObject(i) ?: continue
                    add(
                        IssueReport(
                            type = item.optString("type", "error"),
                            severity = item.optString("severity", "medium"),
                            category = item.optString("category", "app"),
                            affectedModule = item.optString("affectedModule", "unknown"),
                            summary = item.optString("summary", ""),
                            detail = item.optString("detail", ""),
                            firstOccurrence = item.optString("firstOccurrence", ""),
                            occurrences = item.optInt("occurrences", 1),
                        ),
                    )
                }
            }
            AnalysisResponse(hasIssues, issues)
        } catch (e: Exception) {
            KBLog.app.error("CrashAnalyzer: parse JSON fallito", e)
            null
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
