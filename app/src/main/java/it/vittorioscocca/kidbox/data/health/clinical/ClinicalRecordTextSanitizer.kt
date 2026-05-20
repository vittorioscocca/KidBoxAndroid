package it.vittorioscocca.kidbox.data.health.clinical

/** Rimuove artefatti Markdown e simboli AI da testi cartella clinica. */
object ClinicalRecordTextSanitizer {

    fun sanitize(text: String): String {
        var s = text.replace("\r\n", "\n")
        s = s.replace("```", "").replace("**", "").replace("__", "").replace("`", "")
        s = s.replace("*/", "").replace("/*", "")
        val lines = s.split("\n").map { line ->
            var l = line.trim()
            while (l.startsWith("#")) l = l.drop(1).trim()
            when {
                l.startsWith("* ") -> "• ${l.drop(2)}"
                l.startsWith("- ") && !l.startsWith("--") -> "• ${l.drop(2)}"
                else -> l
            }.replace("*", "")
        }
        return lines.joinToString("\n")
            .replace("\n\n\n", "\n\n")
            .trim()
    }

    fun sanitizeLines(lines: List<String>): List<String> =
        lines.map { sanitize(it) }.filter { it.isNotEmpty() || it == "---" }

    fun sanitizeArea(area: ClinicalRecordReportArea): ClinicalRecordReportArea =
        area.copy(
            title = sanitize(area.title),
            summary = sanitize(area.summary),
            narrative = sanitize(area.narrative),
            trendNarrative = area.trendNarrative?.let { sanitize(it) },
            bullets = area.bullets.map { sanitize(it) },
        )

    fun sanitizeReport(report: ClinicalRecordReport): ClinicalRecordReport =
        report.copy(
            subjectName = sanitize(report.subjectName),
            fullDocumentLines = sanitizeLines(report.fullDocumentLines),
            areas = report.areas.map { sanitizeArea(it) },
        )
}
