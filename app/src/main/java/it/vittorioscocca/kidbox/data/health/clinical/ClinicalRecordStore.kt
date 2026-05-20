package it.vittorioscocca.kidbox.data.health.clinical

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ClinicalRecordStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private fun directory(): File {
        val dir = File(context.filesDir, "clinical_records")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun pdfFile(childId: String): File = File(directory(), "cartella_clinica_$childId.pdf")

    fun exists(childId: String): Boolean = pdfFile(childId).exists()

    fun generatedAtEpochMillis(childId: String): Long? {
        val file = pdfFile(childId)
        return if (file.exists()) file.lastModified() else loadReport(childId)?.generatedAtEpochMillis
    }

    fun saveReport(childId: String, report: ClinicalRecordReport) {
        val o = JSONObject().apply {
            put("subjectName", report.subjectName)
            put("sourceNative", report.sourceNative)
            put("sourceAiEnhanced", report.sourceAiEnhanced)
            put("generatedAtEpochMillis", report.generatedAtEpochMillis)
            put("fullDocumentLines", JSONArray(report.fullDocumentLines))
            put("areas", JSONArray(report.areas.map { encodeArea(it) }))
            report.globalSummary?.let { put("globalSummary", encodeGlobal(it)) }
        }
        File(directory(), "report_$childId.json").writeText(o.toString())
    }

    fun loadReport(childId: String): ClinicalRecordReport? {
        val file = File(directory(), "report_$childId.json")
        if (!file.exists()) return null
        return runCatching {
            val o = JSONObject(file.readText())
            ClinicalRecordReport(
                subjectName = o.getString("subjectName"),
                sourceNative = o.optBoolean("sourceNative", true),
                sourceAiEnhanced = o.optBoolean("sourceAiEnhanced", false),
                generatedAtEpochMillis = o.optLong("generatedAtEpochMillis", file.lastModified()),
                fullDocumentLines = o.optJSONArray("fullDocumentLines")?.let { arr ->
                    List(arr.length()) { arr.getString(it) }
                } ?: emptyList(),
                areas = o.optJSONArray("areas")?.let { arr ->
                    List(arr.length()) { decodeArea(arr.getJSONObject(it)) }
                } ?: emptyList(),
                globalSummary = o.optJSONObject("globalSummary")?.let { decodeGlobal(it) },
            )
        }.getOrNull()
    }

    private fun encodeArea(a: ClinicalRecordReportArea): JSONObject = JSONObject().apply {
        put("id", a.id)
        put("title", a.title)
        put("summary", a.summary)
        put("narrative", a.narrative)
        put("bullets", JSONArray(a.bullets))
        a.analisiNarrativa?.let { put("analisiNarrativa", it) }
    }

    private fun decodeArea(o: JSONObject) = ClinicalRecordReportArea(
        id = o.getString("id"),
        title = o.getString("title"),
        summary = o.optString("summary", ""),
        narrative = o.optString("narrative", ""),
        trendNarrative = null,
        bullets = o.optJSONArray("bullets")?.let { arr ->
            List(arr.length()) { arr.getString(it) }
        } ?: emptyList(),
        analisiNarrativa = o.optString("analisiNarrativa").takeIf { it.isNotBlank() },
    )

    private fun encodeGlobal(g: ClinicalRecordGlobalSummary): JSONObject = JSONObject().apply {
        put("monitoredSpecialtiesCount", g.monitoredSpecialtiesCount)
        put("attentionCount", g.attentionCount)
        put("lastUpdatedEpochMillis", g.lastUpdatedEpochMillis)
    }

    private fun decodeGlobal(o: JSONObject) = ClinicalRecordGlobalSummary(
        monitoredSpecialtiesCount = o.optInt("monitoredSpecialtiesCount"),
        attentionCount = o.optInt("attentionCount"),
        lastUpdatedEpochMillis = o.optLong("lastUpdatedEpochMillis"),
        activeTherapyNames = emptyList(),
        nextAppointmentLine = null,
        statusLines = emptyList(),
    )
}
