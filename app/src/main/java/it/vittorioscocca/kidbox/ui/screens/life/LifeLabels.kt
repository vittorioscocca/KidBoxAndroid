package it.vittorioscocca.kidbox.ui.screens.life

import android.content.Context
import it.vittorioscocca.kidbox.R

/**
 * Etichette delle sezioni "vita di casa": animali, Casa, garage, scadenze.
 *
 * Ogni etichetta esiste in due forme, e la distinzione è importante:
 *
 * - `*LabelIt` — italiano fisso. Servono SOLO a comporre il prompt di
 *   [it.vittorioscocca.kidbox.ui.screens.ai.planning.PlanningContextBuilder],
 *   dove il testo non è UI e non va tradotto.
 * - le altre, che prendono un [Context] — le uniche da usare a schermo.
 *
 * Le schermate usavano le `*LabelIt` perché erano le uniche esistenti, e così
 * l'UI Android restava in italiano anche con l'app in inglese.
 */

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

/**
 * Etichette UI per le scadenze di casa, gemelle di `KidBoxHousePaymentType`
 * su iOS.
 *
 * Restano separate dalle `*LabelIt` qui sopra perché quelle servono anche a
 * comporre il prompt AI, dove il testo deve restare italiano.
 */
fun housePaymentTypeLabel(context: Context, raw: String): String = when (raw) {
    "mutuo" -> context.getString(R.string.home_items_type_mortgage)
    "affitto" -> context.getString(R.string.home_items_type_rent)
    "bolletta" -> context.getString(R.string.home_items_type_bill)
    "tassa" -> context.getString(R.string.home_items_type_tax)
    "altro" -> context.getString(R.string.home_items_type_other)
    else -> raw
}

/** Sottotipi predefiniti per tipo: `raw` è il valore salvato, non va tradotto. */
fun housePaymentPresetSubtypes(raw: String): List<String> = when (raw) {
    "bolletta" -> listOf("luce", "gas", "internet", "telefono", "acqua", "condominio")
    "tassa" -> listOf("IMU", "TARI", "dichiarazione redditi", "bollo auto", "altre")
    else -> emptyList()
}

/** `true` se [raw] è un sottotipo predefinito di un tipo qualsiasi. */
fun isHousePaymentPresetSubtype(raw: String): Boolean =
    listOf("bolletta", "tassa").any { raw in housePaymentPresetSubtypes(it) }

/**
 * Etichetta di un sottotipo salvato: tradotta se è un preset, altrimenti il
 * testo libero scritto dall'utente.
 */
fun housePaymentSubtypeLabel(context: Context, raw: String): String = when (raw) {
    "luce" -> context.getString(R.string.home_items_electricity)
    "gas" -> context.getString(R.string.home_items_gas)
    "internet" -> context.getString(R.string.home_items_internet)
    "telefono" -> context.getString(R.string.home_items_phone)
    "acqua" -> context.getString(R.string.home_items_water)
    "condominio" -> context.getString(R.string.home_items_condo)
    "IMU" -> "IMU"
    "TARI" -> "TARI"
    "dichiarazione redditi" -> context.getString(R.string.home_items_tax_return)
    "bollo auto" -> context.getString(R.string.home_items_car_tax)
    "altre" -> context.getString(R.string.home_items_others)
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

/**
 * Varianti UI delle `*LabelIt` qui sopra.
 *
 * Quelle restano in italiano perché servono a comporre il prompt AI; queste
 * sono le uniche da usare a schermo.
 */
fun speciesLabel(context: Context, species: String): String = when (species.lowercase()) {
    "cane" -> context.getString(R.string.life_pet_species_dog)
    "gatto" -> context.getString(R.string.life_pet_species_cat)
    "coniglio" -> context.getString(R.string.life_pet_species_rabbit)
    "criceto" -> context.getString(R.string.life_pet_species_hamster)
    "uccello" -> context.getString(R.string.life_pet_species_bird)
    "altro" -> context.getString(R.string.life_pet_species_other)
    else -> species
}

fun petEventTypeLabel(context: Context, raw: String): String = when (raw) {
    "vaccine" -> context.getString(R.string.life_pet_event_vaccine)
    "vet_visit" -> context.getString(R.string.life_pet_event_vet_visit)
    "medication" -> context.getString(R.string.life_pet_event_medication)
    "grooming" -> context.getString(R.string.life_pet_event_grooming)
    "other" -> context.getString(R.string.life_pet_event_other)
    else -> raw
}

/** Intestazioni di sezione in Casa: plurali, distinte da `home_items_cat_*`. */
fun homeCategoryLabel(context: Context, raw: String): String = when (raw) {
    "appliance" -> context.getString(R.string.home_items_cat_appliance_plural)
    "system" -> context.getString(R.string.home_items_cat_system_plural)
    "contract" -> context.getString(R.string.home_items_cat_contract_plural)
    "other" -> context.getString(R.string.home_items_cat_other_plural)
    else -> raw
}

fun vehicleFuelLabel(context: Context, raw: String?): String = when (raw) {
    "benzina" -> context.getString(R.string.vehicles_fuel_petrol)
    "diesel" -> context.getString(R.string.vehicles_fuel_diesel)
    "elettrica" -> context.getString(R.string.vehicles_fuel_electric)
    "ibrida" -> context.getString(R.string.vehicles_fuel_hybrid)
    "gpl" -> context.getString(R.string.vehicles_fuel_lpg)
    null, "" -> "—"
    else -> raw
}

fun vehicleEventTypeLabel(context: Context, raw: String): String = when (raw) {
    "service" -> context.getString(R.string.vehicles_event_service)
    "oil_filter" -> context.getString(R.string.vehicles_event_oil_filter)
    "gpl_filter" -> context.getString(R.string.vehicles_event_gpl_filter)
    "brake_pads" -> context.getString(R.string.vehicles_event_brake_pads)
    "repair" -> context.getString(R.string.vehicles_event_repair)
    "tire" -> context.getString(R.string.vehicles_event_tire)
    "revision" -> context.getString(R.string.vehicles_event_revision)
    "other" -> context.getString(R.string.vehicles_event_other)
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
