package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.domain.health.HealthAgeFormatting
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import it.vittorioscocca.kidbox.util.KBLocale

/** Documento cartella clinica in formato pulito (intestazione, patologie per area, esami, andamenti sintetici). */
object ClinicalRecordCleanDocumentBuilder {

    data class Context(
        val subjectName: String,
        val birthMillis: Long?,
        val residence: String?,
        val profile: KBPediatricProfile?,
        val health: HealthImportSnapshot?,
        val healthLabel: String,
        val treatments: List<KBTreatmentEntity>,
        val visits: List<KBMedicalVisitEntity>,
        val exams: List<KBMedicalExamEntity>,
        val extracted: List<ExtractedMedicalValue>,
    )

    fun buildDocument(ctx: Context): List<String> = buildList {
        addAll(buildHeader(ctx))
        ctx.health?.let { health ->
            val wearable = ClinicalRecordAppleHealthNarrative.documentLines(
                health, ctx.healthLabel, ctx.birthMillis, ctx.visits,
            )
            if (wearable.isNotEmpty()) {
                add("")
                addAll(wearable)
            }
        }
        add("")
        addAll(buildTherapiesSection(ctx.treatments))
        add("")
        addAll(buildPathologiesSection(ctx))
        val pending = buildPendingSection(ctx.exams)
        if (pending.isNotEmpty()) {
            add("")
            addAll(pending)
        }
        val recent = buildRecentExamsSection(ctx)
        if (recent.isNotEmpty()) {
            add("")
            addAll(recent)
        }
    }.let { ClinicalRecordTextSanitizer.sanitizeLines(it) }

    fun buildUIAreas(ctx: Context): Triple<List<ClinicalRecordReportArea>, List<SpecialtyTrendSnapshot>, ClinicalRecordGlobalSummary> {
        val areas = mutableListOf<ClinicalRecordReportArea>()
        val trends = mutableListOf<SpecialtyTrendSnapshot>()

        areas += buildHeaderArea(ctx)
        ctx.health?.let { health ->
            ClinicalRecordAppleHealthNarrative.reportArea(
                health, ctx.healthLabel, ctx.birthMillis, ctx.visits,
            )?.let { areas += it }
        }
        areas += therapyArea(ctx.treatments)

        val path = pathologyArea(ctx)
        if (path.bullets.isNotEmpty() || path.narrative.isNotBlank()) areas += path

        val pending = pendingArea(ctx.exams)
        if (pending.bullets.isNotEmpty()) areas += pending

        for (topic in ClinicalRecordSectionPolicy.dynamicSpecialtyTopics) {
            val chronology = chronologyFor(topic, ctx)
            val trend = TrendAnalyzer.buildSpecialtyTrend(topic.raw, topic.title, ctx.extracted, chronology) ?: continue
            trends += trend
            areas += areaFromTrend(trend, chronology, ctx)
        }

        val recent = recentExamsArea(ctx)
        if (recent.bullets.isNotEmpty() || recent.narrative.isNotBlank()) areas += recent

        val therapyNames = ctx.treatments.map { it.drugName }
        val nextPending = ctx.exams
            .filter { it.statusRaw == "In attesa" || it.statusRaw == "Prenotato" }
            .minByOrNull { it.deadlineEpochMillis ?: Long.MAX_VALUE }
        val nextLine = nextPending?.let { e ->
            e.deadlineEpochMillis?.let { "${e.name} — ${formatDate(it)}" } ?: e.name
        }
        val global = TrendAnalyzer.buildGlobalSummary(trends, therapyNames, nextLine)
        return Triple(areas, trends, global)
    }

    private fun buildHeader(ctx: Context): List<String> = buildList {
        add("CARTELLA CLINICA — ${ctx.subjectName.uppercase(Locale.getDefault())}")
        ctx.birthMillis?.let {
            val age = HealthAgeFormatting.ageDescriptionFromBirth(it)
            add("Data di nascita: ${formatDate(it)}${if (age.isNotBlank()) " ($age)" else ""}")
        }
        ctx.residence?.takeIf { it.isNotBlank() }?.let { add("Residenza: $it") }
        ctx.profile?.bloodGroup?.takeIf { it.isNotBlank() }?.let { add("Gruppo sanguigno: $it") }
    }

