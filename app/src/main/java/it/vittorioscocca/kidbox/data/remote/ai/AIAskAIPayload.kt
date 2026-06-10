package it.vittorioscocca.kidbox.data.remote.ai

import it.vittorioscocca.kidbox.domain.model.KBAIMessage
import kotlin.math.ceil

/** Allineato a askAI in functions/index.js. */
object AIAskAIPayload {
    const val STANDARD_CHARS: Int = 50_000
    const val ABSOLUTE_MAX_CHARS: Int = 500_000

    /** Unità minime per la cartella clinica (Sonnet ~3× Haiku + niente caching).
     *  Parity con `CLINICAL_RECORD_MIN_UNITS` in `functions/index.js`. */
    const val CLINICAL_RECORD_MIN_UNITS: Int = 3

    fun totalChars(systemPrompt: String, messages: List<KBAIMessage>, pendingUserText: String = ""): Int {
        val history = messages.sumOf { it.content.length }
        val pending = pendingUserText.trim().length
        return systemPrompt.length + history + pending
    }

    fun messageUnits(totalChars: Int): Int {
        if (totalChars <= 0) return 1
        return maxOf(1, ceil(totalChars.toDouble() / STANDARD_CHARS.toDouble()).toInt())
    }

    fun isLargeContext(totalChars: Int): Boolean = messageUnits(totalChars) > 1

    /** Unità per la cartella clinica: minimo fisso [CLINICAL_RECORD_MIN_UNITS],
     *  oppure le unità del payload se il contesto è molto grande. */
    fun clinicalRecordMessageUnits(totalChars: Int): Int =
        maxOf(CLINICAL_RECORD_MIN_UNITS, messageUnits(totalChars))

    const val TRANSIENT_LARGE_CONTEXT_NOTICE: String =
        "Contesto sanitario ampio: alla prossima domanda potrai scegliere tra risposta accurata o contesto riassunto."

    fun choiceDialogMessage(
        fullUnits: Int,
        compactAskUnits: Int,
        compactSetupUnits: Int,
        hasCompactCache: Boolean,
    ): String =
        if (hasCompactCache) {
            "Il profilo sanitario inviato all'AI supera lo standard (~$STANDARD_CHARS caratteri). " +
                "Scegli come procedere per questa domanda: accurata ($fullUnits messaggi) o riassunta " +
                "($compactAskUnits ${if (compactAskUnits == 1) "messaggio" else "messaggi"})."
        } else {
            "Il profilo sanitario è molto ampio. Accuratezza: $fullUnits messaggi per questa domanda. " +
                "Riassunto: $compactAskUnits ${if (compactAskUnits == 1) "messaggio" else "messaggi"} per questa domanda, " +
                "più $compactSetupUnits ${if (compactSetupUnits == 1) "messaggio" else "messaggi"} una sola volta in questa chat " +
                "per creare il riassunto (le domande successive con riassunto costano meno)."
        }

    fun compactChoiceButtonLabel(askUnits: Int, setupUnits: Int): String =
        if (setupUnits > 0) {
            "Contesto riassunto ($askUnits + $setupUnits una tantum)"
        } else {
            val suffix = if (askUnits == 1) "messaggio" else "messaggi"
            "Contesto riassunto ($askUnits $suffix)"
        }
}
