package it.vittorioscocca.kidbox.ui.screens.grocery

import android.content.Context
import it.vittorioscocca.kidbox.R
import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.concurrent.TimeUnit

/**
 * Riga "Aggiunto da ... - oggi" sotto ogni articolo della spesa.
 *
 * Serve soprattutto agli articoli dettati ad Alexa, che compaiono in lista
 * senza che nessuno li abbia scritti: senza attribuzione ci si chiede chi li
 * abbia messi. Ma vale per tutti, perche' in una lista condivisa "chi e quando"
 * e' l'informazione che evita di ricomprare due volte la stessa cosa.
 *
 * Gemello di GroceryAuthorLine.swift su iOS: se cambia una regola qui, va
 * cambiata anche li'.
 */
object GroceryAuthorLine {

    /**
     * Giorni oltre i quali si passa alla data esplicita.
     *
     * Entro tre giorni il riferimento relativo e' piu' utile della data: "ieri"
     * si colloca da solo, "5 settembre" va ricalcolato a mente. Piu' indietro
     * si inverte: "12 giorni fa" non dice niente, la data si'.
     */
    private const val RELATIVE_DAY_LIMIT = 3

    /**
     * Distanza in giorni di calendario, non in multipli di 24 ore: alle 00:30
     * un articolo di ieri sera dev'essere "ieri", non "oggi".
     */
    fun daysAgo(epochMillis: Long, now: Long = System.currentTimeMillis()): Int {
        val start = startOfDay(epochMillis)
        val today = startOfDay(now)
        return TimeUnit.MILLISECONDS.toDays(today - start).toInt()
    }

    private fun startOfDay(epochMillis: Long): Long = Calendar.getInstance().apply {
        timeInMillis = epochMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /**
     * Etichetta relativa: ore dentro la giornata, giorni oltre, poi la data.
     *
     * Dentro la giornata "oggi" dice troppo poco: fra un articolo di cinque
     * minuti fa e uno di stamattina presto c'e' la differenza fra "l'ho appena
     * messo io" e "c'era gia'". Le ore quella differenza la mostrano.
     */
    fun dateLabel(context: Context, epochMillis: Long, now: Long = System.currentTimeMillis()): String {
        val days = daysAgo(epochMillis, now)
        return when {
            // Una data futura non dovrebbe esistere, ma l'orologio del
            // dispositivo puo' essere indietro rispetto al server: meglio
            // "adesso" di un valore negativo.
            days <= 0 -> withinTodayLabel(context, epochMillis, now)
            days == 1 -> context.getString(R.string.grocery_added_yesterday)
            days <= RELATIVE_DAY_LIMIT -> context.getString(R.string.grocery_added_days_ago, days)
            else -> DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(epochMillis))
        }
    }

    /** Sotto la giornata si scende a ore e minuti. */
    private fun withinTodayLabel(context: Context, epochMillis: Long, now: Long): String {
        val minutes = TimeUnit.MILLISECONDS.toMinutes(now - epochMillis).toInt()
        return when {
            // Sotto i due minuti "1 minuto fa" e' piu' preciso che utile, e
            // obbligherebbe a gestire il singolare in quattro lingue per
            // guadagnare niente.
            minutes < 2 -> context.getString(R.string.grocery_added_now)
            minutes < 60 -> context.getString(R.string.grocery_added_minutes_ago, minutes)
            minutes < 120 -> context.getString(R.string.grocery_added_hour_ago)
            else -> context.getString(R.string.grocery_added_hours_ago, minutes / 60)
        }
    }

    /**
     * Riga completa. Senza autore resta la sola data: "Aggiunto da un ignoto"
     * non aggiunge niente e occupa spazio.
     */
    fun text(
        context: Context,
        authorName: String?,
        epochMillis: Long,
        now: Long = System.currentTimeMillis(),
    ): String {
        val when_ = dateLabel(context, epochMillis, now)
        val name = authorName?.trim()
        return if (name.isNullOrEmpty()) when_
        else context.getString(R.string.grocery_added_by, name, when_)
    }
}
