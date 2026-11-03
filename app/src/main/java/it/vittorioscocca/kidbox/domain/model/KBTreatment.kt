package it.vittorioscocca.kidbox.domain.model

import android.content.res.Resources
import it.vittorioscocca.kidbox.R
import kotlin.math.max

private val SLOT_LABELS = listOf(
    R.string.treatment_period_morning,
    R.string.treatment_period_lunch,
    R.string.treatment_period_evening,
    R.string.treatment_period_night,
)

fun slotLabelFor(res: Resources, index: Int): String =
    if (index < SLOT_LABELS.size) res.getString(SLOT_LABELS[index]) else res.getString(R.string.treatment_dose_n, index + 1)

/** Terapia farmacologica — allineato a [KBTreatment] iOS. */
data class KBTreatment(
    val id: String,
    val familyId: String,
    val childId: String,
    /** Cura legata a un animale; vuoto se cura pediatrica. */
    val petId: String = "",
    /** Visita che ha prescritto la cura (se collegata da modulo visita). */
    val prescribingVisitId: String? = null,
    val drugName: String,
    val activeIngredient: String?,
    val dosageValue: Double,
    val dosageUnit: String,
    val isLongTerm: Boolean,
    val durationDays: Int,
    val startDateEpochMillis: Long,
    val endDateEpochMillis: Long?,
    val dailyFrequency: Int,
    /**
     * Se > 0: una dose ogni N giorni di calendario da [startDateEpochMillis], un solo orario in [scheduleTimesData].
     * Se 0: frequenza classica [dailyFrequency] volte al giorno.
     */
    val intervalBetweenDosesDays: Int = 0,
    val scheduleTimesData: String,
    val isActive: Boolean,
    val notes: String?,
    val reminderEnabled: Boolean,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val createdBy: String?,
    val syncStatus: Int,
    val lastSyncError: String?,
    val syncStateRaw: Int,
)

val KBTreatment.usesIntervalSchedule: Boolean get() = intervalBetweenDosesDays > 0

/** [dayNumber1Based] = 1 il primo giorno della cura (stesso significato usato nei dose log Android). */
fun KBTreatment.isScheduledDoseDayTherapeutic(dayNumber1Based: Int): Boolean {
    if (intervalBetweenDosesDays <= 0) return true
    val n = intervalBetweenDosesDays
    if (dayNumber1Based < 1) return false
    return (dayNumber1Based - 1) % n == 0
}

fun KBTreatment.frequencyDisplayLabel(res: Resources): String =
    if (intervalBetweenDosesDays > 0) {
        res.getQuantityString(R.plurals.health_every_n_days, intervalBetweenDosesDays, intervalBetweenDosesDays)
    } else {
        res.getQuantityString(R.plurals.health_times_per_day, dailyFrequency, dailyFrequency)
    }

/** Dosi pianificate totali per cure a durata finita (allineato a iOS [totalDoses]). */
fun KBTreatment.plannedFiniteDosesTotal(): Int {
    if (isLongTerm) return -1
    return if (intervalBetweenDosesDays > 0) {
        val n = intervalBetweenDosesDays
        max(1, (durationDays + n - 1) / n)
    } else {
        dailyFrequency * durationDays
    }
}
