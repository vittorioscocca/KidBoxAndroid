package it.vittorioscocca.kidbox.domain.model

import java.time.Instant
import java.time.ZoneId

/**
 * Fascia oraria per etichettatura assunzioni (stessa logica iOS).
 *
 * Range (minuti da mezzanotte, [start, end] inclusi come da specifica):
 * - 06:00–11:59 → Mattina
 * - 12:00–15:59 → Pranzo
 * - 16:00–21:59 → Sera
 * - 22:00–05:59 → Notte
 */
enum class TreatmentSchedulePeriod(val labelIt: String) {
    MATTINA("Mattina"),
    PRANZO("Pranzo"),
    SERA("Sera"),
    NOTTE("Notte"),
    ;

    companion object {
        /** Da ora locale (0–23, 0–59). */
        fun fromHourMinute(hour: Int, minute: Int): TreatmentSchedulePeriod {
            val h = ((hour % 24) + 24) % 24
            val m = minute.coerceIn(0, 59)
            val total = h * 60 + m
            return when (total) {
                in 360..719 -> MATTINA   // 06:00–11:59
                in 720..959 -> PRANZO    // 12:00–15:59
                in 960..1319 -> SERA     // 16:00–21:59
                else -> NOTTE            // 22:00–05:59
            }
        }

        fun fromEpochMillis(epochMillis: Long, zoneId: ZoneId = ZoneId.systemDefault()): TreatmentSchedulePeriod {
            val zdt = Instant.ofEpochMilli(epochMillis).atZone(zoneId)
            return fromHourMinute(zdt.hour, zdt.minute)
        }

        /** Da stringa orario cura tipo `08:00`, `8:30`, `8.30`. */
        fun fromScheduleTimeString(time: String): TreatmentSchedulePeriod? {
            val mins = parseScheduleTimeToMinutesOfDay(time) ?: return null
            return fromHourMinute(mins / 60, mins % 60)
        }

        /**
         * Minuti da mezzanotte (0–1439) per ordinamento intra-fascia.
         * `null` se formato non valido.
         */
        fun parseScheduleTimeToMinutesOfDay(time: String): Int? {
            val normalized = time.trim().replace('.', ':').replace(',', ':')
            val parts = normalized.split(':').map { it.trim() }.filter { it.isNotEmpty() }
            if (parts.isEmpty()) return null
            val h = parts[0].toIntOrNull() ?: return null
            if (h !in 0..23) return null
            val minutePart = parts.getOrNull(1) ?: "0"
            val digits = minutePart.filter { it.isDigit() }
            val m = when {
                digits.isEmpty() -> minutePart.toIntOrNull() ?: 0
                else -> digits.take(2).toIntOrNull() ?: 0
            }.coerceIn(0, 59)
            return h * 60 + m
        }
    }
}

/**
 * Periodo da orario programmato; se non parsabile e [slotIndexFallback] è 0–3, usa la stessa mappa di [slotLabelFor].
 * Per indici ≥ 4 (etichetta "Dose N") restituisce `null`: nessuna fascia colore predefinita.
 */
fun schedulePeriodForTime(scheduledTime: String, slotIndexFallback: Int = 0): TreatmentSchedulePeriod? =
    TreatmentSchedulePeriod.fromScheduleTimeString(scheduledTime)
        ?: when (slotIndexFallback) {
            0 -> TreatmentSchedulePeriod.MATTINA
            1 -> TreatmentSchedulePeriod.PRANZO
            2 -> TreatmentSchedulePeriod.SERA
            3 -> TreatmentSchedulePeriod.NOTTE
            else -> null
        }

/**
 * Etichetta periodo da orario programmato; se non parsabile usa [slotLabelFor] sull’indice.
 */
fun schedulePeriodLabel(scheduledTime: String, slotIndexFallback: Int = 0): String =
    TreatmentSchedulePeriod.fromScheduleTimeString(scheduledTime)?.labelIt
        ?: slotLabelFor(slotIndexFallback)
