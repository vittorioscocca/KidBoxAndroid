package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.local.entity.KBDocumentEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalExamEntity
import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity

object ClinicalRecordValueIndex {

    fun extractAll(
        exams: List<KBMedicalExamEntity>,
        visits: List<KBMedicalVisitEntity>,
        documents: List<KBDocumentEntity>
    ): List<ExtractedMedicalValue> {
        val all = mutableListOf<ExtractedMedicalValue>()
        exams.forEach { e ->
            val text = listOf(e.name, e.resultText.orEmpty()).joinToString("\n")
            val date = e.resultDateEpochMillis ?: e.deadlineEpochMillis ?: e.updatedAtEpochMillis
            all += MedicalValueExtractor.extract(text, "exam:${e.id}", e.name, date)
        }
        visits.forEach { v ->
            val text = listOfNotNull(v.reason, v.diagnosis, v.recommendations, v.notes).joinToString("\n")
            all += MedicalValueExtractor.extract(text, "visit:${v.id}", v.reason.ifBlank { "Visita" }, v.dateEpochMillis)
        }
        documents.filter { !it.extractedText.isNullOrBlank() }.forEach { d ->
            all += MedicalValueExtractor.extract(
                d.extractedText.orEmpty(),
                "doc:${d.id}",
                d.title,
                d.extractedTextUpdatedAtEpochMillis ?: d.updatedAtEpochMillis
            )
        }
        return all.sortedBy { it.dateEpochMillis }
    }
}