    private fun buildTherapiesSection(treatments: List<KBTreatmentEntity>): List<String> = buildList {
        add("---")
        add("STATO ATTUALE DELLE CURE")
        add("")
        if (treatments.isEmpty()) {
            add("Nessuna terapia farmacologica attiva registrata.")
            return@buildList
        }
        add("TERAPIE IN CORSO (${treatments.size})")
        add("")
        treatments.forEach { t ->
            val freq = frequencyLabel(t)
            var line = "• ${t.drugName} — ${formatDosage(t.dosageValue)} ${t.dosageUnit}, $freq"
            if (t.isLongTerm) {
                line += " (terapia a lungo termine"
                t.notes?.takeIf { it.isNotBlank() }?.let { line += " per $it" }
                line += ")"
            } else {
                t.endDateEpochMillis?.let { line += " (termine previsto: ${formatDate(it)})" }
                    ?: t.notes?.takeIf { it.isNotBlank() }?.let { line += " — $it" }
            }
            add(line)
        }
    }

    private fun buildPathologiesSection(ctx: Context): List<String> {
        val buckets = linkedMapOf(
            "Cardiovascolare" to mutableListOf<String>(),
            "Gastroenterologica" to mutableListOf<String>(),
            "Urologica" to mutableListOf<String>(),
            "Altro" to mutableListOf<String>(),
        )

        fun classify(text: String): String {
            val t = text.lowercase(Locale.getDefault())
            return when {
                t.contains("cuore") || t.contains("cardio") || t.contains("colester") || t.contains("ischem") ||
                    t.contains("coronar") || t.contains("sforzo") || t.contains("ecocardio") || t.contains("pressione") ->
                    "Cardiovascolare"
                t.contains("gast") || t.contains("colon") || t.contains("epat") || t.contains("milza") ||
                    t.contains("ernia iatale") || t.contains("addome") || t.contains("angiom") || t.contains("agiom") ->
                    "Gastroenterologica"
                t.contains("prostata") || t.contains("ren") || t.contains("urolog") || t.contains("inguin") ||
                    t.contains("varicocele") || t.contains("cisti ren") ->
                    "Urologica"
                else -> "Altro"
            }
        }

        fun addUnique(bucket: String, line: String) {
            if (line.isBlank() || line in buckets[bucket]!!) return
            buckets[bucket]!! += line
        }

        ctx.treatments.filter { it.isLongTerm }.forEach { t ->
            val line = "• Terapia: ${t.drugName}${t.notes?.let { " — $it" }.orEmpty()}"
            addUnique(classify(t.drugName + " " + (t.notes.orEmpty())), line)
        }

        ctx.visits.forEach { v ->
            val dateStr = formatMonthYear(v.dateEpochMillis)
            listOfNotNull(v.diagnosis, v.reason, v.recommendations)
                .filter { it.isNotBlank() }
                .forEach { part ->
                    addUnique(classify(part), "• ${clip(part)} ($dateStr)")
                }
        }

        ctx.exams.filter { hasResult(it) }.forEach { e ->
            val text = e.name + " " + e.resultText.orEmpty()
            val dateStr = formatMonthYear(e.resultDateEpochMillis ?: e.updatedAtEpochMillis)
            val lower = text.lowercase(Locale.getDefault())
            if (lower.contains("negativ") || lower.contains("normale") || lower.contains("nei limiti") || lower.contains("stabile")) {
                addUnique(classify(text), "• ${e.name}: esito nei limiti ($dateStr)")
            }
            ctx.extracted.filter {
                it.kind == ExtractedValueKind.LESION &&
                    (it.sourceId == "exam:${e.id}" || it.sourceLabel == e.name)
            }.forEach { lesion ->
                addUnique(
                    "Gastroenterologica",
                    "• ${lesion.lesionType ?: "Lesione"} ${lesion.dimensionMm?.toInt() ?: 0} mm ($dateStr)",
                )
            }
        }

        return buildList {
            add("---")
            add("PRINCIPALI PATOLOGIE E CONDIZIONI")
            add("")
            var any = false
            for (title in listOf("Cardiovascolare", "Gastroenterologica", "Urologica", "Altro")) {
                val items = buckets[title]!!.takeIf { it.isNotEmpty() } ?: continue
                any = true
                add(title)
                add("")
                addAll(items.take(8))
                add("")
            }
            if (!any) add("Nessuna condizione strutturata dalle visite e dai referti in archivio.")
        }
    }

    private fun buildPendingSection(exams: List<KBMedicalExamEntity>): List<String> {
        val pending = exams.filter { it.statusRaw == "In attesa" || it.statusRaw == "Prenotato" }
            .sortedBy { it.deadlineEpochMillis ?: Long.MAX_VALUE }
        if (pending.isEmpty()) return emptyList()
        return buildList {
            add("---")
            add("ESAMI IN ATTESA/PRENOTATI (${pending.size})")
            add("")
            pending.forEachIndexed { i, e ->
                var line = "${i + 1}. ${e.name.uppercase(Locale.getDefault())}"
                e.deadlineEpochMillis?.let { line += " — ${formatDate(it)}" }
                e.preparation?.takeIf { it.isNotBlank() }?.let { line += " (${clip(it)})" }
                add(line)
            }
        }
    }

