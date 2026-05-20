package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.data.local.entity.KBTreatmentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBVaccineEntity
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile

/** Report integrato per argomento clinico — allineato a iOS. */
object ClinicalRecordNativeReportBuilder {

    fun build(
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
        documents: List<it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity> = emptyList(),
        extractedValues: List<ExtractedMedicalValue> = emptyList(),
    ): ClinicalRecordReport = ClinicalRecordTopicBuilder.build(
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
        extractedValues = extractedValues,
    )
}
