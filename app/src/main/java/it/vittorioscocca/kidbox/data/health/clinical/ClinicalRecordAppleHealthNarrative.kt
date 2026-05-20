package it.vittorioscocca.kidbox.data.health.clinical

import it.vittorioscocca.kidbox.data.local.entity.KBMedicalVisitEntity
import it.vittorioscocca.kidbox.domain.model.HealthImportSnapshot
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.regex.Pattern

/** Sezione Health Connect / wearable (allineato a iOS Apple Salute). */
object ClinicalRecordAppleHealthNarrative {

    const val AREA_ID = "apple_health"
    const val SECTION_TITLE = "Health Connect / Wearable"

    private const val DISCLAIMER =
        "I seguenti dati provengono da dispositivo wearable consumer (Apple Watch o compatibile) e hanno valore indicativo, non diagnostico."

    data class Analysis(
        val narrative: String,
        val summary: String,
        val highlights: List<String>,
    )

    fun analyze(
        snapshot: HealthImportSnapshot,
        birthDateEpochMillis: Long?,
        visits: List<KBMedicalVisitEntity>,
    ): Analysis? {
        if (!snapshot.hasCardiacOrActivity && !snapshot.hasWearableExtendedMetrics) return null
        val activity = classifyActivity(snapshot)
        val parts = mutableListOf<String>()
        parts += DISCLAIMER
        parts += "I dati rilevati dal wearable ${wearablePeriodPhrase(snapshot)} mostrano quanto segue."

        (snapshot.restingHeartRateAvg90d ?: snapshot.heartRateBpm)?.let { rhr ->
            var s = "La frequenza cardiaca a riposo media è di ${rhr.toInt()} bpm"
            cardiologistHeartRateHint(visits)?.let { (bpm, date) ->
                s += ", coerente con i $bpm bpm documentati alla visita cardiologica del ${formatShort(date)}"
            }
            parts += "$s, indicativa di buona efficienza cardiovascolare nel contesto consumer."
        }

        (snapshot.vo2MaxRecent ?: snapshot.vo2Max)?.let { vo2 ->
            val band = vo2MaxBand(vo2, birthDateEpochMillis)
            parts += "Il VO₂ max stimato risulta di ${"%.0f".format(Locale.US, vo2)} ml/kg/min, collocandosi nella fascia «${band.label}»${band.ageContext}."
        }

        snapshot.weeklyExerciseMinutesAvg?.takeIf { it > 0 }?.let { weekly ->
            val oms = if (weekly >= 150) "superano regolarmente i 150 minuti settimanali raccomandati dall'OMS"
            else "risultano inferiori ai 150 minuti settimanali raccomandati dall'OMS"
            parts += "I minuti di attività fisica vigorosa settimanali (media) $oms (circa ${weekly.toInt()} min/settimana)."
        } ?: (snapshot.stepsDailyAvg90d ?: averageDailySteps(snapshot))?.let { steps ->
            parts += "La media di passi giornalieri è di circa ${steps.toInt()}, utile come indice di movimento quotidiano."
        }

        snapshot.spo2NightlyAvgPercent?.let { spo2 ->
            parts += "La SpO₂ notturna media si mantiene al ${spo2.toInt()}%, escludendo su base indicativa episodi significativi di desaturazione."
        }

        snapshot.hrvSdnnMsAvg90d?.let { hrv ->
            parts += "La variabilità cardiaca (HRV) media è di ${hrv.toInt()} ms, da interpretare solo come trend benessere."
        }

        parts += "Complessivamente, il profilo da wearable è coerente con ${activity.activityPhrase}, pur richiedendo conferma strumentale per qualsiasi valutazione diagnostica."

        return Analysis(
            narrative = parts.joinToString(" "),
            summary = activity.summaryLabel,
            highlights = buildHighlights(snapshot, activity),
        )
    }

    fun documentLines(
        snapshot: HealthImportSnapshot,
        sourceLabel: String,
        birthDateEpochMillis: Long?,
        visits: List<KBMedicalVisitEntity>,
    ): List<String> {
        val analysis = analyze(snapshot, birthDateEpochMillis, visits) ?: return emptyList()
        return listOf(
            "---",
            "DATI $sourceLabel / WEARABLE",
            "",
            analysis.narrative,
        )
    }

    fun reportArea(
        snapshot: HealthImportSnapshot,
        sourceLabel: String,
        birthDateEpochMillis: Long?,
        visits: List<KBMedicalVisitEntity>,
    ): ClinicalRecordReportArea? {
        val analysis = analyze(snapshot, birthDateEpochMillis, visits) ?: return null
        return ClinicalRecordReportArea(
            id = AREA_ID,
            title = SECTION_TITLE,
            summary = analysis.summary,
            narrative = analysis.narrative,
            trendNarrative = null,
            bullets = analysis.highlights,
            analisiNarrativa = analysis.narrative,
        )
    }

    private data class ActivityProfile(val summaryLabel: String, val activityPhrase: String)

    private data class Vo2Band(val label: String, val ageContext: String)

