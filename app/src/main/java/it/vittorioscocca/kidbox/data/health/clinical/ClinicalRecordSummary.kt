package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity
import it.vittorioscocca.kidbox.domain.health.HealthAgeFormatting
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.min

data class ClinicalRecordSnapshot(
    val subjectName: String,
    val ageDescription: String?,
    val refreshedAtEpochMillis: Long,
    val sections: List<ClinicalRecordSection>,
    val globalSummary: ClinicalRecordGlobalSummary? = null,
    val reportSourceAiEnhanced: Boolean = false,
) {
    val hasAnyData: Boolean get() = sections.any { !it.isEmpty }
}

data class ClinicalRecordSection(
    val id: String,
    val title: String,
    val iconKey: String,
    val tintArgb: Long,
    val badgeCount: Int?,
    val summary: String,
    val highlights: List<String>,
    val isEmpty: Boolean,
    val reportAreaId: String? = null,
    val overallStatus: ClinicalOverallStatus? = null,
)

object ClinicalRecordSummaryBuilder {

    private const val MAX_HIGHLIGHTS = 3

    private val topicOrder = listOf(
        ClinicalRecordAppleHealthNarrative.AREA_ID,
        ClinicalRecordTopicBuilder.TopicId.THERAPIES,
        "pathologies",
        ClinicalRecordTopicBuilder.TopicId.PENDING,
        "recent_exams",
        ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY,
        ClinicalRecordTopicBuilder.TopicId.GASTROENTEROLOGY,
        ClinicalRecordTopicBuilder.TopicId.UROLOGY,
        ClinicalRecordTopicBuilder.TopicId.METABOLISM,
    )

    fun build(
        subjectName: String,
        childBirthDateEpochMillis: Long?,
        profile: KBPediatricProfile?,
        healthSnapshot: HealthImportSnapshot?,
        healthSourceLabel: String,
        treatments: List<KBTreatmentEntity>,
        vaccines: List<KBVaccineEntity>,
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
        report: ClinicalRecordReport? = null,
    ): ClinicalRecordSnapshot {
        val age = childBirthDateEpochMillis?.let(HealthAgeFormatting::ageDescriptionFromBirth)
            ?: healthSnapshot?.ageDescription
        val activeTreatments = treatments.filter { it.isActive && !it.isDeleted && it.petId.isBlank() }

        var sections = mutableListOf<ClinicalRecordSection>()
        if (report != null) {
            for (areaId in topicOrder) {
                val id = areaIdString(areaId)
                if (!ClinicalRecordSectionPolicy.shouldGenerateStandaloneSection(id)) continue
                val area = report.areas.firstOrNull { it.id == id } ?: continue
                val empty = area.bullets.isEmpty() && area.narrative.isBlank()
                if (areaIdString(areaId) == ClinicalRecordTopicBuilder.TopicId.THERAPIES.raw &&
                    activeTreatments.isEmpty() && empty
                ) continue
                if (areaIdString(areaId) == ClinicalRecordTopicBuilder.TopicId.PENDING.raw && empty) continue
                if (empty) continue
                sections += sectionForArea(areaId, area)
            }
        }
        if (sections.isEmpty()) {
            sections = fallbackSections(activeTreatments, exams).toMutableList()
        }

        return ClinicalRecordSnapshot(
            subjectName = subjectName,
            ageDescription = age?.takeIf { it.isNotBlank() },
            refreshedAtEpochMillis = report?.generatedAtEpochMillis ?: System.currentTimeMillis(),
            sections = sections,
            globalSummary = report?.globalSummary,
            reportSourceAiEnhanced = report?.sourceAiEnhanced == true,
        )
    }

    private fun areaIdString(id: Any): String = when (id) {
        is ClinicalRecordTopicBuilder.TopicId -> id.raw
        is String -> id
        else -> id.toString()
    }

