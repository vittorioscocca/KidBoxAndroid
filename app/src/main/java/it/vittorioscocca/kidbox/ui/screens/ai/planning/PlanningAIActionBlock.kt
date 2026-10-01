package it.vittorioscocca.kidbox.ui.screens.ai.planning

import org.json.JSONArray
import org.json.JSONObject

object PlanningAIActionMarkers {
    const val START = "<<<KIDBOX_ACTIONS>>>"
    const val END = "<<<END_KIDBOX_ACTIONS>>>"
}

data class PlanningAIProcessedReply(
    val displayText: String,
    val actions: List<PlanningExecutableActionDto>,
)

data class PlanningExecutableActionDto(
    val type: String,
    val items: List<String>? = null,
    val title: String? = null,
    val body: String? = null,
    val notes: String? = null,
    val category: String? = null,
    val dueAt: String? = null,
    val startAt: String? = null,
    val endAt: String? = null,
    val isAllDay: Boolean? = null,
    val childId: String? = null,
    val listId: String? = null,
    /** `request_add`: nomi dei familiari a cui chiedere (vuoto = tutti). */
    val askMembers: List<String>? = null,
    /** `request_add`: chiedere anche fuori dall'app, con un link. */
    val askOutside: Boolean? = null,
    val outsideLabel: String? = null,
    val includeInvite: Boolean? = null,
)

object PlanningAIActionBlock {
    /**
     * Fuso e prossimi giorni, davanti alle azioni. Senza, il modello scriveva
     * l'ora italiana con la «Z» (un evento delle 16:30 finiva alle 18:30) e
     * sbagliava il giorno della settimana. Stesso testo su iOS e web.
     */
    fun dateHeader(now: java.time.ZonedDateTime = java.time.ZonedDateTime.now()): String {
        val offset = now.offset.id.let { if (it == "Z") "+00:00" else it }
        val locale = java.util.Locale.ITALIAN
        val today = java.time.format.DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale).format(now)
        val dayFmt = java.time.format.DateTimeFormatter.ofPattern("EEEE d/M", locale)
        val next = (1..7).joinToString(", ") { dayFmt.format(now.plusDays(it.toLong())) }
        return "DATE E ORE: l'utente è nel fuso ${now.zone.id} (ora UTC$offset). Scrivi ogni data con questo offset, " +
            "es. \"2026-10-03T16:30:00$offset\" per le 16:30 locali: MAI la Z.\n" +
            "Oggi è $today. Prossimi giorni: $next."
    }

    val promptSection: String
        get() = dateHeader() + "\n\n" + """
            AZIONI ESEGUIBILI (obbligatorio quando modifichi dati nell'app):
            Se confermi di aver aggiunto lista spesa, to-do, nota, calendario o promemoria salute,
            includi SEMPRE alla fine del messaggio (l'app lo nasconde) un blocco JSON:

            ${PlanningAIActionMarkers.START}
            [{"type":"grocery_add","items":["latte","pane"]}]
            ${PlanningAIActionMarkers.END}

            Tipi (date ISO8601 con l'offset del fuso, vedi DATE E ORE): grocery_add, todo_add, event_add, note_add, health_reminder, request_add.
            - request_add: {"type":"request_add","title":"...","dueAt":"...","notes":"...","askMembers":["Luca"],"askOutside":false,"outsideLabel":"Nonna"}

            RICHIESTE (request_add, non todo_add): quando l'utente cerca QUALCUNO che faccia una cosa
            («serve qualcuno per…», «chiedi a Luca se può…», «chi può prendere Marco?»). I familiari ricevono una notifica
            con «Ci penso io» / «Non posso» e il primo che accetta si prende il to-do.
            askMembers: i nomi dei familiari che l'utente ha indicato, come li ha scritti; omettilo per chiedere a tutti.
            askOutside true SOLO se l'utente nomina qualcuno che non è in famiglia (nonni, babysitter…), con outsideLabel = come lo chiama:
            l'app prepara un link da mandargli. dueAt con l'ora esatta: se l'utente dice solo «mattina», «pomeriggio» o «sera»,
            chiedi l'ora e NON includere il blocco finché non la sai. A chi è fuori dall'app NON arriva nessuna notifica:
            di' che l'app prepara un link da mandargli. Non dire chi è libero o occupato: l'app non lo sa.

            NON dire "ho aggiunto" senza il blocco quando l'utente chiede un'aggiunta concreta.
            Vale in ogni chat KidBox (pianificazione, salute, visite, esami).
        """.trimIndent()

    fun process(text: String): PlanningAIProcessedReply {
        val markerStart = PlanningAIActionMarkers.START
        val markerEnd = PlanningAIActionMarkers.END
        val start = text.indexOf(markerStart)
        val end = text.indexOf(markerEnd, startIndex = if (start >= 0) start + markerStart.length else 0)
        if (start < 0 || end < 0 || end <= start) {
            return PlanningAIProcessedReply(displayText = text, actions = emptyList())
        }
        val json = text.substring(start + markerStart.length, end).trim()
        val display = buildString {
            append(text.substring(0, start).trim())
            val tail = text.substring(end + markerEnd.length).trim()
            if (isNotEmpty() && tail.isNotEmpty()) append("\n\n")
            append(tail)
        }.trim()
        val actions = runCatching { parseActionsJson(json) }.getOrNull().orEmpty()
        return PlanningAIProcessedReply(displayText = display, actions = actions)
    }

    private fun parseActionsJson(json: String): List<PlanningExecutableActionDto> {
        val array = JSONArray(json)
        return buildList {
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                add(
                    PlanningExecutableActionDto(
                        type = obj.getString("type"),
                        items = obj.optJSONArray("items")?.toStringList(),
                        title = obj.optStringOrNull("title"),
                        body = obj.optStringOrNull("body"),
                        notes = obj.optStringOrNull("notes"),
                        category = obj.optStringOrNull("category"),
                        dueAt = obj.optStringOrNull("dueAt"),
                        startAt = obj.optStringOrNull("startAt"),
                        endAt = obj.optStringOrNull("endAt"),
                        isAllDay = if (obj.has("isAllDay")) obj.optBoolean("isAllDay") else null,
                        childId = obj.optStringOrNull("childId"),
                        listId = obj.optStringOrNull("listId"),
                        askMembers = obj.optJSONArray("askMembers")?.toStringList(),
                        askOutside = if (obj.has("askOutside")) obj.optBoolean("askOutside") else null,
                        outsideLabel = obj.optStringOrNull("outsideLabel"),
                        includeInvite = if (obj.has("includeInvite")) obj.optBoolean("includeInvite") else null,
                    ),
                )
            }
        }
    }

    private fun JSONArray.toStringList(): List<String> = buildList {
        for (i in 0 until length()) {
            optString(i)?.trim()?.takeIf { it.isNotEmpty() }?.let { add(it) }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? =
        if (has(key) && !isNull(key)) optString(key).takeIf { it.isNotBlank() } else null
}
