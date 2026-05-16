package it.vittorioscocca.kidbox.ui.screens.ai.planning

import it.vittorioscocca.kidbox.data.local.dao.KBChildDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalExamDao
import it.vittorioscocca.kidbox.data.local.dao.KBMedicalVisitDao
import it.vittorioscocca.kidbox.data.local.dao.KBPediatricProfileDao
import it.vittorioscocca.kidbox.data.local.dao.KBTreatmentDao
import it.vittorioscocca.kidbox.data.local.dao.KBVaccineDao
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthPatternDataMessageBuilder @Inject constructor(
    private val childDao: KBChildDao,
    private val medicalVisitDao: KBMedicalVisitDao,
    private val treatmentDao: KBTreatmentDao,
    private val vaccineDao: KBVaccineDao,
    private val medicalExamDao: KBMedicalExamDao,
    private val pediatricProfileDao: KBPediatricProfileDao,
) {
    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.ITALIAN)
    private val todayFmt = SimpleDateFormat("d MMMM yyyy", Locale.ITALIAN)

    suspend fun buildHealthDataMessage(familyId: String, familyName: String): String {
        val sb = StringBuilder()
        sb.appendLine("STORIA SANITARIA FAMIGLIA $familyName — analisi al ${todayFmt.format(Date())}")
        sb.appendLine()

        val children = childDao.getChildrenByFamilyId(familyId)
            .sortedBy { it.birthDateEpochMillis ?: Long.MAX_VALUE }

        for (child in children) {
            val birthMillis = child.birthDateEpochMillis
            val birthDateStr = birthMillis?.let { dateFmt.format(Date(it)) } ?: "N/D"
            val ageYears = birthMillis?.let {
                ((System.currentTimeMillis() - it) / (365.25 * 24 * 3600 * 1000)).toInt()
            }
            val ageLabel = ageYears?.let { "$it anni" } ?: "età N/D"

            sb.appendLine("=== ${child.name} — nato il $birthDateStr ($ageLabel) ===")
            sb.appendLine()

            val profile = pediatricProfileDao.getByChildId(child.id)
            val blood = profile?.bloodGroup?.takeIf { it.isNotBlank() } ?: "N/D"
            val allergies = profile?.allergies?.takeIf { it.isNotBlank() } ?: "nessuna"
            sb.appendLine("PROFILO: gruppo sanguigno: $blood, allergie: $allergies")

            if (child.weightKg != null || child.heightCm != null) {
                val parts = buildList {
                    child.weightKg?.let { add("peso ${"%.1f".format(Locale.ITALIAN, it)} kg") }
                    child.heightCm?.let { add("altezza ${it.toInt()} cm") }
                }
                if (parts.isNotEmpty()) {
                    sb.appendLine("ANTROPOMETRIA: ${parts.joinToString(", ")}")
                }
            }
            sb.appendLine()

            val visits = medicalVisitDao.listRecentForChild(familyId, child.id, limit = 500)
                .sortedBy { it.dateEpochMillis }
                .take(30)
            sb.appendLine("VISITE (${visits.size} totali):")
            if (visits.isEmpty()) {
                sb.appendLine("  (nessuna)")
            } else {
                for (v in visits) {
                    val dateStr = dateFmt.format(Date(v.dateEpochMillis))
                    val diag = v.diagnosis?.takeIf { it.isNotBlank() } ?: "nessuna diagnosi"
                    val spec = v.doctorSpecializationRaw?.takeIf { it.isNotBlank() } ?: "generico"
                    sb.appendLine("  • $dateStr: ${v.reason} — $diag [$spec]")
                }
            }
            sb.appendLine()

            val treatments = treatmentDao.listByFamilyAndChild(familyId, child.id)
                .filter { it.petId.isBlank() }
                .sortedBy { it.startDateEpochMillis }
            sb.appendLine("FARMACI (${treatments.size} totali):")
            if (treatments.isEmpty()) {
                sb.appendLine("  (nessuno)")
            } else {
                for (t in treatments) {
                    val startStr = dateFmt.format(Date(t.startDateEpochMillis))
                    val chronic = if (t.isLongTerm) " [cronico]" else ""
                    sb.appendLine("  • $startStr: ${t.drugName} (${t.durationDays}gg)$chronic")
                }
            }
            sb.appendLine()

            val vaccines = vaccineDao.listByFamilyAndChild(familyId, child.id)
                .sortedBy { it.scheduledDateEpochMillis ?: it.administeredDateEpochMillis ?: 0L }
            sb.appendLine("VACCINI:")
            if (vaccines.isEmpty()) {
                sb.appendLine("  (nessuno)")
            } else {
                for (v in vaccines) {
                    val dateStr = v.administeredDateEpochMillis?.let { dateFmt.format(Date(it)) }
                        ?: v.scheduledDateEpochMillis?.let { dateFmt.format(Date(it)) }
                        ?: "N/D"
                    val label = v.name.takeIf { it.isNotBlank() } ?: v.vaccineTypeRaw
                    sb.appendLine("  • $label: ${v.statusRaw} — $dateStr")
                }
            }
            sb.appendLine()

            val exams = medicalExamDao.listByFamilyAndChild(familyId, child.id)
            sb.appendLine("ESAMI:")
            if (exams.isEmpty()) {
                sb.appendLine("  (nessuno)")
            } else {
                for (e in exams) {
                    val deadline = e.deadlineEpochMillis?.let { dateFmt.format(Date(it)) } ?: "nessuna"
                    sb.appendLine("  • ${e.name}: ${e.statusRaw} — scadenza: $deadline")
                }
            }
            sb.appendLine("---")
            sb.appendLine()
        }

        sb.append("Cerca pattern che un genitore non noterebbe guardando i singoli eventi.")
        return sb.toString()
    }
}
