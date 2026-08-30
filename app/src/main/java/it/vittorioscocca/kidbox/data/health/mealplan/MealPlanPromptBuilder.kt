package it.vittorioscocca.kidbox.data.health.mealplan

import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import it.vittorioscocca.kidbox.domain.model.HealthWorkoutEntry
import it.vittorioscocca.kidbox.domain.model.KBPediatricProfile
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/**
 * System prompt e contenuto utente per la generazione AI del piano alimentare.
 * Il prompt resta in italiano (come gli altri builder Salute): la lingua della
 * RISPOSTA è imposta esplicitamente.
 */
object MealPlanPromptBuilder {

    /** Referti allegati: tetto più basso della chat Salute — serve il quadro clinico, non il referto integrale. */
    const val REFERTO_MAX_CHARS = 1_200

    fun systemPrompt(responseLanguage: String): String = """
        Agisci come un coach di nutrizione e fitness basato sull'evidenza, integrato nell'app KidBox.
        Analizzi età, altezza, peso, livello di attività, allenamenti, alimentazione, visite mediche,
        cure in corso ed esami di laboratorio della persona per costruire un piano alimentare pratico.

        LINGUA DELLA RISPOSTA: $responseLanguage. Scrivi TUTTO il piano in questa lingua.

        COSA DEVI PRODURRE, IN QUEST'ORDINE:
        1) STIMA CALORICA — stima le calorie di mantenimento a partire da età, altezza, peso, livello di
        attività e allenamenti registrati, poi definisci un deficit (o surplus) calorico realistico
        coerente con l'obiettivo. Usa INTERVALLI, non falsa precisione. Aggiungi una riga su come
        adattare le calorie alle variazioni settimanali del peso. Massimo 6 righe.
        2) OBIETTIVI DI MACRONUTRIENTI — proteine, carboidrati e grassi come intervalli giornalieri, più
        una riga sul perché di quella ripartizione. Massimo 5 righe.
        3) PIANO DEI PASTI — UNA sola giornata tipo (colazione, pranzo, cena, 1-2 spuntini) sul target
        calorico stimato, costruita con gli alimenti graditi. Per ogni pasto una riga con porzioni e
        calorie, e una riga con proteine/carboidrati/grassi. Per ogni pasto UNA sola alternativa
        equivalente, su una riga. Se ci sono allenamenti, aggiungi 2 righe su cosa mangiare prima e dopo.
        Massimo 30 righe in tutto.
        4) IDRATAZIONE — acqua e sali, adattati agli allenamenti. Massimo 4 righe.
        5) LISTA DELLA SPESA — solo gli alimenti della giornata tipo, raggruppati per reparto, una riga
        per reparto con gli alimenti separati da virgola. Massimo 8 righe.
        6) PIANO 90 GIORNI — tre blocchi (mese 1, mese 2, mese 3), massimo 3 righe ciascuno, con calorie,
        proteine, allenamento e obiettivo intermedio del mese.
        7) NOTE DI SALUTE — come condizioni cliniche, cure in corso, allergie e valori di laboratorio
        presenti nei dati influenzano il piano. Se un dato manca, dillo. Massimo 6 righe.

        LUNGHEZZA:
        L'INTERO piano deve stare in circa 1200 parole. È un vincolo, non un suggerimento: meglio una
        sezione asciutta che un piano tagliato a metà. Scrivi frasi brevi, niente introduzioni, niente
        riepiloghi di quanto hai appena scritto, niente ripetizioni delle regole tra una sezione e l'altra.
        Devi arrivare fino in fondo alla sezione 7: se stai correndo lungo, accorcia le sezioni successive.

        REGOLE ASSOLUTE:
        Il piano deve essere economico, saziante, bilanciato e realistico da seguire per 90 giorni.
        Dai priorità a un progresso sostenibile, al mantenimento della massa muscolare e alla salute generale.
        NON raccomandare diete estreme, restrizioni eccessive, digiuni prolungati o metodi pericolosi.
        NON inventare valori clinici assenti dai dati forniti.
        Rispetta sempre allergie, intolleranze e alimenti da evitare indicati.
        Se la persona ha meno di 18 anni, è in gravidanza o in allattamento, NON generare un piano
        ipocalorico: fornisci solo indicazioni educative sull'equilibrio dei pasti e rimanda al
        pediatra o allo specialista.
        Chiudi ricordando che il piano è educativo e va validato dal medico o dal nutrizionista curante.

        FORMATO:
        Titoli di sezione in MAIUSCOLO su una riga sola, esattamente nell'ordine sopra.
        Sotto ogni titolo usa testo semplice; per i pasti sono ammessi elenchi brevi con "-".
        Vietato Markdown: niente asterischi, cancelletti, backtick o tabelle.
    """.trimIndent()

