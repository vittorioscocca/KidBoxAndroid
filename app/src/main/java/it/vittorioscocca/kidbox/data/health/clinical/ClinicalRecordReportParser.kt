package it.vittorioscocca.kidbox.data.health.clinical

import java.util.Locale

object ClinicalRecordReportParser {

    fun parse(
        text: String,
        subjectName: String,
        sourceAiEnhanced: Boolean,
    ): ClinicalRecordReport {
        val lines = ClinicalRecordTextSanitizer.sanitize(text).lines()
        val areas = mutableListOf<ClinicalRecordReportArea>()
        var currentTitle = "Sintesi"
        val buffer = mutableListOf<String>()

        fun flush() {
            if (buffer.isEmpty()) return
            val body = buffer.joinToString("\n").trim()
            if (body.isEmpty()) {
                buffer.clear()
                return
            }
            val bullets = body.lines()
                .filter { it.trim().startsWith("•") }
                .map { it.trim() }
            areas += ClinicalRecordReportArea(
                id = areaIdFor(currentTitle),
                title = currentTitle,
                summary = bullets.firstOrNull() ?: body.take(80),
                narrative = body,
                trendNarrative = null,
                bullets = bullets.take(8),
            )
            buffer.clear()
        }

        lines.forEach { line ->
            val trimmed = line.trim()
            if (trimmed == "---" || isSectionHeader(trimmed)) {
                flush()
                if (isSectionHeader(trimmed)) currentTitle = trimmed
                return@forEach
            }
            buffer += line
        }
        flush()

        return ClinicalRecordReport(
            subjectName = subjectName,
            sourceNative = !sourceAiEnhanced,
            sourceAiEnhanced = sourceAiEnhanced,
            generatedAtEpochMillis = System.currentTimeMillis(),
            fullDocumentLines = lines.filter { it.isNotBlank() || it == "---" },
            areas = areas,
        )
    }

    private fun isSectionHeader(line: String): Boolean {
        if (line.startsWith("CARTELLA CLINICA")) return false
        if (line.startsWith("•") || line.startsWith("-")) return false
        if (line.firstOrNull()?.isDigit() == true) return false
        return line == line.uppercase(Locale.getDefault()) && line.length > 8 && !line.contains(":")
    }

    private fun areaIdFor(title: String): String {
        val t = title.lowercase(Locale.getDefault())
        return when {
            t.contains("terapie") || t.contains("cure") -> ClinicalRecordTopicBuilder.TopicId.THERAPIES.raw
            t.contains("attesa") || t.contains("prenotat") -> ClinicalRecordTopicBuilder.TopicId.PENDING.raw
            t.contains("health") || t.contains("wearable") || t.contains("salute") ->
                ClinicalRecordAppleHealthNarrative.AREA_ID
            t.contains("pressione") -> ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY.raw
            t.contains("cardio") || t.contains("cuore") -> ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY.raw
            t.contains("gastro") -> ClinicalRecordTopicBuilder.TopicId.GASTROENTEROLOGY.raw
            t.contains("urolog") -> ClinicalRecordTopicBuilder.TopicId.UROLOGY.raw
            t.contains("metabol") || t.contains("laboratorio") -> ClinicalRecordTopicBuilder.TopicId.METABOLISM.raw
            else -> "section_${title.hashCode()}"
        }
    }
}
