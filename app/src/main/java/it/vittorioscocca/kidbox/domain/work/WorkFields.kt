package it.vittorioscocca.kidbox.domain.work

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

/** Tipi di documento della scheda Lavoro (uguali su iOS, Android e web). */
object WorkRecordKind {
    const val PAYSLIP = "payslip"
    const val CONTRACT = "contract"
    /** Foglio delle ferie e dei permessi: ogni periodo può diventare un evento di calendario. */
    const val LEAVE_PLAN = "leavePlan"
}

/**
 * Un periodo di assenza letto dal foglio ferie. Date `yyyy-MM-dd`; un solo
 * giorno = inizio e fine uguali. Mirror di `WorkLeavePeriod` (iOS).
 */
data class WorkLeavePeriod(
    val id: String = UUID.randomUUID().toString(),
    val start: String,
    val end: String,
    val label: String,
    /** L'utente ha scelto di metterlo nel calendario di famiglia (e l'evento esiste). */
    val inCalendar: Boolean = false,
    /** L'evento creato per questo periodo: serve a spostarlo o revocarlo. */
    val eventId: String? = null,
) {
    val startDay: LocalDate? get() = WorkFields.parseDay(start)
    val endDay: LocalDate? get() = WorkFields.parseDay(end) ?: startDay

    companion object {
        /** «Ferie» per le ferie (anche la sigla PAR del foglio), mai una sigla da sola. */
        fun cleanLabel(raw: String, ferie: String, permessoRol: String, malattia: String): String {
            val t = raw.trim()
            return when (t.lowercase()) {
                "", "par", "fer", "f", "ferie" -> ferie
                "rol" -> permessoRol
                "mal", "m" -> malattia
                else -> t.replaceFirstChar { it.uppercase() }
            }
        }
    }
}

/**
 * I valori letti da una busta paga, un contratto o un foglio ferie. Un solo
 * tipo per tutti: i campi che non c'entrano restano vuoti. JSON identico a
 * `WorkFields` di iOS (stesse chiavi), così un documento letto da un client si
 * apre sugli altri. Importi in euro, ferie in giorni, permessi in ore.
 */
data class WorkFields(
    val title: String = "",
    val employer: String = "",
    val period: String = "",
    val gross: Double? = null,
    val net: Double? = null,
    val taxes: Double? = null,
    val contributions: Double? = null,
    val vacationAccrued: Double? = null,
    val vacationTaken: Double? = null,
    val vacationRemaining: Double? = null,
    val permitHoursAccrued: Double? = null,
    val permitHoursRemaining: Double? = null,
    val tfr: Double? = null,
    val contractType: String = "",
    val startDate: String? = null,
    val endDate: String? = null,
    val grossAnnual: Double? = null,
    val level: String = "",
    val noticePeriod: String = "",
    val vacationDaysPerYear: Double? = null,
    val notes: String = "",
    val leavePeriods: List<WorkLeavePeriod> = emptyList(),
    val eventIds: List<String> = emptyList(),
) {
    fun toJson(): String {
        val o = JSONObject()
        fun s(k: String, v: String) { if (v.isNotEmpty()) o.put(k, v) }
        fun n(k: String, v: Double?) { if (v != null) o.put(k, v) }
        s("title", title); s("employer", employer); s("period", period)
        n("gross", gross); n("net", net); n("taxes", taxes); n("contributions", contributions)
        n("vacationAccrued", vacationAccrued); n("vacationTaken", vacationTaken)
        n("vacationRemaining", vacationRemaining)
        n("permitHoursAccrued", permitHoursAccrued); n("permitHoursRemaining", permitHoursRemaining)
        n("tfr", tfr)
        s("contractType", contractType)
        startDate?.let { o.put("startDate", it) }
        endDate?.let { o.put("endDate", it) }
        n("grossAnnual", grossAnnual)
        s("level", level); s("noticePeriod", noticePeriod)
        n("vacationDaysPerYear", vacationDaysPerYear)
        s("notes", notes)
        if (leavePeriods.isNotEmpty()) {
            o.put(
                "leavePeriods",
                JSONArray(
                    leavePeriods.map { p ->
                        JSONObject().apply {
                            put("start", p.start); put("end", p.end); put("label", p.label)
                            put("inCalendar", p.inCalendar)
                            p.eventId?.let { put("eventId", it) }
                        }
                    },
                ),
            )
        }
        if (eventIds.isNotEmpty()) o.put("eventIds", JSONArray(eventIds))
        return o.toString()
    }

    companion object {
        fun parseDay(s: String?): LocalDate? =
            s?.takeIf { it.length >= 10 }?.let { runCatching { LocalDate.parse(it.substring(0, 10)) }.getOrNull() }

        fun formatDay(d: LocalDate): String = d.toString()

        /** Decodifica tollerante: campi mancanti o importi scritti come testo («1.234,56»). */
        fun fromJson(json: String): WorkFields? = runCatching { fromObject(JSONObject(json)) }.getOrNull()

        fun fromObject(o: JSONObject): WorkFields {
            fun text(k: String) = o.optString(k, "").takeIf { it != "null" } ?: ""
            fun day(k: String) = o.optString(k, "").takeIf { it.isNotEmpty() && it != "null" }?.take(10)
            fun num(k: String): Double? {
                if (!o.has(k) || o.isNull(k)) return null
                val raw = o.get(k)
                if (raw is Number) return raw.toDouble()
                val cleaned = raw.toString().replace("€", "").replace(" ", "")
                val normalized = if (cleaned.contains(",")) cleaned.replace(".", "").replace(",", ".") else cleaned
                return normalized.toDoubleOrNull()
            }
            val periods = mutableListOf<WorkLeavePeriod>()
            o.optJSONArray("leavePeriods")?.let { arr ->
                for (i in 0 until arr.length()) {
                    val p = arr.optJSONObject(i) ?: continue
                    val s = p.optString("start", "").take(10)
                    val e = p.optString("end", "").take(10).ifEmpty { s }
                    periods += WorkLeavePeriod(
                        start = s, end = e, label = p.optString("label", ""),
                        inCalendar = p.optBoolean("inCalendar", false),
                        eventId = p.optString("eventId", "").takeIf { it.isNotEmpty() && it != "null" },
                    )
                }
            }
            val ids = mutableListOf<String>()
            o.optJSONArray("eventIds")?.let { arr -> for (i in 0 until arr.length()) ids += arr.optString(i) }
            return WorkFields(
                title = text("title"), employer = text("employer"), period = text("period"),
                gross = num("gross"), net = num("net"), taxes = num("taxes"), contributions = num("contributions"),
                vacationAccrued = num("vacationAccrued"), vacationTaken = num("vacationTaken"),
                vacationRemaining = num("vacationRemaining"),
                permitHoursAccrued = num("permitHoursAccrued"), permitHoursRemaining = num("permitHoursRemaining"),
                tfr = num("tfr"),
                contractType = text("contractType"), startDate = day("startDate"), endDate = day("endDate"),
                grossAnnual = num("grossAnnual"), level = text("level"), noticePeriod = text("noticePeriod"),
                vacationDaysPerYear = num("vacationDaysPerYear"), notes = text("notes"),
                leavePeriods = periods, eventIds = ids,
            )
        }

        /** Mezzanotte locale del giorno, in millisecondi. */
        fun startOfDayMillis(d: LocalDate): Long = d.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    }
}
