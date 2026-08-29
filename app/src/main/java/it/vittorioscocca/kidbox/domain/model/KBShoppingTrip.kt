package it.vittorioscocca.kidbox.domain.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Una riga dello scontrino, congelata al momento del salvataggio: se il prodotto
 * viene poi rinominato o cancellato dalla lista, lo storico non cambia.
 */
data class KBShoppingTripLine(
    val name: String,
    val quantity: Int?,
)

/** Spesa fatta — allineato a `KBShoppingTrip` iOS. */
data class KBShoppingTrip(
    val id: String,
    val familyId: String,
    val storeName: String?,
    val total: Double,
    val dateEpochMillis: Long,
    val lines: List<KBShoppingTripLine>,
    val notes: String?,
    val linkedExpenseId: String?,
    val isDeleted: Boolean,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val updatedBy: String?,
    val createdBy: String?,
)

/**
 * Serializzazione delle righe. Stesso formato del `Codable` iOS — array di
 * oggetti `{"name": …, "quantity": …}`, con `quantity` assente quando è una sola
 * confezione — così i due client leggono lo stesso documento.
 */
object ShoppingTripLines {

    fun encode(lines: List<KBShoppingTripLine>): String {
        val array = JSONArray()
        lines.forEach { line ->
            val obj = JSONObject()
            obj.put("name", line.name)
            line.quantity?.let { obj.put("quantity", it) }
            array.put(obj)
        }
        return array.toString()
    }

    /** Un JSON illeggibile non fa sparire lo scontrino: restano negozio, totale e data. */
    fun decode(json: String?): List<KBShoppingTripLine> {
        if (json.isNullOrBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(json)
            (0 until array.length()).mapNotNull { index ->
                val obj = array.optJSONObject(index) ?: return@mapNotNull null
                val name = obj.optString("name").trim()
                if (name.isEmpty()) return@mapNotNull null
                KBShoppingTripLine(
                    name = name,
                    quantity = if (obj.has("quantity")) obj.optInt("quantity").takeIf { it > 0 } else null,
                )
            }
        }.getOrDefault(emptyList())
    }
}
