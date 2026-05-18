package it.vittorioscocca.kidbox.domain.model

/** Fascia oraria di ricevimento (giorno in italiano, dalle/alle in HH:mm). */
data class KBDoctorOfficeHourSlot(
    val id: String,
    val weekday: String,
    val fromTime: String,
    val toTime: String,
)

object KBItalianWeekdays {
    val all = listOf(
        "Lunedì",
        "Martedì",
        "Mercoledì",
        "Giovedì",
        "Venerdì",
        "Sabato",
        "Domenica",
    )
}

data class ReferenceDoctorDraft(
    val name: String = "",
    val address: String = "",
    val website: String = "",
    val officeHours: List<KBDoctorOfficeHourSlot> = emptyList(),
) {
    val hasDoctor: Boolean get() = name.isNotBlank()
}

/** Es. `Lunedì: 08:30 – 10:30; 16:30 – 18:30` (fasce dello stesso giorno sulla stessa riga). */
fun List<KBDoctorOfficeHourSlot>.groupedOfficeHourDisplayLines(): List<String> {
    if (isEmpty()) return emptyList()
    val byWeekday = groupBy { it.weekday }
    val lines = mutableListOf<String>()
    for (day in KBItalianWeekdays.all) {
        val slots = byWeekday[day].orEmpty()
        if (slots.isNotEmpty()) {
            lines += formatGroupedOfficeHourLine(day, slots)
        }
    }
    (byWeekday.keys - KBItalianWeekdays.all.toSet())
        .sorted()
        .forEach { day ->
            val slots = byWeekday[day].orEmpty()
            if (slots.isNotEmpty()) {
                lines += formatGroupedOfficeHourLine(day, slots)
            }
        }
    return lines
}

private fun formatGroupedOfficeHourLine(
    weekday: String,
    slots: List<KBDoctorOfficeHourSlot>,
): String {
    val ranges = slots.joinToString("; ") { "${it.fromTime} – ${it.toTime}" }
    return "$weekday: $ranges"
}