    private fun classifyActivity(s: HealthImportSnapshot): ActivityProfile {
        val cutoff = System.currentTimeMillis() - 14L * 24 * 3600 * 1000
        val workouts14 = s.recentWorkouts.count { it.startedAtEpochMillis >= cutoff }
        val workoutMin = s.recentWorkouts.filter { it.startedAtEpochMillis >= cutoff }
            .sumOf { it.durationMinutes ?: 0 }
        val avgSteps = s.stepsDailyAvg90d ?: averageDailySteps(s) ?: (s.stepsToday?.toDouble() ?: 0.0)
        val weekly = s.weeklyExerciseMinutesAvg ?: 0.0
        return when {
            workouts14 >= 4 || workoutMin >= 120 || weekly >= 150 ->
                ActivityProfile("Pratica sportiva regolare", "uno stile di vita attivo e un buon compenso cardiovascolare")
            workouts14 >= 2 || avgSteps >= 9_000 || weekly >= 90 ->
                ActivityProfile("Attività fisica regolare", "un'attività fisica regolare")
            workouts14 >= 1 || avgSteps >= 6_000 || weekly >= 45 ->
                ActivityProfile("Attività moderata", "un'attività fisica moderata")
            avgSteps >= 3_500 ->
                ActivityProfile("Attività leggera", "un'attività quotidiana leggera")
            else ->
                ActivityProfile("Vita prevalentemente sedentaria", "uno stile di vita prevalentemente sedentario")
        }
    }

    private fun buildHighlights(snapshot: HealthImportSnapshot, activity: ActivityProfile): List<String> =
        buildList {
            add(activity.summaryLabel)
            (snapshot.restingHeartRateAvg90d ?: snapshot.heartRateBpm)?.let {
                add("FC a riposo media: ${it.toInt()} bpm")
            }
            (snapshot.vo2MaxRecent ?: snapshot.vo2Max)?.let {
                add("VO₂ max: ${"%.0f".format(Locale.US, it)} ml/kg/min")
            }
            snapshot.stepsDailyAvg90d?.let { add("Passi medi/die: ${it.toInt()}") }
        }

    private fun vo2MaxBand(vo2: Double, birthMillis: Long?): Vo2Band {
        val age = birthMillis?.let {
            val cal = java.util.Calendar.getInstance()
            val birth = cal.apply { timeInMillis = it }
            val now = java.util.Calendar.getInstance()
            now.get(java.util.Calendar.YEAR) - birth.get(java.util.Calendar.YEAR)
        } ?: 40
        val ageCtx = if (birthMillis != null) " per l'età di circa $age anni" else ""
        val label = when (age) {
            in 0..29 -> if (vo2 >= 48) "Eccellente" else if (vo2 >= 42) "Buono" else if (vo2 >= 36) "Discreto" else "Da migliorare"
            in 30..39 -> if (vo2 >= 44) "Eccellente" else if (vo2 >= 40) "Buono" else if (vo2 >= 34) "Discreto" else "Da migliorare"
            in 40..49 -> if (vo2 >= 40) "Eccellente" else if (vo2 >= 36) "Buono" else if (vo2 >= 30) "Discreto" else "Da migliorare"
            in 50..59 -> if (vo2 >= 36) "Eccellente" else if (vo2 >= 32) "Buono" else if (vo2 >= 26) "Discreto" else "Da migliorare"
            else -> if (vo2 >= 32) "Buono" else if (vo2 >= 26) "Discreto" else "Da migliorare"
        }
        return Vo2Band(label, ageCtx)
    }

    private fun cardiologistHeartRateHint(visits: List<KBMedicalVisitEntity>): Pair<Int, Long>? {
        val pattern = Pattern.compile("(?i)(?:FC|frequenza cardiaca|polso)[^\\d]{0,20}(\\d{2,3})\\s*bpm")
        visits.sortedByDescending { it.dateEpochMillis }.forEach { v ->
            val blob = listOfNotNull(v.diagnosis, v.notes, v.recommendations, v.reason).joinToString(" ")
            val m = pattern.matcher(blob)
            if (m.find()) {
                val bpm = m.group(1)?.toIntOrNull() ?: return@forEach
                if (bpm in 40..220) return bpm to v.dateEpochMillis
            }
        }
        return null
    }

    private fun wearablePeriodPhrase(s: HealthImportSnapshot): String {
        val start = s.wearablePeriodStartEpochMillis
        val end = s.wearablePeriodEndEpochMillis
        return if (start != null && end != null) {
            "nel periodo ${formatShort(start)}–${formatShort(end)}"
        } else {
            "negli ultimi tre mesi"
        }
    }

    private fun averageDailySteps(s: HealthImportSnapshot): Double? {
        val vals = s.recentDailyActivity.mapNotNull { it.steps }.filter { it > 0 }
        if (vals.isEmpty()) return null
        return vals.sum().toDouble() / vals.size
    }

    private fun formatShort(epoch: Long): String =
        DateFormat.getDateInstance(DateFormat.SHORT, Locale.getDefault()).format(Date(epoch))
}
