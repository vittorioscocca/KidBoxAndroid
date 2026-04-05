package it.vittorioscocca.kidbox.domain.model

/** Stato estrazione testo documento (KBDocument su iOS). */
enum class KBTextExtractionStatus(val rawValue: Int) {
    NONE(0),
    PENDING(1),
    PROCESSING(2),
    COMPLETED(3),
    FAILED(4),
    ;

    companion object {
        fun fromRaw(value: Int): KBTextExtractionStatus =
            entries.find { it.rawValue == value } ?: NONE
    }
}

/** Categoria evento calendario (KBCalendarEvent). */
enum class KBEventCategory(val rawValue: String) {
    CHILDREN("children"),
    SCHOOL("school"),
    HEALTH("health"),
    FAMILY("family"),
    ADMIN("admin"),
    LEISURE("leisure"),
}

/** Ricorrenza evento calendario. */
enum class KBEventRecurrence(val rawValue: String) {
    NONE("none"),
    DAILY("daily"),
    WEEKLY("weekly"),
    MONTHLY("monthly"),
    YEARLY("yearly"),
}

/** Stato esame (KBMedicalExam). */
enum class KBExamStatus(val rawValue: String) {
    PENDING("In attesa"),
    BOOKED("Prenotato"),
    DONE("Eseguito"),
    RESULT_IN("Risultato disponibile"),
}

/** Tipo vaccino (KBVaccine). */
enum class VaccineType(val rawValue: String) {
    ESVALENTE("esavalente"),
    PNEUMOCCO("pneumococco"),
    MENINGOCOCCO_B("meningococcoB"),
    MPR("mpr"),
    VARICELLA("varicella"),
    MENINGOCOCCO_ACWY("meningococcoACWY"),
    HPV("hpv"),
    INFLUENZA("influenza"),
    ALTRO("altro"),
}

/** Stato vaccino (KBVaccine). */
enum class VaccineStatus(val rawValue: String) {
    ADMINISTERED("administered"),
    SCHEDULED("scheduled"),
    PLANNED("planned"),
}

/** Tipo messaggio chat (KBChatMessageType su iOS). */
enum class KBChatMessageType(val rawValue: String) {
    TEXT("text"),
    AUDIO("audio"),
    PHOTO("photo"),
    VIDEO("video"),
    DOCUMENT("document"),
    LOCATION("location"),
    MEDIA_GROUP("mediaGroup"),
}

enum class KBTranscriptStatus(val rawValue: String) {
    NONE("none"),
    PROCESSING("processing"),
    COMPLETED("completed"),
    FAILED("failed"),
}

enum class KBTranscriptSource(val rawValue: String) {
    APPLE_SPEECH_ANALYZER("appleSpeechAnalyzer"),
}
