package it.vittorioscocca.kidbox.data.devicecalendar

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import it.vittorioscocca.kidbox.R
import it.vittorioscocca.kidbox.util.KBLog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.text.Normalizer
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Un'occorrenza di un evento del telefono, copiata in un valore. */
data class DeviceCalendarEvent(
    /** `eventId` è lo stesso per tutte le occorrenze di un ricorrente: l'inizio lo rende unico. */
    val id: String,
    val eventId: Long,
    val title: String,
    val location: String?,
    val notes: String?,
    val startMillis: Long,
    /**
     * Fine **esclusa**, come la dà il sistema: un evento che dura fino a fine
     * giornata finisce alla mezzanotte seguente e non va disegnato anche lì.
     */
    val endMillis: Long,
    val isAllDay: Boolean,
    val calendarId: Long,
    val calendarTitle: String,
    val color: Int,
    /**
     * Valorizzato per gli eventi di un calendario iscritto da link (feed ICS,
     * `CalendarFeedRepository`): quelli li vede tutta la famiglia e non
     * hanno un'app di sistema in cui aprirli.
     */
    val feedId: String? = null,
) {
    /** Titolo senza maiuscole e accenti, inizio al minuto, tutto-il-giorno: come `dedupKey` su iOS. */
    val dedupKey: String get() = dedupKey(title, startMillis, isAllDay)

    companion object {
        fun dedupKey(title: String, startMillis: Long, isAllDay: Boolean): String {
            val t = Normalizer.normalize(title.trim().lowercase(), Normalizer.Form.NFD)
                .replace(Regex("\\p{M}+"), "")
            val minute = Math.round(startMillis / 60_000.0)
            return "$t|$minute|$isAllDay"
        }
    }
}

/** Un calendario del telefono, per l'elenco degli interruttori. */
data class DeviceCalendarInfo(
    val id: Long,
    val title: String,
    val accountName: String,
    val color: Int,
)

data class DeviceCalendarState(
    val hasPermission: Boolean = false,
    /** L'interruttore generale: parte spento e si accende quando arriva il permesso. */
    val isEnabled: Boolean = false,
    /** La domanda di sistema è già stata fatta almeno una volta. */
    val wasAsked: Boolean = false,
    val promptDismissed: Boolean = false,
    val calendars: List<DeviceCalendarInfo> = emptyList(),
    val hiddenCalendarIds: Set<Long> = emptySet(),
    val events: List<DeviceCalendarEvent> = emptyList(),
) {
    val isShowing: Boolean get() = hasPermission && isEnabled
    /** L'invito nel calendario: solo finché l'utente non ha mai risposto. */
    val showsPrompt: Boolean get() = !hasPermission && !wasAsked && !promptDismissed
}

/**
 * I calendari del telefono mostrati dentro il calendario KidBox, in SOLA
 * LETTURA. Gemello di `DeviceCalendarStore` su iOS.
 *
 * Niente OAuth e niente server: la sincronizzazione con Google o Outlook la fa
 * il sistema, qui si legge `CalendarContract`. Non c'è nulla da tenere
 * allineato: un evento spostato su Google compare spostato appena il telefono
 * lo riceve (un `ContentObserver` fa rileggere).
 *
 * Gli eventi NON escono dal dispositivo e la famiglia non li vede: per
 * condividerne uno c'è «Copia in KidBox», che crea un evento KidBox normale.
 * Le preferenze sono del dispositivo, non della famiglia.
 */
@Singleton
class DeviceCalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val prefs by lazy { context.getSharedPreferences(PREFS, Context.MODE_PRIVATE) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow(DeviceCalendarState())
    val state: StateFlow<DeviceCalendarState> = _state.asStateFlow()

