package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import it.vittorioscocca.kidbox.util.KBLocale

/** Sintesi UI Cardiologia / Urologia da referti, con terminologia originale. */
object ClinicalRecordSpecialtyRefertoSynthesis {

    data class Result(
        val synthesisParagraph: String,
        val timelineDetail: String,
        val highlights: List<String>,
    )

    private data class RefertoEntry(
        val dateEpochMillis: Long,
        val title: String,
        val snippets: List<String>,
    )

    fun synthesize(
        specialty: ClinicalRecordTopicBuilder.TopicId,
        exams: List<KBMedicalExamEntity>,
        visits: List<KBMedicalVisitEntity>,
        parameters: List<ParameterTrend>,
    ): Result? {
        if (specialty != ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY &&
            specialty != ClinicalRecordTopicBuilder.TopicId.UROLOGY
        ) return null

        val vocabulary = vocabulary(specialty)
        val entries = mutableListOf<RefertoEntry>()

        exams.filter { !it.resultText.isNullOrBlank() }.forEach { exam ->
            val blob = exam.name + " " + exam.resultText.orEmpty()
            if (!matchesSpecialty(specialty, blob)) return@forEach
            val snippets = extractSnippets(exam.resultText.orEmpty(), vocabulary)
            if (snippets.isEmpty()) return@forEach
            entries += RefertoEntry(
                exam.resultDateEpochMillis ?: exam.updatedAtEpochMillis,
                exam.name,
                snippets,
            )
        }

        visits.forEach { visit ->
            val blob = listOfNotNull(visit.reason, visit.diagnosis, visit.recommendations).joinToString(" ")
            if (blob.isBlank() || !matchesSpecialty(specialty, blob)) return@forEach
            val snippets = extractSnippets(blob, vocabulary)
            if (snippets.isEmpty()) return@forEach
            entries += RefertoEntry(
                visit.dateEpochMillis,
                visit.reason.ifBlank { "Visita" },
                snippets,
            )
        }

        entries.sortBy { it.dateEpochMillis }
        if (entries.isEmpty()) return fallbackFromParameters(specialty, parameters)

        return Result(
            synthesisParagraph = buildSynthesisParagraph(specialty, entries, parameters),
            timelineDetail = buildTimeline(entries),
            highlights = buildHighlights(entries, parameters),
        )
    }

    private fun vocabulary(specialty: ClinicalRecordTopicBuilder.TopicId): Set<String> = when (specialty) {
        ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY -> setOf(
            "ecocardio", "sforzo", "coronar", "ischem", "ventricol", "valvol", "mets", "aritmi",
            "ldl", "hdl", "colester", "triglicerid", "pressione", "mmhg", "normale", "nei limiti", "negativ", "stabile",
        )
        ClinicalRecordTopicBuilder.TopicId.UROLOGY -> setOf(
            "prostata", "psa", "ren", "renale", "cisti", "varicocele", "inguin", "vescica", "uro",
            "normale", "nei limiti", "negativ", "stabile", "mm", "ml",
        )
        else -> emptySet()
    }

    private fun matchesSpecialty(specialty: ClinicalRecordTopicBuilder.TopicId, text: String): Boolean {
        val t = text.lowercase(Locale.getDefault())
        return when (specialty) {
            ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY ->
                t.contains("cardio") || t.contains("sforzo") || t.contains("ecocardio") ||
                    t.contains("coronar") || t.contains("colester")
            ClinicalRecordTopicBuilder.TopicId.UROLOGY ->
                t.contains("prostata") || t.contains("psa") || t.contains("ren") ||
                    t.contains("urolog") || t.contains("varicocele")
            else -> false
        }
    }

    private fun extractSnippets(text: String, vocabulary: Set<String>): List<String> {
        val clean = text.replace("\r\n", "\n").lines().map { it.trim() }.filter { it.isNotEmpty() }
        val scored = clean.mapNotNull { line ->
            val lower = line.lowercase(Locale.getDefault())
            val hits = vocabulary.count { lower.contains(it) }
            if (hits == 0 || line.length < 12) null
            else (hits + if (line.any { it.isDigit() }) 1 else 0) to clipPhrase(line)
        }.sortedByDescending { it.first }
        return scored.map { it.second }.distinct().take(3)
    }

    private fun clipPhrase(text: String): String {
        val t = text.trim()
        return if (t.length <= 140) t else t.take(139).trimEnd() + "…"
    }

