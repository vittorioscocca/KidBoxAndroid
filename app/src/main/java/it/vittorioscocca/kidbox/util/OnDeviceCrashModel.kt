package it.vittorioscocca.kidbox.util

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

/**
 * Gemini Nano sul telefono (ML Kit GenAI, via AICore), per l'analisi dei log
 * di crash: gemello di Apple Intelligence in `CrashAnalyzer.swift`.
 *
 * Non costa per chiamata e i log non escono dal telefono. Esiste solo sui
 * dispositivi supportati (Pixel 9 e successivi, Galaxy S26, Z Fold7/8 e altri
 * recenti), funziona solo con l'app in primo piano e ha una quota per app: per
 * questo ogni esito diverso da una risposta dice al chiamante se riprovare.
 */
object OnDeviceCrashModel {

    sealed interface Outcome {
        data class Text(val text: String) : Outcome
        /** Il telefono non ha il modello (o non è ancora scaricato): si usa l'euristica. */
        data object Unavailable : Outcome
        /** Non adesso (in background, occupato, quota): si riprova al prossimo giro. */
        data object NotNow : Outcome
        data class Failed(val message: String) : Outcome
    }

    private const val SHRINK_STEPS = 2

    /**
     * Il budget in byte è una stima: il tokenizer può sforare anche sotto la
     * soglia. Con `REQUEST_TOO_LARGE` si riprova dimezzando il log.
     */
    suspend fun analyze(prompt: (maxBytes: Int) -> String): Outcome {
        val model = runCatching { Generation.getClient() }.getOrElse { return Outcome.Unavailable }
        val status = runCatching { model.checkStatus() }.getOrElse { return Outcome.Unavailable }
        // Il download del modello lo lascia al sistema: non lo avvia un'analisi dei log.
        if (status != FeatureStatus.AVAILABLE) return Outcome.Unavailable

        var budget = CrashLogAnalysis.MAX_PROMPT_LOG_BYTES
        repeat(SHRINK_STEPS + 1) { attempt ->
            try {
                val response = model.generateContent(
                    generateContentRequest(TextPart(prompt(budget))) {
                        temperature = 0.1f
                        maxOutputTokens = 600
                    },
                )
                val text = response.candidates.firstOrNull()?.text.orEmpty()
                return if (text.isBlank()) Outcome.Failed("risposta vuota") else Outcome.Text(text)
            } catch (e: GenAiException) {
                when (e.errorCode) {
                    GenAiException.ErrorCode.REQUEST_TOO_LARGE -> if (attempt < SHRINK_STEPS) {
                        budget /= 2
                        KBLog.app.info("CrashAnalyzer: richiesta troppo grande, riprovo con $budget B", TAG)
                    } else {
                        return Outcome.Failed("log troppo lungo anche ridotto")
                    }
                    GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED,
                    GenAiException.ErrorCode.BUSY,
                    GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
                    -> return Outcome.NotNow
                    GenAiException.ErrorCode.NOT_AVAILABLE,
                    GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
                    GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
                    -> return Outcome.Unavailable
                    else -> return Outcome.Failed("errore ${e.errorCode}: ${e.message}")
                }
            } catch (e: Exception) {
                return Outcome.Failed(e.message ?: e.javaClass.simpleName)
            }
        }
        return Outcome.Failed("tentativi esauriti")
    }

    private const val TAG = "CrashAnalyzer"
}
