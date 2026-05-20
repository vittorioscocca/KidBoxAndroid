package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity
import it.vittorioscocca.kidbox.domain.health.HealthAgeFormatting
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

object ClinicalRecordTopicBuilder {

    enum class TopicId(val raw: String, val title: String, val iconKey: String, val tint: Long) {
        THERAPIES("therapies", "Terapie in corso", "pill", 0xFF9573D9),
        PENDING("pending", "Esami in attesa", "exam", 0xFFD98C59),
        BLOOD_PRESSURE("blood_pressure", "Pressione", "heart", 0xFF5996D9),
        CARDIOLOGY("cardiology", "Cardiologia", "heart", 0xFFE85A5A),
        GASTROENTEROLOGY("gastroenterology", "Gastroenterologia", "doc", 0xFF66BFA6),
        UROLOGY("urology", "Urologia", "visit", 0xFF40A6BF),
        METABOLISM("metabolism", "Glicemia e metabolismo", "exam", 0xFFF38D73),
    }

    fun build(
        subjectName: String,
        birthMillis: Long?,
        residence: String?,
        profile: KBPediatricProfile?,
        health: HealthImportSnapshot?,
        healthLabel: String,
        treatments: List<KBTreatmentEntity>,
        @Suppress("UNUSED_PARAMETER") vaccines: List<KBVaccineEntity>,
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
        documents: List<it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity> = emptyList(),
        extractedValues: List<ExtractedMedicalValue> = emptyList(),
    ): ClinicalRecordReport {
        var extracted = extractedValues.ifEmpty {
            ClinicalRecordValueIndex.extractAll(exams, visits, documents)
        }.toMutableList()
        appendHealthValues(health, healthLabel, extracted)
        val active = treatments.filter { it.isActive && !it.isDeleted && it.petId.isBlank() }
        val ctx = ClinicalRecordCleanDocumentBuilder.Context(
            subjectName = subjectName,
            birthMillis = birthMillis,
            residence = residence,
            profile = profile,
            health = health,
            healthLabel = healthLabel,
            treatments = active,
            visits = visits,
            exams = exams,
            extracted = extracted,
        )
        val doc = ClinicalRecordCleanDocumentBuilder.buildDocument(ctx)
        val (areas, specialtyTrends, global) = ClinicalRecordCleanDocumentBuilder.buildUIAreas(ctx)

        return ClinicalRecordTextSanitizer.sanitizeReport(
            ClinicalRecordReport(
                subjectName = subjectName,
                sourceNative = true,
                fullDocumentLines = doc,
                areas = areas.filter {
                    ClinicalRecordSectionPolicy.shouldGenerateStandaloneSection(it.id) &&
                        (it.bullets.isNotEmpty() || it.narrative.isNotBlank())
                },
                globalSummary = global,
                specialtyTrends = specialtyTrends,
            ),
        )
    }

    private fun topicFromTrend(trend: SpecialtyTrendSnapshot, chronology: List<String>): Block {
        val lines = mutableListOf("---", trend.specialtyTitle.uppercase(Locale.getDefault()), "", "ANALISI ANDAMENTO:", trend.narrativeAnalysis, "")
        trend.parameters.forEach { p ->
            val series = p.points.joinToString(" → ") { "${year(it.dateEpochMillis)}: ${it.displayValue}" }
            lines += "• ${p.name}: $series"
        }
        if (chronology.isNotEmpty()) {
            lines += "VISITE E ESAMI:"
            lines += chronology.take(12)
        }
        val trendText = trend.parameters.joinToString("\n") { p ->
            "• ${p.name}: ${p.points.joinToString(" → ") { "${year(it.dateEpochMillis)}: ${it.displayValue}" }}"
        }
        val area = ClinicalRecordReportArea(
            trend.specialtyId, trend.specialtyTitle,
            trend.parameters.lastOrNull()?.points?.lastOrNull()?.displayValue ?: trend.narrativeAnalysis.take(60),
            lines.joinToString("\n"), trendText.ifBlank { null },
            trend.parameters.flatMap { it.points }.takeLast(3).map { "• ${it.displayValue}" },
            trend.overallStatus, trend.narrativeAnalysis, trend.parameters,
        )
        return Block(lines, area)
    }