    /** La finestra di date che le viste stanno guardando, e quella caricata. */
    private var requestedWindow: Pair<Long, Long>? = null
    private var loadedWindow: Pair<Long, Long>? = null
    private var loadJob: Job? = null
    private var observerRegistered = false

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            loadedWindow = null
            reload()
        }
    }

    init {
        _state.value = DeviceCalendarState(
            hasPermission = hasPermission(),
            isEnabled = prefs.getBoolean(KEY_ENABLED, false),
            wasAsked = prefs.getBoolean(KEY_ASKED, false),
            promptDismissed = prefs.getBoolean(KEY_PROMPT_DISMISSED, false),
            hiddenCalendarIds = prefs.getStringSet(KEY_HIDDEN, emptySet())
                .orEmpty().mapNotNull { it.toLongOrNull() }.toSet(),
        )
    }

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Da chiamare quando la schermata torna in primo piano: il permesso può essere cambiato da Impostazioni. */
    fun refreshPermission() {
        val granted = hasPermission()
        if (granted == _state.value.hasPermission) return
        _state.update { it.copy(hasPermission = granted) }
        loadedWindow = null
        reload()
    }

    /** Risposta alla domanda di sistema: se è sì, la funzione si accende. */
    fun onPermissionResult(granted: Boolean) {
        prefs.edit().putBoolean(KEY_ASKED, true).apply()
        _state.update { it.copy(wasAsked = true, hasPermission = hasPermission()) }
        if (granted) setEnabled(true) else reload()
    }

    fun setEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLED, enabled).apply()
        _state.update { it.copy(isEnabled = enabled) }
        loadedWindow = null
        reload()
    }

    fun dismissPrompt() {
        prefs.edit().putBoolean(KEY_PROMPT_DISMISSED, true).apply()
        _state.update { it.copy(promptDismissed = true) }
    }

    /**
     * Si salvano i calendari NASCOSTI, non quelli visibili: un calendario
     * aggiunto dopo (un secondo account Google) compare da solo.
     */
    fun setCalendarVisible(calendarId: Long, visible: Boolean) {
        val hidden = _state.value.hiddenCalendarIds.toMutableSet()
        if (visible) hidden.remove(calendarId) else hidden.add(calendarId)
        prefs.edit().putStringSet(KEY_HIDDEN, hidden.map { it.toString() }.toSet()).apply()
        _state.update { it.copy(hiddenCalendarIds = hidden) }
        loadedWindow = null
        reload()
    }

    /**
     * Le viste dicono quale giorno stanno guardando; si carica l'anno intero
     * più qualche mese, così sfogliare mese e settimana non rilegge a ogni passo.
     */
    fun showAround(date: LocalDate) {
        val zone = ZoneId.systemDefault()
        val loaded = loadedWindow
        if (loaded != null) {
            val marginStart = date.minusMonths(2).atStartOfDay(zone).toInstant().toEpochMilli()
            val marginEnd = date.plusMonths(3).atStartOfDay(zone).toInstant().toEpochMilli()
            if (loaded.first <= marginStart && loaded.second >= marginEnd) return
        }
        val yearStart = date.withDayOfYear(1)
        val start = minOf(yearStart, date.minusMonths(3)).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = maxOf(yearStart.plusYears(1), date.plusMonths(4)).atStartOfDay(zone).toInstant().toEpochMilli()
        requestedWindow = start to end
        reload()
    }

    fun reload() {
        val current = _state.value
        if (!current.isShowing) {
            unregisterObserver()
            loadedWindow = null
            if (current.events.isNotEmpty() || (!current.hasPermission && current.calendars.isNotEmpty())) {
                _state.update {
                    it.copy(events = emptyList(), calendars = if (it.hasPermission) it.calendars else emptyList())
                }
            }
            return
        }
        registerObserver()
        val window = requestedWindow ?: loadedWindow
        loadJob?.cancel()
        loadJob = scope.launch {
            runCatching {
                val calendars = queryCalendars()
                val hidden = _state.value.hiddenCalendarIds
                val visibleIds = calendars.map { it.id }.filterNot { it in hidden }.toSet()
                val events = if (window == null || visibleIds.isEmpty()) {
                    emptyList()
                } else {
                    queryInstances(window.first, window.second, visibleIds)
                }
                _state.update { it.copy(calendars = calendars, events = events) }
                if (window != null) loadedWindow = window
            }.onFailure {
                KBLog.app.error("lettura calendari del telefono fallita", TAG, it)
            }
        }
    }

    private fun queryCalendars(): List<DeviceCalendarInfo> {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
        )
        // Solo i calendari che il telefono stesso mostra e sincronizza.
        val selection = "${CalendarContract.Calendars.VISIBLE} = 1 AND ${CalendarContract.Calendars.SYNC_EVENTS} = 1"
        val result = mutableListOf<DeviceCalendarInfo>()
        context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI, projection, selection, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                result += DeviceCalendarInfo(
                    id = c.getLong(0),
                    title = readableName(c.getString(1).orEmpty()),
                    accountName = readableName(c.getString(2).orEmpty()),
                    color = c.getInt(3),
                )
            }
        }
        return result.sortedWith(compareBy({ it.accountName.lowercase() }, { it.title.lowercase() }))
    }

    /**
     * `Instances` espande da sé le ricorrenze. Gli eventi tutto-il-giorno sono
     * salvati a mezzanotte **UTC**: letti così com'erano, in Italia cadevano
     * all'una di notte e in America il giorno prima. Si riportano alla
     * mezzanotte locale dello stesso giorno di calendario.
     */
    private fun queryInstances(begin: Long, end: Long, calendarIds: Set<Long>): List<DeviceCalendarEvent> {
        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.EVENT_LOCATION,
            CalendarContract.Instances.DESCRIPTION,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.CALENDAR_DISPLAY_NAME,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.STATUS,
        )
        val zone = ZoneId.systemDefault()
        val result = mutableListOf<DeviceCalendarEvent>()
        CalendarContract.Instances.query(context.contentResolver, projection, begin, end)?.use { c ->
            while (c.moveToNext()) {
                val calendarId = c.getLong(7)
                if (calendarId !in calendarIds) continue
                if (!c.isNull(10) && c.getInt(10) == CalendarContract.Events.STATUS_CANCELED) continue
                val allDay = c.getInt(6) == 1
                var startMillis = c.getLong(1)
                var endMillis = c.getLong(2)
                if (allDay) {
                    startMillis = utcMidnightToLocal(startMillis, zone)
                    endMillis = utcMidnightToLocal(endMillis, zone)
                }
                val eventId = c.getLong(0)
                result += DeviceCalendarEvent(
                    id = "$eventId|$startMillis",
                    eventId = eventId,
                    title = c.getString(3)?.takeIf { it.isNotBlank() } ?: "",
                    location = c.getString(4)?.takeIf { it.isNotBlank() },
                    notes = c.getString(5)?.takeIf { it.isNotBlank() },
                    startMillis = startMillis,
                    endMillis = maxOf(endMillis, startMillis),
                    isAllDay = allDay,
                    calendarId = calendarId,
                    calendarTitle = readableName(c.getString(8).orEmpty()),
                    color = c.getInt(9),
                )
            }
        }
        return result.sortedBy { it.startMillis }
    }

    /**
     * Alcuni calendari del sistema non hanno un nome ma una **chiave** che
     * solo l'app Calendario del produttore traduce: su Xiaomi/MIUI arrivano
     * `calendar_displayname_local`, `calendar_displayname_birthday`,
     * `account_name_local`. Mostrate così sembravano un errore.
     */
    private fun readableName(raw: String): String = when (raw) {
        "calendar_displayname_local" -> context.getString(R.string.device_calendar_local_calendar)
        "calendar_displayname_birthday" -> context.getString(R.string.device_calendar_birthdays)
        "account_name_local" -> context.getString(R.string.device_calendar_local_account)
        else -> if (raw.matches(Regex("^(calendar_displayname|account_name)_[a-z_]+$"))) {
            // Altre chiavi dello stesso tipo: meglio la parte finale leggibile
            // che il nome tecnico intero.
            raw.substringAfterLast("_").replaceFirstChar { it.uppercase() }
        } else {
            raw
        }
    }

    private fun utcMidnightToLocal(millis: Long, zone: ZoneId): Long =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
            .atStartOfDay(zone).toInstant().toEpochMilli()

    private fun registerObserver() {
        if (observerRegistered) return
        runCatching {
            context.contentResolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
            observerRegistered = true
        }
    }

    private fun unregisterObserver() {
        if (!observerRegistered) return
        runCatching { context.contentResolver.unregisterContentObserver(observer) }
        observerRegistered = false
    }

    private companion object {
        const val TAG = "DeviceCalendar"
        const val PREFS = "kb_device_calendars"
        const val KEY_ENABLED = "enabled"
        const val KEY_ASKED = "asked"
        const val KEY_PROMPT_DISMISSED = "promptDismissed"
        const val KEY_HIDDEN = "hiddenCalendarIds"
    }
}
