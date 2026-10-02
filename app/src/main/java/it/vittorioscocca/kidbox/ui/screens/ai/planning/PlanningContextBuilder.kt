package it.vittorioscocca.kidbox.ui.screens.ai.planning

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Formati di data della chat dell'assistente. Il contesto dell'assistente non
 * passa più da qui: dal 02/10/2026 lo costruisce il quaderno di schede
 * ([AgentMemoryBook]), vedi `internal/assistente-unico.md`.
 */
object PlanningContextBuilder {
    private val locale = Locale("it", "IT")

    fun formatDateShort(date: Long): String = SimpleDateFormat("EEE d MMM", locale).format(Date(date)).lowercase(locale)
    fun formatTime(date: Long): String = SimpleDateFormat("HH:mm", locale).format(Date(date))
}
