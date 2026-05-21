package it.vittorioscocca.kidbox.util

import org.json.JSONArray

fun encodeStringList(values: List<String>): String {
    val cleaned = values.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
    return JSONArray(cleaned).toString()
}

fun decodeStringList(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        buildList {
            for (i in 0 until arr.length()) {
                val value = arr.optString(i).trim()
                if (value.isNotEmpty()) add(value)
            }
        }
    } catch (_: Exception) {
        emptyList()
    }
}
