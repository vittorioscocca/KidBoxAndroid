package it.vittorioscocca.kidbox.data.local

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * La vista scelta nel calendario (giorno, settimana, mese, anno) sopravvive
 * alla chiusura dell'app: chi lavora a settimana non deve rimetterla a ogni
 * apertura.
 *
 * È una preferenza del **dispositivo**, non della famiglia: resta in
 * SharedPreferences e non viene sincronizzata, come su iOS (`@AppStorage`).
 */
@Singleton
class CalendarViewModePreference @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("kidbox_prefs", Context.MODE_PRIVATE)

    /** Nome del modo salvato, o `null` se non è mai stato scelto. */
    fun read(): String? = prefs.getString(KEY_CALENDAR_MODE, null)

    fun write(modeName: String) {
        prefs.edit().putString(KEY_CALENDAR_MODE, modeName).apply()
    }

    private companion object {
        const val KEY_CALENDAR_MODE = "calendar_view_mode"
    }
}
