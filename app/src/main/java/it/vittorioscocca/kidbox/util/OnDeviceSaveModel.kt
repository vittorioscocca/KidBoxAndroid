package it.vittorioscocca.kidbox.util

import com.google.mlkit.genai.common.FeatureStatus
import com.google.mlkit.genai.common.GenAiException
import com.google.mlkit.genai.prompt.Generation
import com.google.mlkit.genai.prompt.TextPart
import com.google.mlkit.genai.prompt.generateContentRequest

/**
 * Gemini Nano sul telefono per «Salva in…» da un messaggio di chat: gemello di
 * `classifyWithFoundationModels` in `KBSaveClassifier.swift`. Stesso schema di
 * `OnDeviceCrashModel`: ogni esito diverso da una risposta dice al chiamante
 * che si usano le regole, senza mai mostrare un errore all'utente. Il testo
 * del messaggio non lascia il telefono.
 */
object OnDeviceSaveModel {

    sealed interface Outcome {
        data class Text(val text: String) : Outcome
        /** Niente modello su questo telefono (o non ancora scaricato): regole. */
        data object Unavailable : Outcome
        /** Non adesso (app in background, occupato, quota): regole per questa volta. */
        data object NotNow : Outcome
        data class Failed(val message: String) : Outcome
    }

    suspend fun classify(text: String): Outcome {
        val model = runCatching { Generation.getClient() }.getOrElse { return Outcome.Unavailable }
        val status = runCatching { model.checkStatus() }.getOrElse { return Outcome.Unavailable }
        if (status != FeatureStatus.AVAILABLE) return Outcome.Unavailable
        return try {
            val response = model.generateContent(
                generateContentRequest(TextPart(prompt(text))) {
                    temperature = 0.1f
                    maxOutputTokens = 120
                },
            )
            val answer = response.candidates.firstOrNull()?.text.orEmpty()
            if (answer.isBlank()) Outcome.Failed("risposta vuota") else Outcome.Text(answer)
        } catch (e: GenAiException) {
            when (e.errorCode) {
                GenAiException.ErrorCode.BACKGROUND_USE_BLOCKED,
                GenAiException.ErrorCode.BUSY,
                GenAiException.ErrorCode.PER_APP_BATTERY_USE_QUOTA_EXCEEDED,
                -> Outcome.NotNow
                GenAiException.ErrorCode.NOT_AVAILABLE,
                GenAiException.ErrorCode.AICORE_INCOMPATIBLE,
                GenAiException.ErrorCode.NEEDS_SYSTEM_UPDATE,
                -> Outcome.Unavailable
                else -> Outcome.Failed("errore ${e.errorCode}: ${e.message}")
            }
        } catch (e: Exception) {
            Outcome.Failed(e.message ?: e.javaClass.simpleName)
        }
    }

    /** Stesso prompt di iOS: le etichette e le regole devono restare identiche. */
    internal fun prompt(text: String): String = """
        Sei un assistente per un'app di famiglia italiana. Analizza il testo e restituisci SOLO JSON valido, senza markdown, senza spiegazioni.

        Schema esatto:
        {
          "destinations": ["todo"|"event"|"grocery"|"note"],
          "detectedDate": "<ISO8601 oppure null>"
        }

        Regole (ordina per rilevanza, la più pertinente prima):
        - "todo"    → compito, attività, cosa da fare, promemoria, "devo", "dobbiamo", "ricordati", "non dimenticare"
        - "event"   → contiene data o ora, appuntamento, riunione, scadenza, "domani", "venerdì", "prossima settimana"
        - "grocery" → lista spesa, prodotti alimentari, ingredienti, "comprare", "al super", latte, pane, uova, ecc.
        - "note"    → tutto il resto: appunti, link, riflessioni, info

        Per "event" estrai la data in ISO8601 se presente, altrimenti null.

        Testo:
        ${text.take(800)}
    """.trimIndent()
}