    private fun chronologyFor(topic: TopicId, visits: List<KBMedicalVisitEntity>, exams: List<KBMedicalExamEntity>): List<String> {
        val vLines = visits.filter { matchesTopic(topic, textOfVisit(it)) }.sortedBy { it.dateEpochMillis }
            .map { visitLine(it) }
        val eLines = exams.filter { matchesTopic(topic, it.name + " " + it.resultText.orEmpty()) }
            .sortedBy { it.resultDateEpochMillis ?: it.updatedAtEpochMillis }
            .map { "• ${formatDate(it.resultDateEpochMillis ?: it.updatedAtEpochMillis)} — ${it.name}" }
        return vLines + eLines
    }

    private fun matchesTopic(topic: TopicId, text: String): Boolean {
        val t = text.lowercase(Locale.getDefault())
        return when (topic) {
            TopicId.BLOOD_PRESSURE -> t.contains("pressione") || t.contains("mmhg")
            TopicId.CARDIOLOGY -> t.contains("cardio") || t.contains("sforzo") || t.contains("colester")
            TopicId.GASTROENTEROLOGY -> t.contains("gast") || t.contains("colon") || t.contains("epat") || t.contains("angiom")
            TopicId.UROLOGY -> t.contains("prostata") || t.contains("ren") || t.contains("urolog")
            TopicId.METABOLISM -> t.contains("glicemia") || t.contains("ldl") || t.contains("sangue")
            else -> false
        }
    }

    private fun textOfVisit(v: KBMedicalVisitEntity) =
        listOfNotNull(v.reason, v.diagnosis, v.recommendations, v.notes).joinToString(" ")

    private fun globalSummaryLines(global: ClinicalRecordGlobalSummary): List<String> = buildList {
        add(""); add("SINTESI CLINICA GLOBALE"); add("━━━━━━━━━━━━━━━━━━━━━━━━")
        global.statusLines.forEach { row ->
            add("${row.status.emoji} ${row.specialtyTitle} — ${row.status.badgeLabel}: ${row.headline}")
        }
        if (global.activeTherapyNames.isNotEmpty()) add("TERAPIE ATTIVE: ${global.activeTherapyNames.joinToString(", ")}")
        global.nextAppointmentLine?.let { add("PROSSIMI ESAMI: $it") }
    }

