package it.vittorioscocca.kidbox.domain.model

data class HealthTimelineEvent(
    val id: String,
    val sourceId: String,
    val dateEpochMillis: Long,
    val kind: HealthTimelineEventKind,
    val title: String,
    val subtitle: String?,
)

enum class HealthTimelineEventKind(
    val rawLabel: String,
    val tintColorArgb: Long,
    val iconKey: String,
) {
    VISIT("Visita", 0xFF5996D9L, "medical_services"),
    EXAM("Esame", 0xFF40A6BFL, "science"),
    TREATMENT("Cura", 0xFF9573D9L, "medication"),
    VACCINE("Vaccino", 0xFFF38D73L, "vaccines"),
}
