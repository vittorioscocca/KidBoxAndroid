package it.vittorioscocca.kidbox.ui.screens.life

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import java.util.Calendar
import java.util.Locale

@Composable
fun rememberLifeDatePicker(
    onPicked: (Long) -> Unit,
): (Long?) -> Unit {
    val context = LocalContext.current
    val onPickedState = rememberUpdatedState(onPicked)
    return remember(context) {
        { initialMillis: Long? ->
            val cal = Calendar.getInstance(Locale.ITALY)
            initialMillis?.let { cal.timeInMillis = it }
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    val c = Calendar.getInstance(Locale.ITALY)
                    c.set(Calendar.YEAR, y)
                    c.set(Calendar.MONTH, m)
                    c.set(Calendar.DAY_OF_MONTH, d)
                    c.set(Calendar.HOUR_OF_DAY, 12)
                    c.set(Calendar.MINUTE, 0)
                    c.set(Calendar.SECOND, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    onPickedState.value(c.timeInMillis)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            ).show()
        }
    }
}

/** Come [rememberLifeDatePicker] ma mantiene ora, minuti e secondi del timestamp corrente (stile iOS data+ora). */
@Composable
fun rememberLifeDatePickerKeepingTime(
    onPicked: (Long) -> Unit,
): (Long?) -> Unit {
    val context = LocalContext.current
    val onPickedState = rememberUpdatedState(onPicked)
    return remember(context) {
        { initialMillis: Long? ->
            val cal = Calendar.getInstance(Locale.ITALY)
            if (initialMillis != null) cal.timeInMillis = initialMillis
            DatePickerDialog(
                context,
                { _, y, m, d ->
                    val c = Calendar.getInstance(Locale.ITALY)
                    if (initialMillis != null) c.timeInMillis = initialMillis
                    c.set(Calendar.YEAR, y)
                    c.set(Calendar.MONTH, m)
                    c.set(Calendar.DAY_OF_MONTH, d)
                    c.set(Calendar.SECOND, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    onPickedState.value(c.timeInMillis)
                },
                cal.get(Calendar.YEAR),
                cal.get(Calendar.MONTH),
                cal.get(Calendar.DAY_OF_MONTH),
            ).show()
        }
    }
}

@Composable
fun rememberLifeTimePicker(
    onPicked: (Long) -> Unit,
): (Long?) -> Unit {
    val context = LocalContext.current
    val onPickedState = rememberUpdatedState(onPicked)
    return remember(context) {
        { initialMillis: Long? ->
            val cal = Calendar.getInstance(Locale.ITALY)
            if (initialMillis != null) cal.timeInMillis = initialMillis
            TimePickerDialog(
                context,
                { _, hour, minute ->
                    val c = Calendar.getInstance(Locale.ITALY)
                    if (initialMillis != null) c.timeInMillis = initialMillis
                    c.set(Calendar.HOUR_OF_DAY, hour)
                    c.set(Calendar.MINUTE, minute)
                    c.set(Calendar.SECOND, 0)
                    c.set(Calendar.MILLISECOND, 0)
                    onPickedState.value(c.timeInMillis)
                },
                cal.get(Calendar.HOUR_OF_DAY),
                cal.get(Calendar.MINUTE),
                DateFormat.is24HourFormat(context),
            ).show()
        }
    }
}
