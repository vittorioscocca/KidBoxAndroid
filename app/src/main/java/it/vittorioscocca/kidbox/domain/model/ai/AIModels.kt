package it.vittorioscocca.kidbox.domain.model.ai

enum class AIProvider(val value: String) {
    CLAUDE("claude"),
    OPENAI("openai"),
}

enum class AIMessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

/**
 * Finestra della quota AI: `DAILY` si resetta ogni giorno (Pro/Max), `LIFETIME` è un
 * bonus una tantum per famiglia che non si resetta mai (Free, 5 messaggi totali).
 */
enum class AIQuotaPeriod(val raw: String) {
    DAILY("daily"),
    LIFETIME("lifetime"),
    ;

    companion object {
        fun fromRaw(raw: String?): AIQuotaPeriod = entries.firstOrNull { it.raw == raw } ?: DAILY
    }
}

data class AIResponse(
    val reply: String,
    val usageToday: Int,
    val dailyLimit: Int,
    val period: AIQuotaPeriod = AIQuotaPeriod.DAILY,
)

sealed class AIServiceError {
    object RateLimitReached : AIServiceError()
    object NetworkError : AIServiceError()
    data class ServerError(val message: String) : AIServiceError()
}
