package it.vittorioscocca.kidbox.data.health.clinical

/** Regole sezioni cartella clinica (allineato a iOS). */
object ClinicalRecordSectionPolicy {

    val standaloneExcludedIds: Set<String> = setOf(
        ClinicalRecordTopicBuilder.TopicId.BLOOD_PRESSURE.raw,
        "pressione_arteriosa",
    )

    val dynamicSpecialtyTopics: List<ClinicalRecordTopicBuilder.TopicId> = listOf(
        ClinicalRecordTopicBuilder.TopicId.CARDIOLOGY,
        ClinicalRecordTopicBuilder.TopicId.GASTROENTEROLOGY,
        ClinicalRecordTopicBuilder.TopicId.UROLOGY,
        ClinicalRecordTopicBuilder.TopicId.METABOLISM,
    )

    fun shouldGenerateStandaloneSection(id: String): Boolean = id !in standaloneExcludedIds
}
