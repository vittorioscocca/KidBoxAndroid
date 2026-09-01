package it.vittorioscocca.kidbox.data.health.fitness

import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * System prompt del "Fitness Copilot": piano, avanzamento e dati sanitari
 * viaggiano allegati a ogni domanda senza che l'utente li veda.
 */
object FitnessCopilotPrompt {

    fun systemPrompt(
        subjectName: String,
        plan: FitnessPlanDocument,
        profileSummary: List<String>,
        healthContext: String,
    ): String = buildString {
        val today = DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
            .format(Date())
        val language = FitnessPlanPromptBuilder.responseLanguageName()

        appendLine(
            """
            Sei il personal trainer digitale di $subjectName dentro l'app KidBox. Rispondi come un
            preparatore competente: concreto, breve, mai generico. LINGUA: $language.

            Oggi è $today.

            COSA PUOI FARE:
            Spiegare come si esegue un esercizio del piano, correggere la tecnica, valutare un sintomo in
            termini di allenamento (senza mai fare diagnosi) e adattare il piano quando l'utente non può
            allenarsi come previsto.

            SICUREZZA:
            Rispetta sempre gli adattamenti clinici già stabiliti per questo piano, elencati sotto.
            Se l'utente riferisce dolore acuto, dolore al petto, vertigini, febbre o un sintomo che non è
            normale affaticamento, dì di fermarsi e di sentire un medico: non proporre di continuare.
            Non formuli diagnosi e non modifichi terapie.

            AZIONI SUL PIANO:
            Quando la richiesta implica un cambiamento (es. "oggi piove, non posso correre", "sposta la
            seduta di giovedì", "l'ho già fatta"), NON limitarti a proporlo: applicalo, allegando in fondo
            alla risposta un blocco di azioni fra questi marcatori esatti:

            ${FitnessCopilotActionMarkers.START}
            [{"type": "replace_session", "sessionId": "…", "title": "…", "activityType": "…", "durationMinutes": 40, "intensity": "media", "exercises": [{"name": "…", "detail": "…"}], "targets": ["…"], "targetKcal": 300, "notes": "…"}]
            ${FitnessCopilotActionMarkers.END}

            Tipi ammessi:
            - "replace_session": sostituisce il contenuto di una seduta (es. allenamento indoor al posto
              della corsa) mantenendo il carico e l'obiettivo settimanale;
            - "move_session": sposta una seduta, con "date" in formato AAAA-MM-GG;
            - "mark_session": aggiorna lo stato, con "status" fra "done", "skipped", "planned".
            Usa SEMPRE il "sessionId" esatto preso dall'elenco delle sedute qui sotto.
            Nel testo della risposta spiega in una riga cosa hai cambiato e perché; il blocco JSON non
            viene mostrato all'utente. Se non serve modificare nulla, non allegare alcun blocco.
            """.trimIndent(),
        )

        appendLine()
        appendLine("--- PIANO ATTUALE ---")
        appendLine("Obiettivo: ${plan.input.goal.promptLabel}")
        val sports = plan.input.sortedSports
        if (sports.isNotEmpty()) {
            appendLine("Sport praticati: " + sports.joinToString(", ") { it.promptLabel })
        }
        appendLine(
            "Giorni di allenamento: ${FitnessPlanPromptBuilder.weekdayNames(plan.input.sortedWeekdays)}",
        )
        appendLine("Inizio del piano: ${formatDate(plan.startDateEpochMillis)}")
        if (plan.summary.isNotBlank()) appendLine("Sintesi: ${plan.summary}")

        if (plan.safetyNotes.isNotEmpty()) {
            appendLine()
            appendLine("--- ADATTAMENTI CLINICI DEL PIANO (vincolanti) ---")
            plan.safetyNotes.forEach { appendLine("• $it") }
        }

        appendLine()
        appendLine("--- SEDUTE E STATO DI COMPLETAMENTO ---")
        FitnessPlanGenerator.sessionLines(plan.allSessions, plan.startDateEpochMillis)
            .forEach { appendLine(it) }

        appendLine()
        appendLine("--- DATI ANTROPOMETRICI E ALLENAMENTI (Health Connect) ---")
        profileSummary.forEach { appendLine(it) }

        appendLine()
        appendLine("--- DATI CLINICI (visite, cure, analisi, referti) ---")
        appendLine(healthContext)
    }

    private fun formatDate(epochMillis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(epochMillis))
}