    private fun sectionForArea(
        areaId: Any,
        area: ClinicalRecordReportArea,
    ): ClinicalRecordSection {
        val topic = areaId as? ClinicalRecordTopicBuilder.TopicId
            ?: ClinicalRecordTopicBuilder.TopicId.entries.firstOrNull { it.raw == areaIdString(areaId) }
        if (topic != null) return topicSection(topic, area)
        val (icon, tint) = when (areaIdString(areaId)) {
            ClinicalRecordAppleHealthNarrative.AREA_ID -> "heart" to 0xFF34C759L
            "pathologies" -> "heart" to 0xFFE85A5AL
            "recent_exams" -> "exam" to 0xFF40A6BFL
            else -> "folder" to 0xFF5996D9L
        }
        val highlights = buildList {
            when {
                !area.analisiNarrativa.isNullOrBlank() -> add(clip(area.analisiNarrativa))
                !area.trendNarrative.isNullOrBlank() ->
                    area.trendNarrative.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }?.let { add(clip(it)) }
            }
            area.bullets.take(MAX_HIGHLIGHTS - size).forEach { add(clip(it)) }
        }
        return ClinicalRecordSection(
            id = area.id,
            title = area.title,
            iconKey = icon,
            tintArgb = tint,
            badgeCount = area.bullets.size.takeIf { it > 0 },
            summary = ClinicalRecordTextSanitizer.sanitize(area.summary),
            highlights = highlights.take(MAX_HIGHLIGHTS),
            isEmpty = false,
            reportAreaId = area.id,
            overallStatus = area.overallStatus,
        )
    }

    private fun topicSection(
        topic: ClinicalRecordTopicBuilder.TopicId,
        area: ClinicalRecordReportArea,
    ): ClinicalRecordSection {
        val highlights = buildList {
            when {
                !area.analisiNarrativa.isNullOrBlank() -> add(clip(area.analisiNarrativa))
                !area.trendNarrative.isNullOrBlank() -> {
                    area.trendNarrative.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() }?.let { add(clip(it)) }
                }
            }
            area.bullets.take(MAX_HIGHLIGHTS - size).forEach { add(clip(it)) }
        }
        return ClinicalRecordSection(
            id = topic.raw,
            title = topic.title,
            iconKey = topic.iconKey,
            tintArgb = topic.tint,
            badgeCount = area.bullets.size.takeIf { it > 0 },
            summary = ClinicalRecordTextSanitizer.sanitize(area.summary),
            highlights = highlights.take(MAX_HIGHLIGHTS),
            isEmpty = false,
            reportAreaId = topic.raw,
            overallStatus = area.overallStatus,
        )
    }

    private fun fallbackSections(
        treatments: List<KBTreatmentEntity>,
        exams: List<KBMedicalExamEntity>,
    ): List<ClinicalRecordSection> {
        val out = mutableListOf<ClinicalRecordSection>()
        if (treatments.isNotEmpty()) {
            val t = ClinicalRecordTopicBuilder.TopicId.THERAPIES
            out += ClinicalRecordSection(
                id = t.raw, title = t.title, iconKey = t.iconKey, tintArgb = t.tint,
                badgeCount = treatments.size, summary = "${treatments.size} in corso",
                highlights = treatments.take(3).map { "${it.drugName} · ${it.dosageValue} ${it.dosageUnit}" },
                isEmpty = false, reportAreaId = t.raw,
            )
        }
        val pending = exams.filter { it.statusRaw == "In attesa" || it.statusRaw == "Prenotato" }
        if (pending.isNotEmpty()) {
            val p = ClinicalRecordTopicBuilder.TopicId.PENDING
            out += ClinicalRecordSection(
                id = p.raw, title = p.title, iconKey = p.iconKey, tintArgb = p.tint,
                badgeCount = pending.size, summary = "${pending.size} da eseguire",
                highlights = pending.take(3).map { it.name },
                isEmpty = false, reportAreaId = p.raw,
            )
        }
        return out
    }

    private fun clip(text: String): String {
        val t = ClinicalRecordTextSanitizer.sanitize(text).trim()
        return if (t.length <= 72) t else t.take(71) + "…"
    }
}
