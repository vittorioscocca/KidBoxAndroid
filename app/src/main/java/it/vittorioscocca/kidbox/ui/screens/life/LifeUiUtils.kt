package it.vittorioscocca.kidbox.ui.screens.life

import androidx.compose.ui.graphics.Color
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale
import it.vittorioscocca.kidbox.util.KBLocale

private fun itDate() = SimpleDateFormat("dd/MM/yyyy", KBLocale.current())
private fun itDateTime() = SimpleDateFormat("dd/MM/yyyy, HH:mm", KBLocale.current())
private fun itTimeHm() = SimpleDateFormat("HH:mm", KBLocale.current())
private val itDateMedium: DateFormat = DateFormat.getDateInstance(DateFormat.MEDIUM, KBLocale.current())

fun formatItDate(epochMillis: Long?): String =
    epochMillis?.let { itDate().format(Date(it)) }.orEmpty()

/** Es. "11 mag 2026" (stile medio italiano, come iOS sul dettaglio Casa). */
fun formatItDateMedium(epochMillis: Long?): String =
    epochMillis?.let { itDateMedium.format(Date(it)) }.orEmpty()

/** Data breve con ora (allineato a iOS `shortDateTime` sullo storico eventi animale). */
fun formatItDateTime(epochMillis: Long?): String =
    epochMillis?.let { itDateTime().format(Date(it)) }.orEmpty()

/** Solo ora locale (picker compatti tipo iOS). */
fun formatItTimeHm(epochMillis: Long?): String =
    epochMillis?.let { itTimeHm().format(Date(it)) }.orEmpty()

/** Giorni tra oggi e la scadenza (inizio giornata Europe/Rome), come iOS `KidBoxUrgency.daysRemaining`. */
fun daysRemainingCalendarIt(deadlineMillis: Long, fromMillis: Long = System.currentTimeMillis()): Int {
    val zone = ZoneId.of("Europe/Rome")
    val fromDay = Instant.ofEpochMilli(fromMillis).atZone(zone).toLocalDate()
    val toDay = Instant.ofEpochMilli(deadlineMillis).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(fromDay, toDay).toInt()
}

/** Colori urgenza scadenze Casa (come iOS KidBoxUrgency: rosso, arancione, verde). */
fun homeDeadlineUrgencyColor(days: Int): Color = when {
    days < 0 -> Color(0xFFE53935)
    days < 30 -> Color(0xFFE53935)
    days < 60 -> Color(0xFFFF9500)
    else -> Color(0xFF27AE60)
}

fun homeDeadlineUrgencyLabel(days: Int): String =
    if (days < 0) "Scaduto" else "Tra $days giorni"

/** Green >60d, orange 30–60d, red <30d (incluso scaduto), gray if null. */
fun deadlineUrgencyColor(epochMillis: Long?): Color {
    if (epochMillis == null) return Color(0xFF9E9E9E)
    val days = ((epochMillis - System.currentTimeMillis()) / 86_400_000L).toInt()
    return when {
        days > 60 -> Color(0xFF27AE60)
        days >= 30 -> Color(0xFFFF9500)
        else -> Color(0xFFE53935)
    }
}

fun earliestNonNull(vararg values: Long?): Long? =
    values.filterNotNull().minOrNull()
