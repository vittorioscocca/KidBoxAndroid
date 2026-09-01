package it.vittorioscocca.kidbox.data.health.fitness

import it.vittorioscocca.kidbox.data.health.mealplan.MealPlanPromptBuilder
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import java.text.DateFormat
import java.util.Date
import java.util.Locale

/**
 * System prompt e contenuto utente per il Piano Fitness AI.
 * Come gli altri builder di Salute il prompt resta in italiano: la lingua della
 * RISPOSTA (i testi che l'utente legge nel piano) è imposta a parte.
 *
 * A differenza del Piano Alimentare qui l'AI deve rispondere in JSON: il piano
 * popola un calendario con stati, promemoria e riconciliazione con Health
 * Connect, quindi la prosa non basta.
 */
object FitnessPlanPromptBuilder {

    /**
     * Referti allegati: stesso tetto del Piano Alimentare — serve il quadro
     * clinico (controindicazioni), non il referto integrale.
     */
    const val REFERTO_MAX_CHARS = 1_200

    /** Quante settimane genera il piano base. */
    const val PLAN_WEEKS = 4

    fun systemPrompt(responseLanguage: String): String = """
        Agisci come un preparatore atletico basato sull'evidenza, integrato nell'app KidBox.
        Costruisci un piano di allenamento mensile personalizzato leggendo i dati sanitari della
        persona: età, peso, altezza, BMI, referti, patologie in corso, terapie farmacologiche,
        parametri biometrici e allenamenti già registrati.

        LINGUA: $responseLanguage. Scrivi in questa lingua TUTTI i testi destinati all'utente
        (titoli, esercizi, obiettivi, note). Le CHIAVI del JSON restano in inglese come da schema.

        SICUREZZA CLINICA — è la regola che viene prima di tutte le altre:
        Adatta intensità, esercizi e volumi alle controindicazioni che emergono dai dati.
        Esempi di ragionamento richiesto: con ernia discale o lombalgia niente carichi assiali sulla
        colonna (stacchi, squat con bilanciere, military press in piedi) e preferenza per lavoro in
        scarico; con terapie che alterano la frequenza cardiaca (beta-bloccanti, antiaritmici) niente
        lavoro ad alta intensità e sforzo regolato sulla percezione invece che sui battiti; con
        patologie cardiovascolari, respiratorie, metaboliche o articolari riduci l'impatto e la
        progressione; in gravidanza o allattamento niente lavoro ad alta intensità o supino prolungato.
        Ogni adattamento che fai per un motivo clinico DEVE comparire in "safetyNotes", citando il dato
        che lo ha motivato. Se non emergono controindicazioni, scrivilo esplicitamente in una nota.
        Se la persona ha meno di 18 anni, proponi solo attività ludico-motoria e rimanda al pediatra.
        NON formulare diagnosi e NON inventare valori clinici assenti dai dati.

        COSTRUZIONE DEL PIANO:
        Genera esattamente $PLAN_WEEKS settimane, con progressione settimanale sensata (carico che
        cresce e una settimana di scarico se il volume è alto).
        Allena SOLO nei giorni indicati come disponibili: ogni sessione deve avere un "dayOffset"
        compreso nell'elenco di offset ammessi fornito nel messaggio utente. Non inventare altri giorni.
        Ogni sessione deve avere esercizi o attività concrete e obiettivi MISURABILI (minuti, distanza,
        calorie, serie × ripetizioni, ritmo). Niente obiettivi generici tipo "allenati bene".
        Rispetta la durata indicata per sessione, con una tolleranza di ±10 minuti.
        Se la persona indica degli sport, quelli sono la materia del piano: le sedute devono essere
        fatte di quelle attività, non di un generico circuito in palestra. Vale per ogni obiettivo,
        anche quando non c'è nessuna gara: chi vuole solo tonicità e salute e indica tennis e bici
        deve ritrovarsi tennis e bici nel calendario, con il lavoro complementare che serve a
        sostenerli. Se gli sport indicati non bastano a coprire l'obiettivo, aggiungi il minimo
        necessario e spiega in "notes" perché.
        Se l'obiettivo è una gara, struttura il mese come un blocco di preparazione verso quella data.

        FORMATO DELLA RISPOSTA — obbligatorio:
        Rispondi con UN SOLO oggetto JSON valido, senza testo prima o dopo, senza Markdown, senza
        blocchi di codice. Nessun commento. Usa esattamente queste chiavi:

        {
          "summary": "3-4 frasi sul piano e sulla logica di progressione",
          "safetyNotes": ["adattamenti clinici, uno per stringa"],
          "weeks": [
            {
              "index": 1,
              "focus": "obiettivo della settimana in una riga",
              "sessions": [
                {
                  "dayOffset": 0,
                  "title": "titolo breve della seduta",
                  "activityType": "corsa | forza | mobilità | cardio | riposo attivo",
                  "durationMinutes": 45,
                  "intensity": "bassa | media | alta",
                  "exercises": [
                    {"name": "nome esercizio", "detail": "3 serie x 12 ripetizioni", "notes": "opzionale"}
                  ],
                  "targets": ["obiettivo misurabile 1", "obiettivo misurabile 2"],
                  "targetKcal": 350,
                  "notes": "nota breve, opzionale"
                }
              ]
            }
          ]
        }

        LUNGHEZZA: massimo 4 esercizi e 3 obiettivi per sessione, testi brevi. L'intero JSON deve
        restare sotto le 1800 parole: meglio sessioni asciutte che un JSON troncato a metà, che il
        client non riuscirebbe a leggere. Devi arrivare fino alla chiusura del JSON.
    """.trimIndent()