    private fun buildRecentExamsSection(ctx: Context): List<String> {
        val withResult = ctx.exams
            .filter { !it.resultText.isNullOrBlank() || hasResult(it) }
            .sortedByDescending { it.resultDateEpochMillis ?: it.updatedAtEpochMillis }
        if (withResult.isEmpty()) return emptyList()

        return buildList {
            add("---")
            add("ULTIMI ESAMI SIGNIFICATIVI")
            add("")
            val labExams = withResult.filter { isLabLike(it) }
            if (labExams.isNotEmpty()) {
                addAll(buildBloodWorkBlock(labExams, ctx.extracted))
                add("")
            }
            withResult.filter { !isLabLike(it) }.take(8).forEach { e ->
                add("${e.name} (${formatMonthYear(e.resultDateEpochMillis ?: e.updatedAtEpochMillis)})")
                add("")
                addAll(bulletLinesFromExam(e, ctx.extracted))
                add("")
            }
        }
    }

    private fun buildBloodWorkBlock(exams: List<KBMedicalExamEntity>, extracted: List<ExtractedMedicalValue>): List<String> {
        val latest = exams.firstOrNull()
        val date = latest?.resultDateEpochMillis ?: latest?.updatedAtEpochMillis ?: System.currentTimeMillis()
        return buildList {
            add("Esami del sangue (${formatMonthYear(date)}) — più recenti")
            add("")
            val labLabels = listOf(
                "Colesterolo totale", "LDL", "HDL", "Trigliceridi", "Glicemia",
                "GOT", "GPT", "PSA", "Creatinina", "Emoglobina",
            )
            labLabels.forEach { label ->
                val points = extracted.filter { it.kind == ExtractedValueKind.LAB && it.parameterName == label }
                    .sortedBy { it.dateEpochMillis }
                val last = points.lastOrNull() ?: return@forEach
                val qual = labQualitative(label, last.numericValue ?: 0.0)
                val display = last.textValue ?: formatNum(last.numericValue ?: 0.0)
                add("• $label: $display ($qual)")
            }
            compactLabTrend(extracted)?.let { trend ->
                add("")
                add("Andamento nel tempo:")
                add(trend)
            }
        }
    }