    fun userContent(
        subjectName: String,
        input: MealPlanInput,
        profileSummary: List<String>,
        healthContext: String,
    ): String = buildString {
        appendLine("Crea il piano alimentare per $subjectName.")
        appendLine()
        appendLine("--- OBIETTIVO E PREFERENZE ---")
        appendLine("Obiettivo: ${input.goal.promptLabel}")
        appendLine("Livello di attività dichiarato: ${input.activityLevel.promptLabel}")
        val preferred = input.preferredFoods.trim()
        appendLine(
            if (preferred.isEmpty()) {
                "Alimenti graditi: non indicati, usa alimenti comuni, economici e sazianti."
            } else {
                "Alimenti graditi: $preferred"
            },
        )
        input.avoidedFoods.trim().takeIf { it.isNotEmpty() }?.let {
            appendLine("Alimenti da evitare / intolleranze: $it")
        }
        input.notes.trim().takeIf { it.isNotEmpty() }?.let {
            appendLine("Note aggiuntive: $it")
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

    /** Righe compatte su età, altezza, peso e allenamenti recenti. */
    fun profileSummaryLines(
        birthDateEpochMillis: Long?,
        snapshot: HealthImportSnapshot?,
        profile: KBPediatricProfile?,
        input: MealPlanInput = MealPlanInput(),
    ): List<String> {
        val lines = mutableListOf<String>()

        val birth = birthDateEpochMillis ?: snapshot?.birthDateEpochMillis
        val manualAge = input.manualAgeValue
        lines += when {
            birth != null -> "Età: ${yearsSince(birth)} anni"
            manualAge != null -> "Età: $manualAge anni (indicata dall'utente)"
            else -> "Età: non disponibile"
        }

        val height = snapshot?.heightCm
        val manualHeight = input.manualHeightValue
        lines += when {
            height != null -> "Altezza: ${height.roundToInt()} cm"
            manualHeight != null -> "Altezza: ${manualHeight.roundToInt()} cm (indicata dall'utente)"
            else -> "Altezza: non disponibile"
        }

        val weight = snapshot?.weightKg
        val manualWeight = input.manualWeightValue
        lines += when {
            weight != null -> "Peso: ${String.format(Locale.getDefault(), "%.1f", weight)} kg"
            manualWeight != null ->
                "Peso: ${String.format(Locale.getDefault(), "%.1f", manualWeight)} kg (indicato dall'utente)"
            else -> "Peso: non disponibile"
        }

        (profile?.bloodGroup ?: snapshot?.bloodGroup)?.takeIf { it.isNotBlank() }?.let {
            lines += "Gruppo sanguigno: $it"
        }
        profile?.allergies?.takeIf { it.isNotBlank() }?.let { lines += "Allergie registrate: $it" }
        profile?.medicalNotes?.takeIf { it.isNotBlank() }?.let { lines += "Note mediche: $it" }

        snapshot?.let { snap ->
            snap.stepsDailyAvg90d?.let { lines += "Passi medi giornalieri (90 giorni): ${it.roundToInt()}" }
                ?: snap.stepsToday?.let { lines += "Passi di oggi: $it" }
            snap.weeklyExerciseMinutesAvg?.let {
                lines += "Minuti di attività settimanali (media): ${it.roundToInt()}"
            }
            snap.activeEnergyKcal?.let { lines += "Energia attiva recente: ${it.roundToInt()} kcal" }
            (snap.vo2MaxRecent ?: snap.vo2Max)?.let {
                lines += "VO2 max: ${String.format(Locale.getDefault(), "%.1f", it)}"
            }
            (snap.restingHeartRateAvg90d ?: snap.restingHeartRateBpm)?.let {
                lines += "Frequenza cardiaca a riposo: ${it.roundToInt()} bpm"
            }
            lines += workoutLines(snap.recentWorkouts)
        }

        return lines
    }

    private fun workoutLines(workouts: List<HealthWorkoutEntry>): List<String> {
        if (workouts.isEmpty()) return listOf("Allenamenti registrati: nessuno negli ultimi giorni")
        val lines = mutableListOf("Allenamenti registrati (${workouts.size}, più recenti):")
        workouts.sortedByDescending { it.startedAtEpochMillis }.take(12).forEach { workout ->
            val sb = StringBuilder("• ${workout.title} — ${formatDate(workout.startedAtEpochMillis)}")
            workout.durationMinutes?.let { sb.append(", $it min") }
            workout.activeEnergyKcal?.let { sb.append(", ${it.roundToInt()} kcal") }
            lines += sb.toString()
        }
        return lines
    }

    /** Nome (in italiano) della lingua in cui l'AI deve rispondere: segue la lingua dell'app. */
    fun responseLanguageName(): String = when (Locale.getDefault().language) {
        "en" -> "inglese"
        "fr" -> "francese"
        "es" -> "spagnolo"
        else -> "italiano"
    }

    private fun yearsSince(epochMillis: Long): Int {
        val birth = Calendar.getInstance().apply { timeInMillis = epochMillis }
        val now = Calendar.getInstance()
        var years = now.get(Calendar.YEAR) - birth.get(Calendar.YEAR)
        if (now.get(Calendar.DAY_OF_YEAR) < birth.get(Calendar.DAY_OF_YEAR)) years--
        return years
    }

    private fun formatDate(epochMillis: Long): String =
        DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(epochMillis))
}
