package it.vittorioscocca.kidbox.ui.screens.pharmacies

/**
 * Le risposte di `getPharmacies` (functions/pharmacies). Gemello di
 * `PharmacyModels.swift`. Il disegno è in internal/farmacie-di-turno.md.
 */
data class PharmacyShift(
    val date: String,
    val name: String,
    val comune: String = "",
    val address: String = "",
    val phone: String = "",
) {
    val id: String get() = "$date|$name|$comune|$address"

    /** Solo cifre e «+»: quello che `tel:` accetta. */
    val dialNumber: String get() = phone.filter { it.isDigit() || it == '+' }

    /** Indirizzo e comune per la ricerca su Mappe. */
    val mapQuery: String get() = listOf(name, address, comune).filter { it.isNotEmpty() }.joinToString(", ")
}

data class PharmacyDay(val date: String, val items: List<PharmacyShift>)

/**
 * Da dove vengono i turni. [confidence]: verified (scritta da noi), ai (ente
 * ufficiale trovato dall'AI), uncertain (da verificare), manual (scelta dall'utente).
 */
data class PharmacySource(
    val label: String = "",
    val url: String = "",
    val confidence: String = "",
    val fetchedAtMs: Long? = null,
    val readable: Boolean = false,
    val manual: Boolean = false,
    val hasManual: Boolean = false,
)

data class PharmacyFeed(
    /** ready, stale, unreadable, none, no-place, unsupported */
    val status: String,
    val source: PharmacySource? = null,
    val estimateFind: Int = 0,
    val estimateManual: Int = 0,
    val today: List<PharmacyShift> = emptyList(),
    val upcoming: List<PharmacyDay> = emptyList(),
    val others: List<PharmacyShift> = emptyList(),
)

internal object PharmacyParser {

    private fun shift(m: Map<*, *>) = PharmacyShift(
        date = m["date"] as? String ?: "",
        name = m["name"] as? String ?: "",
        comune = m["comune"] as? String ?: "",
        address = m["address"] as? String ?: "",
        phone = m["phone"] as? String ?: "",
    )

    private fun shifts(any: Any?): List<PharmacyShift> =
        (any as? List<*>)?.mapNotNull { (it as? Map<*, *>)?.let(::shift) }?.filter { it.name.isNotEmpty() } ?: emptyList()

    fun feed(data: Map<*, *>): PharmacyFeed {
        val source = (data["source"] as? Map<*, *>)?.let {
            PharmacySource(
                label = it["label"] as? String ?: "",
                url = it["url"] as? String ?: "",
                confidence = it["confidence"] as? String ?: "",
                fetchedAtMs = (it["fetchedAtMs"] as? Number)?.toLong(),
                readable = it["readable"] as? Boolean ?: false,
                manual = it["manual"] as? Boolean ?: false,
                hasManual = it["hasManual"] as? Boolean ?: false,
            )
        }
        val estimate = data["estimate"] as? Map<*, *>
        val upcoming = (data["upcoming"] as? List<*>)?.mapNotNull { d ->
            val m = d as? Map<*, *> ?: return@mapNotNull null
            PharmacyDay(m["date"] as? String ?: return@mapNotNull null, shifts(m["items"]))
        } ?: emptyList()
        return PharmacyFeed(
            status = data["status"] as? String ?: "none",
            source = source,
            estimateFind = (estimate?.get("find") as? Number)?.toInt() ?: 0,
            estimateManual = (estimate?.get("manual") as? Number)?.toInt() ?: 0,
            today = shifts(data["today"]),
            upcoming = upcoming,
            others = shifts(data["others"]),
        )
    }
}