    fun userContent(
        subjectName: String,
        input: FitnessPlanInput,
        startDateEpochMillis: Long,
        allowedDayOffsets: List<Int>,
        profileSummary: List<String>,
        healthContext: String,
    ): String = buildString {
        appendLine("Crea il piano di allenamento mensile per $subjectName.")
        appendLine()
        appendLine("--- OBIETTIVO E DISPONIBILITÀ ---")
        appendLine("Obiettivo principale: ${input.goal.promptLabel}")

        val sports = input.sortedSports
        if (sports.isEmpty()) {
            appendLine("Sport preferiti: non indicati, scegli tu le attività più adatte all'obiettivo.")
        } else {
            appendLine(
                "Sport che la persona vuole praticare: " +
                    sports.joinToString(", ") { it.promptLabel },
            )
            appendLine(
                "Costruisci le sedute attorno a questi sport. Aggiungi forza, mobilità o cardio " +
                    "solo dove servono per completare l'obiettivo o per prevenire gli infortuni tipici " +
                    "di queste discipline, spiegandolo nella seduta.",
            )
        }

        if (input.goal == FitnessGoal.RACE) {
            val race = StringBuilder("Tipo di gara/evento: ")
            race.append(input.raceType?.racePromptLabel ?: "non specificato")
            input.raceDetail.trim().takeIf { it.isNotBlank() }?.let { race.append(" — ").append(it) }
            appendLine(race.toString())
            val raceDate = input.raceDateEpochMillis
            if (raceDate != null) {
                val weeks = (FitnessPlanDates.daysBetween(System.currentTimeMillis(), raceDate) / 7)
                    .coerceAtLeast(0)
                appendLine("Data della gara: ${formatDate(raceDate)} (tra circa $weeks settimane)")
            } else {
                appendLine("Data della gara: non indicata, imposta una preparazione generica.")
            }
        }

        appendLine("Esperienza: ${input.experience.promptLabel}")
        appendLine("Luogo di allenamento: ${input.place.promptLabel}")
        appendLine("Durata per sessione: circa ${input.sessionMinutes} minuti")
        appendLine("Giorni disponibili: ${weekdayNames(input.sortedWeekdays)}")
        appendLine("Inizio del piano: ${formatDate(startDateEpochMillis)} (dayOffset 0)")
        appendLine(
            "Offset dei giorni ammessi (giorni trascorsi dall'inizio del piano): " +
                allowedDayOffsets.joinToString(", "),
        )

        input.notes.trim().takeIf { it.isNotBlank() }?.let {
            appendLine("Note dell'utente (infortuni, limiti, preferenze): $it")
        }

        appendLine()
        appendLine("--- DATI ANTROPOMETRICI E ALLENAMENTI (Health Connect) ---")
        if (profileSummary.isEmpty()) {
            appendLine("Nessun dato antropometrico disponibile.")
        } else {
            profileSummary.forEach { appendLine(it) }
        }

        appendLine()
        appendLine("--- DATI CLINICI (visite, cure, analisi, referti) ---")
        appendLine(healthContext)
    }

