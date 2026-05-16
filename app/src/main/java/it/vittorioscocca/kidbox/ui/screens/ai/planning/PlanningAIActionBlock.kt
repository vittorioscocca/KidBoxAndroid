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
)

object PlanningAIActionBlock {
    val promptSection: String
        get() = """
            AZIONI ESEGUIBILI (obbligatorio quando modifichi dati nell'app):
            Se confermi di aver aggiunto lista spesa, to-do, nota, calendario o promemoria salute,
            includi SEMPRE alla fine del messaggio (l'app lo nasconde) un blocco JSON:

            ${PlanningAIActionMarkers.START}
            [{"type":"grocery_add","items":["latte","pane"]}]
            ${PlanningAIActionMarkers.END}

            Tipi (date ISO8601 UTC): grocery_add, todo_add, event_add, note_add, health_reminder.
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