    private fun year(epoch: Long) = Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.YEAR)

    private data class Block(val lines: List<String>, val area: ClinicalRecordReportArea)

    private fun buildHeaderLines(
        subjectName: String,
        birthMillis: Long?,
        residence: String?,
        profile: KBPediatricProfile?,
    ): List<String> {
        val lines = mutableListOf("CARTELLA CLINICA — ${subjectName.uppercase(Locale.getDefault())}")
        birthMillis?.let {
            val age = HealthAgeFormatting.ageDescriptionFromBirth(it)
            lines += "Data di nascita: ${formatDate(it)}${if (age.isNotBlank()) " ($age)" else ""}"
        }
        residence?.takeIf { it.isNotBlank() }?.let { lines += "Residenza: $it" }
        profile?.bloodGroup?.takeIf { it.isNotBlank() }?.let { lines += "Gruppo sanguigno: $it" }
        return lines
    }

    private fun buildTherapies(treatments: List<KBTreatmentEntity>): Block {
        val lines = mutableListOf("---", "TERAPIE IN CORSO", "")
        val bullets = mutableListOf<String>()
        if (treatments.isEmpty()) {
            lines += "Nessuna terapia farmacologica attiva registrata."
        } else {
            treatments.forEach { t ->
                val freq = frequencyLabel(t)
                var line = "• ${t.drugName} — ${formatDosage(t.dosageValue)} ${t.dosageUnit}, $freq"
                if (t.isLongTerm) line += " (lungo termine)"
                else t.endDateEpochMillis?.let { line += " (fine prevista: ${formatDate(it)})" }
                t.notes?.takeIf { it.isNotBlank() }?.let { line += " — $it" }
                lines += line
                bullets += line
            }
        }
        val area = ClinicalRecordReportArea(
            TopicId.THERAPIES.raw, TopicId.THERAPIES.title,
            if (treatments.isEmpty()) "Nessuna cura" else "${treatments.size} in corso",
            lines.joinToString("\n"), null, bullets,
        )
        return Block(lines, area)
    }

    private fun buildPending(exams: List<KBMedicalExamEntity>): Block {
        val pending = exams.filter { it.statusRaw == "In attesa" || it.statusRaw == "Prenotato" }
            .sortedBy { it.deadlineEpochMillis ?: Long.MAX_VALUE }
        if (pending.isEmpty()) {
            return Block(emptyList(), ClinicalRecordReportArea(TopicId.PENDING.raw, TopicId.PENDING.title, "", "", null, emptyList()))
        }
        val lines = mutableListOf("---", "ESAMI IN ATTESA O PRENOTATI", "")
        val bullets = mutableListOf<String>()
        pending.forEachIndexed { i, e ->
            var line = "${i + 1}. ${e.name.uppercase(Locale.getDefault())}"
            e.deadlineEpochMillis?.let { line += " — ${formatDate(it)}" }
            if (e.isUrgent) line += " [urgente]"
            lines += line
            bullets += line
        }
        val area = ClinicalRecordReportArea(
            TopicId.PENDING.raw, TopicId.PENDING.title, "${pending.size} da eseguire",
            lines.joinToString("\n"), null, bullets,
        )
        return Block(lines, area)
    }

    private fun buildBloodPressure(
        health: HealthImportSnapshot?,
        healthLabel: String,
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
    ): Block? {
        val points = mutableListOf<Triple<Long, String, String>>()
        health?.heartRateBpm?.let {
            // Health Connect Android: no BP in snapshot yet — only exam/visit text
        }
        exams.forEach { e ->
            extractBp(e.resultText.orEmpty(), e.name, e.resultDateEpochMillis ?: e.updatedAtEpochMillis, points)
        }
        visits.forEach { v ->
            val blob = listOfNotNull(v.diagnosis, v.recommendations, v.notes, v.reason).joinToString(" ")
            extractBp(blob, "Visita", v.dateEpochMillis, points)
        }
        points.sortBy { it.first }
        if (points.isEmpty()) return null

        val narrative = mutableListOf("Monitoraggio pressione arteriosa", "")
        points.takeLast(8).forEach { (date, value, src) ->
            narrative += "• ${formatDate(date)} — $value ($src)"
        }
        val bullets = points.takeLast(3).map { (date, value, _) -> "• ${formatDate(date)} — $value" }
        val trend = if (points.size >= 2) {
            val series = points.joinToString(" → ") {
                val y = Calendar.getInstance().apply { timeInMillis = it.first }.get(Calendar.YEAR)
                "$y: ${it.second}"
            }
            "Andamento pressione: $series"
        } else {
            points.lastOrNull()?.let { "Ultima misura: ${it.second} (${formatDate(it.first)})" }
        }
        val consideration = bpConsideration(points.last().second)
        if (consideration != null) {
            narrative += ""
            narrative += "Considerazioni: $consideration"
        }
        val area = ClinicalRecordReportArea(
            TopicId.BLOOD_PRESSURE.raw, TopicId.BLOOD_PRESSURE.title,
            points.last().second, narrative.joinToString("\n"), trend, bullets,
        )
        return Block(sectionDoc(area), area)
    }

    private fun buildCardiology(
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
        trends: Map<LabMetricFamily, List<LabMeasurementPoint>>,
    ): Block? {
        val cVisits = visits.filter { matchesCardio(textOfVisit(it)) }
        val cExams = exams.filter { matchesCardio(it.name + " " + it.resultText.orEmpty()) }
        if (cVisits.isEmpty() && cExams.isEmpty() && (trends[LabMetricFamily.LIPIDS].isNullOrEmpty())) return null

        val narrative = mutableListOf<String>()
        val bullets = mutableListOf<String>()
        ClinicalRecordLabTrends.narrative(LabMetricFamily.LIPIDS, trends[LabMetricFamily.LIPIDS].orEmpty())?.let {
            narrative += it; narrative += ""
        }
        if (cExams.isNotEmpty()) {
            narrative += "Esami cardiologici:"
            cExams.sortedByDescending { it.resultDateEpochMillis ?: it.updatedAtEpochMillis }.take(6).forEach { e ->
                narrative += ""
                narrative += "${e.name} (${formatDate(e.resultDateEpochMillis ?: e.updatedAtEpochMillis)})"
                bullets += e.name
                appendExcerpt(e.resultText, narrative)
            }
        }
        if (cVisits.isNotEmpty()) {
            narrative += ""
            narrative += "Visite nel tempo:"
            cVisits.sortedBy { it.dateEpochMillis }.forEach { v ->
                val line = visitLine(v)
                narrative += line
                bullets += clip(line)
            }
        }
        val trend = listOfNotNull(
            ClinicalRecordLabTrends.narrative(LabMetricFamily.CARDIAC, trends[LabMetricFamily.CARDIAC].orEmpty()),
            ClinicalRecordLabTrends.narrative(LabMetricFamily.LIPIDS, trends[LabMetricFamily.LIPIDS].orEmpty()),
        ).joinToString("\n\n").ifBlank { null }

        val area = ClinicalRecordReportArea(
            TopicId.CARDIOLOGY.raw, TopicId.CARDIOLOGY.title,
            summaryTopic(cExams.size, cVisits.size),
            narrative.joinToString("\n"), trend, bullets.take(5),
        )
        return Block(sectionDoc(area), area)
    }

    private fun buildGastroenterology(
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
        trends: Map<LabMetricFamily, List<LabMeasurementPoint>>,
    ): Block? {
        val gVisits = visits.filter { matchesGastro(textOfVisit(it)) }
        val gExams = exams.filter { matchesGastro(it.name + " " + it.resultText.orEmpty()) }
        if (gVisits.isEmpty() && gExams.isEmpty() && trends[LabMetricFamily.LIVER_KIDNEY].isNullOrEmpty()) return null

        val narrative = mutableListOf<String>()
        val bullets = mutableListOf<String>()
        ClinicalRecordLabTrends.narrative(LabMetricFamily.LIVER_KIDNEY, trends[LabMetricFamily.LIVER_KIDNEY].orEmpty())?.let {
            narrative += it; narrative += ""
        }
        if (gExams.isNotEmpty()) {
            narrative += "Esami e referti:"
            gExams.sortedByDescending { it.resultDateEpochMillis ?: it.updatedAtEpochMillis }.take(6).forEach { e ->
                narrative += ""
                narrative += "${e.name} (${formatDate(e.resultDateEpochMillis ?: e.updatedAtEpochMillis)})"
                bullets += e.name
                appendExcerpt(e.resultText, narrative)
            }
        }
        if (gVisits.isNotEmpty()) {
            narrative += ""
            narrative += "Visite nel tempo:"
            gVisits.sortedBy { it.dateEpochMillis }.forEach { narrative += visitLine(it) }
        }
        val area = ClinicalRecordReportArea(
            TopicId.GASTROENTEROLOGY.raw, TopicId.GASTROENTEROLOGY.title,
            summaryTopic(gExams.size, gVisits.size),
            narrative.joinToString("\n"),
            ClinicalRecordLabTrends.narrative(LabMetricFamily.LIVER_KIDNEY, trends[LabMetricFamily.LIVER_KIDNEY].orEmpty()),
            bullets.take(5),
        )
        return Block(sectionDoc(area), area)
    }

    private fun buildUrology(visits: List<KBMedicalVisitEntity>, exams: List<KBMedicalExamEntity>): Block? {
        val uVisits = visits.filter { matchesUrology(textOfVisit(it)) }
        val uExams = exams.filter { matchesUrology(it.name + " " + it.resultText.orEmpty()) }
        if (uVisits.isEmpty() && uExams.isEmpty()) return null

        val narrative = mutableListOf<String>()
        val bullets = mutableListOf<String>()
        if (uExams.isNotEmpty()) {
            narrative += "Esami urologici:"
            uExams.sortedByDescending { it.resultDateEpochMillis ?: it.updatedAtEpochMillis }.take(6).forEach { e ->
                narrative += ""
                narrative += "${e.name} (${formatDate(e.resultDateEpochMillis ?: e.updatedAtEpochMillis)})"
                bullets += e.name
                appendExcerpt(e.resultText, narrative)
            }
        }
        if (uVisits.isNotEmpty()) {
            narrative += ""
            narrative += "Visite e controlli nel tempo:"
            uVisits.sortedBy { it.dateEpochMillis }.forEach { v ->
                val line = visitLine(v)
                narrative += line
                bullets += clip(line)
            }
        }
        val trend = if (uVisits.size >= 2) {
            "Confronta le visite urologiche per evidenziare stabilità o nuovi sintomi nel periodo."
        } else null
        val area = ClinicalRecordReportArea(
            TopicId.UROLOGY.raw, TopicId.UROLOGY.title,
            summaryTopic(uExams.size, uVisits.size),
            narrative.joinToString("\n"), trend, bullets.take(5),
        )
        return Block(sectionDoc(area), area)
    }

    private fun buildMetabolism(trends: Map<LabMetricFamily, List<LabMeasurementPoint>>): Block? {
        val glycemic = trends[LabMetricFamily.GLYCEMIC].orEmpty()
        val blood = trends[LabMetricFamily.BLOOD_COUNT].orEmpty()
        if (glycemic.isEmpty() && blood.isEmpty()) return null
        val narrative = buildList {
            ClinicalRecordLabTrends.narrative(LabMetricFamily.GLYCEMIC, glycemic)?.let { add(it) }
            ClinicalRecordLabTrends.narrative(LabMetricFamily.BLOOD_COUNT, blood)?.let {
                if (isNotEmpty()) add("")
                add(it)
            }
        }
        val bullets = (glycemic + blood).takeLast(4).map { p ->
            "• ${p.metricLabel}: ${p.value}${p.unit?.let { " $it" }.orEmpty()} (${p.year})"
        }
        val area = ClinicalRecordReportArea(
            TopicId.METABOLISM.raw, TopicId.METABOLISM.title,
            "Parametri ematici monitorati",
            narrative.joinToString("\n"),
            narrative.joinToString("\n\n"),
            bullets,
        )
        return Block(sectionDoc(area), area)
    }

    private fun sectionDoc(area: ClinicalRecordReportArea): List<String> =
        listOf("---", area.title.uppercase(Locale.getDefault()), "", area.narrative)

    private fun visitLine(v: KBMedicalVisitEntity): String {
        var line = "• ${formatDate(v.dateEpochMillis)} — ${v.reason.ifBlank { "Visita" }}"
        v.diagnosis?.takeIf { it.isNotBlank() }?.let { line += "\n  Diagnosi: ${clip(it)}" }
        v.recommendations?.takeIf { it.isNotBlank() }?.let { line += "\n  Indicazioni: ${clip(it)}" }
        return line
    }

    private fun appendExcerpt(result: String?, narrative: MutableList<String>) {
        result?.lines()?.map { it.trim() }?.filter { it.isNotEmpty() }?.take(8)?.forEach {
            narrative += "  • $it"
        }
    }

    private fun matchesCardio(text: String): Boolean {
        val t = text.lowercase(Locale.getDefault())
        return t.contains("cardio") || t.contains("cuore") || t.contains("colester") || t.contains("ldl") ||
            t.contains("sforzo") || t.contains("ecocardio") || t.contains("coronarografia")
    }

    private fun matchesGastro(text: String): Boolean {
        val t = text.lowercase(Locale.getDefault())
        return t.contains("gast") || t.contains("colon") || t.contains("epat") || t.contains("milza") ||
            t.contains("addome") || t.contains("ernia iatale")
    }

    private fun matchesUrology(text: String): Boolean {
        val t = text.lowercase(Locale.getDefault())
        return t.contains("prostata") || t.contains("ren") || t.contains("urolog") ||
            t.contains("inguin") || t.contains("varicocele") || t.contains("psa")
    }

    private fun summaryTopic(exams: Int, visits: Int): String = when {
        exams == 0 && visits == 0 -> "Nessun dato in archivio"
        visits == 0 -> "$exams esami documentati"
        exams == 0 -> "$visits visite nel tempo"
        else -> "$exams esami · $visits visite"
    }

    private val bpPattern: Pattern = Pattern.compile(
        "(?i)(?:PA|pressione)[^\\d]{0,20}(\\d{2,3})\\s*/\\s*(\\d{2,3})",
    )

    private fun extractBp(
        text: String,
        source: String,
        date: Long,
        points: MutableList<Triple<Long, String, String>>,
    ) {
        val m = bpPattern.matcher(text)
        while (m.find()) {
            points += Triple(date, "${m.group(1)}/${m.group(2)} mmHg", source)
        }
    }

    private fun bpConsideration(last: String): String? {
        val parts = last.split("/").mapNotNull { it.filter(Char::isDigit).toDoubleOrNull() }
        if (parts.size < 2) return "Verifica le misure con il medico curante."
        return when {
            parts[0] >= 140 -> "L'ultima sistolica risulta elevata; utile monitoraggio medico."
            parts[0] < 90 -> "Pressione bassa nell'ultima rilevazione; consultare il medico se sintomatica."
            else -> "Ultima pressione nei limiti usuali; continua il monitoraggio periodico."
        }
    }

    private fun frequencyLabel(t: KBTreatmentEntity): String =
        if (t.intervalBetweenDosesDays > 0) "Ogni ${t.intervalBetweenDosesDays} giorni"
        else if (t.dailyFrequency == 1) "1 volta al giorno"
        else "${t.dailyFrequency} volte al giorno"

    private fun formatDosage(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(Locale.US, v)

    private fun formatDate(epoch: Long): String =
        DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(Date(epoch))

    private fun clip(text: String): String {
        val t = text.trim()
        return if (t.length <= 120) t else t.take(119) + "…"
    }

    private fun appendHealthValues(
        health: HealthImportSnapshot?,
        label: String,
        extracted: MutableList<ExtractedMedicalValue>,
    ) {
        val h = health ?: return
        val synced = h.syncedAtEpochMillis
        h.restingHeartRateBpm?.let { rhr ->
            extracted += ExtractedMedicalValue(
                kind = ExtractedValueKind.HEART_RATE,
                parameterName = "Frequenza a riposo",
                numericValue = rhr,
                textValue = "${rhr.toInt()} bpm",
                unit = "bpm",
                systolic = null,
                diastolic = null,
                lesionType = null,
                dimensionMm = null,
                dateEpochMillis = h.heartRateMeasuredAtEpochMillis ?: synced,
                sourceId = "health:rhr:$label",
                sourceLabel = label
            )
        }
        h.heartRateBpm?.let { hr ->
            extracted += ExtractedMedicalValue(
                kind = ExtractedValueKind.HEART_RATE,
                parameterName = "Frequenza cardiaca",
                numericValue = hr,
                textValue = "${hr.toInt()} bpm",
                unit = "bpm",
                systolic = null,
                diastolic = null,
                lesionType = null,
                dimensionMm = null,
                dateEpochMillis = h.heartRateMeasuredAtEpochMillis ?: synced,
                sourceId = "health:hr:$label",
                sourceLabel = label
            )
        }
    }
}
