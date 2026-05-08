package it.vittorioscocca.kidbox.domain.model.ai

enum class AIProvider(val value: String) {
    CLAUDE("claude"),
    OPENAI("openai"),
}

enum class AIMessageRole(val value: String) {
    USER("user"),
    ASSISTANT("assistant"),
}

data class AIResponse(
    val reply: String,
    val usageToday: Int,
    val dailyLimit: Int,
)

sealed class AIServiceError {
    object RateLimitReached : AIServiceError()
    object NetworkError : AIServiceError()
    data class ServerError(val message: String) : AIServiceError()
}
