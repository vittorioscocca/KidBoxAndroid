package it.vittorioscocca.kidbox.data.vehicles

import org.json.JSONArray
import org.json.JSONObject

/**
 * Offset di preavviso (in giorni) configurabili per ciascuna scadenza veicolo.
 * Valori ammessi: 0 (giorno stesso), 2 (2 giorni prima), 7 (1 settimana prima) — fino a 3 attivi per scadenza.
 */
data class VehicleReminderOffsets(
    val insurance: List<Int> = DEFAULT_OFFSETS,
    val revision: List<Int> = DEFAULT_OFFSETS,
    val tax: List<Int> = DEFAULT_OFFSETS,
    val service: List<Int> = DEFAULT_OFFSETS,
) {
    fun offsets(kind: String): List<Int> = when (kind) {
        "insurance" -> insurance
        "revision" -> revision
        "tax" -> tax
        "service" -> service
        else -> emptyList()
    }

    fun encode(): String {
        val root = JSONObject()
        root.put("insurance", JSONArray(insurance))
        root.put("revision", JSONArray(revision))
        root.put("tax", JSONArray(tax))
        root.put("service", JSONArray(service))
        return root.toString()
    }

    fun toFirestoreMap(): Map<String, List<Int>> =
        mapOf("insurance" to insurance, "revision" to revision, "tax" to tax, "service" to service)

    companion object {
        val ALLOWED_OFFSETS = listOf(0, 2, 7)
        val DEFAULT_OFFSETS = listOf(0, 7)
        val DEFAULT = VehicleReminderOffsets()

        fun decode(json: String?): VehicleReminderOffsets {
            if (json.isNullOrBlank()) return DEFAULT
            return try {
                val root = JSONObject(json)
                VehicleReminderOffsets(
                    insurance = root.optJSONArray("insurance").toIntList(),
                    revision = root.optJSONArray("revision").toIntList(),
                    tax = root.optJSONArray("tax").toIntList(),
                    service = root.optJSONArray("service").toIntList(),
                )
            } catch (e: Exception) {
                DEFAULT
            }
        }

        fun fromFirestoreMap(map: Map<*, *>?): VehicleReminderOffsets {
            if (map == null) return DEFAULT
            fun list(key: String): List<Int> {
                val raw = map[key] as? List<*> ?: return DEFAULT_OFFSETS
                return raw.mapNotNull { (it as? Number)?.toInt() }
            }
            return VehicleReminderOffsets(
                insurance = list("insurance"),
                revision = list("revision"),
                tax = list("tax"),
                service = list("service"),
            )
        }

        private fun JSONArray?.toIntList(): List<Int> {
            if (this == null) return DEFAULT_OFFSETS
            return (0 until length()).map { getInt(it) }
        }
    }
}
