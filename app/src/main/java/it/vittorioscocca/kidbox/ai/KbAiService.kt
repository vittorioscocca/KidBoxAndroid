package it.vittorioscocca.kidbox.ai

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class KbAiService @Inject constructor() {
    suspend fun sendMessage(
        systemPrompt: String,
        userMessage: String,
    ): String {
        // Stub iniziale: sostituire con integrazione API reale.
        return "Risposta AI di test"
    }
}
