package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.health.HealthLinkStore
import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity
import it.vittorioscocca.kidbox.data.remote.ai.AiRepository
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile

object ClinicalRecordOrchestrator {

    data class Bundle(
        val report: ClinicalRecordReport,
        val snapshot: ClinicalRecordSnapshot,
        val exportLines: List<String>,
        val aiUsage: ClinicalRecordAIUsageInfo?,
    )

    suspend fun build(
        subjectName: String,
        birthMillis: Long?,
        residence: String?,
        profile: KBPediatricProfile?,
        health: HealthImportSnapshot?,
        healthLabel: String,
        treatments: List<KBTreatmentEntity>,
        vaccines: List<KBVaccineEntity>,
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
        documents: List<KBDocumentEntity>,
        useAI: Boolean,
        familyId: String,
        aiRepository: AiRepository,
    ): Bundle {
        val extracted = ClinicalRecordValueIndex.extractAll(exams, visits, documents)
        var report = filterExcludedAreas(
            ClinicalRecordNativeReportBuilder.build(
                subjectName = subjectName,
                birthMillis = birthMillis,
                residence = residence,
                profile = profile,
                health = health,
                healthLabel = healthLabel,
                treatments = treatments,
                vaccines = vaccines,
                visits = visits,
                exams = exams,
                documents = documents,
                extractedValues = extracted,
            ),
        )
        var aiUsage: ClinicalRecordAIUsageInfo? = null

        if (useAI) {
            val healthContext = ClinicalRecordHealthContextBuilder.buildClinicalPrompt(
                subjectName = subjectName,
                treatments = treatments.filter { it.isActive && it.petId.isBlank() },
                visits = visits,
                exams = exams,
                health = health,
                healthLabel = healthLabel,
                birthDateEpochMillis = birthMillis,
            )
            val enhanced = ClinicalRecordAISynthesizer.enhance(
                aiRepository = aiRepository,
                familyId = familyId,
                nativeReport = report,
                healthContext = healthContext,
            )
            aiUsage = enhanced.usage
            report = filterExcludedAreas(
                enhanced.report.copy(
                    generatedAtEpochMillis = System.currentTimeMillis(),
                    globalSummary = report.globalSummary,
                    specialtyTrends = report.specialtyTrends,
                    areas = report.areas,
                ),
            )
        }

        val snapshot = ClinicalRecordSummaryBuilder.build(
            subjectName = subjectName,
            childBirthDateEpochMillis = birthMillis,
            profile = profile,
            healthSnapshot = health,
            healthSourceLabel = healthLabel,
            treatments = treatments,
            vaccines = vaccines,
            visits = visits,
            exams = exams,
            report = report,
        )

        return Bundle(
            report = report,
            snapshot = snapshot,
            exportLines = report.fullDocumentLines,
            aiUsage = aiUsage,
        )
    }

    fun filterExcludedAreas(report: ClinicalRecordReport): ClinicalRecordReport =
        report.copy(areas = report.areas.filter { ClinicalRecordSectionPolicy.shouldGenerateStandaloneSection(it.id) })

    fun estimateAIMessageUnits(
        subjectName: String,
        birthMillis: Long?,
        residence: String?,
        profile: KBPediatricProfile?,
        health: HealthImportSnapshot?,
        healthLabel: String,
        treatments: List<KBTreatmentEntity>,
        vaccines: List<KBVaccineEntity>,
        visits: List<KBMedicalVisitEntity>,
        exams: List<KBMedicalExamEntity>,
        documents: List<KBDocumentEntity>,
    ): ClinicalRecordAISynthesizer.PayloadEstimate? {
        val extracted = ClinicalRecordValueIndex.extractAll(exams, visits, documents)
        val native = filterExcludedAreas(
            ClinicalRecordNativeReportBuilder.build(
                subjectName = subjectName,
                birthMillis = birthMillis,
                residence = residence,
                profile = profile,
                health = health,
                healthLabel = healthLabel,
                treatments = treatments,
                vaccines = vaccines,
                visits = visits,
                exams = exams,
                documents = documents,
                extractedValues = extracted,
            ),
        )
        val healthContext = ClinicalRecordHealthContextBuilder.buildClinicalPrompt(
            subjectName = subjectName,
            treatments = treatments.filter { it.isActive && it.petId.isBlank() },
            visits = visits,
            exams = exams,
            health = health,
            healthLabel = healthLabel,
            birthDateEpochMillis = birthMillis,
        )
        return ClinicalRecordAISynthesizer.estimatePayload(native, healthContext)
    }
}