    private fun buildSynthesisParagraph(
        specialty: ClinicalRecordTopicBuilder.TopicId,
        entries: List<RefertoEntry>,
        parameters: List<ParameterTrend>,
    ): String {
        val area = if (specialty == ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY) "cardiologico" else "urologico"
        val yFirst = year(entries.first().dateEpochMillis)
        val yLast = year(entries.last().dateEpochMillis)
        val parts = mutableListOf<String>()
        parts += "Sintesi $area da ${entries.size} referti in archivio ($yFirst–$yLast), con formulazioni tratte dai documenti originali."
        if (entries.size >= 2) {
            parts += if (specialty == ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY) {
                "Nel periodo considerato la documentazione cardiologica mostra un percorso clinico ricostruibile dai referti."
            } else {
                "Nel periodo considerato i controlli urologici documentati nei referti delineano l'evoluzione del quadro."
            }
        }
        entries.forEach { entry ->
            val whenStr = formatMonthYear(entry.dateEpochMillis)
            parts += "$whenStr, ${entry.title}: ${entry.snippets.joinToString(" Inoltre, ")}"
        }
        parameterEvolutionNote(parameters, specialty)?.let { parts += it }
        parts += if (specialty == ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY) {
            "Complessivamente, i referti vanno interpretati nel contesto delle terapie in corso e dei prossimi controlli programmati."
        } else {
            "Nel complesso, la documentazione urologica suggerisce di mantenere il follow-up indicato dai referti più recenti."
        }
        return parts.joinToString(" ")
    }

    private fun buildTimeline(entries: List<RefertoEntry>): String =
        entries.joinToString("\n\n") { entry ->
            "${formatMonthYear(entry.dateEpochMillis)} — ${entry.title}\n${entry.snippets.joinToString(" ")}"
        }

    private fun buildHighlights(entries: List<RefertoEntry>, parameters: List<ParameterTrend>): List<String> {
        val out = mutableListOf<String>()
        entries.takeLast(3).forEach { e ->
            e.snippets.firstOrNull()?.let { out += "${formatMonthYear(e.dateEpochMillis)}: $it" }
        }
        parameters.takeLast(2).forEach { p ->
            p.points.lastOrNull()?.let { out += "${p.name} (${year(it.dateEpochMillis)}): ${it.displayValue}" }
        }
        return out.take(4)
    }

    private fun parameterEvolutionNote(parameters: List<ParameterTrend>, specialty: ClinicalRecordTopicBuilder.TopicId): String? {
        if (parameters.isEmpty()) return null
        val bits = parameters.take(4).mapNotNull { param ->
            if (param.points.size < 2) {
                param.points.lastOrNull()?.let { "${param.name} nell'ultimo referto: ${it.displayValue}" }
            } else {
                val series = param.points.joinToString(" → ") { "${year(it.dateEpochMillis)}: ${it.displayValue}" }
                val dir = when (param.trend) {
                    ClinicalTrendDirection.STABILE -> "stabile"
                    ClinicalTrendDirection.IN_AUMENTO -> "in aumento"
                    else -> "in diminuzione"
                }
                "${param.name} $dir nel periodo ($series)"
            }
        }
        if (bits.isEmpty()) return null
        val label = if (specialty == ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY) "Parametri ematici e funzionali" else "Parametri monitorati"
        return "$label: ${bits.joinToString("; ")}."
    }

    private fun fallbackFromParameters(
        specialty: ClinicalRecordTopicBuilder.TopicId,
        parameters: List<ParameterTrend>,
    ): Result? {
        val note = parameterEvolutionNote(parameters, specialty) ?: return null
        return Result(
            synthesisParagraph = "Per ${specialty.title}, dai dati estratti risulta: $note",
            timelineDetail = "",
            highlights = parameters.take(3).mapNotNull { p -> p.points.lastOrNull()?.let { "${p.name}: ${it.displayValue}" } },
        )
    }

    private fun formatMonthYear(epoch: Long): String =
        SimpleDateFormat("MMMM yyyy", KBLocale.current()).format(Date(epoch))

    private fun year(epoch: Long): Int {
        val cal = java.util.Calendar.getInstance()
        cal.timeInMillis = epoch
        return cal.get(java.util.Calendar.YEAR)
    }
}
