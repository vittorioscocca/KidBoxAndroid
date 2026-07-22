package it.vittorioscocca.kidbox.ui.screens.grocery

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import it.vittorioscocca.kidbox.R

/**
 * Categorie della lista della spesa.
 *
 * La categoria viene persistita (Room + Firestore) come **chiave stabile**, non come
 * etichetta tradotta: altrimenti l'etichetta salvata resterebbe nella lingua di chi ha
 * creato il prodotto e due membri con lingue diverse creerebbero due categorie distinte
 * per la stessa cosa, spezzando il raggruppamento.
 *
 * [fromStored] riconosce anche le vecchie etichette italiane già salvate sui dispositivi,
 * così i dati esistenti continuano a raggrupparsi correttamente.
 */
enum class GroceryCategory(val key: String, @StringRes val labelRes: Int) {
    PRODUCE("produce", R.string.grocery_cat_produce),
    MEAT_FISH("meat_fish", R.string.grocery_cat_meat_fish),
    DAIRY("dairy", R.string.grocery_cat_dairy),
    BAKERY("bakery", R.string.grocery_cat_bakery),
    FROZEN("frozen", R.string.grocery_cat_frozen),
    DRINKS("drinks", R.string.grocery_cat_drinks),
    OTHER("other", R.string.home_items_cat_other),
    ;

    companion object {
        /** Etichette storiche salvate prima dell'introduzione delle chiavi. */
        private val legacyItalianLabels: Map<String, GroceryCategory> = mapOf(
            "frutta e verdura" to PRODUCE,
            "carne e pesce" to MEAT_FISH,
            "latticini" to DAIRY,
            "pane e cereali" to BAKERY,
            "surgelati" to FROZEN,
            "bevande" to DRINKS,
            "altro" to OTHER,
        )

        fun fromStored(raw: String?): GroceryCategory? {
            val value = raw?.trim()?.lowercase().orEmpty()
            if (value.isEmpty()) return null
            return entries.firstOrNull { it.key == value } ?: legacyItalianLabels[value]
        }
    }
}

/**
 * Etichetta localizzata per una categoria salvata. Le categorie personalizzate scritte
 * a mano dall'utente non sono traducibili e vengono mostrate così come sono.
 */
@Composable
fun groceryCategoryLabel(stored: String?): String {
    val known = GroceryCategory.fromStored(stored)
    return if (known != null) {
        stringResource(known.labelRes)
    } else {
        stored?.trim().orEmpty().ifEmpty { stringResource(R.string.home_items_cat_other) }
    }
}
