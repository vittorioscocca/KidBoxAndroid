package it.vittorioscocca.kidbox.ui.screens.life

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

/**
 * Etichetta specie localizzata, per la UI.
 *
 * Le funzioni `*LabelIt` sotto restano in italiano di proposito: sono usate anche da
 * [it.vittorioscocca.kidbox.ui.screens.ai.planning.PlanningContextBuilder] per comporre
 * il prompt inviato al modello, dove il testo non è UI e non va tradotto.
 */
@Composable
fun speciesLabel(species: String): String = when (species.lowercase()) {
    "cane" -> stringResource(R.string.life_pet_species_dog)
    "gatto" -> stringResource(R.string.life_pet_species_cat)
    "coniglio" -> stringResource(R.string.life_pet_species_rabbit)
    "criceto" -> stringResource(R.string.life_pet_species_hamster)
    "uccello" -> stringResource(R.string.life_pet_species_bird)
    "altro" -> stringResource(R.string.life_pet_species_other)
    else -> species
}

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
