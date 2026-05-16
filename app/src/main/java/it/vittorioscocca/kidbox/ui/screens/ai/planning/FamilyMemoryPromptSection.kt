package it.vittorioscocca.kidbox.ui.screens.ai.planning

/**
 * Sezione "## Memoria famiglia" da appendere ai system prompt AI (allineata a PlanningContextBuilder).
 */
object FamilyMemoryPromptSection {

    suspend fun append(basePrompt: String, familyMemoryService: FamilyMemoryService, familyId: String): String {
        if (familyId.isBlank()) return basePrompt
        val memFacts = familyMemoryService.fetchFactTexts(familyId)
        if (memFacts.isEmpty()) return basePrompt
        return buildString {
            append(basePrompt.trimEnd())
            appendLine()
            appendLine()
            appendLine("## Memoria famiglia (fatti appresi dalle conversazioni precedenti)")
            memFacts.forEach { fact -> appendLine("• $fact") }
            appendLine()
            append(
                "Usa questi fatti per personalizzare le risposte senza menzionare esplicitamente " +
                    "che li hai memorizzati, a meno che non sia rilevante.",
            )
        }
    }
}
