package it.vittorioscocca.kidbox.ui.screens.ai.common

/**
 * Allineato a iOS `AIChatStreamingDelivery`: id del messaggio assistant in typewriter reveal.
 */
object AIChatStreamingDelivery {
    fun beginAssistantReveal(messageId: String): String = messageId

    fun finishReveal(messageId: String, currentStreamingMessageId: String?): String? =
        if (currentStreamingMessageId == messageId) null else currentStreamingMessageId
}