    /**
     * Prompt breve per la logica "Sposta": costa poco perché non rimanda il
     * contesto clinico completo, solo le sedute della settimana.
     */
    fun rescheduleSystemPrompt(responseLanguage: String): String = """
        Sei il preparatore atletico dell'app KidBox. L'utente ha spostato una seduta.
        Riorganizza SOLO le sedute rimanenti della settimana indicata, senza aumentare il carico
        totale e senza mettere due sedute intense di fila. Non toccare le sedute già completate.
        LINGUA dei testi: $responseLanguage.

        Rispondi con UN SOLO oggetto JSON valido, niente testo attorno, niente Markdown:
        {
          "rationale": "una riga sul criterio usato",
          "sessions": [
            {
              "id": "id della seduta esistente",
              "dayOffset": 3,
              "title": "…",
              "activityType": "…",
              "durationMinutes": 45,
              "intensity": "…",
              "exercises": [{"name": "…", "detail": "…"}],
              "targets": ["…"],
              "targetKcal": 300,
              "notes": "…"
            }
          ]
        }
        Includi solo le sedute che cambiano, con l'id identico a quello ricevuto.
    """.trimIndent()

    /** Prompt breve per la proposta di adeguamento di fine settimana. */
    fun weeklyAdjustSystemPrompt(responseLanguage: String): String = """
        Sei il preparatore atletico dell'app KidBox. Analizza l'andamento della settimana appena
        conclusa e proponi come impostare la settimana successiva. Tieni conto dei giorni saltati in
        modo sistematico, dei dati biometrici e delle controindicazioni cliniche già note.
        LINGUA dei testi: $responseLanguage.

        Rispondi con UN SOLO oggetto JSON valido, niente testo attorno, niente Markdown:
        {
          "rationale": "2-3 frasi sul perché di queste modifiche",
          "changes": ["modifica proposta 1", "modifica proposta 2"],
          "sessions": [
            {
              "id": "id della seduta da riscrivere",
              "dayOffset": 10,
              "title": "…",
              "activityType": "…",
              "durationMinutes": 45,
              "intensity": "…",
              "exercises": [{"name": "…", "detail": "…"}],
              "targets": ["…"],
              "targetKcal": 300,
              "notes": "…"
            }
          ]
        }
        Includi solo le sedute della settimana successiva che vuoi modificare, con l'id ricevuto.
        Se non serve cambiare nulla, restituisci "sessions": [] spiegando il perché in "rationale".
    """.trimIndent()

    /**
     * Righe compatte su età, altezza, peso e allenamenti recenti.
     * Riusa il builder del Piano Alimentare: la fotografia antropometrica è la stessa.
     */
    fun profileSummaryLines(
        birthDateEpochMillis: Long?,
        snapshot: HealthImportSnapshot?,
        profile: KBPediatricProfile?,
        input: FitnessPlanInput,
    ): List<String> {
        val lines = MealPlanPromptBuilder.profileSummaryLines(
            birthDateEpochMillis = birthDateEpochMillis,
            snapshot = snapshot,
            profile = profile,
            manualAge = input.manualAgeValue,
            manualWeight = input.manualWeightValue,
            manualHeight = input.manualHeightValue,
        ).toMutableList()
        bmiLine(snapshot, input)?.let { lines += it }
        return lines
    }

    private fun bmiLine(snapshot: HealthImportSnapshot?, input: FitnessPlanInput): String? {
        val weight = snapshot?.weightKg ?: input.manualWeightValue ?: return null
        val heightCm = snapshot?.heightCm ?: input.manualHeightValue ?: return null
        if (heightCm <= 0) return null
        val heightM = heightCm / 100.0
        val bmi = weight / (heightM * heightM)
        return String.format(Locale.getDefault(), "BMI calcolato: %.1f", bmi)
    }

    fun weekdayNames(weekdays: List<Int>): String {
        val symbols = java.text.DateFormatSymbols.getInstance(Locale.getDefault()).weekdays
        val names = weekdays.mapNotNull { symbols.getOrNull(it)?.takeIf { name -> name.isNotBlank() } }
        return if (names.isEmpty()) "nessuno" else names.joinToString(", ")
    }

    /** Nome (in italiano) della lingua in cui l'AI deve rispondere: segue la lingua dell'app. */
    fun responseLanguageName(): String = MealPlanPromptBuilder.responseLanguageName()

    private fun formatDate(epochMillis: Long): String =
        DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault()).format(Date(epochMillis))

    /** Offset (giorni dall'inizio) delle giornate in cui l'utente si allena. */
    fun allowedDayOffsets(input: FitnessPlanInput, startDateEpochMillis: Long): List<Int> {
        val total = PLAN_WEEKS * 7
        return (0 until total).filter { offset ->
            val day = FitnessPlanDates.plusDays(startDateEpochMillis, offset)
            FitnessPlanDates.weekdayOf(day) in input.trainingWeekdays
        }
    }

    /** Il calendario parte da oggi: le settimane sono blocchi scorrevoli di 7 giorni. */
    fun planStartDate(): Long = FitnessPlanDates.today()
}
