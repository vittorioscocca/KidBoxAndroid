package it.vittorioscocca.kidbox.util

import org.json.JSONObject

/**
 * La parte pura dell'analisi dei log con il modello sul telefono: quali righe
 * dargli, il prompt, come leggere la risposta. Stesse regole di
 * `CrashAnalyzer.swift` (Apple Intelligence), così i report delle due
 * piattaforme si leggono allo stesso modo nella console.
 */
object CrashLogAnalysis {

    /** Gemini Nano accetta meno di 4.000 token: il log resta ben sotto. */
    const val MAX_PROMPT_LOG_BYTES = 6 * 1024

    data class Result(val hasIssues: Boolean, val issues: List<CrashAnalyzer.IssueReport>)

    /**
     * Solo le righe che indicano un problema vero. Con log tutti INFO/DEBUG non
     * c'è niente da analizzare, e mandare l'intero file produrrebbe problemi
     * inventati. Le righe scritte dall'analisi stessa restano fuori: altrimenti
     * il modello le rilegge come errori a ogni giro.
     */
    fun significantLines(logs: String): String =
        logs.lineSequence()
            .filterNot { it.contains("[CrashAnalyzer.kt:") || it.contains("[OnDeviceCrashModel.kt:") }
            .filter { line ->
                line.contains("[ERROR]") || line.contains("[CRASH]") || line.contains("[FATAL]") ||
                    line.contains("FATAL EXCEPTION")
            }
            .joinToString("\n")

    fun buildPrompt(significantLogs: String, maxBytes: Int): String = """
        You analyse the log of the KidBox app. Rules:
        - Lines look like: [timestamp] [LEVEL] [category] [File.kt:line] message
        - Only [ERROR], [CRASH] and [FATAL] lines are real problems.
        - Set hasIssues to true only if there are concrete [ERROR] or [CRASH] lines.
        Reply ONLY with valid JSON, no other text:
        {"hasIssues": true, "issues": [{"type": "crash|error|malfunction|warning",
        "severity": "critical|high|medium|low", "category": "sync|auth|data|ui|ai|storage|navigation",
        "affectedModule": "file or function", "summary": "short description in Italian, max 120 characters",
        "detail": "probable technical cause", "firstOccurrence": "timestamp", "occurrences": 1}]}
        Log:
        ${tail(significantLogs, maxBytes)}
    """.trimIndent()

    /** Il JSON fra la prima `{` e l'ultima `}`; `null` se non c'è o non si legge. */
    fun parse(text: String): Result? = runCatching {
        val start = text.indexOf('{')
        val end = text.lastIndexOf('}')
        if (start < 0 || end <= start) return null
        val o = JSONObject(text.substring(start, end + 1))
        val arr = o.optJSONArray("issues")
        val issues = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
            val it = arr!!.optJSONObject(i) ?: return@mapNotNull null
            CrashAnalyzer.IssueReport(
                type = it.optString("type", "error"),
                severity = it.optString("severity", "medium"),
                category = it.optString("category", "data"),
                affectedModule = it.optString("affectedModule", "unknown"),
                summary = it.optString("summary").take(200),
                detail = it.optString("detail").take(1000),
                firstOccurrence = it.optString("firstOccurrence"),
                occurrences = it.optInt("occurrences", 1).coerceAtLeast(1),
            )
        }.filter { it.summary.isNotBlank() }
        Result(hasIssues = o.optBoolean("hasIssues", false) && issues.isNotEmpty(), issues = issues)
    }.getOrNull()

    /** Le ultime righe che stanno in [maxBytes]: i problemi recenti contano di più. */
    fun tail(raw: String, maxBytes: Int): String {
        if (raw.toByteArray(Charsets.UTF_8).size <= maxBytes) return raw
        val lines = raw.split('\n').toMutableList()
        while (lines.isNotEmpty()) {
            val candidate = lines.joinToString("\n")
            if (candidate.toByteArray(Charsets.UTF_8).size <= maxBytes) return candidate
            lines.removeAt(0)
        }
        return raw.takeLast(maxBytes)
    }
}
