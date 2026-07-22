package it.vittorioscocca.kidbox.data.health.ai

import it.vittorioscocca.kidbox.ui.screens.ai.planning.PlanningAIActionBlock

enum class HealthContextSendMode {
    FULL_ACCURACY,
    COMPACT_SUMMARY,
}

/** Preferenza utente per la chat Salute (Impostazioni AI + scelta nel dialog). */
enum class HealthContextSendPreference(val storageValue: String) {
    ASK_EACH_TIME("ask_each_time"),
    FULL_ACCURACY("full_accuracy"),
    COMPACT_SUMMARY("compact_summary"),
    ;

    @get:androidx.annotation.StringRes
    val displayNameRes: Int
        get() = when (this) {
            ASK_EACH_TIME -> it.vittorioscocca.kidbox.R.string.settings_ai_ctx_ask_title
            FULL_ACCURACY -> it.vittorioscocca.kidbox.R.string.settings_ai_ctx_full_title
            COMPACT_SUMMARY -> it.vittorioscocca.kidbox.R.string.settings_ai_ctx_summary_title
        }

    @get:androidx.annotation.StringRes
    val detailRes: Int
        get() = when (this) {
            ASK_EACH_TIME -> it.vittorioscocca.kidbox.R.string.settings_ai_ctx_ask_detail
            FULL_ACCURACY -> it.vittorioscocca.kidbox.R.string.settings_ai_ctx_full_detail
            COMPACT_SUMMARY -> it.vittorioscocca.kidbox.R.string.settings_ai_ctx_summary_detail
        }

    val sendMode: HealthContextSendMode?
        get() = when (this) {
            ASK_EACH_TIME -> null
            FULL_ACCURACY -> HealthContextSendMode.FULL_ACCURACY
            COMPACT_SUMMARY -> HealthContextSendMode.COMPACT_SUMMARY
        }

    companion object {
        fun fromStorage(value: String?): HealthContextSendPreference =
            entries.firstOrNull { it.storageValue == value } ?: ASK_EACH_TIME

        fun fromSendMode(mode: HealthContextSendMode): HealthContextSendPreference =
            when (mode) {
                HealthContextSendMode.FULL_ACCURACY -> FULL_ACCURACY
                HealthContextSendMode.COMPACT_SUMMARY -> COMPACT_SUMMARY
            }
    }
}

object HealthContextCompaction {

    val SUMMARIZATION_SYSTEM_PROMPT: String = """
Sei un assistente che comprime dati sanitari per uso come contesto di un'altra AI.
Riassumi fedelmente il testo seguente mantenendo:
- cure attive e dosaggi
- vaccini e date rilevanti
- visite, diagnosi, raccomandazioni ed esami prescritti
- risultati e valori chiave citati nei referti
- scadenze urgenti o esami in attesa
Non inventare dati. Usa elenchi chiari. Rispondi solo con il riassunto, in italiano.
    """.trimIndent()

    fun buildCompactSystemPrompt(summary: String, subjectName: String): String {
        val trimmed = summary.trim()
        return """
Sei un assistente medico informativo integrato in KidBox, pensato per genitori.
Stai assistendo $subjectName. Il contesto sanitario completo è stato riassunto per limiti tecnici: se manca un dettaglio, chiedi all'utente o indica il limite.
Usa un linguaggio semplice. Ricorda di consultare il medico per pareri clinici vincolanti. Rispondi in italiano.

--- CONTESTO SANITARIO (RIASSUNTO) ---
$trimmed

--- FINE RIASSUNTO ---
${PlanningAIActionBlock.promptSection}
        """.trimIndent()
    }
}
