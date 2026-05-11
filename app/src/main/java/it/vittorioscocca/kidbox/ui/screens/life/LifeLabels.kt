package it.vittorioscocca.kidbox.ui.screens.life

fun speciesEmoji(species: String): String = when (species.lowercase()) {
    "cane" -> "🐕"
    "gatto" -> "🐈"
    "coniglio" -> "🐇"
    "criceto" -> "🐹"
    "uccello" -> "🐦"
    else -> "🐾"
}

fun speciesLabelIt(species: String): String = when (species.lowercase()) {
    "cane" -> "Cane"
    "gatto" -> "Gatto"
    "coniglio" -> "Coniglio"
    "criceto" -> "Criceto"
    "uccello" -> "Uccello"
    "altro" -> "Altro"
    else -> species
}

fun petEventTypeLabelIt(raw: String): String = when (raw) {
    "vaccine" -> "Vaccino"
    "vet_visit" -> "Visita veterinaria"
    "medication" -> "Farmaco"
    "grooming" -> "Toelettatura"
    "other" -> "Altro"
    else -> raw
}

fun homeCategoryLabelIt(raw: String): String = when (raw) {
    "appliance" -> "Elettrodomestici"
    "system" -> "Impianti"
    "contract" -> "Contratti"
    "other" -> "Altro"
    else -> raw
}

fun housePaymentTypeLabelIt(raw: String): String = when (raw) {
    "mutuo" -> "Mutuo"
    "affitto" -> "Affitto"
    "bolletta" -> "Bolletta"
    "tassa" -> "Tassa"
    "altro" -> "Altro"
    else -> raw
}

fun vehicleFuelLabelIt(raw: String?): String = when (raw) {
    "benzina" -> "Benzina"
    "diesel" -> "Diesel"
    "elettrica" -> "Elettrica"
    "ibrida" -> "Ibrida"
    "gpl" -> "GPL"
    null, "" -> "—"
    else -> raw
}

fun vehicleEventTypeLabelIt(raw: String): String = when (raw) {
    "service" -> "Tagliando"
    "oil_filter" -> "Filtro olio"
    "gpl_filter" -> "Filtro GPL"
    "brake_pads" -> "Pasticche freni"
    "repair" -> "Riparazione"
    "tire" -> "Gomme"
    "revision" -> "Revisione"
    "other" -> "Altro"
    else -> raw
}

/** Emoji per riga storico interventi (stile iOS simbolo + tint arancione). */
fun vehicleEventTypeEmoji(raw: String): String = when (raw.lowercase()) {
    "service" -> "🔧"
    "oil_filter" -> "🛢️"
    "gpl_filter" -> "⛽"
    "brake_pads" -> "🛑"
    "repair" -> "🔩"
    "tire" -> "🛞"
    "revision" -> "📋"
    "other" -> "📝"
    else -> "🚗"
}