    private fun compactLabTrend(extracted: List<ExtractedMedicalValue>): String? {
        val parts = mutableListOf<String>()
        listOf("Colesterolo totale", "LDL", "HDL").forEach { label ->
            val pts = extracted.filter { it.parameterName == label }.sortedBy { it.dateEpochMillis }
            if (pts.size < 2) return@forEach
            val first = pts.first().numericValue ?: return@forEach
            val last = pts.last().numericValue ?: return@forEach
            val y1 = year(pts.first().dateEpochMillis)
            val y2 = year(pts.last().dateEpochMillis)
            val dir = when {
                last > first + 5 -> "in aumento"
                last < first - 5 -> "in diminuzione"
                else -> "stabile"
            }
            parts += "$label $dir tra $y1 (${first.toInt()} mg/dL) e $y2 (${last.toInt()} mg/dL)"
        }
        val bp = extracted.filter { it.kind == ExtractedValueKind.BLOOD_PRESSURE }.sortedBy { it.dateEpochMillis }
        if (bp.size >= 2) {
            val series = bp.joinToString(" → ") { "${year(it.dateEpochMillis)}: ${it.textValue.orEmpty()}" }
            parts += "Pressione sostanzialmente stabile ($series)"
        }
        val lesions = extracted.filter { it.kind == ExtractedValueKind.LESION && it.dimensionMm != null }
            .sortedBy { it.dateEpochMillis }
        if (lesions.size >= 2) {
            val f = lesions.first().dimensionMm!!
            val l = lesions.last().dimensionMm!!
            val dir = when {
                l > f + 1 -> "aumentata"
                l < f - 1 -> "diminuita"
                else -> "stabile"
            }
            parts += "Lesione focali: dimensione $dir (${f.toInt()} mm → ${l.toInt()} mm)"
        } else {
            lesions.lastOrNull()?.dimensionMm?.let { mm ->
                parts += "Lesione rilevata: ${mm.toInt()} mm (${year(lesions.last().dateEpochMillis)}) — confrontare con controlli successivi"
            }
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(". ")?.let { "$it." }
    }

    private fun bulletLinesFromExam(exam: KBMedicalExamEntity, extracted: List<ExtractedMedicalValue>): List<String> {
        val examExtracted = extracted.filter { it.sourceId == "exam:${exam.id}" || it.sourceLabel == exam.name }
        if (examExtracted.isNotEmpty()) {
            return examExtracted.take(10).mapNotNull { v ->
                when (v.kind) {
                    ExtractedValueKind.BLOOD_PRESSURE -> "• Profilo pressorio: ${v.textValue.orEmpty()}"
                    ExtractedValueKind.STRESS_TEST -> "• ${v.parameterName}: ${v.textValue.orEmpty()}"
                    ExtractedValueKind.HEART_RATE -> "• Frequenza cardiaca massima: ${v.textValue.orEmpty()}"
                    ExtractedValueKind.LESION -> v.dimensionMm?.let { "• ${v.lesionType ?: "Lesione"}: ${it.toInt()} mm" }
                    ExtractedValueKind.LAB -> "• ${v.parameterName}: ${v.textValue.orEmpty()}"
                    else -> null
                }
            }
        }
        return exam.resultText.orEmpty().lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .take(10)
            .map { s -> if (s.length > 100) "• ${s.take(99)}…" else "• $s" }
            .toList()
    }

    private fun buildHeaderArea(ctx: Context) = ClinicalRecordReportArea(
        "header", "Intestazione", ctx.subjectName,
        buildHeader(ctx).joinToString("\n"), null, emptyList(),
    )

    private fun therapyArea(treatments: List<KBTreatmentEntity>): ClinicalRecordReportArea {
        val lines = buildTherapiesSection(treatments)
        return ClinicalRecordReportArea(
            ClinicalRecordTopicBuilder.TopicId.THERAPIES.raw,
            "Terapie in corso",
            if (treatments.isEmpty()) "Nessuna cura" else "${treatments.size} in corso",
            lines.joinToString("\n"), null, lines.filter { it.startsWith("•") },
        )
    }

    private fun pathologyArea(ctx: Context): ClinicalRecordReportArea {
        val lines = buildPathologiesSection(ctx)
        val bullets = lines.filter { it.startsWith("•") }
        return ClinicalRecordReportArea(
            "pathologies", "Patologie e condizioni",
            if (bullets.isEmpty()) "Da referti" else "${bullets.size} elementi",
            lines.joinToString("\n"), compactLabTrend(ctx.extracted), bullets.take(5),
            ClinicalOverallStatus.DA_MONITORARE, null, null,
        )
    }

    private fun pendingArea(exams: List<KBMedicalExamEntity>): ClinicalRecordReportArea {
        val lines = buildPendingSection(exams)
        return ClinicalRecordReportArea(
            ClinicalRecordTopicBuilder.TopicId.PENDING.raw, "Esami in attesa",
            if (lines.isEmpty()) "Nessuno" else "Prenotati",
            lines.joinToString("\n"), null,
            lines.filter { it.firstOrNull()?.isDigit() == true },
        )
    }

    private fun recentExamsArea(ctx: Context): ClinicalRecordReportArea {
        val lines = buildRecentExamsSection(ctx)
        return ClinicalRecordReportArea(
            "recent_exams", "Ultimi esami", "Referti recenti",
            lines.joinToString("\n"), compactLabTrend(ctx.extracted),
            lines.filter { it.startsWith("•") }.take(5),
        )
    }

    private fun areaFromTrend(
        trend: SpecialtyTrendSnapshot,
        chronology: List<String>,
        ctx: Context,
    ): ClinicalRecordReportArea {
        val trendText = trend.parameters.joinToString("\n") { p ->
            val series = p.points.joinToString(" → ") { "${year(it.dateEpochMillis)}: ${it.displayValue}" }
            "• ${p.name}: $series"
        }
        var analisi = trend.narrativeAnalysis
        var narrative = trend.narrativeAnalysis
        var bullets = trend.parameters.take(3).map { "• ${it.name}: ${it.points.lastOrNull()?.displayValue.orEmpty()}" }
        var summary = trend.narrativeAnalysis.take(72)

        val topic = ClinicalRecordTopicBuilder.TopicId.entries.firstOrNull { it.raw == trend.specialtyId }
        if (topic == ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY || topic == ClinicalRecordTopicBuilder.TopicId.UROLOGY) {
            ClinicalRecordSpecialtyRefertoSynthesis.synthesize(
                topic, ctx.exams, ctx.visits, trend.parameters,
            )?.let { syn ->
                analisi = syn.synthesisParagraph
                narrative = syn.timelineDetail.ifBlank { chronology.take(8).joinToString("\n") }
                if (syn.highlights.isNotEmpty()) bullets = syn.highlights.map { "• $it" }
                summary = syn.synthesisParagraph.take(72)
            }
        } else if (chronology.isNotEmpty()) {
            narrative += "\n\n" + chronology.take(6).joinToString("\n")
        }

        return ClinicalRecordReportArea(
            trend.specialtyId, trend.specialtyTitle,
            summary, narrative, trendText.ifBlank { null },
            bullets, trend.overallStatus, analisi, trend.parameters,
        )
    }

    private fun chronologyFor(topic: ClinicalRecordTopicBuilder.TopicId, ctx: Context): List<String> {
        val vLines = ctx.visits.filter { matchesTopic(topic, visitText(it)) }
            .map { "• ${formatMonthYear(it.dateEpochMillis)} — ${it.reason}" }
        val eLines = ctx.exams.filter { matchesTopic(topic, it.name + " " + it.resultText.orEmpty()) }
            .map { "• ${formatMonthYear(it.resultDateEpochMillis ?: it.updatedAtEpochMillis)} — ${it.name}" }
        return vLines + eLines
    }

    private fun matchesTopic(topic: ClinicalRecordTopicBuilder.TopicId, text: String): Boolean {
        val t = text.lowercase(Locale.getDefault())
        return when (topic) {
            ClinicalRecordTopicBuilder.TopicId.BLOOD_PRESSURE ->
                t.contains("pressione") || t.contains("mmhg")
            ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY ->
                t.contains("cardio") || t.contains("sforzo") || t.contains("colester") ||
                    t.contains("ecocardio") || t.contains("coronar")
            ClinicalRecordTopicBuilder.TopicId.GASTROENTEROLOGY ->
                t.contains("gast") || t.contains("colon") || t.contains("epat") || t.contains("angiom")
            ClinicalRecordTopicBuilder.TopicId.UROLOGY ->
                t.contains("prostata") || t.contains("ren") || t.contains("urolog") ||
                    t.contains("psa") || t.contains("varicocele")
            ClinicalRecordTopicBuilder.TopicId.METABOLISM ->
                t.contains("glicemia") || t.contains("emocromo") || t.contains("sangue")
            else -> false
        }
    }

    private fun visitText(v: KBMedicalVisitEntity) =
        listOfNotNull(v.reason, v.diagnosis, v.recommendations).joinToString(" ")

    private fun hasResult(e: KBMedicalExamEntity): Boolean =
        !e.resultText.isNullOrBlank() ||
            e.statusRaw.contains("Risultato", ignoreCase = true) ||
            e.statusRaw.contains("Complet", ignoreCase = true)

    private fun isLabLike(exam: KBMedicalExamEntity): Boolean {
        val n = exam.name.lowercase(Locale.getDefault())
        val t = exam.resultText.orEmpty().lowercase(Locale.getDefault())
        return n.contains("sangue") || n.contains("emocromo") || n.contains("lipid") || n.contains("colester") ||
            t.contains("ldl") || t.contains("hdl") || t.contains("glicemia") || t.contains("creatinina")
    }

    private fun labQualitative(label: String, value: Double): String = when (label) {
        "LDL" -> when {
            value < 100 -> "ottimale"
            value < 130 -> "nei limiti"
            else -> "da valutare"
        }
        "HDL" -> when {
            value < 35 -> "alto rischio, < 35"
            value < 40 -> "basso"
            else -> "nei limiti"
        }
        "Colesterolo totale" -> if (value < 200) "normale" else "da valutare"
        "Glicemia" -> if (value in 70.0..100.0) "normale" else "da valutare"
        else -> "nei limiti"
    }

    private fun frequencyLabel(t: KBTreatmentEntity): String =
        if (t.intervalBetweenDosesDays > 0) "Ogni ${t.intervalBetweenDosesDays} giorni"
        else if (t.dailyFrequency == 1) "1 volta al giorno"
        else "${t.dailyFrequency} volte al giorno"

    private fun formatDosage(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(Locale.US, v)

    private fun formatDate(epoch: Long): String =
        DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(Date(epoch))

    private fun formatMonthYear(epoch: Long): String =
        SimpleDateFormat("MMMM yyyy", KBLocale.current()).format(Date(epoch))

    private fun year(epoch: Long): Int =
        Calendar.getInstance().apply { timeInMillis = epoch }.get(Calendar.YEAR)

    private fun formatNum(v: Double): String =
        if (v == v.toLong().toDouble()) v.toLong().toString() else "%.1f".format(Locale.US, v)

    private fun clip(text: String): String {
        val t = text.trim()
        return if (t.length <= 120) t else t.take(119) + "…"
    }
}
