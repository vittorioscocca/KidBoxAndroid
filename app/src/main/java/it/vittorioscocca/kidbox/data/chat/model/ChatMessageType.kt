package it.vittorioscocca.kidbox.data.chat.model

/**
 * Android mirror of iOS `KBChatMessageType.swift`.
 * Raw values must stay byte-for-byte compatible with Firestore payloads.
 */
enum class ChatMessageType(val rawValue: String) {
    TEXT("text"),
    AUDIO("audio"),
    PHOTO("photo"),
    VIDEO("video"),
    DOCUMENT("document"),
    LOCATION("location"),
    MEDIA_GROUP("mediaGroup"),
    CONTACT("contact"),
    ;

    companion object {
        fun fromRaw(raw: String?): ChatMessageType =
            entries.firstOrNull { it.rawValue == raw } ?: TEXT
    }
}

enum class TranscriptStatus(val rawValue: String) {
    NONE("none"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
    ;

    companion object {
        fun fromRaw(raw: String?): TranscriptStatus =
            entries.firstOrNull { it.rawValue == raw } ?: NONE
    }
}

enum class TranscriptSource(val rawValue: String) {
    APPLE_SPEECH_ANALYZER("appleSpeechAnalyzer"),
    ;

    companion object {
        fun fromRaw(raw: String?): TranscriptSource? =
            entries.firstOrNull { it.rawValue == raw }
    }
}
