package it.vittorioscocca.kidbox.ui.screens.travel

import kotlin.math.max

/** 1 minuto e 30 secondi ogni 3 giorni di itinerario (indicativo). */
object TravelPlanningCountdown {
    const val SECONDS_PER_THREE_DAYS = 90
    /** Messaggi scalati sul limite giornaliero famiglia (allineato a Cloud Functions). */
    const val MESSAGES_PER_THREE_DAY_BLOCK = 2

    fun planningBlocks(plannedDayCount: Int): Int {
        val days = plannedDayCount.coerceAtLeast(1)
        return max(1, (days + 2) / 3)
    }

    /** Es. 1–3 giorni → 2 messaggi; 4–6 → 4; 7–9 → 6. */
    fun messageCost(plannedDayCount: Int): Int =
        planningBlocks(plannedDayCount) * MESSAGES_PER_THREE_DAY_BLOCK

    fun totalSeconds(plannedDayCount: Int): Int =
        planningBlocks(plannedDayCount) * SECONDS_PER_THREE_DAYS

    fun estimateLabel(plannedDayCount: Int): String {
        val total = totalSeconds(plannedDayCount)
        val minutes = total / 60
        val seconds = total % 60
        val messages = messageCost(plannedDayCount)
        val timePart = if (seconds == 0) {
            "L'AI può impiegare fino a circa $minutes minuti."
        } else {
            "L'AI può impiegare fino a circa $minutes min ${seconds}s."
        }
        return "$timePart Conta $messages messaggi sul limite AI giornaliero della famiglia. Il contatore è solo indicativo."
    }
}
